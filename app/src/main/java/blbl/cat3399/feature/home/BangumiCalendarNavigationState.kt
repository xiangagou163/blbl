package blbl.cat3399.feature.home

import blbl.cat3399.core.bangumi.BangumiCalendarPeriod

internal class BangumiCalendarNavigationState(
    initialPeriod: BangumiCalendarPeriod,
) {
    var period: BangumiCalendarPeriod = initialPeriod
        private set

    private var lastFocusedCardPosition: Int? = null
    private var pendingSearchReturnPosition: Int? = null

    fun selectQuarter(quarter: Int): Boolean {
        if (quarter !in 1..4 || quarter == period.quarter) return false
        period = BangumiCalendarPeriod(period.year, quarter)
        return true
    }

    fun selectYear(
        year: Int,
        currentYear: Int,
    ): Boolean {
        if (year <= 0 || year == period.year || year > currentYear) return false
        period = BangumiCalendarPeriod(year, period.quarter)
        return true
    }

    fun searchOpenedFromCard(position: Int) {
        lastFocusedCardPosition = position
        pendingSearchReturnPosition = position
    }

    fun cancelPendingSearchReturn() {
        pendingSearchReturnPosition = null
    }

    fun takePendingSearchReturnPosition(): Int? {
        val position = pendingSearchReturnPosition
        pendingSearchReturnPosition = null
        return position
    }

    fun rememberFocusedCard(position: Int) {
        lastFocusedCardPosition = position
    }

    fun focusPositionForContentSwitch(): Int = lastFocusedCardPosition ?: 0
}
