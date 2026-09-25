package dev.statup.app.ai.classifier

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measures the real on-device cost of a stat guess, under ART, reading the blob from assets
 * exactly as the app does. Not a correctness test - `HashedLinearTaskClassifierTest` covers
 * that - this exists to keep the classifier honest about being cheap enough to run on the
 * keystroke path of a mid-range phone.
 */
class ClassifierBenchmarkTest {

    private val samples = listOf(
        "go for a 5k run in the morning",
        "Read the GIS spatial analysis guide",
        "call mom about the weekend plans",
        "pay the electricity bill before friday",
        "practice guitar scales for 30 mins",
        "book the dentist appointment"
    )

    @Test
    fun benchmarkClassification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val load = { context.assets.open("classifier/stat_clf_v1.bin").use { it.readBytes() } }

        // Cold: includes reading 96 KB off disk and parsing the header + weights.
        val coldStart = System.nanoTime()
        val classifier = HashedLinearTaskClassifier(load, threshold = 0f)
        classifier.classify(samples[0])
        val coldMs = (System.nanoTime() - coldStart) / 1_000_000.0

        // Warm: steady-state cost of one guess, which is what a keystroke pays.
        repeat(200) { samples.forEach { classifier.classify(it) } }   // let ART JIT settle
        val iterations = 500
        val warmStart = System.nanoTime()
        repeat(iterations) { samples.forEach { classifier.classify(it) } }
        val perCallMs = (System.nanoTime() - warmStart) / 1_000_000.0 / (iterations * samples.size)

        println("CLASSIFIER_BENCH cold_load_and_first_call_ms=%.2f".format(coldMs))
        println("CLASSIFIER_BENCH warm_per_call_ms=%.4f".format(perCallMs))
        println("CLASSIFIER_BENCH calls_per_second=%.0f".format(1000.0 / perCallMs))

        assertTrue("a guess must stay far under one frame (16.7ms)", perCallMs < 16.0)
    }
}
