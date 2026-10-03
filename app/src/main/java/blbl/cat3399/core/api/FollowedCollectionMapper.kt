package blbl.cat3399.core.api

import blbl.cat3399.core.model.FollowedUgcCollection

internal object FollowedCollectionMapper {
    const val UGC_SEASON_TYPE = 21

    fun fromSubscriptionFields(
        viewerMid: Long,
        type: Int,
        seasonId: Long,
        ownerMid: Long,
        upperMid: Long,
        ownerName: String?,
        upperName: String?,
        title: String?,
        coverUrl: String?,
        description: String?,
        videoCount: Int?,
    ): FollowedUgcCollection? {
        if (type != UGC_SEASON_TYPE || seasonId <= 0L) return null
        val resolvedOwnerMid = ownerMid.takeIf { it > 0L } ?: upperMid.takeIf { it > 0L } ?: return null
        if (viewerMid <= 0L) return null

        return FollowedUgcCollection(
            viewerMid = viewerMid,
            seasonId = seasonId,
            ownerMid = resolvedOwnerMid,
            ownerName = ownerName.normalizedOrNull() ?: upperName.normalizedOrNull(),
            title = title.orEmpty().trim(),
            coverUrl = coverUrl.normalizedOrNull(),
            description = description.normalizedOrNull(),
            videoCount = videoCount?.takeIf { it > 0 },
        )
    }

    fun distinct(collections: Iterable<FollowedUgcCollection>): List<FollowedUgcCollection> =
        collections.distinctBy { it.stableKey }

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotBlank() }
}