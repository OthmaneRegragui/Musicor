package com.regtho.musicor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.regtho.musicor.shared.R
import java.io.File

/**
 * Foreground service that owns playback so music keeps playing when the UI
 * goes away. Exposes media controls through a MediaStyle notification and a
 * [MediaSessionCompat] (lock screen / quick settings).
 */
class MediaPlaybackService : Service() {

    companion object {
        const val ACTION_PLAY = "com.regtho.musicor.action.PLAY"
        const val ACTION_LOAD = "com.regtho.musicor.action.LOAD"
        const val ACTION_TOGGLE = "com.regtho.musicor.action.TOGGLE"
        const val ACTION_NEXT = "com.regtho.musicor.action.NEXT"
        const val ACTION_PREVIOUS = "com.regtho.musicor.action.PREVIOUS"
        const val ACTION_STOP = "com.regtho.musicor.action.STOP"
        const val ACTION_SEEK = "com.regtho.musicor.action.SEEK"

        private const val EXTRA_PATHS = "paths"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_ARTISTS = "artists"
        private const val EXTRA_ART_REFS = "artRefs"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_POSITION = "position"
        private const val CHANNEL_ID = "musicor_playback"
        private const val NOTIFICATION_ID = 1

        // Session persistence (survives a process kill so START_STICKY can
        // bring playback back when the app is running in the background).
        private const val PREFS_NAME = "musicor_session"
        private const val KEY_PATHS = "paths"
        private const val KEY_TITLES = "titles"
        private const val KEY_ARTISTS = "artists"
        private const val KEY_ART_REFS = "artRefs"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_PLAYING = "playing"
        private const val KEY_LOOP = "loop"
        private const val SEPARATOR = "\u0001"

        @Volatile
        var currentTrackPath: String? = null
            private set

        @Volatile
        var currentIsPlaying: Boolean = false
            private set

        @Volatile
        var currentPositionMillis: Long = 0L
            private set

        @Volatile
        var currentDurationMillis: Long = 0L
            private set

        @Volatile
        var currentTitle: String = ""
            private set

        @Volatile
        var currentArtist: String = ""
            private set

        /** When true, the current track repeats when it finishes. */
        @Volatile
        var loopEnabled: Boolean = false

        @Volatile
        var stateListener: (() -> Unit)? = null

        fun startPlayback(context: Context, queue: List<PlayableItem>, startIndex: Int) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_PATHS, queue.map { it.path }.toTypedArray())
                putExtra(EXTRA_TITLES, queue.map { it.title }.toTypedArray())
                putExtra(EXTRA_ARTISTS, queue.map { it.artist }.toTypedArray())
                putExtra(EXTRA_ART_REFS, queue.map { it.artRef ?: "" }.toTypedArray())
                putExtra(EXTRA_INDEX, startIndex)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Loads a session paused at [positionMillis] without auto-playing. */
        fun loadPlayback(context: Context, queue: List<PlayableItem>, startIndex: Int, positionMillis: Long) {
            val intent = Intent(context, MediaPlaybackService::class.java).apply {
                action = ACTION_LOAD
                putExtra(EXTRA_PATHS, queue.map { it.path }.toTypedArray())
                putExtra(EXTRA_TITLES, queue.map { it.title }.toTypedArray())
                putExtra(EXTRA_ARTISTS, queue.map { it.artist }.toTypedArray())
                putExtra(EXTRA_ART_REFS, queue.map { it.artRef ?: "" }.toTypedArray())
                putExtra(EXTRA_INDEX, startIndex)
                putExtra(EXTRA_POSITION, positionMillis)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun sendAction(context: Context, action: String) {
            sendAction(context, action, positionMillis = 0L)
        }

        fun sendAction(context: Context, action: String, positionMillis: Long) {
            context.startService(
                Intent(context, MediaPlaybackService::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_POSITION, positionMillis)
                },
            )
        }
    }

