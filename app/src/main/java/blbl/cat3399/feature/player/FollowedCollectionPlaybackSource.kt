package blbl.cat3399.feature.player

internal data class FollowedCollectionPlaybackSource(
    val viewerMid: Long,
    val ownerMid: Long,
    val seasonId: Long,
) {
    fun encode(): String = "$PREFIX:$viewerMid:$ownerMid:$seasonId"

    companion object {
        private const val PREFIX = "MyFollowedCollection"

        fun parse(value: String?): FollowedCollectionPlaybackSource? {
            val parts = value?.split(':') ?: return null
            if (parts.size != 4 || parts[0] != PREFIX) return null
            val viewerMid = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val ownerMid = parts[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
            val seasonId = parts[3].toLongOrNull()?.takeIf { it > 0L } ?: return null
            return FollowedCollectionPlaybackSource(viewerMid, ownerMid, seasonId)
        }
    }
}
