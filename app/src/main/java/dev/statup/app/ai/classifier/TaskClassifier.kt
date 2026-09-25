package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType

/** A guessed stat plus how sure the model is, in `0f..1f`. */
data class StatSuggestion(val stat: StatType, val confidence: Float)

/**
 * Guesses which stat a task belongs to, fully offline.
 *
 * Returns `null` whenever the model is not confident enough — callers must then behave exactly
 * as they did before the classifier existed. That keeps this strictly additive: it can only
 * improve on the status quo, never regress it.
 */
interface TaskClassifier {
    fun classify(text: String): StatSuggestion?

    companion object {
        /**
         * Minimum confidence to offer a guess at all. Measured on the 300-row held-out set:
         * 0.55 covers 87% of tasks at 89.3% precision (0.65 would be 92.2% precise but only
         * cover 76.7%). One number for every call site — the uncovered tasks fall back to the
         * picker's default, which is what happens today.
         */
        const val CONFIDENCE_THRESHOLD = 0.55f
    }
}
