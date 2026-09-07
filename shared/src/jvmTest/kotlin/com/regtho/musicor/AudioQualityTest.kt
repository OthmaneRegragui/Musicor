package com.regtho.musicor

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies the desktop audio-quality path: 24-bit (and float) sources are
 * downmixed to 16-bit little-endian with dithering when needed, and the
 * common 16-bit little-endian case is passed through untouched.
 */
class AudioQualityTest {

    @Test
    fun sixteenBitLittleEndianPassesThroughUntouched() {
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false)
        val stream = AudioInputStream(
            ByteArrayInputStream(ByteArray(8000)),
            format,
            2000L,
        )
        assertSame(stream, ditherTo16Bit(stream))
    }

    @Test
    fun twentyFourBitDownmixesToDithered16Bit() {
        val sampleRate = 8000f
        val amplitude = 1L shl 19 // well inside 24-bit range, ~2048 after >> 8
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 24, 1, 3, sampleRate, false)
        val frames = 8000L
        val src = ByteArray((frames * 3).toInt())
        for (i in 0 until frames.toInt()) {
            writeInt24LE(src, i * 3, (sin(2.0 * PI * 440.0 * i / sampleRate.toDouble()) * amplitude).toLong())
        }
        val decoded = AudioInputStream(ByteArrayInputStream(src), format, frames)

        val down = ditherTo16Bit(decoded)
        assertEquals(16, down.format.sampleSizeInBits, "output must be 16-bit")
        assertFalse(down.format.isBigEndian, "output must be little-endian")
        assertEquals(sampleRate, down.format.sampleRate, "sample rate must be preserved")
        assertEquals(1, down.format.channels, "channels must be preserved")

        val out = down.readNBytes((frames * 2).toInt())
        assertEquals(frames * 2, out.size.toLong(), "every frame must be converted")
        // 440Hz at 8kHz: the first quarter period (the sine peak) is around
        // frame 4.5, so frame 4 is a near-peak sample.
        val peakFrame = 4
        val peak = readInt16LE(out, peakFrame * 2)
        val expectedPeak = sin(2.0 * PI * 440.0 * peakFrame / sampleRate.toDouble()) * (amplitude shr 8).toDouble()
        assertEquals(expectedPeak, peak.toDouble(), 4.0, "24-bit sample must convert accurately")
        // And a silent region must stay silent (no DC offset from the dither,
        // triangular noise is zero-mean).
        val silence = readInt16LE(out, 0 * 2)
        assertTrue(kotlin.math.abs(silence) <= 2, "silent samples stay near zero, got $silence")
    }

    @Test
    fun float32DownmixesToDithered16Bit() {
        val sampleRate = 8000f
        val format = AudioFormat(AudioFormat.Encoding.PCM_FLOAT, sampleRate, 32, 2, 8, sampleRate, false)
        val frames = 400L
        val src = ByteArray((frames * 8).toInt())
        for (i in 0 until frames.toInt()) {
            val v = (sin(2.0 * PI * 440.0 * i / sampleRate.toDouble()) * 0.5).toFloat()
            writeFloat32LE(src, i * 8 + 0, v)
            writeFloat32LE(src, i * 8 + 4, v)
        }
        val decoded = AudioInputStream(ByteArrayInputStream(src), format, frames)

        val down = ditherTo16Bit(decoded)
        assertEquals(16, down.format.sampleSizeInBits)
        assertEquals(2, down.format.channels)
        val out = down.readNBytes((frames * 4).toInt())
        assertEquals(frames * 4, out.size.toLong())
        // Same near-peak frame as the 24-bit test (stereo: sample is at frame*4).
        val peakFrame = 4
        val peak = readInt16LE(out, peakFrame * 4)
        val expectedPeak = sin(2.0 * PI * 440.0 * peakFrame / sampleRate.toDouble()) * 0.5 * 32768.0
        assertEquals(expectedPeak, peak.toDouble(), 8.0, "float samples scale into 16-bit range cleanly")
    }

    private fun writeInt24LE(buf: ByteArray, at: Int, v: Long) {
        buf[at] = (v and 0xFF).toByte()
        buf[at + 1] = ((v shr 8) and 0xFF).toByte()
        buf[at + 2] = ((v shr 16) and 0xFF).toByte()
    }

    private fun writeFloat32LE(buf: ByteArray, at: Int, v: Float) {
        val bits = v.toRawBits()
        buf[at] = (bits and 0xFF).toByte()
        buf[at + 1] = ((bits shr 8) and 0xFF).toByte()
        buf[at + 2] = ((bits shr 16) and 0xFF).toByte()
        buf[at + 3] = ((bits shr 24) and 0xFF).toByte()
    }

    private fun readInt16LE(buf: ByteArray, at: Int): Int {
        val lo = buf[at].toInt() and 0xFF
        val hi = buf[at + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }
}