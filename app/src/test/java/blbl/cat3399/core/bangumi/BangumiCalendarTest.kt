package blbl.cat3399.core.bangumi

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiCalendarTest {
    @Test
    fun quarterDatesUseInclusiveStartAndExclusiveNextQuarter() {
        assertEquals("2024-01-01", BangumiCalendarPeriod(2024, 1).startDate)
        assertEquals("2024-04-01", BangumiCalendarPeriod(2024, 1).endDateExclusive)
        assertEquals("2024-04-01", BangumiCalendarPeriod(2024, 2).startDate)
        assertEquals("2024-07-01", BangumiCalendarPeriod(2024, 2).endDateExclusive)
        assertEquals("2024-10-01", BangumiCalendarPeriod(2024, 4).startDate)
        assertEquals("2025-01-01", BangumiCalendarPeriod(2024, 4).endDateExclusive)
        assertFalse(BangumiCalendarPeriod(2024, 1).cacheKey == BangumiCalendarPeriod(2024, 2).cacheKey)
        assertFalse(BangumiCalendarPeriod(2024, 1).cacheKey == BangumiCalendarPeriod(2025, 1).cacheKey)
    }

    @Test
    fun fixedBangumiResponseUsesChineseTitleAndDeduplicatesSubjects() {
        val page =
            BangumiCalendarResponseParser.parsePage(
                responseBody =
                    """
                    {
                      "total": 4,
                      "limit": 20,
                      "offset": 0,
                      "data": [
                        {
                          "id": 101,
                          "name": "Japanese title",
                          "name_cn": "中文标题",
                          "date": "2024-01-10",
                          "platform": "TV",
                          "images": {"large": null, "common": "https://lain.bgm.tv/cover.jpg"},
                          "rating": {"score": 8.5},
                          "tags": [{"name": "动作"}, {"name": "动作"}, {"name": "奇幻"}],
                          "eps": 12,
                          "total_episodes": 13
                        },
                        {
                          "id": 101,
                          "name": "Duplicate title",
                          "rating": {"score": 9.0}
                        },
                        {
                          "id": 202,
                          "name": "Fallback title",
                          "name_cn": null,
                          "images": {},
                          "rating": {"score": 0},
                          "tags": [],
                          "eps": 0,
                          "total_episodes": 0
                        }
                      ]
                    }
                    """.trimIndent(),
                requestedOffset = 0,
            )

        assertEquals(listOf(101L, 202L), page.items.map { it.id })
        assertEquals("中文标题", page.items[0].title)
        assertEquals("https://lain.bgm.tv/cover.jpg", page.items[0].coverUrl)
        assertEquals(8.5, page.items[0].ratingScore!!, 0.0)
        assertEquals(listOf("动作", "奇幻"), page.items[0].tags)
        assertEquals(13, page.items[0].episodeCount)
        assertEquals("Fallback title", page.items[1].title)
        assertEquals(null, page.items[1].ratingScore)
        assertEquals(null, page.items[1].episodeCount)
        assertEquals(3, page.nextOffset)
        assertTrue(page.hasNext)
    }

    @Test
    fun freshCacheAvoidsNetworkAndExpiredCacheRefreshes() = runBlocking {
        val now = 1_000_000_000L
        val period = BangumiCalendarPeriod(2024, 1)
        val freshSnapshot = snapshot(subject(1L, "cached"), now - 60L * 60L * 1000L)
        val cache = MemoryCache().apply { write(period, freshSnapshot) }
        val api =
            FakeApi { _, _ ->
                BangumiCalendarPage(items = listOf(subject(2L, "remote")), nextOffset = 1, hasNext = false)
            }
        var currentTime = now
        val repository = BangumiCalendarRepository(api, cache) { currentTime }

        val cachedResult = repository.loadInitial(period)
        assertEquals(freshSnapshot, cachedResult.snapshot)
        assertFalse(cachedResult.usedCacheFallback)
        assertEquals(0, api.requestCount)

        currentTime = freshSnapshot.cachedAtMillis + BangumiCalendarRepository.CACHE_TTL_MILLIS + 1L
        val refreshed = repository.loadInitial(period)
        assertEquals(listOf(2L), refreshed.snapshot.items.map { it.id })
        assertEquals(1, api.requestCount)
    }

    @Test
    fun failedRefreshReturnsAndKeepsStaleCache() = runBlocking {
        val now = 2_000_000_000L
        val period = BangumiCalendarPeriod(2023, 4)
        val staleSnapshot =
            snapshot(
                subject(8L, "last saved season"),
                now - BangumiCalendarRepository.CACHE_TTL_MILLIS - 1L,
            )
        val cache = MemoryCache().apply { write(period, staleSnapshot) }
        val api =
            FakeApi { _, _ ->
                throw IOException("offline")
            }
        val repository = BangumiCalendarRepository(api, cache) { now }

        val result = repository.loadInitial(period, forceRefresh = true)

        assertTrue(result.usedCacheFallback)
        assertEquals(staleSnapshot, result.snapshot)
        assertEquals(staleSnapshot, repository.cached(period))
        assertEquals(1, api.requestCount)
    }

    private fun subject(
        id: Long,
        title: String,
    ) = BangumiCalendarSubject(
        id = id,
        title = title,
        coverUrl = null,
        ratingScore = null,
        tags = emptyList(),
        episodeCount = null,
    )

    private fun snapshot(
        subject: BangumiCalendarSubject,
        cachedAtMillis: Long,
    ) = BangumiCalendarSnapshot(
        items = listOf(subject),
        nextOffset = 1,
        hasNext = true,
        cachedAtMillis = cachedAtMillis,
    )

    private class MemoryCache : BangumiCalendarCache {
        private val values = HashMap<String, BangumiCalendarSnapshot>()

        override fun read(period: BangumiCalendarPeriod): BangumiCalendarSnapshot? = values[period.cacheKey]

        override fun write(
            period: BangumiCalendarPeriod,
            snapshot: BangumiCalendarSnapshot,
        ) {
            values[period.cacheKey] = snapshot
        }
    }

    private class FakeApi(
        private val response: suspend (BangumiCalendarPeriod, Int) -> BangumiCalendarPage,
    ) : BangumiCalendarApi {
        var requestCount = 0
            private set

        override suspend fun search(
            period: BangumiCalendarPeriod,
            offset: Int,
        ): BangumiCalendarPage {
            requestCount++
            return response(period, offset)
        }
    }
}
