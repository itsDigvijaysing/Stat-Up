package dev.statup.app.data.local.datastore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the defect where `tutorial_complete`/`starter_content_seeded` both default to
 * false, indistinguishable from "predates them" - every updating user looked new.
 */
class FirstRunPolicyTest {

    @Test
    fun `a fresh install is a first run`() {
        assertTrue(FirstRunPolicy.isFirstRun(isFreshInstall = true, onboardingComplete = null))
    }

    @Test
    fun `an install that has completed onboarding is not a first run`() {
        // The pre-v4 population: they must not be handed starter content or a walkthrough.
        assertFalse(FirstRunPolicy.isFirstRun(isFreshInstall = false, onboardingComplete = true))
    }

    @Test
    fun `installed but never opened, then updated, is still a first run`() {
        // Install timestamps have diverged, so isFreshInstall is false - but this user has seen
        // nothing at all, and denying them the tour would be the opposite mistake.
        assertTrue(FirstRunPolicy.isFirstRun(isFreshInstall = false, onboardingComplete = null))
        assertTrue(FirstRunPolicy.isFirstRun(isFreshInstall = false, onboardingComplete = false))
    }

    @Test
    fun `a fresh install that somehow already onboarded is still a first run`() {
        // Reinstall after uninstall: timestamps match, and whatever the flag says, the tour is right.
        assertTrue(FirstRunPolicy.isFirstRun(isFreshInstall = true, onboardingComplete = true))
    }
}
