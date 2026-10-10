package com.serialscope.rs485.engine

import com.serialscope.rs485.core.SampleRingBuffer
import com.serialscope.rs485.core.ScopePalette
import com.serialscope.rs485.core.SignalStats

/**
 * One acquisition channel: the raw sample history plus the values the reader
 * thread publishes for the UI (last decoded sample and window statistics).
 *
 * Visibility is owned by the view model so toggling it is observable by Compose.
 */
class Channel(val index: Int, capacity: Int) {

    val buffer = SampleRingBuffer(capacity)

    @Volatile
    var stats: SignalStats = SignalStats.EMPTY

    @Volatile
    var lastValue: Float = 0f

    @Volatile
    var lastValueMillis: Long = 0L

    val color: Int get() = ScopePalette.colorFor(index)

    val name: String get() = "CH${index + 1}"

    fun clear() {
        buffer.clear()
        stats = SignalStats.EMPTY
        lastValue = 0f
        lastValueMillis = 0L
    }
}