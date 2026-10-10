package com.serialscope.rs485.core

/** Fixed capacity byte log used by the raw monitor view. */
class ByteRingLog(val capacity: Int) {

    private val data = ByteArray(capacity)
    private var writeIndex = 0
    private var filled = 0
    private var totalWritten = 0L

    @Synchronized
    fun add(bytes: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        totalWritten += length
        if (length >= capacity) {
            System.arraycopy(bytes, offset + length - capacity, data, 0, capacity)
            writeIndex = 0
            filled = capacity
            return
        }
        val firstChunk = minOf(length, capacity - writeIndex)
        System.arraycopy(bytes, offset, data, writeIndex, firstChunk)
        if (firstChunk < length) {
            System.arraycopy(bytes, offset + firstChunk, data, 0, length - firstChunk)
        }
        writeIndex += length
        if (writeIndex >= capacity) writeIndex -= capacity
        filled = minOf(capacity, filled + length)
    }

    /** Copies the newest bytes into [out], oldest first. */
    @Synchronized
    fun newest(out: ByteArray): Int {
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
    fun totalWritten(): Long = totalWritten

    @Synchronized
    fun clear() {
        writeIndex = 0
        filled = 0
        totalWritten = 0L
        data.fill(0)
    }
}