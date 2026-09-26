package dev.statup.app.data.local.datastore

/**
 * The single rule for "is this a brand-new user?" - wrong one way floods an updating user with
 * starter content and a tour; wrong the other way denies a new user the tour.
 */
object FirstRunPolicy {

    /**
     * isFreshInstall = `firstInstallTime == lastUpdateTime`. onboardingComplete backstops it: a
     * user who installs but never opens the app before an update has diverged timestamps too.
     */
    fun isFirstRun(isFreshInstall: Boolean, onboardingComplete: Boolean?): Boolean =
        isFreshInstall || onboardingComplete != true
}
