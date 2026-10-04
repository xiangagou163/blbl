package blbl.cat3399.core.history

data class PlaybackHistoryRecord(
    val workId: String,
    val episodeId: String,
    val title: String,
    val bvid: String = "",
    val aid: Long? = null,
    val cid: Long,
    val epId: Long? = null,
    val seasonId: Long? = null,
    val coverUrl: String = "",
    val progressMs: Long,
    val durationMs: Long,
    val watchedAtMs: Long,
)

interface PlaybackHistoryPersistence {
    fun load(): List<PlaybackHistoryRecord>

    fun save(records: List<PlaybackHistoryRecord>)
}

class LocalPlaybackHistoryRepository(
    private val persistence: PlaybackHistoryPersistence,
) {
    @Synchronized
    fun records(): List<PlaybackHistoryRecord> =
        persistence.load()
            .sortedByDescending { it.watchedAtMs }
            .take(MAX_RECORDS)

    @Synchronized
    fun upsert(record: PlaybackHistoryRecord) {
        require(record.workId.isNotBlank()) { "playback_history_missing_work_id" }
        require(record.episodeId.isNotBlank()) { "playback_history_missing_episode_id" }

        val updated =
            records()
                .filterNot { it.workId == record.workId && it.episodeId == record.episodeId }
                .toMutableList()
                .apply { add(0, record) }
                .sortedByDescending { it.watchedAtMs }
                .take(MAX_RECORDS)
        persistence.save(updated)
    }

    @Synchronized
    fun resumeRecord(
        workId: String,
        episodeId: String,
    ): PlaybackHistoryRecord? {
        if (workId.isBlank() || episodeId.isBlank()) return null
        return records().firstOrNull { it.workId == workId && it.episodeId == episodeId }
    }

    @Synchronized
    fun delete(
        workId: String,
        episodeId: String,
    ): Boolean {
        val current = records()
        val updated = current.filterNot { it.workId == workId && it.episodeId == episodeId }
        if (current.size == updated.size) return false
        persistence.save(updated)
        return true
    }

    @Synchronized
    fun clear() {
        persistence.save(emptyList())
    }

    companion object {
        const val MAX_RECORDS = 200
    }
}
