package com.regtho.musicor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DOWNLOAD_CHUNK = 64 * 1024
private const val PROVIDER_AUTHORITY = "com.regtho.musicor.fileprovider"

actual suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}

actual suspend fun downloadToFile(
    url: String,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): String? = withContext(Dispatchers.IO) {
    val appContext = AndroidAppContext.appContext
    runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }

        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, fileName)
        val total = connection.contentLength.takeIf { it > 0 }?.toLong()
        var totalRead = 0L
        connection.inputStream.use { input ->
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
        offerApkForInstall(appContext, target)
        target.absolutePath
    }.getOrNull()
}

/** Hands the APK to the package installer via the app's FileProvider. */
private fun offerApkForInstall(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, PROVIDER_AUTHORITY, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

actual fun currentAppVersion(): String =
    runCatching {
        val context = AndroidAppContext.appContext
        val packageManager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull() ?: ""

actual fun updateAssetExtensions(): List<String> = listOf("apk")