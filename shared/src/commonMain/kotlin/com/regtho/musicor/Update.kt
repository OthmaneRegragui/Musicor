package com.regtho.musicor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fetches a URL and returns the response body, or null on any failure. */
expect suspend fun fetchText(url: String): String?

/**
 * Downloads [url] next to [fileName] into a platform-appropriate location
 * and returns the local path, or null on failure. [onProgress] is called
 * with (downloaded bytes, total bytes when known). Platforms that can open
 * or install the downloaded file do so before returning.
 */
expect suspend fun downloadToFile(
    url: String,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): String?

/** The version of the running app, e.g. "1.0.0". Blank when unknown. */
expect fun currentAppVersion(): String

/**
 * Asset file-name suffixes this platform can consume, most preferred first
 * (e.g. Android -> ["apk"], Linux -> ["AppImage", "tar.gz", "deb"]).
 * Empty on platforms that cannot self-update.
 */
expect fun updateAssetExtensions(): List<String>

/** Details of a newer release, as reported by the GitHub releases API. */
data class UpdateInfo(
    val version: String,
    val releaseUrl: String,
    val notes: String?,
    val asset: Asset?,
) {
    data class Asset(
        val fileName: String,
        val url: String,
        val sizeBytes: Long?,
    )
}

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
internal data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long? = null,
)

private val githubJson = Json { ignoreUnknownKeys = true }

/**
 * Checks the GitHub repo's latest release against [currentVersion] and
 * returns an [UpdateInfo] when a newer version exists, otherwise null
 * (also null when the request fails or the response is not a release).
 */
class UpdateChecker(
    private val repo: String = "OthmaneRegragui/Musicor",
    private val fetch: suspend (String) -> String? = ::fetchText,
) {
    suspend fun check(
        currentVersion: String,
        extensions: List<String> = updateAssetExtensions(),
    ): UpdateInfo? {
        if (currentVersion.isBlank()) return null
        val body = fetch("https://api.github.com/repos/$repo/releases/latest") ?: return null
        val release = runCatching { githubJson.decodeFromString<GithubRelease>(body) }.getOrNull()
            ?: return null

        val latest = release.tagName.trim().removePrefix("v").ifBlank { return null }
        if (compareVersions(currentVersion, latest) >= 0) return null

        val asset = pickAsset(release, extensions)?.let {
            UpdateInfo.Asset(
                fileName = it.name,
                url = it.browserDownloadUrl,
                sizeBytes = it.size,
            )
        }
        return UpdateInfo(
            version = latest,
            releaseUrl = release.htmlUrl ?: "https://github.com/$repo/releases",
            notes = release.body,
            asset = asset,
        )
    }
}

/** Returns <0 when [a] is older than [b], 0 when equal, >0 when newer. */
internal fun compareVersions(a: String, b: String): Int {
    val pa = parseVersion(a)
    val pb = parseVersion(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

/** "v1.2.3" -> [1, 2, 3]; drops pre-release/build suffixes. */
internal fun parseVersion(v: String): List<Int> =
    v.trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '+')
        .mapNotNull { it.toIntOrNull() }

/** First asset whose name ends with one of [extensions], in priority order. */
internal fun pickAsset(release: GithubRelease, extensions: List<String>): GithubAsset? {
    for (ext in extensions) {
        val match = release.assets.firstOrNull { it.name.endsWith(ext, ignoreCase = true) }
        if (match != null) return match
    }
    return null
}