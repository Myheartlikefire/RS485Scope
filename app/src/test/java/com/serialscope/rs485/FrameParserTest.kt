package com.serialscope.rs485

import com.serialscope.rs485.parse.AsciiFrameParser
import com.serialscope.rs485.parse.BinaryFrameConfig
import com.serialscope.rs485.parse.BinaryFrameParser
import com.serialscope.rs485.parse.ChecksumMode
import com.serialscope.rs485.parse.SampleSink
import com.serialscope.rs485.parse.SampleType
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameParserTest {

    private class Collector : SampleSink {
        val frames = mutableListOf<List<Float>>()
        override fun onFrame(values: FloatArray, count: Int) {
            frames.add((0 until count).map { values[it] })
        }
    }

    private fun AsciiFrameParser.feedText(text: String, sink: SampleSink) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        feed(bytes, 0, bytes.size, sink)
    }

    @Test
    fun `csv line becomes one frame with three channels`() {
        val parser = AsciiFrameParser(',')
        val sink = Collector()
        parser.feedText("1.5,-2,3e2\n", sink)

        assertEquals(1, sink.frames.size)
        assertEquals(listOf(1.5f, -2f, 300f), sink.frames[0])
    }

    @Test
    fun `crlf and lf are both accepted without phantom frames`() {
        val parser = AsciiFrameParser(',')
        val sink = Collector()
        parser.feedText("1,2\r\n3,4\n5,6\r\n", sink)

        assertEquals(3, sink.frames.size)
        assertEquals(listOf(1f, 2f), sink.frames[0])
        assertEquals(listOf(3f, 4f), sink.frames[1])
        assertEquals(listOf(5f, 6f), sink.frames[2])
    }

    @Test
    fun `lines without numbers are ignored so csv headers are harmless`() {
        val parser = AsciiFrameParser(',')
        val sink = Collector()
        parser.feedText("time,voltage,current\n0.0,1.0,2.0\n", sink)

        assertEquals(1, sink.frames.size)
        assertEquals(listOf(0f, 1f, 2f), sink.frames[0])
    }

    @Test
    fun `space separated values work too`() {
        val parser = AsciiFrameParser(',')
        val sink = Collector()
        parser.feedText("7 8\t9\n", sink)

        assertEquals(listOf(7f, 8f, 9f), sink.frames[0])
    }

    @Test
    fun `frames split across reads are reassembled`() {
        val parser = AsciiFrameParser(',')
        val sink = Collector()
        val bytes = "12.5,13.5\n".toByteArray()
        parser.feed(bytes, 0, 6, sink)
        assertEquals(0, sink.frames.size)
        parser.feed(bytes, 6, bytes.size - 6, sink)

        assertEquals(1, sink.frames.size)
        assertEquals(listOf(12.5f, 13.5f), sink.frames[0])
    }

    @Test
    fun `binary int16 little endian frames decode with sync recovery`() {
        val config = BinaryFrameConfig(
            header = byteArrayOf(0xAA.toByte(), 0x55.toByte()),
            channelCount = 2,
            sampleType = SampleType.INT16,
            bigEndian = false
        )
        val parser = BinaryFrameParser(config)
        val sink = Collector()

        val stream = byteArrayOf(
            0x00, 0x11, 0x22,                                  // garbage before the header
            0xAA.toByte(), 0x55.toByte(), 0x01, 0x00, 0xFF.toByte(), 0xFF.toByte(),
            0xAA.toByte(), 0x55.toByte(), 0x00, 0x01, 0x00, 0x80.toByte()
        )
        parser.feed(stream, 0, stream.size, sink)

        assertEquals(2, sink.frames.size)
        assertEquals(listOf(1f, -1f), sink.frames[0])
        assertEquals(listOf(256f, -32768f), sink.frames[1])
    }

    @Test
    fun `big endian keeps the byte order`() {
        val config = BinaryFrameConfig(
            header = ByteArray(0),
            channelCount = 1,
            sampleType = SampleType.INT16,
            bigEndian = true
        )
        val parser = BinaryFrameParser(config)
        val sink = Collector()
        val stream = byteArrayOf(0x01, 0x00)
        parser.feed(stream, 0, stream.size, sink)
        assertEquals(listOf(256f), sink.frames[0])
    }

    @Test
    fun `float32 and scale plus offset are applied`() {
        val config = BinaryFrameConfig(
            header = ByteArray(0),
            channelCount = 1,
            sampleType = SampleType.FLOAT32,
            bigEndian = false,
            scale = 2f,
            offset = 1f
        )
        val parser = BinaryFrameParser(config)
        val sink = Collector()
        val bits = java.lang.Float.floatToIntBits(0.5f)
        val stream = byteArrayOf(
            (bits and 0xFF).toByte(),
            ((bits ushr 8) and 0xFF).toByte(),
            ((bits ushr 16) and 0xFF).toByte(),
            ((bits ushr 24) and 0xFF).toByte()
        )
        parser.feed(stream, 0, stream.size, sink)
        assertEquals(2f, sink.frames[0][0], 1e-6f)
    }

    @Test
    fun `sum8 checksum rejects a corrupted frame`() {
        val config = BinaryFrameConfig(
            header = byteArrayOf(0xAA.toByte()),
            channelCount = 2,
            sampleType = SampleType.INT16,
            checksum = ChecksumMode.SUM8
        )
        val parser = BinaryFrameParser(config)
        val sink = Collector()

        val good = byteArrayOf(
            0xAA.toByte(), 0x01, 0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        )
        parser.feed(good, 0, good.size, sink)
        assertEquals(1, sink.frames.size)

        val bad = good.copyOf()
        bad[bad.size - 1] = 0x00
        parser.feed(bad, 0, bad.size, sink)
        assertEquals("corrupted frame must be dropped", 1, sink.frames.size)
        assertEquals(1L, parser.discardedFrames)
    }

    @Test
    fun `frame split across reads is decoded once`() {
        val config = BinaryFrameConfig(
            header = byteArrayOf(0xAA.toByte(), 0x55.toByte()),
            channelCount = 1,
            sampleType = SampleType.INT16
        )
        val parser = BinaryFrameParser(config)
        val sink = Collector()
        val stream = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x0A, 0x00)
        parser.feed(stream, 0, 3, sink)
        assertEquals(0, sink.frames.size)
        parser.feed(stream, 3, 1, sink)
        assertEquals(listOf(10f), sink.frames[0])
    }
}