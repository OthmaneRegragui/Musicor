package com.regtho.musicor

import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioInputStream
import javazoom.spi.mpeg.sampled.convert.MpegFormatConversionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedLogicDesktopTest {

    /** Mirrors the desktop player's decode fallback: mp3spi's provider is unreachable
     *  through AudioSystem's encoding-level lookup, so it must be asked directly
     *  for a concrete PCM format. Needs no audio device. */
    private fun decodeMp3ToPcm(file: File): Long {
        val input = AudioSystem.getAudioInputStream(file)
        input.use { source ->
            val format = source.format
            val target = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, format.sampleRate, 16,
                format.channels, format.channels * 2, format.sampleRate, false,
            )
            val decoded: AudioInputStream = MpegFormatConversionProvider()
                .getAudioInputStream(target, source)
            decoded.use { pcm ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    val n = pcm.read(buffer)
                    if (n < 0) break
                    total += n
                }
                return total
            }
        }
    }

    @Test
    fun mp3DecodesToPcm() {
        // Regression: the desktop player must decode MP3 to PCM before opening a Clip,
        // otherwise the sound line rejects the stream's raw MPEG format and nothing plays.
        val file = File(checkNotNull(
            SharedLogicDesktopTest::class.java.classLoader.getResource("music/tagged_track.mp3"),
            { "missing jvmTest resources/music fixture" },
        ).toURI())
        val pcmBytes = decodeMp3ToPcm(file)
        assertTrue(pcmBytes > 1000, "expected real PCM audio data, got $pcmBytes bytes")
    }

    @Test
    fun playbackStateRoundTripsThroughStore() {
        // The store writes to ~/.musicor, so point it at a temp dir for this test.
        val tempDir = Files.createTempDirectory("musicor-state-test").toFile()
        val homeBackup = System.getProperty("user.home")
        try {
            System.setProperty("user.home", tempDir.absolutePath)
            val snapshot = PlaybackSnapshot(
                queuePaths = listOf("/music/a.mp3", "/music/b.mp3"),
                currentPath = "/music/b.mp3",
                positionMillis = 42_000L,
            )
            PlaybackStateStore.save(encodePlaybackSnapshot(snapshot))
            val restored = decodePlaybackSnapshot(checkNotNull(PlaybackStateStore.load()))
            assertEquals(snapshot, restored)
        } finally {
            System.setProperty("user.home", homeBackup)
        }
    }
}