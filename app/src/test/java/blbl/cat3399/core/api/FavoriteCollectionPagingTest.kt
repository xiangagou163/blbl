package blbl.cat3399.core.api

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedCollectionPagingTest {
    @Test
    fun loadAll_continuesAcrossEmptyPageWhenHasMoreIsTrue() =
        runTest {
            val requestedPages = mutableListOf<Int>()
            val scan =
                FollowedCollectionPaging.loadAll<String>(pageSize = 20) { page ->
                    requestedPages += page
                    when (page) {
                        1 -> BiliApi.HasMorePage(emptyList(), page, hasMore = true, total = 40)
                        2 -> BiliApi.HasMorePage(listOf("collection"), page, hasMore = false, total = 40)
                        else -> error("unexpected page=$page")
                    }
                }

            assertEquals(listOf(1, 2), requestedPages)
            assertEquals(listOf("collection"), scan.items)
            assertFalse(scan.truncated)
        }

    @Test
    fun loadAll_marksUnknownEndAsTruncatedAtSafetyLimit() =
        runTest {
            var requestCount = 0
            val scan =
                FollowedCollectionPaging.loadAll<String>(pageSize = 20) { page ->
                    requestCount++
                    BiliApi.HasMorePage(listOf("page$page"), page, hasMore = true, total = 0)
                }

            assertEquals(500, requestCount)
            assertEquals(500, scan.items.size)
            assertTrue(scan.truncated)
        }
}