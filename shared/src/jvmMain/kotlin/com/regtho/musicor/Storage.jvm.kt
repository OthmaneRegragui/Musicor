package com.regtho.musicor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File

class JvmStorage(
    private val root: File = File(System.getProperty("user.home"), ".musicor"),
) : AppStorage {

    private val artDir: File = File(root, "art").apply { mkdirs() }

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

actual fun createAppStorage(): AppStorage = JvmStorage()

actual fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()