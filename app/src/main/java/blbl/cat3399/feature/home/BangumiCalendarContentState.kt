package blbl.cat3399.feature.home

import blbl.cat3399.core.bangumi.BangumiCalendarSnapshot

internal enum class BangumiCalendarEmptyState {
    HIDDEN,
    NO_RESULTS,
    LOAD_FAILED,
}

internal fun bangumiCalendarEmptyState(
    snapshot: BangumiCalendarSnapshot?,
    loadFailed: Boolean,
): BangumiCalendarEmptyState =
    when {
        snapshot?.items?.isNotEmpty() == true -> BangumiCalendarEmptyState.HIDDEN
        loadFailed -> BangumiCalendarEmptyState.LOAD_FAILED
        snapshot != null -> BangumiCalendarEmptyState.NO_RESULTS
        else -> BangumiCalendarEmptyState.HIDDEN
    }
