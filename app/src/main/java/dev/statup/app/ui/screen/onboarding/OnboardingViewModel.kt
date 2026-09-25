package dev.statup.app.ui.screen.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.statup.app.data.local.datastore.UserPreferences
import dev.statup.app.data.repository.PlayerRepository
import kotlinx.coroutines.launch

/**
 * Drives the first-run onboarding. Completing it persists the chosen name (if any) and flips the
 * `onboardingComplete` flag - which AppNavigation observes to swap from the onboarding screen to
 * the main shell.
 */
class OnboardingViewModel(
    private val userPreferences: UserPreferences,
    private val playerRepository: PlayerRepository
) : ViewModel() {

    fun complete(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) playerRepository.setUsername(trimmed)
            userPreferences.setOnboardingComplete(true)
        }
    }
}
