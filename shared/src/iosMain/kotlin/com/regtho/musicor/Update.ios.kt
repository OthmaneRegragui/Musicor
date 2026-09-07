package com.regtho.musicor

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.dataTaskWithURL
import platform.Foundation.writeToFile
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalForeignApi::class)
private suspend fun httpData(url: String): NSData? = suspendCancellableCoroutine { cont: Continuation<NSData?> ->
    val request = NSURL.URLWithString(url)
    if (request == null) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }
    val task = NSURLSession.sharedSession.dataTaskWithURL(request) { data, _, error ->
        cont.resume(if (error != null) null else data)
    }
    task.resume()
}

actual suspend fun fetchText(url: String): String? =
    httpData(url)?.let { data ->
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

@OptIn(ExperimentalForeignApi::class)
actual suspend fun downloadToFile(
    url: String,
    fileName: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
): String? {
    onProgress(0, null)
    val data = httpData(url) ?: return null
    val size = data.length.toLong()
    onProgress(size, size)
    val dir = NSTemporaryDirectory() + "musicor-updates"
    NSFileManager.defaultManager.createDirectoryAtPath(
        dir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    val target = "$dir/$fileName"
    return if (data.writeToFile(target, atomically = true)) target else null
}

actual fun currentAppVersion(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String) ?: ""

// iOS app is interface-only for now; an empty list suppresses the dialog
// when there are no platform assets to download.
actual fun updateAssetExtensions(): List<String> = emptyList()