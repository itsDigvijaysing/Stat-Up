package dev.statup.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.statup.app.ai.classifier.StatSuggestion
import dev.statup.app.ai.classifier.TaskClassifier
import androidx.compose.runtime.collectAsState
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.repository.PointsRepository
import dev.statup.app.domain.model.StatType
import dev.statup.app.ui.theme.AccentPrimary
import dev.statup.app.ui.theme.Inter
import dev.statup.app.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Debounced offline guess at which stat [text] belongs to; returns `null` while typing, when too
 * short, or when the model isn't confident - the caller then keeps whatever the picker already had.
 */
@Composable
fun rememberStatSuggestion(text: String): StatSuggestion? {
    val classifier = koinInject<TaskClassifier>()
    val userPreferences = koinInject<UserPreferences>()
    // Off means off: the classifier is never called, so its asset is never even loaded.
    val enabled by userPreferences.autoCategorise.collectAsState(initial = true)
    var suggestion by remember { mutableStateOf<StatSuggestion?>(null) }

    LaunchedEffect(text, enabled) {
        val trimmed = text.trim()
        if (!enabled || trimmed.length < MIN_CHARS_TO_CLASSIFY) {
            suggestion = null
            return@LaunchedEffect
        }
        // Debounce: re-scoring on every keystroke is wasted work and makes the chip flicker
        // between stats while a sentence is still being typed.
        delay(DEBOUNCE_MS)
        suggestion = withContext(Dispatchers.Default) { classifier.classify(trimmed) }
    }

    return suggestion
}

/** The user's configured default stat, used to seed a picker before any guess arrives. */
@Composable
fun rememberDefaultStat(): StatType {
    val pointsRepository = koinInject<PointsRepository>()
    var stat by remember { mutableStateOf(StatType.STR) }
    LaunchedEffect(Unit) { stat = pointsRepository.getDefaultStat() }
    return stat
}

/** One caption line under a stat picker: what the stat means, plus a marker if the app chose it. */
@Composable
fun StatPickerCaption(
    stat: StatType,
    isSuggested: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isSuggested) {
            Text(
                text = "✨ Suggested - tap another to change",
                color = AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = Inter
            )
        }
        Text(
            text = "${stat.displayName}: ${stat.blurb}",
            color = TextTertiary,
            fontSize = 11.sp,
            fontFamily = Inter
        )
    }
}

private const val DEBOUNCE_MS = 300L
// Below three words the training set measured 35-42% accurate, so short text is not scored.
private const val MIN_CHARS_TO_CLASSIFY = 8
