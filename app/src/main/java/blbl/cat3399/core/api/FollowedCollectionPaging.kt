package blbl.cat3399.core.api

internal data class FollowedCollectionPageScan<T>(
    val items: List<T>,
    val truncated: Boolean,
)

internal object FollowedCollectionPaging {
    private const val MAX_PAGES_WITH_UNKNOWN_TOTAL = 500
    private const val MAX_PAGES = 10_000

    suspend fun <T> loadAll(
        pageSize: Int,
        fetchPage: suspend (page: Int) -> BiliApi.HasMorePage<T>,
    ): FollowedCollectionPageScan<T> {
        val safePageSize = pageSize.coerceAtLeast(1)
        val items = ArrayList<T>()
        var page = 1
        var maxPages = MAX_PAGES_WITH_UNKNOWN_TOTAL
        var hasReportedPageBound = false

        while (page <= maxPages) {
            val result = fetchPage(page)
            items.addAll(result.items)
            if (!result.hasMore) return FollowedCollectionPageScan(items = items, truncated = false)

            if (!hasReportedPageBound && result.total > 0) {
                val declaredPages = ((result.total.toLong() + safePageSize - 1) / safePageSize).toInt()
                maxPages = maxOf(page + 1, declaredPages + 1).coerceAtMost(MAX_PAGES)
                hasReportedPageBound = true
            }
            if (page >= maxPages) return FollowedCollectionPageScan(items = items, truncated = true)
            page++
        }

        return FollowedCollectionPageScan(items = items, truncated = true)
    }
}