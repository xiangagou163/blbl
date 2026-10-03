package blbl.cat3399.feature.my

import org.junit.Assert.assertEquals
import org.junit.Test

class MyTabsTest {
    @Test
    fun collectionsTab_followsDramaTab() {
        val keys = MyTabs.all.map { it.key }

        assertEquals(MyTabs.KEY_COLLECTIONS, keys[keys.indexOf(MyTabs.KEY_DRAMA) + 1])
    }
}