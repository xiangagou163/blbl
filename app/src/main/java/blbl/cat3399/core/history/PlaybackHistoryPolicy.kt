package blbl.cat3399.core.history

enum class PlaybackHistoryMode(
    val preferenceValue: String,
) {
    SERVER("server"),
    LOCAL_ONLY("local_only"),
    PRIVATE("private"),
    ;

    companion object {
        fun fromPreferenceValue(value: String?): PlaybackHistoryMode =
            entries.firstOrNull { it.preferenceValue == value } ?: SERVER
    }
}

data class PlaybackHistoryPolicy(
    val reportRemoteProgress: Boolean,
    val readRemoteProgress: Boolean,
    val saveLocalHistory: Boolean,
    val readLocalHistory: Boolean,
    val persistSearchHistory: Boolean,
) {
    companion object {
        fun forMode(mode: PlaybackHistoryMode): PlaybackHistoryPolicy =
            when (mode) {
                PlaybackHistoryMode.SERVER ->
                    PlaybackHistoryPolicy(
                        reportRemoteProgress = true,
                        readRemoteProgress = true,
                        saveLocalHistory = false,
                        readLocalHistory = false,
                        persistSearchHistory = true,
                    )
                PlaybackHistoryMode.LOCAL_ONLY ->
                    PlaybackHistoryPolicy(
                        reportRemoteProgress = false,
                        readRemoteProgress = false,
                        saveLocalHistory = true,
                        readLocalHistory = true,
                        persistSearchHistory = true,
                    )
                PlaybackHistoryMode.PRIVATE ->
                    PlaybackHistoryPolicy(
                        reportRemoteProgress = false,
                        readRemoteProgress = false,
                        saveLocalHistory = false,
                        readLocalHistory = false,
                        persistSearchHistory = false,
                    )
            }
    }
}

internal suspend fun recordPlaybackProgress(
    mode: PlaybackHistoryMode,
    localRecord: PlaybackHistoryRecord?,
    remoteProgressEligible: Boolean,
    saveLocal: suspend (PlaybackHistoryRecord) -> Unit,
    reportRemote: suspend () -> Unit,
) {
    val policy = PlaybackHistoryPolicy.forMode(mode)
    if (policy.saveLocalHistory && localRecord != null) saveLocal(localRecord)
    if (policy.reportRemoteProgress && remoteProgressEligible) reportRemote()
}

fun persistSearchHistoryIfAllowed(
    mode: PlaybackHistoryMode,
    keyword: String,
    persist: (String) -> Unit,
) {
    if (keyword.isBlank()) return
    if (PlaybackHistoryPolicy.forMode(mode).persistSearchHistory) persist(keyword)
}
