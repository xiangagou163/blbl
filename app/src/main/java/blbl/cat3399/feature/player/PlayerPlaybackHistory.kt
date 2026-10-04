package blbl.cat3399.feature.player

import blbl.cat3399.core.history.PlaybackHistoryRecord

internal fun PlayerActivity.buildLocalPlaybackHistoryRecord(
    progressMs: Long,
    durationMs: Long,
    watchedAtMs: Long,
): PlaybackHistoryRecord? {
    val cid = currentCid.takeIf { it > 0L } ?: return null
    val bvid = currentBvid.trim()
    val aid = currentAid?.takeIf { it > 0L }
    if (bvid.isBlank() && aid == null) return null

    val seasonId = currentSeasonId?.takeIf { it > 0L }
    val epId = currentEpId?.takeIf { it > 0L }
    val workId =
        seasonId?.let { "season:$it" }
            ?: bvid.takeIf { it.isNotBlank() }?.let { "bvid:$it" }
            ?: aid?.let { "aid:$it" }
            ?: return null
    val episodeId = epId?.let { "ep:$it" } ?: "cid:$cid"
    val title =
        currentMainTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: bvid.takeIf { it.isNotBlank() }
            ?: "av${aid ?: return null}"

    return PlaybackHistoryRecord(
        workId = workId,
        episodeId = episodeId,
        title = title,
        bvid = bvid,
        aid = aid,
        cid = cid,
        epId = epId,
        seasonId = seasonId,
        progressMs = progressMs.coerceAtLeast(0L),
        durationMs = durationMs.coerceAtLeast(0L),
        watchedAtMs = watchedAtMs,
    )
}
