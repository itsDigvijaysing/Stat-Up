package dev.statup.app.domain.model

import kotlinx.serialization.Serializable

/**
 * A single daily quote. [author] is the character name for anime quotes, the person for
 * motivational ones. [origin] is the anime/show title (null for motivational quotes).
 * [attribution] names the online provider when one was used (e.g. "ZenQuotes.io") - when
 * present it MUST be rendered visibly; ZenQuotes' free tier requires it.
 *
 * Serializable because the day's quote is cached as JSON in DataStore.
 */
@Serializable
data class Quote(
    val text: String,
    val author: String,
    val origin: String? = null,
    val attribution: String? = null
)

/**
 * Where the daily quote comes from. OFFLINE (default) keeps the app's offline-first /
 * no-network-without-opt-in stance - the online sources are an explicit Settings choice.
 * MIXED alternates anime/motivation by day.
 */
enum class QuoteSource(val label: String) {
    OFFLINE("Offline pack"),
    ANIME("Anime (Animechan)"),
    MOTIVATION("Motivation (ZenQuotes)"),
    MIXED("Mixed - alternate daily");

    companion object {
        fun fromName(name: String?): QuoteSource =
            entries.firstOrNull { it.name == name } ?: OFFLINE
    }
}
