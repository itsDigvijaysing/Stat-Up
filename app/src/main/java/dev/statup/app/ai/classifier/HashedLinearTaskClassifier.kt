package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Multinomial logistic regression over hashed character n-grams plus word uni/bigrams — the
 * fastText shape, trained by `scripts/train_stat_classifier.py` and shipped as a 96 KB int8
 * blob in `assets/classifier/`.
 *
 * Takes [modelBytes] rather than a `Context` (the narrow-port style of `DecayEngine`) so the
 * scoring path is JVM-testable with no Android dependency. The blob is parsed on first use and
 * held for the process lifetime; scoring is sub-millisecond.
 *
 * **Feature extraction must match the trainer byte for byte** or every score is garbage.
 * `HashedLinearTaskClassifierTest` pins it against probabilities computed in Python. The
 * non-obvious parts, each verified against `featurise()`:
 *  - weights ACCUMULATE (`+=`); a repeated word or an n-gram collision adds up, so this can
 *    never be a set-of-hashes implementation
 *  - char n-grams are taken inside each space-padded word only, never across word boundaries,
 *    and a word shorter than `n - 2` simply contributes nothing at that `n`
 *  - [String.lowercase] is called without a locale on purpose: the locale-sensitive overload
 *    maps `I` to `ı` under a Turkish locale, which would corrupt every score on those devices
 *  - Python's `str.split()` also splits on non-breaking space, which Java's `\s` does not
 *  - CRC32 is unsigned; `CRC32.getValue()` returns a `Long`, and taking the modulus there
 *    avoids the negative bucket a naive `Int` modulus would produce
 *  - the bias vector is stored un-quantised — it is added raw, never multiplied by the scale
 */
class HashedLinearTaskClassifier(
    private val modelBytes: () -> ByteArray,
    private val threshold: Float = TaskClassifier.CONFIDENCE_THRESHOLD
) : TaskClassifier {

    private val model: Model? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { parse(modelBytes()) }.getOrNull()
    }

    override fun classify(text: String): StatSuggestion? {
        val m = model ?: return null
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null

        val features = featurise(tokens, m.buckets)
        val probabilities = softmax(scores(features, m))

        var best = 0
        for (i in 1 until m.classes) if (probabilities[i] > probabilities[best]) best = i
        val confidence = probabilities[best]
        if (confidence < threshold) return null

        val stat = StatType.entries.getOrNull(best) ?: return null
        return StatSuggestion(stat, confidence)
    }

    // ---- feature extraction (mirrors featurise() in the trainer) ----

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(*WHITESPACE).filter { it.isNotEmpty() }

    private fun featurise(tokens: List<String>, buckets: Int): FloatArray {
        val v = FloatArray(buckets)

        for (token in tokens) {
            val padded = " $token "
            for (n in NGRAM_MIN..NGRAM_MAX) {
                for (i in 0..padded.length - n) {
                    v[bucket(padded.substring(i, i + n), buckets)] += 1f
                }
            }
        }
        for (token in tokens) {
            v[bucket("W#$token", buckets)] += WORD_WEIGHT
        }
        for (i in 0 until tokens.size - 1) {
            v[bucket("W#${tokens[i]}_${tokens[i + 1]}", buckets)] += WORD_WEIGHT
        }

        var sumSquares = 0.0
        for (value in v) sumSquares += (value * value).toDouble()
        val norm = sqrt(sumSquares).toFloat()
        // Zero guard: whitespace-only input yields the all-zero vector rather than NaN, and
        // then scores on pure bias.
        if (norm > 0f) for (i in v.indices) v[i] /= norm
        return v
    }

    private fun bucket(feature: String, buckets: Int): Int {
        val crc = CRC32()
        crc.update(feature.toByteArray(Charsets.UTF_8))
        return (crc.value % buckets).toInt()
    }

    // ---- scoring ----

    private fun scores(features: FloatArray, m: Model): FloatArray {
        val out = FloatArray(m.classes)
        for (c in 0 until m.classes) {
            var acc = 0f
            val offset = c * m.buckets
            for (b in 0 until m.buckets) {
                val f = features[b]
                if (f != 0f) acc += f * m.weights[offset + b] * m.scale
            }
            out[c] = acc + m.bias[c]
        }
        return out
    }

    private fun softmax(z: FloatArray): FloatArray {
        val max = z.max()
        var sum = 0f
        val e = FloatArray(z.size) { exp((z[it] - max).toDouble()).toFloat() }
        for (value in e) sum += value
        for (i in e.indices) e[i] /= sum
        return e
    }

    // ---- blob parsing ----

    private class Model(
        val buckets: Int,
        val classes: Int,
        val scale: Float,
        val bias: FloatArray,
        /** int8 weights laid out `[class][bucket]`, i.e. flat index `class * buckets + bucket`. */
        val weights: ByteArray
    )

    private fun parse(bytes: ByteArray): Model {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }
        require(magic.contentEquals(MAGIC)) { "not a Stat Up classifier blob" }

        val version = buffer.short.toInt() and 0xFFFF
        require(version == SUPPORTED_VERSION) { "unsupported classifier version $version" }
        val buckets = buffer.short.toInt() and 0xFFFF
        val classes = buffer.short.toInt() and 0xFFFF
        require(classes == StatType.entries.size) { "model has $classes classes, app has ${StatType.entries.size}" }

        val scale = buffer.float
        val bias = FloatArray(classes) { buffer.float }
        val weights = ByteArray(buckets * classes).also { buffer.get(it) }
        return Model(buckets, classes, scale, bias, weights)
    }

    private companion object {
        val MAGIC = "STCL".toByteArray(Charsets.US_ASCII)
        const val SUPPORTED_VERSION = 1
        const val NGRAM_MIN = 3
        const val NGRAM_MAX = 6
        const val WORD_WEIGHT = 2f
        // Python's str.split() treats these as separators; Java's \s covers only the first four.
        val WHITESPACE = charArrayOf(
            ' ', '\t', '\n', '\r', '\u000B', '\u000C', '\u001C', '\u001D', '\u001E', '\u001F',
            ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
            ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
            ' ', '　'
        )
    }
}
