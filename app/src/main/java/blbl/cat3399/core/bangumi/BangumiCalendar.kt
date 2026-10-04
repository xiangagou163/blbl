package blbl.cat3399.core.bangumi

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class BangumiCalendarPeriod(
    val year: Int,
    val quarter: Int,
) {
    init {
        require(year > 0) { "year must be positive" }
        require(quarter in 1..4) { "quarter must be between 1 and 4" }
    }

    val startDate: String
        get() = String.format(Locale.ROOT, "%04d-%02d-01", year, (quarter - 1) * 3 + 1)

    val endDateExclusive: String
        get() {
            val endMonth = quarter * 3 + 1
            val endYear = if (endMonth > 12) year + 1 else year
            val month = if (endMonth > 12) 1 else endMonth
            return String.format(Locale.ROOT, "%04d-%02d-01", endYear, month)
        }

    val cacheKey: String
        get() = "$year-Q$quarter"
}

data class BangumiCalendarSubject(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val ratingScore: Double?,
    val tags: List<String>,
    val episodeCount: Int?,
)

data class BangumiCalendarPage(
    val items: List<BangumiCalendarSubject>,
    val nextOffset: Int,
    val hasNext: Boolean,
)

data class BangumiCalendarSnapshot(
    val items: List<BangumiCalendarSubject>,
    val nextOffset: Int,
    val hasNext: Boolean,
    val cachedAtMillis: Long,
)

data class BangumiCalendarLoadResult(
    val snapshot: BangumiCalendarSnapshot,
    val usedCacheFallback: Boolean,
)

interface BangumiCalendarApi {
    suspend fun search(
        period: BangumiCalendarPeriod,
        offset: Int,
    ): BangumiCalendarPage
}

object BangumiCalendarResponseParser {
    const val PAGE_SIZE = 20

    fun parsePage(
        responseBody: String,
        requestedOffset: Int,
    ): BangumiCalendarPage {
        val response = JSONObject(responseBody)
        val data = response.optJSONArray("data") ?: JSONArray()
        val itemsById = LinkedHashMap<Long, BangumiCalendarSubject>()
        for (index in 0 until data.length()) {
            val rawSubject = data.optJSONObject(index) ?: continue
            val subject = parseSubject(rawSubject) ?: continue
            itemsById.putIfAbsent(subject.id, subject)
        }

        val responseOffset = response.optInt("offset", requestedOffset).coerceAtLeast(0)
        val responseLimit = response.optInt("limit", PAGE_SIZE).coerceAtLeast(1)
        val nextOffset = responseOffset + data.length()
        val total = response.optInt("total", -1)
        val hasNext =
            data.length() > 0 &&
                if (total >= 0) {
                    nextOffset < total
                } else {
                    data.length() >= responseLimit
                }
        return BangumiCalendarPage(
            items = itemsById.values.toList(),
            nextOffset = nextOffset,
            hasNext = hasNext,
        )
    }

    private fun parseSubject(raw: JSONObject): BangumiCalendarSubject? {
        val id = raw.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val title = optionalString(raw, "name_cn") ?: optionalString(raw, "name") ?: return null
        val images = raw.optJSONObject("images")
        val rating = raw.optJSONObject("rating")
        val score = rating?.optDouble("score", 0.0)?.takeIf { it.isFinite() && it > 0.0 }
        val tags =
            raw.optJSONArray("tags")
                ?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            val name = array.optJSONObject(index)?.let { optionalString(it, "name") }
                            if (name != null && name !in this) add(name)
                        }
                    }
                }.orEmpty()
        val episodeCount =
            positiveInt(raw, "total_episodes")
                ?: positiveInt(raw, "eps")

        return BangumiCalendarSubject(
            id = id,
            title = title,
            coverUrl =
                images?.let {
                    sequenceOf("large", "common", "medium", "small", "grid")
                        .mapNotNull { key -> optionalString(it, key) }
                        .firstOrNull()
                },
            ratingScore = score,
            tags = tags,
            episodeCount = episodeCount,
        )
    }

    private fun optionalString(
        json: JSONObject,
        key: String,
    ): String? = (json.opt(key) as? String)?.trim()?.takeIf { it.isNotBlank() }

    private fun positiveInt(
        json: JSONObject,
        key: String,
    ): Int? {
        val raw = json.opt(key)
        val value =
            when (raw) {
                is Number -> raw.toInt()
                is String -> raw.toIntOrNull()
                else -> null
            }
        return value?.takeIf { it > 0 }
    }
}
