package dev.statup.app.domain.model

import kotlinx.serialization.Serializable

/**
 * [attribution] names the online provider (e.g. "ZenQuotes.io"); when present it MUST be
 * rendered visibly - ZenQuotes' free tier requires it.
 */
@Serializable
data class Quote(
    val text: String,
    val author: String,
    val origin: String? = null,
    val attribution: String? = null
)

/**
 * OFFLINE (default) keeps the app's offline-first stance - online sources are an explicit
 * opt-in. MIXED alternates anime/motivation by day.
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
