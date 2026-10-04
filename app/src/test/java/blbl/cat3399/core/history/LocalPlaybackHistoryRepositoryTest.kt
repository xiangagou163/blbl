package blbl.cat3399.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackHistoryRepositoryTest {
    @Test
    fun historyKeepsOnlyTheNewestTwoHundredAndSupportsSingleDeleteAndClear() {
        val history = LocalPlaybackHistoryRepository(InMemoryPlaybackHistoryPersistence())

        (1..201).forEach { index ->
            history.upsert(
                record(
                    workId = "bvid:BV$index",
                    episodeId = "cid:$index",
                    watchedAtMs = index.toLong(),
                ),
            )
        }

        val records = history.records()
        assertEquals(LocalPlaybackHistoryRepository.MAX_RECORDS, records.size)
        assertEquals("bvid:BV201", records.first().workId)
        assertFalse(history.delete(workId = "bvid:BV201", episodeId = "cid:wrong"))
        assertTrue(history.delete(workId = "bvid:BV201", episodeId = "cid:201"))
        assertEquals(199, history.records().size)
        assertFalse(history.records().any { it.workId == "bvid:BV201" })

        history.clear()

        assertEquals(emptyList<PlaybackHistoryRecord>(), history.records())
    }

    @Test
    fun updatingAnEpisodeMovesItsSingleRecordToTheNewestPositionAndResumeRequiresBothIds() {
        val persistence = InMemoryPlaybackHistoryPersistence()
        val history = LocalPlaybackHistoryRepository(persistence)
        val firstEpisode = record(workId = "bvid:BV1A", episodeId = "cid:10", watchedAtMs = 1L)
        val secondWork = record(workId = "bvid:BV2B", episodeId = "cid:20", watchedAtMs = 2L)
        val updatedEpisode =
            record(
                workId = "bvid:BV1A",
                episodeId = "cid:10",
                watchedAtMs = 3L,
                progressMs = 45_000L,
            )

        history.upsert(firstEpisode)
        history.upsert(secondWork)
        history.upsert(updatedEpisode)

        assertEquals(listOf(updatedEpisode, secondWork), history.records())
        assertEquals(updatedEpisode, history.resumeRecord(workId = "bvid:BV1A", episodeId = "cid:10"))
        assertNull(history.resumeRecord(workId = "bvid:BV1A", episodeId = "cid:11"))
        assertNull(history.resumeRecord(workId = "bvid:OTHER", episodeId = "cid:10"))
        assertEquals(listOf(updatedEpisode, secondWork), persistence.records)
    }

    private fun record(
        workId: String,
        episodeId: String,
        watchedAtMs: Long,
        progressMs: Long = 10_000L,
    ) = PlaybackHistoryRecord(
        workId = workId,
        episodeId = episodeId,
        title = workId,
        bvid = workId.removePrefix("bvid:"),
        cid = episodeId.removePrefix("cid:").toLong(),
        progressMs = progressMs,
        durationMs = 120_000L,
        watchedAtMs = watchedAtMs,
    )

    private class InMemoryPlaybackHistoryPersistence : PlaybackHistoryPersistence {
        var records: List<PlaybackHistoryRecord> = emptyList()

        override fun load(): List<PlaybackHistoryRecord> = records

        override fun save(records: List<PlaybackHistoryRecord>) {
            this.records = records.toList()
        }
    }
}
