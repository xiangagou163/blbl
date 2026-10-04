package blbl.cat3399.core.history

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryRecordingTest {
    private val record =
        PlaybackHistoryRecord(
            workId = "bvid:BV1test",
            episodeId = "cid:42",
            title = "A watched title",
            bvid = "BV1test",
            aid = 123L,
            cid = 42L,
            progressMs = 90_000L,
            durationMs = 600_000L,
            watchedAtMs = 1_700_000_000_000L,
        )

    @Test
    fun serverModeReportsProgressRemotelyWithoutSavingLocally() = runBlocking {
        val localSaves = mutableListOf<PlaybackHistoryRecord>()
        var remoteReports = 0

        recordPlaybackProgress(
            mode = PlaybackHistoryMode.SERVER,
            localRecord = record,
            remoteProgressEligible = true,
            saveLocal = localSaves::add,
            reportRemote = { remoteReports++ },
        )

        assertEquals(emptyList<PlaybackHistoryRecord>(), localSaves)
        assertEquals(1, remoteReports)
    }

    @Test
    fun localOnlyModeSavesProgressWithoutReportingRemotely() = runBlocking {
        val localSaves = mutableListOf<PlaybackHistoryRecord>()
        var remoteReports = 0

        recordPlaybackProgress(
            mode = PlaybackHistoryMode.LOCAL_ONLY,
            localRecord = record,
            remoteProgressEligible = true,
            saveLocal = localSaves::add,
            reportRemote = { remoteReports++ },
        )

        assertEquals(listOf(record), localSaves)
        assertEquals(0, remoteReports)
    }

    @Test
    fun privateModeDoesNotSaveOrReportProgress() = runBlocking {
        val localSaves = mutableListOf<PlaybackHistoryRecord>()
        var remoteReports = 0

        recordPlaybackProgress(
            mode = PlaybackHistoryMode.PRIVATE,
            localRecord = record,
            remoteProgressEligible = true,
            saveLocal = localSaves::add,
            reportRemote = { remoteReports++ },
        )

        assertEquals(emptyList<PlaybackHistoryRecord>(), localSaves)
        assertEquals(0, remoteReports)
    }
}
