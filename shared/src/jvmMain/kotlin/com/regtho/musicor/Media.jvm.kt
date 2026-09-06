package com.regtho.musicor

import androidx.compose.runtime.Composable
import com.mpatric.mp3agic.Mp3File
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "wav", "flac", "m4a", "m4b", "mp4", "aac", "ogg", "opus", "3gp", "amr", "mid", "midi",
    "aif", "aiff", "wma", "ape", "dsf", "dff",
)

private fun isAudioFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in AUDIO_EXTENSIONS
}

@Composable
actual fun rememberFolderPicker(onResult: (PickedFolder?) -> Unit): () -> Unit {
    return {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("linux") -> thread(name = "musicor-folder-picker") {
                when (val picked = pickFolderWithNativeTool()) {
                    is NativePick.Picked -> EventQueue.invokeLater { onResult(picked.folder) }
                    NativePick.Cancelled -> EventQueue.invokeLater { onResult(null) }
                    NativePick.NoTool -> EventQueue.invokeLater { pickFolderWithFileChooser(onResult) }
                }
            }
            os.contains("mac") -> EventQueue.invokeLater { pickFolderWithNativeDialog(onResult) }
            else -> EventQueue.invokeLater { pickFolderWithFileChooser(onResult) }
        }
    }
}

private sealed class NativePick {
    data class Picked(val folder: PickedFolder) : NativePick()
    object Cancelled : NativePick()
    object NoTool : NativePick()
}

/** Linux: use the desktop environment's native folder picker (zenity on GNOME, kdialog on KDE).
 *  Only ONE dialog is ever shown: whichever native tool launches first decides the outcome,
 *  so a failed parse never pops a second picker afterwards. */
private fun pickFolderWithNativeTool(): NativePick {
    val commands = listOf(
        listOf("zenity", "--file-selection", "--directory", "--title=Select a music folder"),
        listOf("kdialog", "--getexistingdirectory", ".", "--title", "Select a music folder"),
    )
    for (command in commands) {
        val process = try {
            ProcessBuilder(command).start()
        } catch (_: IOException) {
            continue // Tool is not installed; try the next one.
        }
        // GTK tools write warnings to stderr; read stdout only and drain stderr
        // on a daemon thread so the child never blocks on a full stderr pipe.
        val stderrDrain = thread(start = true, isDaemon = true, name = "musicor-picker-stderr") {
            process.errorStream.bufferedReader().use { it.readText() }
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        stderrDrain.join()
        val exit = process.waitFor()
        if (exit != 0) {
            System.err.println("Musicor: folder picker ${command.first()} cancelled (exit=$exit)")
            return NativePick.Cancelled
        }
        // Accept the last non-blank line if it resolves to a real directory.
        val path = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && File(it).isDirectory }
            ?: ""
        if (path.isNotEmpty()) {
            val dir = File(path)
            val canonical = runCatching { dir.canonicalFile }.getOrElse { dir.absoluteFile }
            System.err.println("Musicor: folder picker ${command.first()} picked \"${canonical.absolutePath}\"")
            return NativePick.Picked(PickedFolder(canonical.absolutePath, canonical.name))
        }
        // The dialog was shown but produced nothing usable; don't show another one.
        System.err.println(
            "Musicor: folder picker ${command.first()} returned an unusable path \"$path\" (exit=$exit)",
        )
        return NativePick.Cancelled
    }
    return NativePick.NoTool
}

/** macOS: AWT FileDialog shows the native folder chooser when set to directories mode. */
private fun pickFolderWithNativeDialog(onResult: (PickedFolder?) -> Unit) {
    val previous = System.getProperty("apple.awt.fileDialogForDirectories")
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    try {
        val dialog = FileDialog(null as Frame?, "Select a music folder", FileDialog.LOAD)
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        val picked = if (dir != null && file != null) {
            val path = File(dir, file).absolutePath
            PickedFolder(path, File(path).name)
        } else {
            null
        }
        onResult(picked)
    } finally {
        if (previous == null) {
            System.clearProperty("apple.awt.fileDialogForDirectories")
        } else {
            System.setProperty("apple.awt.fileDialogForDirectories", previous)
        }
    }
}

private fun pickFolderWithFileChooser(onResult: (PickedFolder?) -> Unit) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Select a music folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val dir = chooser.selectedFile
        onResult(PickedFolder(dir.absolutePath, dir.name))
    } else {
        onResult(null)
    }
}

