package blbl.cat3399.feature.home

import blbl.cat3399.core.bangumi.BangumiCalendarPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BangumiCalendarNavigationStateTest {
    @Test
    fun selectingAnotherQuarterUpdatesTheCalendarPeriod() {
        val navigation = BangumiCalendarNavigationState(BangumiCalendarPeriod(2025, 1))

        assertTrue(navigation.selectQuarter(3))

        assertEquals(BangumiCalendarPeriod(2025, 3), navigation.period)
        assertFalse(navigation.selectQuarter(3))
        assertFalse(navigation.selectQuarter(5))
    }

    @Test
    fun returningFromInAppSearchRestoresTheCardFocusPosition() {
        val navigation = BangumiCalendarNavigationState(BangumiCalendarPeriod(2025, 2))

        navigation.searchOpenedFromCard(6)

        assertEquals(6, navigation.takePendingSearchReturnPosition())
        assertNull(navigation.takePendingSearchReturnPosition())
        assertEquals(6, navigation.focusPositionForContentSwitch())
    }
}
