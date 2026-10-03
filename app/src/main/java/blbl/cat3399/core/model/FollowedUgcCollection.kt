package blbl.cat3399.core.model

data class FollowedUgcCollection(
    val viewerMid: Long,
    val seasonId: Long,
    val ownerMid: Long,
    val ownerName: String?,
    val title: String,
    val coverUrl: String?,
    val description: String?,
    val videoCount: Int?,
) {
    val stableKey: String get() = "$ownerMid:$seasonId"
}