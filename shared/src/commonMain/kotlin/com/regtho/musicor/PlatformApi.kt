package com.regtho.musicor

import androidx.compose.runtime.Composable

/**
 * Returns a launcher that opens the platform folder picker.
 * The result is delivered through [onResult] (null when cancelled).
 */
@Composable
expect fun rememberFolderPicker(onResult: (PickedFolder?) -> Unit): () -> Unit

/**
 * Returns a launcher that opens the platform image picker.
 * The result is delivered through [onResult] (null when cancelled).
 */
@Composable
expect fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit

/** Scans a user-selected folder (recursively) for playable audio files. */
expect suspend fun scanFolderForSongs(folderUri: String): List<ScannedSong>

/**
 * Persists the last playback session (queue, current track, playhead
 * position) so the app can resume after a restart. A no-op on platforms
 * that have their own session handling.
 */
expect object PlaybackStateStore {
    fun save(payload: String)
    fun load(): String?
}

/** Everything needed to resume playback later. */
data class PlaybackSnapshot(
    val queuePaths: List<String>,
    val currentPath: String,
    val positionMillis: Long,
)

internal fun encodePlaybackSnapshot(snapshot: PlaybackSnapshot): String =
    buildString {
        appendLine(snapshot.positionMillis)
        appendLine(snapshot.currentPath.replace("\n", " "))
        snapshot.queuePaths.forEach { appendLine(it.replace("\n", " ")) }
    }

internal fun decodePlaybackSnapshot(raw: String): PlaybackSnapshot? {
    val lines = raw.lineSequence().filter { it.isNotEmpty() }.toList()
    if (lines.size < 2) return null
    val position = lines[0].toLongOrNull() ?: return null
    return PlaybackSnapshot(
        queuePaths = lines.drop(2),
        currentPath = lines[1],
        positionMillis = position,
    )
}

/**
 * Playback controller backed by the platform media APIs.
 * The platform owns the queue and keeps playing even when the UI goes away.
 */
expect class PlayerController() {
    fun playFromQueue(queue: List<PlayableItem>, startIndex: Int)
    /** Loads [queue] at [startIndex] without starting audio; the next
     *  [toggle] resumes from [positionMillis]. */
    fun loadAt(queue: List<PlayableItem>, startIndex: Int, positionMillis: Long)
    fun toggle()
    fun next()
    fun previous()
    fun stop()
    fun seekTo(positionMillis: Long)
    val isPlaying: Boolean
    val currentPath: String?
    /** Duration of the current track in milliseconds; 0 when unknown. */
    val durationMillis: Long
    /** Playback position in milliseconds of the current track. */
    val positionMillis: Long
    /** When true, the current track repeats when it finishes. */
    var loopEnabled: Boolean
    var onStateChanged: (() -> Unit)?
    val currentTitle: String?
    val currentArtist: String?
}