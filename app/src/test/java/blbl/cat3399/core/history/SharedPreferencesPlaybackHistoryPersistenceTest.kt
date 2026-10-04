package blbl.cat3399.core.history

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedPreferencesPlaybackHistoryPersistenceTest {
    @Test
    fun playbackRecordsSurviveRepositoryRecreation() {
        val preferences = InMemorySharedPreferences()
        val record =
            PlaybackHistoryRecord(
                workId = "season:42",
                episodeId = "ep:91",
                title = "Episode title",
                bvid = "BV1TEST",
                aid = 123L,
                cid = 456L,
                epId = 91L,
                seasonId = 42L,
                coverUrl = "https://example.invalid/cover.jpg",
                progressMs = 67_000L,
                durationMs = 1_800_000L,
                watchedAtMs = 1_728_000_000_000L,
            )
        LocalPlaybackHistoryRepository(SharedPreferencesPlaybackHistoryPersistence(preferences)).upsert(record)

        val restored =
            LocalPlaybackHistoryRepository(SharedPreferencesPlaybackHistoryPersistence(preferences)).records()

        assertEquals(listOf(record), restored)
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = LinkedHashMap<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = values[key] as? Float ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val changes = LinkedHashMap<String, Any?>()
            private val removals = HashSet<String>()
            private var clearAll = false

            override fun putString(
                key: String,
                value: String?,
            ): SharedPreferences.Editor = update(key, value)

            override fun putStringSet(
                key: String,
                values: MutableSet<String>?,
            ): SharedPreferences.Editor = update(key, values)

            override fun putInt(
                key: String,
                value: Int,
            ): SharedPreferences.Editor = update(key, value)

            override fun putLong(
                key: String,
                value: Long,
            ): SharedPreferences.Editor = update(key, value)

            override fun putFloat(
                key: String,
                value: Float,
            ): SharedPreferences.Editor = update(key, value)

            override fun putBoolean(
                key: String,
                value: Boolean,
            ): SharedPreferences.Editor = update(key, value)

            override fun remove(key: String): SharedPreferences.Editor =
                apply {
                    changes.remove(key)
                    removals += key
                }

            override fun clear(): SharedPreferences.Editor =
                apply {
                    clearAll = true
                }

            override fun commit(): Boolean {
                if (clearAll) values.clear()
                removals.forEach(values::remove)
                changes.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                return true
            }

            override fun apply() {
                commit()
            }

            private fun update(
                key: String,
                value: Any?,
            ): SharedPreferences.Editor =
                apply {
                    removals.remove(key)
                    changes[key] = value
                }
        }
    }
}