@Composable
actual fun rememberImagePicker(onResult: (PickedImage?) -> Unit): () -> Unit {
    return {
        val chooser = JFileChooser().apply {
            dialogTitle = "Pick a cover image"
            fileFilter = FileNameExtensionFilter(
                "Images (jpg, png, webp)",
                "jpg", "jpeg", "png", "webp", "bmp", "gif",
            )
        }
        EventQueue.invokeLater {
            val choice = chooser.showOpenDialog(null)
            if (choice == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                val bytes = runCatching { file.readBytes() }.getOrNull()
                if (bytes != null && bytes.isNotEmpty()) {
                    onResult(PickedImage(bytes, file.name))
                } else {
                    onResult(null)
                }
            } else {
                onResult(null)
            }
        }
    }
}

actual suspend fun scanFolderForSongs(folderUri: String): List<ScannedSong> =
    withContext(Dispatchers.IO) {
        val root = File(folderUri)
        if (!root.isDirectory || !root.exists() || !root.canRead()) {
            System.err.println(
                "Musicor: folder scan skipped \"$folderUri\" " +
                    "(exists=${root.exists()}, dir=${root.isDirectory}, readable=${root.canRead()})",
            )
            return@withContext emptyList()
        }
        val result = mutableListOf<ScannedSong>()
        root.walkTopDown().forEach { file ->
            if (file.isFile && isAudioFile(file.name)) {
                result += ScannedSong(
                    path = file.absolutePath,
                    displayName = file.name,
                    metadata = extractMetadata(file),
                )
            }
        }
        System.err.println("Musicor: folder scan \"${root.absolutePath}\" found ${result.size} audio files")
        result
    }

private fun extractMetadata(file: File): SongMetadata? {
    if (!file.extension.equals("mp3", ignoreCase = true)) return null
    return runCatching {
        val mp3 = Mp3File(file)
        val tag2 = mp3.id3v2Tag
        val tag1 = mp3.id3v1Tag
        if (tag2 == null && tag1 == null) return@runCatching null
        val title = tag2?.title?.takeIf { it.isNotBlank() }
            ?: tag1?.title?.takeIf { it.isNotBlank() }
        val artist = tag2?.artist?.takeIf { it.isNotBlank() }
            ?: tag1?.artist?.takeIf { it.isNotBlank() }
        val year = tag2?.year?.takeIf { it.isNotBlank() }
            ?: tag1?.year?.takeIf { it.isNotBlank() }
        val art = tag2?.albumImage
        SongMetadata(
            title = title,
            artist = artist,
            releaseDate = year,
            artBytes = art?.takeIf { it.isNotEmpty() },
        )
    }.getOrNull()
}

private fun isLinux(): Boolean =
    System.getProperty("os.name").lowercase().contains("linux")

/**
 * Receives decoded PCM frames. A [PcmSink] can back either the Java Sound
 * line or an external player process (pacat -> PulseAudio/PipeWire), which
 * is what actually produces sound on many Linux desktops.
 */
private interface PcmSink {
    val isRunning: Boolean
    fun start()
    fun write(buffer: ByteArray, offset: Int, length: Int)
    fun stop()
    fun flush()
    fun close()
}

private class JavaLineSink(private val line: SourceDataLine) : PcmSink {
    override val isRunning: Boolean get() = line.isRunning
    override fun start() {
        runCatching { line.start() }
    }
    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        line.write(buffer, offset, length)
    }
    override fun stop() {
        runCatching { line.stop() }
    }
    override fun flush() {
        runCatching { line.flush() }
    }
    override fun close() {
        runCatching { line.close() }
    }
}

/**
 * Plays PCM through PulseAudio/PipeWire (the desktop's normal output), so
 * the music comes out of the same device and volume as every other app.
 */
private class PacatSink(private val audioFormat: AudioFormat) : PcmSink {
    private var process: Process? = null
    private var out: OutputStream? = null

    override val isRunning: Boolean
        get() = process?.isAlive == true

    private fun ensureStarted() {
        if (process != null) return
        val command = listOf(
            "pacat", "--playback",
            "--format=s16le",
            "--rate=${audioFormat.sampleRate.toInt()}",
            "--channels=${audioFormat.channels}",
            "--latency-msec=500",
        )
        val fresh = runCatching {
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull() ?: return
        process = fresh
        out = fresh.outputStream
    }

    override fun start() = ensureStarted()

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        ensureStarted()
        val stream = out ?: return
        runCatching {
            stream.write(buffer, offset, length)
            stream.flush() // keep the pipe feeding promptly
        }
    }

