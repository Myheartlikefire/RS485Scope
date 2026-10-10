package com.serialscope.rs485.core

/** Trace colours shared by the waveform and the channel list. */
object ScopePalette {
    val TRACE_COLORS = intArrayOf(
        0xFF38E1B0.toInt(), // teal
        0xFFFFC44D.toInt(), // amber
        0xFF6FA8FF.toInt(), // blue
        0xFFFF7A8B.toInt(), // rose
        0xFFB48CFF.toInt(), // violet
        0xFF7BE38B.toInt(), // green
        0xFFFF9F45.toInt(), // orange
        0xFF4FD8E8.toInt()  // cyan
    )

    fun colorFor(index: Int): Int = TRACE_COLORS[index % TRACE_COLORS.size]
}