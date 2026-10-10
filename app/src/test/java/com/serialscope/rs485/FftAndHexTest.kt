package com.serialscope.rs485

import com.serialscope.rs485.dsp.Fft
import com.serialscope.rs485.util.Hex
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FftAndHexTest {

    @Test
    fun `a pure tone peaks in its own bin`() {
        val n = 256
        val bin = 16
        val signal = FloatArray(n) { i ->
            sin(2.0 * PI * bin * i / n).toFloat()
        }
        val bins = FloatArray(n / 2 + 1)
        val count = Fft.spectrumDb(signal, 0, n, bins, null)

        assertEquals(n / 2 + 1, count)

        var peak = 0
        for (k in 1 until count) if (bins[k] > bins[peak]) peak = k
        assertEquals(bin, peak)
        assertTrue("peak should be near 0 dB, was ${bins[peak]}", bins[peak] > -3f)
    }

    @Test
    fun `floorPow2 picks the largest power of two that fits`() {
        assertEquals(2, Fft.floorPow2(2))
        assertEquals(2, Fft.floorPow2(3))
        assertEquals(1024, Fft.floorPow2(1024))
        assertEquals(1024, Fft.floorPow2(2000))
    }

    @Test
    fun `hann window is zero at both ends and one in the middle`() {
        val w = Fft.hannWindow(9)
        assertEquals(0f, w[0], 1e-6f)
        assertEquals(0f, w[8], 1e-6f)
        assertEquals(1f, w[4], 1e-6f)
    }

    @Test
    fun `hex round trip tolerates common separators`() {
        val bytes = byteArrayOf(0x01, 0xAB.toByte(), 0xFF.toByte())
        val text = Hex.encode(bytes)
        assertEquals("01 AB FF", text)
        assertArrayEquals(bytes, Hex.parse(text))
        assertArrayEquals(bytes, Hex.parse("01abff"))
        assertArrayEquals(bytes, Hex.parse("0x01, 0xAB, 0xFF"))
    }

    @Test
    fun `hex rejects an odd number of nibbles`() {
        assertNull(Hex.parse("0A1"))
        assertNull(Hex.parse("ZZ"))
    }

    @Test
    fun `text to bytes appends the line ending`() {
        val bytes = Hex.textToBytes("AT", "UTF-8", "\r\n")
        assertEquals(4, bytes.size)
        assertEquals('A'.code.toByte(), bytes[0])
        assertEquals(13.toByte(), bytes[2])
        assertEquals(10.toByte(), bytes[3])
    }

    @Test
    fun `printable ascii masks control characters`() {
        val bytes = byteArrayOf(0x41, 0x00, 0x42)
        assertEquals("A.B", Hex.toPrintableAscii(bytes))
    }
}