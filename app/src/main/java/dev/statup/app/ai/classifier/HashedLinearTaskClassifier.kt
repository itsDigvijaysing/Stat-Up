package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Feature extraction must exactly match `scripts/train_stat_classifier.py` or every score is
 * garbage (pinned by `HashedLinearTaskClassifierTest`). Non-obvious traps: `lowercase()` has no
 * Locale (avoids the Turkish `I`->`ı` mapping), CRC32's `Long` value is modulused before the
 * `Int` cast (avoids a negative bucket), and weights accumulate via `+=`, never overwrite.
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

    /**
     * Hands back only the touched buckets (a few hundred of 16384), not the full dense vector.
     * L2 normalisation is deferred to scoring - dividing the dot product once per class is
     * equivalent and skips a full pass over the array.
     */
    private fun featurise(tokens: List<String>, buckets: Int): Features {
        val dense = FloatArray(buckets)
        // Per-call scratch arrays, not shared: this classifier is a Koin singleton scored concurrently
        // by both stat pickers, Todoist sync, and category backfill - shared buffers would corrupt scores.
        val indices = IntArray(buckets)
        var count = 0
        fun bump(bucket: Int, by: Float) {
            if (dense[bucket] == 0f) indices[count++] = bucket
            dense[bucket] += by
        }

        for (token in tokens) {
            val padded = " $token "
            // Byte-range hashing needs one char == one byte, so non-ASCII falls back to the slow
            // substring path - Python slices n-grams by character, and byte offsets wouldn't match.
            val asciiBytes = padded.asciiBytesOrNull()
            for (n in NGRAM_MIN..NGRAM_MAX) {
                for (i in 0..padded.length - n) {
                    val bucket = if (asciiBytes != null) {
                        bucketOf(asciiBytes, i, n, buckets)
                    } else {
                        bucketOf(padded.substring(i, i + n), buckets)
                    }
                    bump(bucket, 1f)
                }
            }
        }
        for (token in tokens) {
            bump(bucketOf("W#$token", buckets), WORD_WEIGHT)
        }
        for (i in 0 until tokens.size - 1) {
            bump(bucketOf("W#${tokens[i]}_${tokens[i + 1]}", buckets), WORD_WEIGHT)
        }

        // Norm over the touched buckets only - same result as the full scan, since every untouched
        // slot contributes zero.
        var sumSquares = 0.0
        for (i in 0 until count) {
            val v = dense[indices[i]]
            sumSquares += (v * v).toDouble()
        }
        return Features(dense, indices, count, sqrt(sumSquares).toFloat())
    }

    private class Features(
        val values: FloatArray,
        val indices: IntArray,
        val size: Int,
        val norm: Float
    )

    private fun String.asciiBytesOrNull(): ByteArray? {
        val out = ByteArray(length)
        for (i in indices) {
            val c = this[i]
            if (c.code > 0x7F) return null
            out[i] = c.code.toByte()
        }
        return out
    }

    private fun bucketOf(bytes: ByteArray, offset: Int, length: Int, buckets: Int): Int {
        val crc = CRC32()
        crc.update(bytes, offset, length)
        return (crc.value % buckets).toInt()
    }

    private fun bucketOf(feature: String, buckets: Int): Int {
        val crc = CRC32()
        crc.update(feature.toByteArray(Charsets.UTF_8))
        return (crc.value % buckets).toInt()
    }

    // ---- scoring ----

    /**
     * Sparse dot product, with the L2 normalisation folded in as a single divide per class.
     * Walking the full weight matrix would do ~98k multiply-adds to read ~2k useful ones.
     */
    private fun scores(features: Features, m: Model): FloatArray {
        val out = FloatArray(m.classes)
        // A blank or whitespace-only input normalises to nothing; scoring it on pure bias
        // matches the trainer's zero guard.
        val inverseNorm = if (features.norm > 0f) 1f / features.norm else 0f
        for (c in 0 until m.classes) {
            var acc = 0f
            val offset = c * m.buckets
            for (i in 0 until features.size) {
                val b = features.indices[i]
                acc += features.values[b] * m.weights[offset + b]
            }
            // One multiply by the quantisation scale per class rather than per weight; the bias
            // is stored un-quantised, so it is added after.
            out[c] = acc * inverseNorm * m.scale + m.bias[c]
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
            '\u00A0', '\u1680', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005',
            '\u2006', '\u2007', '\u2008', '\u2009', '\u200A', '\u2028', '\u2029', '\u202F',
            '\u205F', '\u3000'
        )
    }
}