    /** Kills the player process; pause()/stop() must silence the output. */
    override fun stop() {
        runCatching { out?.close() }
        runCatching { process?.destroy() }
        process = null
        out = null
    }

    override fun flush() = Unit

    override fun close() {
        runCatching { out?.close() }
        runCatching { process?.destroy() }
        process = null
        out = null
    }
}

private val pacatAvailable: Boolean by lazy {
    runCatching {
        val p = ProcessBuilder("pacat", "--version").redirectErrorStream(true).start()
        p.waitFor()
        p.exitValue() == 0
    }.getOrDefault(false)
}

/** Stores the last playback session in `~/.musicor/playback-state.txt`. */
actual object PlaybackStateStore {
    private val file: File? by lazy {
        runCatching {
            val dir = File(System.getProperty("user.home"), ".musicor")
            dir.mkdirs()
            File(dir, "playback-state.txt")
        }.getOrNull()
    }

    actual fun save(payload: String) {
        val target = file ?: return
        runCatching { target.writeText(payload) }
    }

    actual fun load(): String? {
        val target = file ?: return null
        return runCatching { if (target.isFile) target.readText() else null }.getOrNull()
    }
}

/** True when the format is raw 16-bit little-endian PCM (what pacat pipes). */
private fun is16BitLittleEndianPcm(format: AudioFormat): Boolean =
    format.encoding == AudioFormat.Encoding.PCM_SIGNED &&
        format.sampleSizeInBits == 16 &&
        !format.isBigEndian &&
        format.frameSize == format.channels * 2 &&
        format.sampleRate > 0f

/**
 * Picks the best audio sink for [format]: PulseAudio/PipeWire when the
 * machine has pacat, then any Java Sound mixer whose line actually advances
 * in real time (a self-test skips null sinks that swallow audio silently),
 * and the plain default line as a last resort.
 */
private fun openBestSink(format: AudioFormat): PcmSink {
    if (isLinux() && is16BitLittleEndianPcm(format) && pacatAvailable) {
        return PacatSink(format)
    }
    javaSink(format)?.let { return it }
    val line = AudioSystem.getSourceDataLine(format)
    return JavaLineSink(line)
}

private fun javaSink(format: AudioFormat): PcmSink? {
    if (format.frameRate <= 0f || format.frameSize <= 0) return null
    val lineInfo = DataLine.Info(SourceDataLine::class.java, format)
    val mixers = AudioSystem.getMixerInfo()
        .sortedBy { if (it.name.lowercase().contains("default")) 1 else 0 }
    for (mixerInfo in mixers) {
        val candidate = try {
            AudioSystem.getMixer(mixerInfo).getLine(lineInfo) as? SourceDataLine
        } catch (_: Exception) {
            null
        } ?: continue
        val paces = try {
            candidate.open(format)
            candidate.start()
            val probeSize = maxOf(4096, (format.frameRate * format.frameSize * 0.2).toInt())
            val probe = ByteArray(probeSize)
            val start = System.nanoTime()
            var written = 0
            while (written < probeSize) {
                written += candidate.write(probe, written, probeSize - written)
            }
            candidate.drain()
            val wallMs = (System.nanoTime() - start) / 1_000_000L
            val expectedMs = probeSize * 1000.0 / format.frameRate / format.frameSize
            wallMs >= expectedMs * 0.35
        } catch (_: Exception) {
            false
        }
        if (paces) return JavaLineSink(candidate)
        runCatching { candidate.close() }
    }
    return null
}

