package dev.statup.app.quotes

import android.content.Context
import dev.statup.app.domain.model.Quote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Deterministic daily quote source. Implemented by [OfflineQuotePack] (bundled assets) and
 * faked in unit tests.
 */
interface QuotePack {
    /** A quote for [epochDay], stable for the whole day. [anime] selects the pack. */
    fun quoteForDay(epochDay: Long, anime: Boolean): Quote
}

/**
 * Bundled, fully-offline quote packs (assets/quotes/anime.json + motivation.json).
 *
 * This is the DEFAULT quote source - it keeps the app's offline-first stance (network
 * sources are an explicit opt-in in Settings) - and the fallback whenever an online fetch
 * fails. Selection is `epochDay % size`: deterministic per day, no stored state, and the
 * pack naturally rotates.
 *
 * The motivational pack is public-domain authors only; the anime pack is short quotes with
 * character/show attribution (see assets/quotes/README.txt).
 */
class OfflineQuotePack(private val context: Context) : QuotePack {

    private val json = Json { ignoreUnknownKeys = true }

    private val animeQuotes: List<Quote> by lazy { load("quotes/anime.json") }
    private val motivationQuotes: List<Quote> by lazy { load("quotes/motivation.json") }

    override fun quoteForDay(epochDay: Long, anime: Boolean): Quote {
        val pack = if (anime) animeQuotes else motivationQuotes
        // floorMod-style index: epochDay is always positive in practice, but stay safe.
        val index = ((epochDay % pack.size) + pack.size) % pack.size
        return pack[index.toInt()]
    }

    private fun load(assetPath: String): List<Quote> {
        val raw = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        return json.decodeFromString<List<PackEntry>>(raw).map {
            Quote(text = it.text, author = it.author, origin = it.origin, attribution = null)
        }
    }

    @Serializable
    private data class PackEntry(
        val text: String,
        val author: String,
        val origin: String? = null
    )
}
