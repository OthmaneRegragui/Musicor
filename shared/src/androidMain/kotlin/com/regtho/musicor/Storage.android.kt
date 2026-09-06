package com.regtho.musicor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

class AndroidStorage : AppStorage {

    private val root: File by lazy { File(AndroidAppContext.appContext.filesDir, "musicor") }
    private val artDir: File by lazy { File(root, "art").apply { mkdirs() } }

    override fun readText(fileName: String): String? {
        val file = File(root, fileName)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    override fun writeText(fileName: String, content: String) {
        runCatching {
            root.mkdirs()
            File(root, fileName).writeText(content)
        }
    }

    override fun saveArt(categoryId: String, songPath: String, bytes: ByteArray): String {
        val ref = artFileRef(categoryId, songPath)
        runCatching {
            artDir.mkdirs()
            File(artDir, ref).writeBytes(bytes)
        }
        return ref
    }

    override fun readArt(artRef: String): ByteArray? {
        val file = File(artDir, artRef)
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    override fun deleteArt(artRef: String) {
        runCatching { File(artDir, artRef).delete() }
    }
}

actual fun createAppStorage(): AppStorage = AndroidStorage()

actual fun decodeImage(bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    // Art is only ever shown as a small thumbnail (<= 48dp), so cap the decode
    // at 256px: crisp on 3x displays yet ~16x less RAM than a full-size cover.
    val bitmap = decodeScaled(bytes, maxDimension = 256) ?: return null
    return bitmap.asImageBitmap()
}

private fun decodeScaled(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxDimension &&
        bounds.outHeight / (sampleSize * 2) >= maxDimension
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}