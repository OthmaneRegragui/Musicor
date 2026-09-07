package com.regtho.musicor

import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DOWNLOAD_CHUNK = 64 * 1024

actual suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .send(
                HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            .body()
    }.getOrNull()
}

actual suspend fun downloadToFile(
    url: String,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
        val response = client.send(
            HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(30)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream(),
        )
        check(response.statusCode() in 200..299) { "HTTP ${response.statusCode()}" }

        val downloads = File(System.getProperty("user.home"), "Downloads")
        val dir = if (downloads.isDirectory || downloads.mkdirs()) {
            downloads
        } else {
            File(System.getProperty("java.io.tmpdir"))
        }
        val target = File(dir, fileName)
        val total = runCatching { response.headers().firstValueAsLong("Content-Length") }
            .getOrNull()
            ?.orElse(-1L)
            ?.takeIf { it > 0 }
        var totalRead = 0L
        response.body().use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(DOWNLOAD_CHUNK)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    totalRead += read
                    onProgress(totalRead, total)
                }
            }
        }
        openInFileManager(target)
        target.absolutePath
    }.getOrNull()
}

// Keep in sync with packageVersion in desktopApp/build.gradle.kts.
actual fun currentAppVersion(): String = "1.0.0"

actual fun updateAssetExtensions(): List<String> {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("linux") -> listOf("AppImage", "tar.gz", "deb")
        os.contains("win") -> listOf("exe")
        os.contains("mac") -> listOf("dmg")
        else -> emptyList()
    }
}

/** Reveals the download in the system file manager / default opener. */
private fun openInFileManager(file: File) {
    runCatching {
        val desktop = Desktop.getDesktop()
        if (Desktop.isDesktopSupported() && desktop.isSupported(Desktop.Action.OPEN)) {
            desktop.open(file)
        } else {
            ProcessBuilder("xdg-open", file.absolutePath).start()
        }
    }
}