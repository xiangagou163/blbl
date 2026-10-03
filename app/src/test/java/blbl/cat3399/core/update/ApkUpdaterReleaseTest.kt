package blbl.cat3399.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkUpdaterReleaseTest {
    @Test
    fun parseReleases_uses_latest_stable_release_and_preserves_stable_history() {
        val update =
            ApkUpdater.parseReleases(
                """
                [
                  ${release("v0.3.0", prerelease = false)},
                  ${release("v0.4.0-beta", prerelease = true)},
                  ${release("v0.2.0", prerelease = false)}
                ]
                """.trimIndent(),
                isDebugBuild = false,
            )

        assertEquals("0.3.0", update.versionName)
        assertTrue(update.changelog.contains("notes for v0.3.0"))
        assertEquals(listOf("0.3.0", "0.2.0"), update.versions.map { it.versionName })
    }

    @Test
    fun parseReleases_requires_the_apk_for_the_installed_build_type() {
        val releaseWithBothApks = "[${release("v0.3.0", prerelease = false)}]"
        val releaseWithReleaseApkOnly =
            "[${release("v0.3.0", prerelease = false, includeDebugApk = false)}]"

        assertEquals(
            "0.3.0",
            ApkUpdater.parseReleases(releaseWithBothApks, isDebugBuild = true).versionName,
        )
        assertEquals(
            "0.3.0",
            ApkUpdater.parseReleases(releaseWithReleaseApkOnly, isDebugBuild = false).versionName,
        )
        assertThrows(IllegalStateException::class.java) {
            ApkUpdater.parseReleases(releaseWithReleaseApkOnly, isDebugBuild = true)
        }
    }

    @Test
    fun parseReleases_reports_when_no_stable_release_is_available() {
        val error =
            assertThrows(IllegalStateException::class.java) {
                ApkUpdater.parseReleases("[]", isDebugBuild = false)
            }

        assertEquals("暂无可用版本", error.message)
    }

    @Test
    fun apkAssetNameFor_selects_debug_or_release_asset() {
        assertEquals(
            "blbl-android-0.3.0-debug.apk",
            ApkUpdater.apkAssetNameFor("v0.3.0", isDebugBuild = true),
        )
        assertEquals(
            "blbl-android-0.3.0-release.apk",
            ApkUpdater.apkAssetNameFor("v0.3.0", isDebugBuild = false),
        )
    }

    @Test
    fun apkUrlFor_uses_this_repository_release_asset() {
        assertTrue(
            ApkUpdater.apkUrlFor("v0.3.0")
                .startsWith("https://github.com/xiangagou163/blbl/releases/download/v0.3.0/"),
        )
        assertTrue(ApkUpdater.apkUrlFor("0.3.0").endsWith("-debug.apk"))
    }

    private fun release(
        tagName: String,
        prerelease: Boolean,
        includeDebugApk: Boolean = true,
    ): String {
        val debugAsset =
            if (includeDebugApk) {
                """{"name":"blbl-android-${tagName.removePrefix("v")}-debug.apk"},"""
            } else {
                ""
            }
        val version = tagName.removePrefix("v")
        return """
            {
              "tag_name": "$tagName",
              "body": "notes for $tagName",
              "draft": false,
              "prerelease": $prerelease,
              "assets": [
                $debugAsset
                {"name":"blbl-android-$version-release.apk"}
              ]
            }
        """.trimIndent()
    }
}
