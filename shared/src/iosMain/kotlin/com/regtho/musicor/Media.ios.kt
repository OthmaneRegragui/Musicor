package com.regtho.musicor

import androidx.compose.runtime.Composable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// iOS folder/image picking currently requires native UIDocumentPicker wiring.
// These act as no-ops so the UI stays functional on other platforms.

@Composable
actual fun rememberFolderPicker(onResult: (PickedFolder?) -> Unit): () -> Unit {
    return { onResult(null) }
}

actual object PlaybackStateStore {
    actual fun save(payload: String) = Unit
    actual fun load(): String? = null
}

@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit {
    return { onResult(null) }
}

actual suspend fun scanFolderForSongs(folderUri: String): List<ScannedSong> =
    withContext(Dispatchers.Default) {
        emptyList()
    }

actual class PlayerController actual constructor() {

    actual var onStateChanged: (() -> Unit)? = null

    actual fun playFromQueue(queue: List<PlayableItem>, startIndex: Int) {}
    actual fun loadAt(queue: List<PlayableItem>, startIndex: Int, positionMillis: Long) {}
    actual fun toggle() {}
    actual fun next() {}
    actual fun previous() {}
    actual fun stop() {}
    actual fun seekTo(positionMillis: Long) {}

    actual val isPlaying: Boolean
        get() = false

    actual val currentPath: String?
        get() = null

    actual val durationMillis: Long
        get() = 0L

    actual val positionMillis: Long
        get() = 0L

    actual var loopEnabled: Boolean = false

    actual val currentTitle: String? get() = null

    actual val currentArtist: String? get() = null
}