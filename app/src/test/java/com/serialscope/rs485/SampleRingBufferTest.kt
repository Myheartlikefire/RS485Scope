package com.serialscope.rs485

import com.serialscope.rs485.core.SampleRingBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SampleRingBufferTest {

    @Test
    fun `newest returns chronological order while filling`() {
        val ring = SampleRingBuffer(8)
        ring.add(1f)
        ring.add(2f)
        ring.add(3f)

        val out = FloatArray(3)
        assertEquals(3, ring.newest(out))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), out, 0f)
        assertEquals(3, ring.size())
    }

    @Test
    fun `wrapping keeps only the newest samples`() {
        val ring = SampleRingBuffer(4)
        for (i in 1..6) ring.add(i.toFloat())

        val out = FloatArray(4)
        assertEquals(4, ring.newest(out))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f), out, 0f)
        assertEquals(6L, ring.totalWritten())
    }

    @Test
    fun `asked window smaller than content returns the tail`() {
        val ring = SampleRingBuffer(16)
        for (i in 1..10) ring.add(i.toFloat())

        val out = FloatArray(3)
        assertEquals(3, ring.newest(out))
        assertArrayEquals(floatArrayOf(8f, 9f, 10f), out, 0f)
    }

    @Test
    fun `bulk append larger than capacity keeps the tail`() {
        val ring = SampleRingBuffer(4)
        val input = FloatArray(9) { (it + 1).toFloat() }
        ring.addAll(input, input.size)

        val out = FloatArray(4)
        assertEquals(4, ring.newest(out))
        assertArrayEquals(floatArrayOf(6f, 7f, 8f, 9f), out, 0f)
    }

    @Test
    fun `bulk append wraps correctly`() {
        val ring = SampleRingBuffer(4)
        ring.addAll(floatArrayOf(1f, 2f, 3f), 3)
        ring.addAll(floatArrayOf(4f, 5f), 2)

        val out = FloatArray(4)
        assertEquals(4, ring.newest(out))
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f), out, 0f)
    }
}