package blbl.cat3399.core.bangumi

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface BangumiCalendarCache {
    fun read(period: BangumiCalendarPeriod): BangumiCalendarSnapshot?

    fun write(
        period: BangumiCalendarPeriod,
        snapshot: BangumiCalendarSnapshot,
    )
}

class SharedPreferencesBangumiCalendarCache(context: Context) : BangumiCalendarCache {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(period: BangumiCalendarPeriod): BangumiCalendarSnapshot? {
        val key = storageKey(period)
        val raw = preferences.getString(key, null) ?: return null
        return try {
            decodeSnapshot(JSONObject(raw))
        } catch (error: JSONException) {
            preferences.edit().remove(key).apply()
            null
        }
    }

    override fun write(
        period: BangumiCalendarPeriod,
        snapshot: BangumiCalendarSnapshot,
    ) {
        preferences.edit().putString(storageKey(period), encodeSnapshot(snapshot).toString()).apply()
    }

    private fun storageKey(period: BangumiCalendarPeriod): String = "season_${period.cacheKey}"

    private fun encodeSnapshot(snapshot: BangumiCalendarSnapshot): JSONObject =
        JSONObject()
            .put("nextOffset", snapshot.nextOffset)
            .put("hasNext", snapshot.hasNext)
            .put("cachedAtMillis", snapshot.cachedAtMillis)
            .put(
                "items",
                JSONArray().apply {
                    snapshot.items.forEach { item ->
                        put(
                            JSONObject()
                                .put("id", item.id)
                                .put("title", item.title)
                                .put("coverUrl", item.coverUrl)
                                .put("ratingScore", item.ratingScore)
                                .put("episodeCount", item.episodeCount)
                                .put("tags", JSONArray(item.tags)),
                        )
                    }
                },
            )

    private fun decodeSnapshot(json: JSONObject): BangumiCalendarSnapshot {
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items =
            buildList {
                for (index in 0 until itemsJson.length()) {
                    val item = itemsJson.optJSONObject(index) ?: continue
                    val id = item.optLong("id", 0L)
                    val title = item.optString("title").trim()
                    if (id <= 0L || title.isBlank()) continue
                    val tagsJson = item.optJSONArray("tags") ?: JSONArray()
                    val tags =
                        buildList {
                            for (tagIndex in 0 until tagsJson.length()) {
                                tagsJson.optString(tagIndex).trim().takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    add(
                        BangumiCalendarSubject(
                            id = id,
                            title = title,
                            coverUrl = item.optString("coverUrl").trim().takeIf { it.isNotBlank() },
                            ratingScore = item.optDouble("ratingScore", 0.0).takeIf { it.isFinite() && it > 0.0 },
                            tags = tags,
                            episodeCount = item.optInt("episodeCount", 0).takeIf { it > 0 },
                        ),
                    )
                }
            }
        return BangumiCalendarSnapshot(
            items = items,
            nextOffset = json.optInt("nextOffset", items.size).coerceAtLeast(0),
            hasNext = json.optBoolean("hasNext", false),
            cachedAtMillis = json.optLong("cachedAtMillis", 0L),
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "bangumi_calendar_cache"
    }
}

class BangumiCalendarRepository(
    private val api: BangumiCalendarApi,
    private val cache: BangumiCalendarCache,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun cached(period: BangumiCalendarPeriod): BangumiCalendarSnapshot? = cache.read(period)

    fun isFresh(snapshot: BangumiCalendarSnapshot): Boolean {
        val age = clock() - snapshot.cachedAtMillis
        return age in 0..CACHE_TTL_MILLIS
    }

    suspend fun loadInitial(
        period: BangumiCalendarPeriod,
        forceRefresh: Boolean = false,
    ): BangumiCalendarLoadResult {
        val previous = cache.read(period)
        if (!forceRefresh && previous != null && isFresh(previous)) {
            return BangumiCalendarLoadResult(snapshot = previous, usedCacheFallback = false)
        }

        return try {
            val page = api.search(period, offset = 0)
            val snapshot =
                BangumiCalendarSnapshot(
                    items = page.items,
                    nextOffset = page.nextOffset,
                    hasNext = page.hasNext,
                    cachedAtMillis = clock(),
                )
            cache.write(period, snapshot)
            BangumiCalendarLoadResult(snapshot = snapshot, usedCacheFallback = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (previous != null) {
                BangumiCalendarLoadResult(snapshot = previous, usedCacheFallback = true)
            } else {
                throw error
            }
        }
    }

    suspend fun loadMore(
        period: BangumiCalendarPeriod,
        current: BangumiCalendarSnapshot,
    ): BangumiCalendarSnapshot {
        if (!current.hasNext) return current
        val page = api.search(period, offset = current.nextOffset)
        val latest = cache.read(period)
        if (latest != null && latest.nextOffset != current.nextOffset) return latest
        val base = latest ?: current
        val itemsById = LinkedHashMap<Long, BangumiCalendarSubject>()
        base.items.forEach { itemsById.putIfAbsent(it.id, it) }
        page.items.forEach { itemsById.putIfAbsent(it.id, it) }
        val updated =
            BangumiCalendarSnapshot(
                items = itemsById.values.toList(),
                nextOffset = page.nextOffset,
                hasNext = page.hasNext,
                cachedAtMillis = clock(),
            )
        cache.write(period, updated)
        return updated
    }

    companion object {
        const val CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
    }
}