    private var player: MediaPlayer? = null
    private var paths: List<String> = emptyList()
    private var titles: List<String> = emptyList()
    private var artists: List<String> = emptyList()
    private var artRefs: List<String> = emptyList()
    private var currentIndex: Int = -1
    private var pendingResumePosition: Long = -1L
    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager: AudioManager
        get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences
        get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val audioAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private val audioAttributesCompat: AudioAttributesCompat =
        AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .build()

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took the focus permanently: stop playing quietly.
                pauseCurrent()
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseCurrent()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck so the other app's audio stays audible over ours.
                player?.setVolume(0.2f, 0.2f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus is back (or another ducking session ended): restore volume.
                player?.setVolume(1f, 1f)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        createMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playFromIntent(intent)
            ACTION_LOAD -> loadFromIntent(intent)
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREVIOUS -> previous()
            ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_POSITION, 0L))
            ACTION_STOP -> {
                stopPlayback()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // START_STICKY recreation after the process was killed: the queue is
        // gone with the old process, so revive it from disk and keep playing
        // in the background.
        if (intent?.action == null) {
            if (!restorePersistedState()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        abandonAudioFocus()
        releasePlayer()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        currentTrackPath = null
        currentIsPlaying = false
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Music playback controls"
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMediaSession() {
        val session = MediaSessionCompat(this, "MusicorPlayback")
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = playCurrent()
            override fun onPause() = pauseCurrent()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
            override fun onSeekTo(pos: Long) = seekTo(pos)
            override fun onStop() {
                stopPlayback()
                ServiceCompat.stopForeground(this@MediaPlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        })
        session.isActive = true
        mediaSession = session
    }

    private fun playFromIntent(intent: Intent) {
        paths = intent.getStringArrayExtra(EXTRA_PATHS)?.toList().orEmpty()
        titles = intent.getStringArrayExtra(EXTRA_TITLES)?.toList().orEmpty()
        artists = intent.getStringArrayExtra(EXTRA_ARTISTS)?.toList().orEmpty()
        artRefs = intent.getStringArrayExtra(EXTRA_ART_REFS)?.toList()
            ?.map { it.takeIf(String::isNotBlank) ?: "" }
            .orEmpty()
        currentIndex = -1

        if (paths.isEmpty()) {
            // Nothing playable; satisfy the foreground contract and shut down.
            startForeground(NOTIFICATION_ID, buildNotification())
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, paths.lastIndex)
        currentIndex = index
        currentTrackPath = paths.getOrNull(index)
        startForeground(NOTIFICATION_ID, buildNotification())
        playAt(index)
    }

    private fun loadFromIntent(intent: Intent) {
        paths = intent.getStringArrayExtra(EXTRA_PATHS)?.toList().orEmpty()
        titles = intent.getStringArrayExtra(EXTRA_TITLES)?.toList().orEmpty()
        artists = intent.getStringArrayExtra(EXTRA_ARTISTS)?.toList().orEmpty()
        artRefs = intent.getStringArrayExtra(EXTRA_ART_REFS)?.toList()
            ?.map { it.takeIf(String::isNotBlank) ?: "" }
            .orEmpty()

        if (paths.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, paths.lastIndex)
        pendingResumePosition = intent.getLongExtra(EXTRA_POSITION, 0L)
        currentIndex = index
        currentTrackPath = paths.getOrNull(index)
        startForeground(NOTIFICATION_ID, buildNotification())
        playAt(index, autoPlay = false)
    }

    private fun playAt(index: Int, autoPlay: Boolean = true) {
        if (index !in paths.indices) {
            stopPlayback()
            return
        }
        releasePlayer()
        currentIndex = index
        val path = paths[index]
        val mp = MediaPlayer()
        try {
            // Route through the media stream and keep the CPU awake while it
            // plays, so music continues with the screen off.
            mp.setAudioAttributes(audioAttributes)
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            if (path.startsWith("content://")) {
                mp.setDataSource(this, Uri.parse(path))
            } else {
                mp.setDataSource(path)
            }
            mp.setOnPreparedListener {
                if (pendingResumePosition >= 0L) {
                    runCatching { it.seekTo(pendingResumePosition.toInt()) }
                    pendingResumePosition = -1L
                }
                if (autoPlay) it.start()
                notifyStateChanged()
            }
            mp.setOnCompletionListener { onTrackCompleted() }
            mp.setOnErrorListener { _, _, _ ->
                onTrackCompleted()
                true
            }
            mp.prepareAsync()
            player = mp
        } catch (_: Exception) {
            releasePlayer()
        }
        notifyStateChanged()
    }

    private fun playCurrent() {
        val mp = player ?: return
        if (!requestAudioFocus()) return
        runCatching { mp.start() }
        notifyStateChanged()
    }

    private fun pauseCurrent() {
        val mp = player ?: return
        runCatching { mp.pause() }
        abandonAudioFocus()
        notifyStateChanged()
    }

    private fun toggle() {
        val mp = player ?: return
        if (mp.isPlaying) {
            pauseCurrent()
        } else {
            playCurrent()
        }
    }

    private fun seekTo(positionMillis: Long) {
        val mp = player ?: return
        runCatching { mp.seekTo(positionMillis.toInt()) }
        notifyStateChanged()
    }

    private fun next() {
        playAt(currentIndex + 1)
    }

    private fun previous() {
        if (currentIndex > 0) {
            playAt(currentIndex - 1)
        } else {
            playAt(currentIndex)
        }
    }

    private fun stopPlayback() {
        releasePlayer()
        currentIndex = -1
        pendingResumePosition = -1L
        abandonAudioFocus()
        clearPersistedState()
        notifyStateChanged()
    }

    private fun releasePlayer() {
        val current = player
        player = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    // -- Audio focus ----------------------------------------------------------

    private fun requestAudioFocus(): Boolean {
        val request = audioFocusRequest
            ?: AudioFocusRequestCompat.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributesCompat)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .build()
                .also { audioFocusRequest = it }
        // AudioManagerCompat handles both the API 26+ and legacy request paths.
        return AudioManagerCompat.requestAudioFocus(audioManager, request) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
        }
    }

    // -- Session persistence --------------------------------------------------

    /** Writes the current queue/position to disk for a cold restart. */
    private fun persistState() {
        // Nothing loaded (or stopped): don't leave a stale session behind.
        if (currentIndex < 0) {
            clearPersistedState()
            return
        }
        prefs.edit()
            .putString(KEY_PATHS, paths.joinToString(SEPARATOR))
            .putString(KEY_TITLES, titles.joinToString(SEPARATOR))
            .putString(KEY_ARTISTS, artists.joinToString(SEPARATOR))
            .putString(KEY_ART_REFS, artRefs.joinToString(SEPARATOR))
            .putInt(KEY_INDEX, currentIndex)
            .putLong(KEY_POSITION, player?.currentPosition?.toLong() ?: 0L)
            .putBoolean(KEY_PLAYING, player?.isPlaying == true)
            .putBoolean(KEY_LOOP, loopEnabled)
            .apply()
    }

    private fun clearPersistedState() {
        prefs.edit().clear().apply()
    }

    /**
     * Loads the session written to disk by [persistState] and starts playing
     * (or just seeking) to exactly where it was.  Called by [onStartCommand]
     * after a START_STICKY process recreation.  Returns false when there is
     * nothing to restore.
     */
    private fun restorePersistedState(): Boolean {
        val savedPaths = prefs.getString(KEY_PATHS, null)
        if (savedPaths.isNullOrEmpty()) return false
        paths = savedPaths.split(SEPARATOR)
        titles = prefs.getString(KEY_TITLES, "").orEmpty().split(SEPARATOR)
        artists = prefs.getString(KEY_ARTISTS, "").orEmpty().split(SEPARATOR)
        artRefs = prefs.getString(KEY_ART_REFS, "").orEmpty().split(SEPARATOR)
        currentIndex = prefs.getInt(KEY_INDEX, 0).coerceIn(0, paths.lastIndex)
        pendingResumePosition = prefs.getLong(KEY_POSITION, 0L)
        val wasPlaying = prefs.getBoolean(KEY_PLAYING, false)
        loopEnabled = prefs.getBoolean(KEY_LOOP, false)
        currentTrackPath = paths.getOrNull(currentIndex)
        startForeground(NOTIFICATION_ID, buildNotification())
        playAt(currentIndex, autoPlay = wasPlaying)
        if (wasPlaying) requestAudioFocus()
        return true
    }

    private fun onTrackCompleted() {
        if (loopEnabled) {
            val mp = player
            if (mp != null) {
                runCatching { mp.seekTo(0) }
                runCatching { mp.start() }
                notifyStateChanged()
                return
            }
        }
        val nextIndex = currentIndex + 1
        if (nextIndex < paths.size) {
            playAt(nextIndex)
        } else {
            stopPlayback()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun notifyStateChanged() {
        currentTrackPath = paths.getOrNull(currentIndex)
        currentIsPlaying = player?.isPlaying == true
        val mp = player
        currentPositionMillis = mp?.currentPosition?.toLong() ?: 0L
        currentDurationMillis = mp?.duration?.toLong()?.takeIf { it > 0 } ?: 0L
        currentTitle = titles.getOrNull(currentIndex) ?: ""
        currentArtist = artists.getOrNull(currentIndex) ?: ""
        updateNotification()
        updateMediaSession()
        stateListener?.invoke()
        persistState()
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val playing = player?.isPlaying == true
        val title = titles.getOrNull(currentIndex) ?: ""
        val artist = artists.getOrNull(currentIndex) ?: ""

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val prevIntent = actionIntent(1, ACTION_PREVIOUS)
        val toggleIntent = actionIntent(2, ACTION_TOGGLE)
        val nextIntent = actionIntent(3, ACTION_NEXT)
        val stopIntent = actionIntent(4, ACTION_STOP)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(contentIntent)
            .setLargeIcon(loadArt(artRefs.getOrNull(currentIndex)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .addAction(R.drawable.ic_prev, "Previous", prevIntent)
            .addAction(if (playing) R.drawable.ic_pause else R.drawable.ic_play, if (playing) "Pause" else "Play", toggleIntent)
            .addAction(R.drawable.ic_next, "Next", nextIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .build()
    }

    private fun actionIntent(requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, MediaPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun loadArt(artRef: String?): Bitmap? {
        if (artRef.isNullOrBlank()) return null
        val file = File(filesDir, "musicor/art/$artRef")
        if (!file.isFile) return null
        return runCatching {
            // Notification icon only needs ~256px. Decoding a full-size cover
            // here (e.g. 4000x4000) would pin ~64MB on low-RAM devices.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= 256 &&
                bounds.outHeight / (sampleSize * 2) >= 256
            ) {
                sampleSize *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        }.getOrNull()
    }

    private fun updateMediaSession() {
        val session = mediaSession ?: return
        val title = titles.getOrNull(currentIndex) ?: ""
        val artist = artists.getOrNull(currentIndex) ?: ""
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, artist)
                .build(),
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SEEK_TO
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    if (player?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    player?.currentPosition?.toLong() ?: 0L,
                    1f,
                    if (player?.isPlaying == true) SystemClock.elapsedRealtime() else 0L,
                )
                .build(),
        )
    }
}