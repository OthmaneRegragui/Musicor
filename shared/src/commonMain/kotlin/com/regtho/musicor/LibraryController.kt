package com.regtho.musicor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class LibraryController(private val storage: AppStorage = createAppStorage()) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var library by mutableStateOf(loadLibrary())
        private set

    var isScanning by mutableStateOf(false)
        private set

    private fun loadLibrary(): Library {
        val raw = storage.readText(LIBRARY_FILE) ?: return Library()
        return runCatching { json.decodeFromString(Library.serializer(), raw) }
            .getOrElse { Library() }
    }

    private fun persist() {
        storage.writeText(LIBRARY_FILE, json.encodeToString(Library.serializer(), library))
    }

    val categories: List<Category>
        get() = library.categories

    fun categoryById(id: String): Category? =
        library.categories.firstOrNull { it.id == id }

    fun isPathInLibrary(path: String): Boolean =
        library.categories.any { category -> category.songs.any { it.path == path } }

    fun findSongByPath(path: String): Song? =
        library.categories.asSequence()
            .flatMap { it.songs.asSequence() }
            .firstOrNull { it.path == path }

    /** Returns the category (playlist) that owns the given song path, if any. */
    fun categoryForPath(path: String): Category? =
        library.categories.firstOrNull { category -> category.songs.any { it.path == path } }

    /** Adds a new category (or renames an existing one for the same folder) and returns its id. */
    fun addCategory(picked: PickedFolder, customName: String?): String? {
        val existing = library.categories.firstOrNull { it.folderUri == picked.uri }
        val name = customName?.takeIf { it.isNotBlank() }
            ?: picked.displayName?.takeIf { it.isNotBlank() }
            ?: "Music ${library.categories.size + 1}"
        val id = existing?.id ?: "cat_${kotlin.random.Random.nextLong().toString(16)}"
        val category = Category(id = id, name = name, folderUri = picked.uri)
        library = library.copy(
            categories = if (existing != null) {
                library.categories.map { if (it.id == id) it.copy(name = name) else it }
            } else {
                library.categories + category
            },
        )
        persist()
        return id
    }

    fun updateCategoryName(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        library = library.copy(
            categories = library.categories.map {
                if (it.id == id) it.copy(name = trimmed) else it
            },
        )
        persist()
    }

    fun deleteCategory(id: String) {
        val category = categoryById(id) ?: return
        category.songs.forEach { song -> song.imageRef?.let { storage.deleteArt(it) } }
        library = library.copy(categories = library.categories.filterNot { it.id == id })
        persist()
    }

    /** Rescans a category folder and rebuilds its song list (tags re-extracted from files).
     * Scans are serialized so adding several folders in a row never silently drops one. */
    suspend fun scanCategory(id: String) {
        scanMutex.withLock {
            val category = categoryById(id) ?: return@withLock
            isScanning = true
            try {
                val scanned = scanFolderForSongs(category.folderUri)
                val songs = scanned.distinctBy { it.path }.map { scannedSong ->
                    val artRef = scannedSong.metadata?.artBytes?.let { bytes ->
                        if (bytes.isNotEmpty()) storage.saveArt(category.id, scannedSong.path, bytes) else null
                    }
                    Song(
                        path = scannedSong.path,
                        fileName = scannedSong.displayName,
                        title = scannedSong.metadata?.title?.takeIf { it.isNotBlank() },
                        artist = scannedSong.metadata?.artist?.takeIf { it.isNotBlank() },
                        releaseDate = scannedSong.metadata?.releaseDate?.takeIf { it.isNotBlank() },
                        imageRef = artRef,
                        artSource = if (artRef != null) ArtSource.EMBEDDED else ArtSource.NONE,
                    )
                }
                library = library.copy(
                    categories = library.categories.map {
                        if (it.id == id) it.copy(songs = songs) else it
                    },
                )
                persist()
            } finally {
                isScanning = false
            }
        }
    }

    fun updateSongInfo(
        categoryId: String,
        songPath: String,
        name: String,
        artist: String,
        releaseDate: String,
    ) {
        library = library.copy(
            categories = library.categories.map { category ->
                if (category.id != categoryId) category
                else category.copy(
                    songs = category.songs.map { song ->
                        if (song.path != songPath) song
                        else song.copy(
                            title = name.trim().ifEmpty { null },
                            artist = artist.trim().ifEmpty { null },
                            releaseDate = releaseDate.trim().ifEmpty { null },
                        )
                    },
                )
            },
        )
        persist()
    }

    fun setSongArt(categoryId: String, songPath: String, bytes: ByteArray) {
        val ref = storage.saveArt(categoryId, songPath, bytes)
        library = library.copy(
            categories = library.categories.map { category ->
                if (category.id != categoryId) category
                else category.copy(
                    songs = category.songs.map { song ->
                        if (song.path != songPath) song
                        else song.copy(imageRef = ref, artSource = ArtSource.CUSTOM)
                    },
                )
            },
        )
        persist()
    }

    fun removeSongArt(categoryId: String, songPath: String) {
        val song = categoryById(categoryId)?.songs?.firstOrNull { it.path == songPath } ?: return
        song.imageRef?.let { storage.deleteArt(it) }
        library = library.copy(
            categories = library.categories.map { category ->
                if (category.id != categoryId) category
                else category.copy(
                    songs = category.songs.map { s ->
                        if (s.path != songPath) s
                        else s.copy(imageRef = null, artSource = ArtSource.NONE)
                    },
                )
            },
        )
        persist()
    }

    fun readArt(ref: String): ByteArray? = storage.readArt(ref)

    private val scanMutex = Mutex()

    private companion object {
        const val LIBRARY_FILE = "library.json"
    }
}