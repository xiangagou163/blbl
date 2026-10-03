package blbl.cat3399.core.update

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import blbl.cat3399.BuildConfig
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.net.await
import blbl.cat3399.core.net.ipv4OnlyDns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object ApkUpdater {
    private const val LATEST_RELEASES_URL =
        "https://api.github.com/repos/xiangagou163/blbl/releases?per_page=100"
    private const val RELEASE_DOWNLOAD_URL =
        "https://github.com/xiangagou163/blbl/releases/download"

    private const val COOLDOWN_MS = 5_000L

    @Volatile
    private var lastStartedAtMs: Long = 0L

    private val okHttpLazy: Lazy<OkHttpClient> =
        lazy {
            OkHttpClient.Builder()
                .dns(ipv4OnlyDns { BiliClient.prefs.ipv4OnlyEnabled })
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }

    private val okHttp: OkHttpClient
        get() = okHttpLazy.value

    fun evictConnections() {
        if (okHttpLazy.isInitialized()) okHttp.connectionPool.evictAll()
    }

    sealed class Progress {
        data object Connecting : Progress()

        data class Downloading(
            val downloadedBytes: Long,
            val totalBytes: Long?,
            val bytesPerSecond: Long,
        ) : Progress() {
            val percent: Int? =
                totalBytes?.takeIf { it > 0 }?.let { total ->
                    ((downloadedBytes.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
                }

            val hint: String =
                buildString {
                    if (totalBytes != null && totalBytes > 0) {
                        append("${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}")
                    } else {
                        append(formatBytes(downloadedBytes))
                    }
                    if (bytesPerSecond > 0) append("（${formatBytes(bytesPerSecond)}/s）")
                }
        }
    }

    data class RemoteUpdate(
        val versionName: String,
        val changelog: String,
        val versions: List<RemoteUpdate> = emptyList(),
    ) {
        val displayChangelog: String
            get() = changelog.ifBlank { "暂无更新日志" }
    }

    fun markStarted(nowMs: Long = System.currentTimeMillis()) {
        lastStartedAtMs = nowMs
    }

    fun cooldownLeftMs(nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastStartedAtMs
        val left = (last + COOLDOWN_MS) - nowMs
        return left.coerceAtLeast(0)
    }

    suspend fun fetchLatestUpdate(
        url: String = LATEST_RELEASES_URL,
    ): RemoteUpdate {
        return withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            val maxAttempts = 3
            for (attempt in 1..maxAttempts) {
                ensureActive()
                try {
                    return@withContext fetchLatestUpdateOnce(url)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    lastError = t
                    val shouldRetry =
                        attempt < maxAttempts &&
                            (t is IOException || t.message?.startsWith("HTTP ") == true)
                    if (!shouldRetry) throw t
                    delay(400L * attempt)
                }
            }
            throw lastError ?: IllegalStateException("fetch latest version failed")
        }
    }

    private fun fetchLatestUpdateOnce(url: String): RemoteUpdate {
        val req =
            Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "blbl-android")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
        val call = okHttp.newCall(req)
        val res = call.execute()
        res.use { r ->
            if (r.code == 404) error("暂无可用版本")
            check(r.isSuccessful) { "HTTP ${r.code} ${r.message}" }
            val body = r.body ?: error("empty body")
            return parseReleases(body.string())
        }
    }

    internal fun parseReleases(
        raw: String,
        isDebugBuild: Boolean = BuildConfig.DEBUG,
    ): RemoteUpdate {
        val releases = JSONArray(raw)
        check(releases.length() > 0) { "暂无可用版本" }

        val stableReleases =
            (0 until releases.length())
                .map { index ->
                    releases.optJSONObject(index) ?: error("Release 数据格式无效")
                }
                .filterNot { release ->
                    release.optBoolean("draft") || release.optBoolean("prerelease")
                }
        check(stableReleases.isNotEmpty()) { "暂无可用版本" }

        val latestRelease = stableReleases.first()
        val latest =
            parseRelease(latestRelease, isDebugBuild)
                ?: error(
                    "稳定版 Release 缺少 APK：${
                        apkAssetNameFor(latestRelease.optString("tag_name"), isDebugBuild)
                    }",
                )
        val versions =
            buildList {
                add(latest)
                stableReleases.drop(1).forEach { release ->
                    parseRelease(release, isDebugBuild)?.let(::add)
                }
            }
        return latest.copy(versions = versions)
    }

    private fun parseRelease(
        release: JSONObject,
        isDebugBuild: Boolean,
    ): RemoteUpdate? {
        val versionName = release.optString("tag_name").trim().removePrefix("v")
        check(versionName.isNotBlank()) { "Release 缺少版本标签" }

        val assetName = apkAssetNameFor(versionName, isDebugBuild)
        val assets = release.optJSONArray("assets") ?: JSONArray()
        val hasMatchingAsset =
            (0 until assets.length()).any { index ->
                val asset = assets.optJSONObject(index) ?: error("Release APK 数据格式无效")
                asset.optString("name") == assetName
            }
        if (!hasMatchingAsset) return null

        return RemoteUpdate(
            versionName = versionName,
            changelog = release.optString("body", "").trim(),
        )
    }

    internal fun apkAssetNameFor(versionName: String, isDebugBuild: Boolean): String {
        val cleanVersion = versionName.trim().removePrefix("v")
        require(cleanVersion.isNotBlank()) { "版本号不能为空" }
        return if (isDebugBuild) {
            "app-debug.apk"
        } else {
            "blbl-android-$cleanVersion-release.apk"
        }
    }

    internal fun parseChangelog(raw: String): RemoteUpdate {
        val versions = parseChangelogVersions(raw)
        return versions.first().copy(versions = versions)
    }

    internal fun parseChangelogVersions(raw: String): List<RemoteUpdate> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        check(normalized.isNotBlank()) { "更新日志为空" }

        val lines = normalized.lines()
        val allHeadings =
            lines.withIndex()
                .mapNotNull { (index, line) ->
                    parseVersionHeading(line)?.let { heading -> index to heading }
                }
        check(allHeadings.isNotEmpty()) { "未找到版本标题" }

        val versionLevel = allHeadings.first().second.level
        val headings = allHeadings.filter { (_, heading) -> heading.level == versionLevel }
        return headings.mapIndexed { index, (headingIndex, heading) ->
            val nextHeadingIndex = headings.getOrNull(index + 1)?.first ?: lines.size
            val sectionLines =
                lines.subList(headingIndex + 1, nextHeadingIndex)
                    .dropLastWhile { it.isBlank() }
            val changelog =
                sectionLines
                    .joinToString("\n")
                    .trim()

            RemoteUpdate(
                versionName = heading.versionName,
                changelog = changelog,
            )
        }
    }

    fun apkUrlFor(versionName: String): String {
        val cleanVersion = versionName.trim().removePrefix("v")
        val assetName = apkAssetNameFor(versionName, BuildConfig.DEBUG)
        return "$RELEASE_DOWNLOAD_URL/v$cleanVersion/$assetName"
    }

    private data class VersionHeading(
        val level: Int,
        val versionName: String,
    )

    private fun parseVersionHeading(line: String): VersionHeading? {
        val trimmed = line.trim()
        val match = Regex("""^(#{1,6})\s+\[?v?([0-9]+(?:\.[0-9]+)*(?:[-+][A-Za-z0-9_.-]+)?)\]?(?:\s+.*)?$""").matchEntire(trimmed)
            ?: return null
        val level = match.groupValues[1].length
        val versionName = match.groupValues[2].trim()
        if (parseVersion(versionName) == null) return null
        return VersionHeading(level = level, versionName = versionName)
    }

    fun isRemoteNewer(remoteVersionName: String, currentVersionName: String = BuildConfig.VERSION_NAME): Boolean {
        val remote = parseVersion(remoteVersionName) ?: return false
        val current = parseVersion(currentVersionName) ?: return remoteVersionName.trim() != currentVersionName.trim()
        return compareVersion(remote, current) > 0
    }

    suspend fun downloadApkToCache(
        context: Context,
        url: String,
        onProgress: (Progress) -> Unit,
    ): File {
        onProgress(Progress.Connecting)

        val dir = File(context.cacheDir, "test_update").apply { mkdirs() }
        val part = File(dir, "update.apk.part")
        val target = File(dir, "update.apk")
        runCatching { part.delete() }
        runCatching { target.delete() }

        val req = Request.Builder().url(url).get().build()
        val call = okHttp.newCall(req)
        val res = call.await()
        res.use { r ->
            check(r.isSuccessful) { "HTTP ${r.code} ${r.message}" }
            val body = r.body ?: error("empty body")
            val total = body.contentLength().takeIf { it > 0 }
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    FileOutputStream(part).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var downloaded = 0L

                        var lastEmitAtMs = 0L
                        var speedAtMs = System.currentTimeMillis()
                        var speedBytes = 0L
                        var bytesPerSecond = 0L

                        while (true) {
                            ensureActive()
                            val read = input.read(buf)
                            if (read <= 0) break
                            output.write(buf, 0, read)
                            downloaded += read

                            // Speed estimate (1s window)
                            speedBytes += read
                            val nowMs = System.currentTimeMillis()
                            val speedElapsedMs = nowMs - speedAtMs
                            if (speedElapsedMs >= 1_000) {
                                bytesPerSecond = (speedBytes * 1_000L / speedElapsedMs.coerceAtLeast(1)).coerceAtLeast(0)
                                speedBytes = 0L
                                speedAtMs = nowMs
                            }

                            // UI progress: at most 5 updates per second.
                            if (nowMs - lastEmitAtMs >= 200) {
                                lastEmitAtMs = nowMs
                                onProgress(Progress.Downloading(downloadedBytes = downloaded, totalBytes = total, bytesPerSecond = bytesPerSecond))
                            }
                        }
                        output.fd.sync()
                    }
                }
            }
        }

        check(part.exists() && part.length() > 0) { "downloaded file is empty" }
        check(part.renameTo(target)) { "rename failed" }
        return target
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = installUriFor(context, apkFile)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        grantInstallerReadPermissions(context, intent, uri)
        context.startActivity(intent)
    }

    @SuppressLint("SetWorldReadable")
    private fun installUriFor(context: Context, apkFile: File): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val authority = "${context.packageName}.fileprovider"
            FileProvider.getUriForFile(context, authority, apkFile)
        } else {
            apkFile.setReadable(true, false)
            Uri.fromFile(apkFile)
        }
    }

    @Suppress("DEPRECATION")
    private fun grantInstallerReadPermissions(
        context: Context,
        intent: Intent,
        uri: Uri,
    ) {
        if (uri.scheme != "content") return

        val installers =
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        for (installer in installers) {
            val packageName = installer.activityInfo?.packageName ?: continue
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        if (b < 1024) return "${b}B"
        val kb = b / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1fKB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1fMB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2fGB", gb)
    }

    private fun parseVersion(raw: String): List<Int>? {
        val cleaned = raw.trim().removePrefix("v")
        val digitsOnly =
            cleaned.takeWhile { ch ->
                ch.isDigit() || ch == '.'
            }
        if (digitsOnly.isBlank()) return null
        val parts = digitsOnly.split('.').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        return nums
    }

    private fun compareVersion(a: List<Int>, b: List<Int>): Int {
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai.compareTo(bi)
        }
        return 0
    }
}
