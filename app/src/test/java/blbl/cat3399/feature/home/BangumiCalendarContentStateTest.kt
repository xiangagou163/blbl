package blbl.cat3399.feature.home

import blbl.cat3399.core.bangumi.BangumiCalendarSnapshot
import blbl.cat3399.core.bangumi.BangumiCalendarSubject
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiCalendarContentStateTest {
    @Test
    fun failedInitialLoadWithoutCacheShowsFailureState() {
        assertEquals(
            BangumiCalendarEmptyState.LOAD_FAILED,
            bangumiCalendarEmptyState(snapshot = null, loadFailed = true),
        )
    }

    @Test
    fun successfulEmptySnapshotShowsNoResultsState() {
        assertEquals(
            BangumiCalendarEmptyState.NO_RESULTS,
            bangumiCalendarEmptyState(
                snapshot = BangumiCalendarSnapshot(emptyList(), nextOffset = 0, hasNext = false, cachedAtMillis = 1L),
                loadFailed = false,
            ),
        )
    }

    @Test
    fun existingItemsHideFailureOverlay() {
        assertEquals(
            BangumiCalendarEmptyState.HIDDEN,
            bangumiCalendarEmptyState(
                snapshot =
                    BangumiCalendarSnapshot(
                        items = listOf(subject()),
                        nextOffset = 1,
                        hasNext = false,
                        cachedAtMillis = 1L,
                    ),
                loadFailed = true,
            ),
        )
    }

    private fun subject() =
        BangumiCalendarSubject(
            id = 1L,
            title = "cached",
            coverUrl = null,
            ratingScore = null,
            tags = emptyList(),
            episodeCount = null,
        )
}
