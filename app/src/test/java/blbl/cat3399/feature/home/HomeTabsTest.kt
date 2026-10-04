package blbl.cat3399.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTabsTest {
    @Test
    fun emptySelectionKeepsOnlyLegacyTabsEnabledByDefault() {
        val legacyKeys =
            listOf(
                HomeTabs.KEY_RECOMMEND,
                HomeTabs.KEY_POPULAR,
                HomeTabs.KEY_BANGUMI,
                HomeTabs.KEY_CINEMA,
            )

        assertEquals(legacyKeys, HomeTabs.visibleTabs(emptyList()).map { it.key })
        assertEquals(legacyKeys, HomeTabs.selectedKeysForUi(emptyList()))
    }

    @Test
    fun explicitlySelectedCalendarAppearsInCanonicalTabOrder() {
        assertEquals(
            listOf(HomeTabs.KEY_POPULAR, HomeTabs.KEY_BANGUMI_CALENDAR),
            HomeTabs.visibleTabs(listOf(HomeTabs.KEY_BANGUMI_CALENDAR, HomeTabs.KEY_POPULAR)).map { it.key },
        )
        assertEquals(
            listOf(HomeTabs.KEY_RECOMMEND, HomeTabs.KEY_POPULAR, HomeTabs.KEY_BANGUMI, HomeTabs.KEY_CINEMA),
            HomeTabs.selectedKeysForUi(emptyList()),
        )
    }
}
