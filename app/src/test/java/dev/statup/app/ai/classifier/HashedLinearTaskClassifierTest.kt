package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the Kotlin feature-extraction + scoring contract against the Python trainer.
 *
 * The expected probabilities were produced by `scripts/train_stat_classifier.py`'s own
 * `featurise()`/`softmax()` running on the **committed** blob with its int8 weights
 * dequantised — i.e. exactly the arithmetic the app performs. Any drift in tokenisation,
 * n-gram ranges, hashing, weighting or normalisation moves these numbers well outside the
 * tolerance, which is the whole point: a silent featurisation mismatch scores garbage while
 * still looking like a working classifier.
 *
 * Two fixtures are deliberate model misses and three sit below the confidence threshold —
 * this asserts model OUTPUT, never model correctness.
 */
class HashedLinearTaskClassifierTest {

    private val modelFile = File("src/main/assets/classifier/stat_clf_v1.bin")

    /** No threshold, so every fixture can be compared including the low-confidence ones. */
    private fun scorer() = HashedLinearTaskClassifier({ modelFile.readBytes() }, threshold = 0f)

    private data class Fixture(val text: String, val expected: DoubleArray, val argmax: StatType)

    // order: STR, INT, WIS, DEX, CHA, VIT
    private val fixtures = listOf(
        Fixture(
            "Rowing machine 2000 m today",
            doubleArrayOf(0.8722008823809495, 0.00847327367287952, 0.01682721125464838, 0.004827710088642602, 0.026631769721689284, 0.07103915288119068),
            StatType.STR
        ),
        Fixture(
            "Crossfit wod at the box today",
            doubleArrayOf(0.371986065562834, 0.02032866156237692, 0.0893577274146248, 0.006238942147892144, 0.028381717767208187, 0.4837068855450639),
            StatType.VIT
        ),
        Fixture(
            "Read the GIS spatial analysis guide",
            doubleArrayOf(0.0009406889011194349, 0.9805216853565637, 0.013938710411981492, 0.0005859140358945737, 0.0007252286315224291, 0.003287772662918518),
            StatType.INT
        ),
        Fixture(
            "Prepare for CFA level one",
            doubleArrayOf(0.16654192977228738, 0.5056001345624178, 0.06118147832062399, 0.008992521488607257, 0.2068622976778699, 0.050821638178193754),
            StatType.INT
        ),
        Fixture(
            "Track the pf withdrawal request",
            doubleArrayOf(0.026169920536023367, 0.24081300423984425, 0.4475936016306629, 0.00647939247247836, 0.015947362898526905, 0.26299671822246423),
            StatType.WIS
        ),
        Fixture(
            "Question whether this goal still matters",
            doubleArrayOf(0.04440893682045629, 0.39789008427879075, 0.36023735670866897, 0.006747110259410899, 0.17617492917930166, 0.014541582753371468),
            StatType.INT
        ),
        Fixture(
            "Do the screen printing squeegee practice",
            doubleArrayOf(0.0005575870476158346, 0.00016294467839436305, 0.0013699119428070379, 0.9969019307301252, 4.885755791302568e-05, 0.0009587680431447488),
            StatType.DEX
        ),
        Fixture(
            "Complain to the customer care executive politely and get my issue solved",
            doubleArrayOf(0.06962880243052974, 0.02239764934245961, 0.13162494952817194, 0.003734459231292757, 0.7492375314721078, 0.02337660799543805),
            StatType.CHA
        ),
        Fixture(
            "Teach kids at the NGO on sundey",
            doubleArrayOf(0.15785905845081272, 0.042720034908158976, 0.051604875271760345, 0.020366444713806702, 0.5118795768819532, 0.21557000977350818),
            StatType.CHA
        ),
        Fixture(
            "Book the ecg test appointment",
            doubleArrayOf(0.0021655588580243553, 0.10561660351187226, 0.10469149196802205, 0.002567971358850508, 0.015447379768111042, 0.7695109945351197),
            StatType.VIT
        ),
    )

    @Test
    fun `kotlin scores reproduce the python scores on every fixture`() {
        val classifier = scorer()
        for (f in fixtures) {
            val result = classifier.classify(f.text)
            assertNotNull("no suggestion for '${f.text}'", result)
            assertEquals("argmax for '${f.text}'", f.argmax, result!!.stat)
            assertEquals(
                "confidence for '${f.text}'",
                f.expected.max().toFloat(),
                result.confidence,
                1e-4f
            )
        }
    }

    @Test
    fun `low confidence returns null at the shipped threshold`() {
        val gated = HashedLinearTaskClassifier({ modelFile.readBytes() })
        // Python confidence 0.4476 — under the 0.55 bar, so the app must stay silent.
        assertNull(gated.classify("Track the pf withdrawal request"))
        // Python confidence 0.9805 — comfortably over it.
        assertNotNull(gated.classify("Read the GIS spatial analysis guide"))
    }

    @Test
    fun `blank and unparseable input degrade to null instead of throwing`() {
        assertNull(scorer().classify("   "))
        assertNull(HashedLinearTaskClassifier({ ByteArray(0) }).classify("go for a run"))
        assertNull(HashedLinearTaskClassifier({ error("asset missing") }).classify("go for a run"))
    }

    @Test
    fun `repeated words accumulate rather than being deduplicated`() {
        // A set-of-hashes implementation would score these two identically.
        val classifier = scorer()
        val once = classifier.classify("run")!!.confidence
        val twice = classifier.classify("run run")!!.confidence
        assertTrue("repeating a word must change the score", once != twice)
    }
}
