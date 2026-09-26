package dev.statup.app.ai.classifier

import dev.statup.app.domain.model.StatType

/** A guessed stat plus how sure the model is, in `0f..1f`. */
data class StatSuggestion(val stat: StatType, val confidence: Float)

/**
 * Guesses which stat a task belongs to, fully offline. Returns `null` when not confident enough -
 * callers must then behave exactly as before the classifier existed, so this can only improve, never regress.
 */
interface TaskClassifier {
    fun classify(text: String): StatSuggestion?

    companion object {
        /**
         * Minimum confidence to offer a guess - 0.55 covers 87% of tasks at 89.3% precision on the
         * held-out set (0.65 would hit 92.2% precision but only cover 76.7%). One threshold for every call site.
         */
        const val CONFIDENCE_THRESHOLD = 0.55f
    }
}