actual class PlayerController actual constructor() {

    private var queue: List<PlayableItem> = emptyList()
    private var currentIndex: Int = -1
    private var manualStop = false
    private var generation = 0

    /** The audio sink for the whole playback session (reused across tracks). */
    private var line: PcmSink? = null
    private var lineFormat: AudioFormat? = null

    @Volatile
    private var playedBytes: Long = 0L

    @Volatile
    private var pendingSeekMs: Long = -1L

    /** When true the pump is blocked (paused); the sink stays open so resuming
     *  is instant and glitch-free. */
    @Volatile
    private var paused = false
    private val pauseMonitor = Object()

    @Volatile
    private var streamFrameLength: Long = 0L
    @Volatile
    private var streamDurationMs: Long = 0L
    @Volatile
    private var streamFormat: AudioFormat? = null

    actual var onStateChanged: (() -> Unit)? = null
    actual var loopEnabled: Boolean = false

    actual val currentTitle: String?
        get() = queue.getOrNull(currentIndex)?.title

    actual val currentArtist: String?
        get() = queue.getOrNull(currentIndex)?.artist

    /** Reuses the open sink while the track format stays the same. */
    private fun sinkFor(format: AudioFormat): PcmSink {
        val current = line
        if (current != null && lineFormat == format) return current
        releaseLine()
        val fresh = openBestSink(format)
        line = fresh
        lineFormat = format
        return fresh
    }

    actual fun playFromQueue(queue: List<PlayableItem>, startIndex: Int) {
        this.queue = queue
        stop()
        if (queue.isEmpty() || startIndex !in queue.indices) return
        currentIndex = startIndex
        startPlayback()
    }

    actual fun loadAt(queue: List<PlayableItem>, startIndex: Int, positionMillis: Long) {
        this.queue = queue
        stop()
        if (queue.isEmpty() || startIndex !in queue.indices) return
        currentIndex = startIndex
        manualStop = false
        // Remember the saved playhead; playLoop picks it up once the user
        // presses play (toggle sees no running line and starts the engine).
        pendingSeekMs = positionMillis.coerceAtLeast(0L)
        onStateChanged?.invoke()
    }

    actual fun toggle() {
        val current = line
        if (current == null) {
            // No active sink: either nothing is loaded, or (after loadAt) the
            // session is loaded but not yet playing. Start it now.
            if (queue.isNotEmpty() && currentIndex in queue.indices && !manualStop) {
                startPlayback()
            }
            onStateChanged?.invoke()
            return
        }
        synchronized(pauseMonitor) {
            paused = !paused
            pauseMonitor.notifyAll()
        }
        onStateChanged?.invoke()
    }

    actual fun next() {
        val target = currentIndex + 1
        if (target in queue.indices) {
            currentIndex = target
            startPlayback()
        } else {
            stop()
        }
    }

    actual fun previous() {
        if (currentIndex > 0) {
            currentIndex -= 1
            startPlayback()
        } else if (currentIndex >= 0) {
            startPlayback()
        }
    }

    actual fun seekTo(positionMillis: Long) {
        if (line == null) return
        pendingSeekMs = positionMillis.coerceAtLeast(0L)
        // The pump thread notices the flag, restarts decoding from the file
        // and skips ahead to the requested position.
        onStateChanged?.invoke()
    }

    actual fun stop() {
        generation++
        manualStop = true
        synchronized(pauseMonitor) {
            paused = false
            pauseMonitor.notifyAll()
        }
        currentIndex = -1
        releaseLine()
        playedBytes = 0
        pendingSeekMs = -1
        onStateChanged?.invoke()
    }

    actual val isPlaying: Boolean
        get() = !paused && line?.isRunning == true

    actual val currentPath: String?
        get() = queue.getOrNull(currentIndex)?.path

    actual val durationMillis: Long
        get() {
            if (streamDurationMs > 0L) return streamDurationMs
            val format = streamFormat ?: return 0L
            if (streamFrameLength <= 0L || format.frameRate <= 0f) return 0L
            return streamFrameLength * 1000L / format.frameRate.toLong()
        }

    /**
     * Duration of the current decoded stream in milliseconds. Most formats
     * report it through the stream length, but MP3 (mp3spi) gives `-1`, so
     * read the duration straight from the MPEG frame headers instead.
     */
    private fun streamDurationFor(frameLength: Long, format: AudioFormat, path: String): Long {
        if (frameLength > 0L && format.frameRate > 0f) {
            return frameLength * 1000L / format.frameRate.toLong()
        }
        if (!path.substringAfterLast('.', "").equals("mp3", ignoreCase = true)) return 0L
        return runCatching { Mp3File(File(path)).lengthInMilliseconds }.getOrDefault(0L)
    }

    actual val positionMillis: Long
        get() {
            val format = streamFormat ?: return 0L
            if (format.frameRate <= 0f || format.frameSize <= 0) return 0L
            return playedBytes * 1000L / (format.frameRate * format.frameSize).toLong()
        }

    /** Spawns the single playback thread for the current [currentIndex]. */
    private fun startPlayback() {
        manualStop = false
        paused = false
        val gen = generation + 1
        generation = gen
        thread(name = "musicor-playback") {
            playLoop(gen)
        }
    }

    /**
     * Streams the queue until the controller is stopped: decodes each file to
     * PCM and feeds it to a [PcmSink] as it goes, so playback starts as soon
     * as the first buffer is ready instead of after the whole file is decoded.
     * Handles seeking, loop, and auto-advance.
     */
    private fun playLoop(gen: Int) {
        var skipToMs = 0L
        while (generation == gen && !manualStop) {
            if (pendingSeekMs >= 0) {
                skipToMs = pendingSeekMs
                pendingSeekMs = -1L
            }
            val path = queue.getOrNull(currentIndex)?.path ?: break
            var decoded: AudioInputStream? = null
            var currentLine: PcmSink? = null
            try {
                decoded = openDecodedStream(path)
                val format = decoded.format
                streamFormat = format
                streamFrameLength = decoded.frameLength.takeIf { it > 0 } ?: 0L
                streamDurationMs = streamDurationFor(streamFrameLength, format, path)
                playedBytes = msToBytes(skipToMs, format)
                skipToMs = 0L
                if (playedBytes > 0) {
                    skipPcm(decoded, playedBytes)
                }
                currentLine = sinkFor(format)
                currentLine.start()
                line = currentLine
                onStateChanged?.invoke()

                val finished = pump(decoded, currentLine, gen)
                if (!finished) {
                    // Aborted while reading: either the controller stopped
                    // (bumps generation / manualStop) or a seek was requested.
                    if (generation != gen || manualStop) break
                    if (pendingSeekMs >= 0) {
                        skipToMs = pendingSeekMs
                        pendingSeekMs = -1L
                        // Flush the sink but keep it alive: destroying and
                        // recreating the pacat / PulseAudio stream on every
                        // seek causes audible noise bursts (the "TV static"
                        // artefact).  Keeping the same stream open lets the
                        // pipe drain naturally during skipPcm, and playback
                        // resumes cleanly on the same PulseAudio stream.
                        runCatching { line?.flush() }
                        continue // same track, restarted at the new position
                    }
                    break
                }
            } catch (e: Exception) {
                if (generation == gen && !manualStop) {
                    System.err.println("Musicor: playback failed for \"$path\": $e")
                }
                break
            } finally {
                // Keep the sink open: consecutive tracks reuse it.
                runCatching { decoded?.close() }
            }

            if (generation != gen || manualStop) break
            when {
                loopEnabled -> continue // replay the same track
                currentIndex + 1 < queue.size -> {
                    currentIndex++
                    continue
                }
                else -> {
                    stop()
                    break
                }
            }
        }
    }

    /** Reads decoded bytes into the sink until the stream ends. */
    private fun pump(decoded: AudioInputStream, out: PcmSink, gen: Int): Boolean {
        val buffer = ByteArray(32768)
        while (generation == gen && !manualStop) {
            if (pendingSeekMs >= 0L) return false
            if (paused) {
                // Block (keeping the sink open) until play is pressed again.
                synchronized(pauseMonitor) {
                    while (paused && generation == gen && !manualStop) {
                        try { pauseMonitor.wait(100) } catch (_: InterruptedException) { break }
                    }
                }
                if (generation != gen || manualStop) return false
                continue
            }
            val n = decoded.read(buffer)
            if (n < 0) return true // natural end of file
            out.write(buffer, 0, n)
            playedBytes += n
        }
        return false
    }

    private fun openDecodedStream(path: String): AudioInputStream {
        val input = AudioSystem.getAudioInputStream(File(path))
        return runCatching {
            AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, input)
        }.getOrElse {
            // mp3spi's conversion provider is unreachable through AudioSystem's
            // encoding-level lookup (tritonus gates it on the exact "MP3"
            // Encoding class), so ask the provider directly for a concrete
            // PCM format instead.
            MpegFormatConversionProvider().getAudioInputStream(pcmTargetFor(input.format), input)
        }
    }

    private fun pcmTargetFor(source: AudioFormat): AudioFormat {
        val sampleRate = if (source.sampleRate > 0f) source.sampleRate else 44100f
        val channels = if (source.channels > 0) source.channels else 2
        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels,
            channels * 2, sampleRate, false,
        )
    }

    private fun msToBytes(millis: Long, format: AudioFormat): Long {
        if (millis <= 0L || format.frameRate <= 0f || format.frameSize <= 0) return 0L
        return (millis / 1000.0 * format.frameRate * format.frameSize).toLong()
    }

    private fun skipPcm(decoded: AudioInputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(16384)
        while (remaining > 0) {
            val n = decoded.read(buffer, 0, minOf(remaining.toInt(), buffer.size))
            if (n < 0) return
            remaining -= n
        }
    }

    private fun releaseLine() {
        val current = line
        line = null
        lineFormat = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.flush() }
            runCatching { current.close() }
        }
    }
}