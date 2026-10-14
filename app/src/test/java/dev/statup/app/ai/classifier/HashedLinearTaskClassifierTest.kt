package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pins the Kotlin feature-extraction + scoring contract against probabilities the Python
 * trainer computed on the same committed, dequantised blob - catches silent featurisation drift. */
class HashedLinearTaskClassifierTest {

    private val modelFile = File("src/main/assets/classifier/stat_clf_v1.bin")

    /** No threshold, so every fixture can be compared including the low-confidence ones. */
    private fun scorer() = HashedLinearTaskClassifier({ modelFile.readBytes() }, threshold = 0f)

    private data class Fixture(val text: String, val expected: DoubleArray, val argmax: StatType)

    // order: STR, INT, WIS, DEX, CHA, VIT
    private val fixtures = listOf(
        Fixture(
            "Rowing machine 2000 m today",
            doubleArrayOf(0.8321853591113075, 0.017550766756106187, 0.04783617339123768, 0.010899843608865962, 0.020990757855273687, 0.07053709927720912),
            StatType.STR
        ),
        Fixture(
            "Crossfit wod at the box today",
            doubleArrayOf(0.3286911906528278, 0.018405805249912945, 0.09842325208273338, 0.007957894532695013, 0.02550265240864627, 0.5210192050731846),
            StatType.VIT
        ),
        Fixture(
            "Read the GIS spatial analysis guide",
            doubleArrayOf(0.001411484430844971, 0.9683438249429682, 0.023260361924660564, 0.0012093591610465135, 0.0009023701526530855, 0.004872599387826878),
            StatType.INT
        ),
        Fixture(
            "Prepare for CFA level one",
            doubleArrayOf(0.11996440850187293, 0.619351774793513, 0.13876883248776273, 0.014404657606202064, 0.0778712594208971, 0.029639067189752188),
            StatType.INT
        ),
        Fixture(
            "Track the pf withdrawal request",
            doubleArrayOf(0.013084586864562552, 0.09922983532290883, 0.7875950237561973, 0.004800092919437214, 0.006711998725344264, 0.08857846241154996),
            StatType.WIS
        ),
        Fixture(
            "Question whether this goal still matters",
            doubleArrayOf(0.06341965228499129, 0.3221038802361036, 0.4418936981467959, 0.00914790033134322, 0.1482468758706245, 0.01518799313014135),
            StatType.WIS
        ),
        Fixture(
            "Do the screen printing squeegee practice",
            doubleArrayOf(0.0007459223636815584, 0.0003477735994394511, 0.001551719519897863, 0.9962274475382439, 4.737206533494686e-05, 0.0010797649134024084),
            StatType.DEX
        ),
        Fixture(
            "Complain to the customer care executive politely and get my issue solved",
            doubleArrayOf(0.04253536359212082, 0.01658747071635548, 0.052174257171632266, 0.0025692552580604553, 0.8701005435399888, 0.016033109721842236),
            StatType.CHA
        ),
        Fixture(
            "Teach kids at the NGO on sundey",
            doubleArrayOf(0.15006635460596096, 0.0643031055920363, 0.035411768951310874, 0.025377885484890915, 0.46946884794092586, 0.25537203742487496),
            StatType.CHA
        ),
        Fixture(
            "Book the ecg test appointment",
            doubleArrayOf(0.00135611121635402, 0.14610102790310775, 0.17322371236635792, 0.002765842712108685, 0.010663683602796174, 0.6658896221992755),
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
        // Python confidence 0.4419 - under the 0.55 bar, so the app must stay silent.
        assertNull(gated.classify("Question whether this goal still matters"))
        // Python confidence 0.9683 - comfortably over it.
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
