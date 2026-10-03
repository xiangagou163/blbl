package blbl.cat3399.core.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowedCollectionMapperTest {
    @Test
    fun fromSubscriptionFields_keepsOnlyValidType21Collections() {
        val accepted = map(type = 21, seasonId = 100L, ownerMid = 200L)
        assertEquals(100L, accepted?.seasonId)
        assertEquals(300L, accepted?.viewerMid)
        assertEquals(200L, accepted?.ownerMid)
        assertEquals("Collection", accepted?.title)
        assertEquals(12, accepted?.videoCount)

        assertNull(map(type = 11, seasonId = 100L, ownerMid = 200L))
        assertNull(map(type = 21, seasonId = 0L, ownerMid = 200L))
        assertNull(map(type = 21, seasonId = 100L, ownerMid = 0L, upperMid = 0L))
    }

    @Test
    fun distinct_usesOwnerAndSeason() {
        val first = map(type = 21, seasonId = 100L, ownerMid = 200L)!!
        val duplicate = map(type = 21, seasonId = 100L, ownerMid = 200L)!!
        val sameSeasonDifferentOwner = map(type = 21, seasonId = 100L, ownerMid = 201L)!!

        val distinct = FollowedCollectionMapper.distinct(listOf(first, duplicate, sameSeasonDifferentOwner))

        assertEquals(2, distinct.size)
        assertEquals(201L, distinct.last().ownerMid)
    }

    private fun map(
        type: Int,
        seasonId: Long,
        ownerMid: Long,
        upperMid: Long = 0L,
    ) =
        FollowedCollectionMapper.fromSubscriptionFields(
            viewerMid = 300L,
            type = type,
            seasonId = seasonId,
            ownerMid = ownerMid,
            upperMid = upperMid,
            ownerName = "UP",
            upperName = "UP from upper",
            title = " Collection ",
            coverUrl = " https://example.test/cover.jpg ",
            description = " description ",
            videoCount = 12,
        )
}