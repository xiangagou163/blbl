package blbl.cat3399.core.bangumi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class BangumiCalendarHttpApi(
    private val client: OkHttpClient = newIndependentClient(),
) : BangumiCalendarApi {
    override suspend fun search(
        period: BangumiCalendarPeriod,
        offset: Int,
    ): BangumiCalendarPage =
        withContext(Dispatchers.IO) {
            val body =
                JSONObject()
                    .put("keyword", "")
                    .put("sort", "rank")
                    .put(
                        "filter",
                        JSONObject()
                            .put("type", JSONArray().put(2))
                            .put(
                                "air_date",
                                JSONArray()
                                    .put(">=${period.startDate}")
                                    .put("<${period.endDateExclusive}"),
                            )
                            .put("meta_tags", JSONArray().put("TV")),
                    ).toString()
            val request =
                Request.Builder()
                    .url("$SEARCH_URL?limit=${BangumiCalendarResponseParser.PAGE_SIZE}&offset=${offset.coerceAtLeast(0)}")
                    .header("User-Agent", USER_AGENT)
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Bangumi search failed with HTTP ${response.code}")
                }
                val responseBody = response.body?.string() ?: throw IOException("Bangumi search returned an empty body")
                BangumiCalendarResponseParser.parsePage(responseBody, requestedOffset = offset)
            }
        }

    companion object {
        private const val SEARCH_URL = "https://api.bgm.tv/v0/search/subjects"
        private const val USER_AGENT = "blbl (https://github.com/xiangagou163/blbl)"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun newIndependentClient(): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(CookieJar.NO_COOKIES)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
