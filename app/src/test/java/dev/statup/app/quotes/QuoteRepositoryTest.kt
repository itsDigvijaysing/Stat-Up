package dev.statup.app.quotes

import dev.statup.app.domain.model.Quote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuoteRepositoryTest {

    private class FakeStore(private var source: String = "OFFLINE") : DailyQuoteStore {
        var cachedDate: String? = null
        var cachedSource: String? = null
        var cachedJson: String? = null

        override suspend fun getQuoteSource(): String = source
        fun setSource(s: String) { source = s }

        override suspend fun getCachedQuote(date: String, source: String): String? =
            cachedJson?.takeIf { cachedDate == date && cachedSource == source }

        override suspend fun setCachedQuote(date: String, source: String, quoteJson: String) {
            cachedDate = date; cachedSource = source; cachedJson = quoteJson
        }
    }

    private class FakeApi(private val result: Result<Quote>) : QuoteApi {
        var calls = 0
        override suspend fun fetchQuote(): Result<Quote> {
            calls++
            return result
        }
    }

    private object FakePack : QuotePack {
        override fun quoteForDay(epochDay: Long, anime: Boolean): Quote =
            Quote(text = "pack-$epochDay", author = if (anime) "anime-pack" else "stoic-pack")
    }

    private val day = LocalDate.of(2026, 6, 10) // epochDay 20614 - even

    @Test
    fun `OFFLINE source never touches the network`() = runTest {
        val anime = FakeApi(Result.success(Quote("net", "x")))
        val motivation = FakeApi(Result.success(Quote("net", "y")))
        val repo = QuoteRepository(FakeStore("OFFLINE"), anime, motivation, FakePack)

        val quote = repo.getDailyQuote(day)

        assertTrue(quote.author.endsWith("-pack"))
        assertEquals(0, anime.calls)
        assertEquals(0, motivation.calls)
    }

    @Test
    fun `second call the same day is served from cache without refetching`() = runTest {
        val anime = FakeApi(Result.success(Quote("fresh", "Luffy", "One Piece", "Animechan")))
        val repo = QuoteRepository(FakeStore("ANIME"), anime, FakeApi(Result.success(Quote("m", "z"))), FakePack)

        val first = repo.getDailyQuote(day)
        val second = repo.getDailyQuote(day)

        assertEquals(first, second)
        assertEquals("only one network call per day (Animechan free tier is 5/hour)", 1, anime.calls)
    }

    @Test
    fun `API failure falls back to the offline pack`() = runTest {
        val failing = FakeApi(Result.failure(Exception("network down")))
        val repo = QuoteRepository(FakeStore("ANIME"), failing, failing, FakePack)

        val quote = repo.getDailyQuote(day)

        assertEquals("anime-pack", quote.author)
        assertEquals(1, failing.calls)
    }

    @Test
    fun `changing the source in Settings re-resolves instead of serving the old cache`() = runTest {
        val store = FakeStore("ANIME")
        val anime = FakeApi(Result.success(Quote("anime quote", "Luffy", "One Piece", "Animechan")))
        val motivation = FakeApi(Result.success(Quote("stoic quote", "Seneca", null, "ZenQuotes.io")))
        val repo = QuoteRepository(store, anime, motivation, FakePack)

        assertEquals("Luffy", repo.getDailyQuote(day).author)
        store.setSource("MOTIVATION")
        assertEquals("Seneca", repo.getDailyQuote(day).author)
    }

    @Test
    fun `MIXED alternates anime and motivation by day parity`() = runTest {
        val store = FakeStore("MIXED")
        val anime = FakeApi(Result.success(Quote("a", "anime-api")))
        val motivation = FakeApi(Result.success(Quote("m", "motivation-api")))
        val repo = QuoteRepository(store, anime, motivation, FakePack)

        val evenDay = repo.getDailyQuote(day)            // epochDay even → anime
        val oddDay = repo.getDailyQuote(day.plusDays(1)) // odd → motivation

        assertEquals("anime-api", evenDay.author)
        assertEquals("motivation-api", oddDay.author)
    }
}
