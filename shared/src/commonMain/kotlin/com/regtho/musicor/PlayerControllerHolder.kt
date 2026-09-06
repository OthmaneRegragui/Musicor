package com.regtho.musicor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerControllerHolder(private val controller: PlayerController = PlayerController()) {

    var currentPath by mutableStateOf<String?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var durationMillis by mutableStateOf(0L)
        private set

    var positionMillis by mutableStateOf(0L)
        private set

    private val stateLoopEnabled = mutableStateOf(false)

    /** When true, the current track repeats when it finishes. */
    var loopEnabled: Boolean
        get() = stateLoopEnabled.value
        set(value) {
            stateLoopEnabled.value = value
            controller.loopEnabled = value
        }

    private var queue: List<Song> = emptyList()
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private val stateListeners = mutableListOf<() -> Unit>()
    // Tracks the last-computed snapshot so listeners only fire on real changes.
    private var lastSyncedPath: String? = null
    private var lastSyncedPlaying: Boolean = false
    private var lastSyncedDuration: Long = 0L
    private var lastSyncedPosition: Long = 0L

    /** Notified on every playback state change (track, play/pause, position). */
    fun addStateListener(listener: () -> Unit) {
        stateListeners += listener
    }

    val currentTitle: String?
        get() = controller.currentTitle

    val currentArtist: String?
        get() = controller.currentArtist

    init {
        controller.onStateChanged = { syncFromController() }
        syncFromController()
    }

    fun play(song: Song, queue: List<Song>) {
        this.queue = queue
        val playable = queue.map {
            PlayableItem(
                path = it.path,
                title = it.displayTitle,
                artist = it.displayArtist,
                artRef = it.imageRef,
            )
        }
        val index = queue.indexOfFirst { it.path == song.path }.coerceAtLeast(0)
        controller.playFromQueue(playable, index)
        syncFromController()
    }

    fun togglePlayPause() {
        if (currentPath == null) return
        controller.toggle()
        syncFromController()
    }

    fun next() {
        controller.next()
        syncFromController()
    }

    fun previous() {
        controller.previous()
        syncFromController()
    }

    fun seekTo(positionMillis: Long) {
        if (currentPath == null) return
        controller.seekTo(positionMillis)
        this.positionMillis = positionMillis
    }

    /** Moves the playhead by [deltaMillis] (negative = backwards). */
    fun seekBy(deltaMillis: Long) {
        if (currentPath == null) return
        val duration = durationMillis
        val target = if (duration > 0L) {
            (positionMillis + deltaMillis).coerceIn(0L, duration)
        } else {
            (positionMillis + deltaMillis).coerceAtLeast(0L)
        }
        seekTo(target)
    }

    /** Persists the current track, queue, and playhead for the next launch. */
    fun savePlaybackState() {
        val path = currentPath ?: return
        val snapshot = PlaybackSnapshot(
            queuePaths = queue.map { it.path },
            currentPath = path,
            positionMillis = positionMillis,
        )
        PlaybackStateStore.save(encodePlaybackSnapshot(snapshot))
    }

    /**
     * Loads the last session (queue, current track, playhead position)
     * without starting playback. The user presses play to resume.
     * Returns true when a session was actually restored.
     */
    fun restoreLastSession(resolveSong: (String) -> Song?): Boolean {
        val raw = PlaybackStateStore.load() ?: return false
        val snapshot = decodePlaybackSnapshot(raw) ?: return false
        val currentSong = resolveSong(snapshot.currentPath) ?: return false
        val queue = snapshot.queuePaths.mapNotNull { resolveSong(it) }
        if (queue.isEmpty()) return false
        val playable = queue.map {
            PlayableItem(
                path = it.path,
                title = it.displayTitle,
                artist = it.displayArtist,
                artRef = it.imageRef,
            )
        }
        val index = queue.indexOfFirst { it.path == snapshot.currentPath }.coerceAtLeast(0)
        controller.loadAt(playable, index, snapshot.positionMillis)
        this.queue = queue
        this.positionMillis = snapshot.positionMillis
        syncFromController()
        return true
    }

    fun stop() {
        controller.stop()
        queue = emptyList()
        currentPath = null
        isPlaying = false
        durationMillis = 0
        positionMillis = 0
        stopPolling()
    }

    private fun syncFromController() {
        val playing = controller.isPlaying
        val path = controller.currentPath
        val duration = controller.durationMillis
        val position = controller.positionMillis
        currentPath = path
        isPlaying = playing
        durationMillis = duration
        positionMillis = position
        if (playing) {
            startPolling()
        } else {
            stopPolling()
        }
        // Only fan out to listeners when the snapshot actually changed, which
        // avoids redundant recompositions and signal storms.
        if (path != lastSyncedPath ||
            playing != lastSyncedPlaying ||
            duration != lastSyncedDuration ||
            position != lastSyncedPosition
        ) {
            lastSyncedPath = path
            lastSyncedPlaying = playing
            lastSyncedDuration = duration
            lastSyncedPosition = position
            stateListeners.forEach { it() }
        }
    }

    private fun startPolling() {
        if (pollJob != null) return
        pollJob = pollScope.launch {
            while (isActive) {
                delay(500)
                syncFromController()
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }
}
