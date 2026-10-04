package blbl.cat3399.core.history

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryPolicyTest {
    @Test
    fun missingOrUnknownModeKeepsTheServerBehaviorDefault() {
        assertEquals(PlaybackHistoryMode.SERVER, PlaybackHistoryMode.fromPreferenceValue(null))
        assertEquals(PlaybackHistoryMode.SERVER, PlaybackHistoryMode.fromPreferenceValue("invalid"))
        PlaybackHistoryMode.entries.forEach { mode ->
            assertEquals(mode, PlaybackHistoryMode.fromPreferenceValue(mode.preferenceValue))
        }
    }

    @Test
    fun eachPrivacyModeSelectsOnlyItsPlaybackAndSearchHistorySources() {
        assertEquals(
            PlaybackHistoryPolicy(
                reportRemoteProgress = true,
                readRemoteProgress = true,
                saveLocalHistory = false,
                readLocalHistory = false,
                persistSearchHistory = true,
            ),
            PlaybackHistoryPolicy.forMode(PlaybackHistoryMode.SERVER),
        )
        assertEquals(
            PlaybackHistoryPolicy(
                reportRemoteProgress = false,
                readRemoteProgress = false,
                saveLocalHistory = true,
                readLocalHistory = true,
                persistSearchHistory = true,
            ),
            PlaybackHistoryPolicy.forMode(PlaybackHistoryMode.LOCAL_ONLY),
        )
        assertEquals(
            PlaybackHistoryPolicy(
                reportRemoteProgress = false,
                readRemoteProgress = false,
                saveLocalHistory = false,
                readLocalHistory = false,
                persistSearchHistory = false,
            ),
            PlaybackHistoryPolicy.forMode(PlaybackHistoryMode.PRIVATE),
        )
    }

    @Test
    fun privateModeDoesNotPersistNewSearchTerms() {
        val persistedTerms = mutableListOf<String>()

        persistSearchHistoryIfAllowed(PlaybackHistoryMode.SERVER, "server term", persistedTerms::add)
        persistSearchHistoryIfAllowed(PlaybackHistoryMode.LOCAL_ONLY, "local term", persistedTerms::add)
        persistSearchHistoryIfAllowed(PlaybackHistoryMode.PRIVATE, "private term", persistedTerms::add)

        assertEquals(listOf("server term", "local term"), persistedTerms)
    }
}
