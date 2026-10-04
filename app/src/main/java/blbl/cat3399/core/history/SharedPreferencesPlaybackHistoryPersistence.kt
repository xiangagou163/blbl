package blbl.cat3399.core.history

import android.content.SharedPreferences
import blbl.cat3399.core.log.AppLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SharedPreferencesPlaybackHistoryPersistence(
    private val preferences: SharedPreferences,
) : PlaybackHistoryPersistence {
    override fun load(): List<PlaybackHistoryRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        val records =
            try {
                JSONArray(raw)
            } catch (exception: JSONException) {
                AppLog.w(TAG, "local playback history is corrupted", exception)
                return emptyList()
            }

        return buildList(records.length()) {
            for (index in 0 until records.length()) {
                val item = records.optJSONObject(index) ?: continue
                try {
                    add(item.toPlaybackHistoryRecord())
                } catch (exception: JSONException) {
                    AppLog.w(TAG, "ignoring invalid local playback history entry index=$index", exception)
                }
            }
        }
    }

    override fun save(records: List<PlaybackHistoryRecord>) {
        val json =
            JSONArray().apply {
                records.forEach { record -> put(record.toJson()) }
            }
        preferences.edit().putString(KEY_RECORDS, json.toString()).apply()
    }

    private fun PlaybackHistoryRecord.toJson(): JSONObject =
        JSONObject()
            .put("work_id", workId)
            .put("episode_id", episodeId)
            .put("title", title)
            .put("bvid", bvid)
            .put("aid", aid ?: JSONObject.NULL)
            .put("cid", cid)
            .put("ep_id", epId ?: JSONObject.NULL)
            .put("season_id", seasonId ?: JSONObject.NULL)
            .put("cover_url", coverUrl)
            .put("progress_ms", progressMs)
            .put("duration_ms", durationMs)
            .put("watched_at_ms", watchedAtMs)

    private fun JSONObject.toPlaybackHistoryRecord(): PlaybackHistoryRecord =
        PlaybackHistoryRecord(
            workId = getString("work_id"),
            episodeId = getString("episode_id"),
            title = getString("title"),
            bvid = optString("bvid", ""),
            aid = optPositiveLong("aid"),
            cid = getLong("cid"),
            epId = optPositiveLong("ep_id"),
            seasonId = optPositiveLong("season_id"),
            coverUrl = optString("cover_url", ""),
            progressMs = getLong("progress_ms"),
            durationMs = getLong("duration_ms"),
            watchedAtMs = getLong("watched_at_ms"),
        )

    private fun JSONObject.optPositiveLong(name: String): Long? =
        if (isNull(name) || !has(name)) null else optLong(name).takeIf { it > 0L }

    private companion object {
        const val KEY_RECORDS = "records"
        const val TAG = "LocalPlaybackHistory"
    }
}
