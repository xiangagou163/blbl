package blbl.cat3399.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowedCollectionPlaybackSourceTest {
    @Test
    fun encodeAndParse_preservesViewerAndCollectionIdentity() {
        val source = FollowedCollectionPlaybackSource(viewerMid = 10L, ownerMid = 20L, seasonId = 30L)

        assertEquals(source, FollowedCollectionPlaybackSource.parse(source.encode()))
    }

    @Test
    fun parse_rejectsOtherPlaylistSourcesAndInvalidIds() {
        assertNull(FollowedCollectionPlaybackSource.parse("MyFavFolder:10"))
        assertNull(FollowedCollectionPlaybackSource.parse("MyFollowedCollection:0:20:30"))
        assertNull(FollowedCollectionPlaybackSource.parse("MyFollowedCollection:10:20:0"))
    }
}
