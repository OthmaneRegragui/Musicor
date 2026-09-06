package com.regtho.musicor

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import platform.Foundation.NSUserDefaults

class IosStorage : AppStorage {

    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    override fun readText(fileName: String): String? =
        defaults.stringForKey(fileName)?.takeIf { it.isNotEmpty() }

    override fun writeText(fileName: String, content: String) {
        defaults.setObject(content, forKey = fileName)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun saveArt(categoryId: String, songPath: String, bytes: ByteArray): String {
        val ref = artFileRef(categoryId, songPath)
        defaults.setObject(Base64.encode(bytes), forKey = "art_$ref")
        return ref
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun readArt(artRef: String): ByteArray? {
        val encoded = defaults.stringForKey("art_$artRef") ?: return null
        return runCatching { Base64.decode(encoded) }.getOrNull()
    }

    override fun deleteArt(artRef: String) {
        defaults.removeObjectForKey("art_$artRef")
    }
}

actual fun createAppStorage(): AppStorage = IosStorage()

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = null