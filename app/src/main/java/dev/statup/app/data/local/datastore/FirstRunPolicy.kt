package dev.statup.app.data.local.datastore

/**
 * The single rule for "is this a brand-new user?".
 *
 * Lives in its own file, free of Android types, because it decides whether someone gets the guided
 * tour and a set of starter missions and rewards - and getting it wrong in either direction is
 * user-visible. Wrong one way, an updating user is handed 13 sample items and a walkthrough they
 * never asked for; wrong the other way, a genuinely new user is denied the tour.
 */
object FirstRunPolicy {

    /**
     * @param isFreshInstall from `firstInstallTime == lastUpdateTime`.
     * @param onboardingComplete the stored flag, or null if it has never been written.
     *
     * The `onboardingComplete` term exists because [isFreshInstall] alone is wrong for one real case:
     * someone installs from the store, never opens the app, then takes an update and opens it for the
     * first time. Their install timestamps have diverged, so they look like an existing user, but they
     * have seen nothing. Not having completed onboarding is the signal that settles it.
     *
     * Conversely a pre-v4 install always has `onboardingComplete = true`, so it is never mistaken for
     * new - which is the case this whole rule exists to get right.
     */
    fun isFirstRun(isFreshInstall: Boolean, onboardingComplete: Boolean?): Boolean =
        isFreshInstall || onboardingComplete != true
}
