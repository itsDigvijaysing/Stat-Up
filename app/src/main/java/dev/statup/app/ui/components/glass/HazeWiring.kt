package dev.statup.app.ui.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Threaded down from [dev.statup.app.ui.navigation.AppNavigation] so glass components get
 * blur without knowing about Haze; null (Preview, non-wired screen) falls through to no-op. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Apply [hazeEffect] when a [HazeState] is available in the local; otherwise no-op. */
@OptIn(ExperimentalHazeApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.hazeEffectOrFallback(elevated: Boolean = false): Modifier {
    val state = LocalHazeState.current ?: return this
    return this.hazeEffect(
        state = state,
        style = if (elevated) HazeMaterials.thin() else HazeMaterials.ultraThin()
    )
}

/** Apply [hazeSource] when a [HazeState] is available in the local; otherwise no-op. */
@Composable
fun Modifier.hazeSourceOrFallback(): Modifier {
    val state = LocalHazeState.current ?: return this
    return this.hazeSource(state = state)
}
