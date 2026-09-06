package com.regtho.musicor

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Session persistence is handled by [MediaPlaybackService] on Android. */
actual object PlaybackStateStore {
    actual fun save(payload: String) = Unit
    actual fun load(): String? = null
}

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "flac", "m4a", "m4b", "mp4", "aac", "ogg", "opus", "3gp", "amr", "mid", "midi",
    "aif", "aiff", "wma", "ape", "dsf", "dff",
)

private fun isAudioFile(name: String?): Boolean {
    val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
    return ext in AUDIO_EXTENSIONS
}

@Composable
actual fun rememberFolderPicker(onResult: (PickedFolder?) -> Unit): () -> Unit {
    val context = AndroidAppContext.appContext
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Permission may already be held or was not grantable; scanning still works for this session.
        }
        val displayName = DocumentFile.fromTreeUri(context, uri)?.name
        onResult(PickedFolder(uri.toString(), displayName))
    }
    return remember(context) { { launcher.launch(null) } }
}

@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit {
    val context = AndroidAppContext.appContext
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        val displayName = uri.lastPathSegment
        if (bytes != null && bytes.isNotEmpty()) {
            onResult(PickedImage(bytes, displayName))
        } else {
            onResult(null)
        }
    }
    return remember(context) { { launcher.launch("image/*") } }
}

actual suspend fun scanFolderForSongs(folderUri: String): List<ScannedSong> =
    withContext(Dispatchers.IO) {
        val context = AndroidAppContext.appContext
        val treeUri = runCatching { Uri.parse(folderUri) }.getOrNull()
            ?: return@withContext emptyList()
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()
        val result = mutableListOf<ScannedSong>()

        fun walk(doc: DocumentFile) {
            doc.listFiles().forEach { file ->
                when {
                    file.isDirectory -> walk(file)
                    file.isFile && isAudioFile(file.name) -> {
                        val metadata = extractMetadata(file.uri)
                        result += ScannedSong(
                            path = file.uri.toString(),
                            displayName = file.name ?: "Unknown",
                            metadata = metadata,
                        )
                    }
                }
            }
        }
        walk(root)
        result
    }

private fun extractMetadata(uri: Uri): SongMetadata? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(AndroidAppContext.appContext, uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
        val art = retriever.embeddedPicture
        SongMetadata(
            title = title,
            artist = artist,
            releaseDate = year,
            artBytes = art?.takeIf { it.isNotEmpty() },
        )
    } finally {
        retriever.release()
    }
}.getOrNull()

actual class PlayerController actual constructor() {

    actual var onStateChanged: (() -> Unit)? = null

    actual fun playFromQueue(queue: List<PlayableItem>, startIndex: Int) {
        MediaPlaybackService.stateListener = { onStateChanged?.invoke() }
        if (queue.isEmpty() || startIndex !in queue.indices) {
            stop()
            return
        }
        MediaPlaybackService.startPlayback(AndroidAppContext.appContext, queue, startIndex)
        onStateChanged?.invoke()
    }

    actual fun loadAt(queue: List<PlayableItem>, startIndex: Int, positionMillis: Long) {
        if (queue.isEmpty() || startIndex !in queue.indices) return
        // Don't stomp on a live session: if the service is already playing
        // the same track, the in-memory position is fresher than the saved one.
        val current = MediaPlaybackService.currentTrackPath
        val target = queue[startIndex].path
        if (current == target && MediaPlaybackService.currentIsPlaying) return
        MediaPlaybackService.loadPlayback(AndroidAppContext.appContext, queue, startIndex, positionMillis)
        onStateChanged?.invoke()
    }

    actual fun toggle() {
        MediaPlaybackService.sendAction(AndroidAppContext.appContext, MediaPlaybackService.ACTION_TOGGLE)
        onStateChanged?.invoke()
    }

    actual fun next() {
        MediaPlaybackService.sendAction(AndroidAppContext.appContext, MediaPlaybackService.ACTION_NEXT)
        onStateChanged?.invoke()
    }

    actual fun previous() {
        MediaPlaybackService.sendAction(AndroidAppContext.appContext, MediaPlaybackService.ACTION_PREVIOUS)
        onStateChanged?.invoke()
    }

    actual fun seekTo(positionMillis: Long) {
        MediaPlaybackService.sendAction(
            AndroidAppContext.appContext,
            MediaPlaybackService.ACTION_SEEK,
            positionMillis,
        )
        onStateChanged?.invoke()
    }

    actual fun stop() {
        MediaPlaybackService.sendAction(AndroidAppContext.appContext, MediaPlaybackService.ACTION_STOP)
        onStateChanged?.invoke()
    }

    actual val isPlaying: Boolean
        get() = MediaPlaybackService.currentIsPlaying

    actual val currentPath: String?
        get() = MediaPlaybackService.currentTrackPath

    actual val durationMillis: Long
        get() = MediaPlaybackService.currentDurationMillis

    actual val positionMillis: Long
        get() = MediaPlaybackService.currentPositionMillis

    actual var loopEnabled: Boolean
        get() = MediaPlaybackService.loopEnabled
        set(value) {
            MediaPlaybackService.loopEnabled = value
        }

    actual val currentTitle: String?
        get() = MediaPlaybackService.currentTitle.ifEmpty { null }

    actual val currentArtist: String?
        get() = MediaPlaybackService.currentArtist.ifEmpty { null }
}