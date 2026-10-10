package com.serialscope.rs485.core

/**
 * Fixed capacity ring buffer for 32-bit float samples.
 *
 * The producer is the USB reader thread, the consumer is the render loop, so every
 * public method is synchronized. Capacity sized to hold a few seconds of data.
 */
class SampleRingBuffer(val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val data = FloatArray(capacity)
    private var writeIndex = 0
    private var filled = 0
    private var totalWritten = 0L

    @Synchronized
    fun add(value: Float) {
        data[writeIndex] = value
        writeIndex++
        if (writeIndex == capacity) writeIndex = 0
        if (filled < capacity) filled++
        totalWritten++
    }

    /** Bulk append; only the first [count] values of [values] are consumed. */
    @Synchronized
    fun addAll(values: FloatArray, count: Int) {
        val n = if (count > values.size) values.size else count
        if (n <= 0) return

        if (n >= capacity) {
            System.arraycopy(values, n - capacity, data, 0, capacity)
            writeIndex = 0
            filled = capacity
            totalWritten += n
            return
        }

        val firstChunk = minOf(n, capacity - writeIndex)
        System.arraycopy(values, 0, data, writeIndex, firstChunk)
        if (firstChunk < n) {
            System.arraycopy(values, firstChunk, data, 0, n - firstChunk)
        }
        writeIndex += n
        if (writeIndex >= capacity) writeIndex -= capacity
        filled = minOf(capacity, filled + n)
        totalWritten += n
    }

    /**
     * Copies the newest samples into [out], oldest first. Returns how many samples
     * were written (equals out.size once the buffer is warm).
     */
    @Synchronized
    fun newest(out: FloatArray): Int {
        val want = minOf(out.size, filled)
        if (want == 0) return 0
        var start = writeIndex - want
        if (start < 0) start += capacity
        val firstChunk = minOf(want, capacity - start)
        System.arraycopy(data, start, out, 0, firstChunk)
        if (firstChunk < want) {
            System.arraycopy(data, 0, out, firstChunk, want - firstChunk)
        }
        return want
    }

    @Synchronized
    fun size(): Int = filled

    @Synchronized
    fun totalWritten(): Long = totalWritten

    @Synchronized
    fun clear() {
        writeIndex = 0
        filled = 0
        totalWritten = 0
        data.fill(0f)
    }
}