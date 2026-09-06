package com.regtho.musicor

import androidx.compose.ui.graphics.ImageBitmap

/** Persistent storage for library data and cover art. */
interface AppStorage {
    fun readText(fileName: String): String?
    fun writeText(fileName: String, content: String)

    /** Stores cover art bytes and returns a ref usable with [readArt]/[deleteArt]. */
    fun saveArt(categoryId: String, songPath: String, bytes: ByteArray): String
    fun readArt(artRef: String): ByteArray?
    fun deleteArt(artRef: String)
}

expect fun createAppStorage(): AppStorage

/** Decodes image bytes into a composable bitmap, or null when undecodable. */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/**
 * Shared in-memory cache for decoded cover art. Keyed by art ref and bounded
 * by bytes rather than entry count, so a large library of thumbnails (or a
 * handful of big covers) can never balloon memory on low-RAM devices. Every
 * [SongArt] instance (list rows, player bar, cards) reuses the same bitmap
 * instead of decoding a duplicate copy.
 *
 * Uses only the common stdlib (no JVM-only [synchronized]/`removeEldestEntry`).
 * Recency is tracked by re-inserting a key at the tail on access; the eldest
 * entry is evicted first. All access happens on the UI thread (Composition and
 * click handlers), so it needs no locking.
 */
object ArtCache {
    private const val MAX_BYTES = 8L * 1024 * 1024

    // Insertion order = recency order; touching a key re-inserts it at the end.
    private val map = LinkedHashMap<String, ImageBitmap>()
    private var cachedBytes = 0L

    fun get(ref: String): ImageBitmap? {
        val bitmap = map.remove(ref) ?: return null
        map[ref] = bitmap
        return bitmap
    }

    fun put(ref: String, bitmap: ImageBitmap) {
        map.remove(ref)?.let { cachedBytes -= bytesOf(it) }
        map[ref] = bitmap
        cachedBytes += bytesOf(bitmap)
        evictIfOverBudget()
    }

    fun clear() {
        map.clear()
        cachedBytes = 0L
    }

    private fun evictIfOverBudget() {
        if (cachedBytes <= MAX_BYTES) return
        val iterator = map.entries.iterator()
        while (cachedBytes > MAX_BYTES && iterator.hasNext()) {
            val eldest = iterator.next()
            cachedBytes -= bytesOf(eldest.value)
            iterator.remove()
        }
    }

    // 4 bytes per pixel (ARGB_8888) is a safe upper estimate for every platform.
    private fun bytesOf(bitmap: ImageBitmap): Long =
        bitmap.width.toLong() * bitmap.height.toLong() * 4L
}

internal fun artFileRef(categoryId: String, songPath: String): String {
    val cleanCategory = categoryId.filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { "cat" }
    val hash = songPath.hashCode().toUInt().toString(16)
    return "${cleanCategory}_$hash.jpg"
}