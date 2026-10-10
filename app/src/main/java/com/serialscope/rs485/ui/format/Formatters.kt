package com.serialscope.rs485.ui.format

import kotlin.math.abs

/** Number formatting helpers shared by the panels. */
object Fmt {

    fun hz(value: Float): String = when {
        value <= 0f -> "-"
        value >= 1_000_000f -> "%.2f MHz".format(value / 1_000_000f)
        value >= 1_000f -> "%.2f kHz".format(value / 1_000f)
        else -> "%.1f Hz".format(value)
    }

    fun rate(value: Float): String = if (value <= 0f) "-" else hz(value)

    fun bytes(value: Long): String = when {
        value >= 1_048_576L -> "%.2f MB".format(value / 1_048_576.0)
        value >= 1024L -> "%.1f KB".format(value / 1024.0)
        else -> "$value B"
    }

    fun count(value: Long): String = when {
        value >= 1_000_000L -> "%.2f M".format(value / 1_000_000.0)
        value >= 1_000L -> "%.1f K".format(value / 1_000.0)
        else -> value.toString()
    }

    /** Compact engineering notation for a sample value. */
    fun sample(value: Float): String {
        if (value.isNaN()) return "NaN"
        val a = abs(value)
        return when {
            a >= 100_000f -> "%.0f".format(value)
            a >= 1000f -> "%.1f".format(value)
            a >= 1f -> "%.3f".format(value)
            a >= 0.001f -> "%.4f".format(value)
            a == 0f -> "0"
            else -> "%.3e".format(value)
        }
    }

    fun msPerWindow(samples: Int, sampleRate: Double): String {
        if (sampleRate <= 0.0) return "-"
        val ms = samples * 1000.0 / sampleRate
        return if (ms >= 1000.0) "%.2f s".format(ms / 1000.0) else "%.1f ms".format(ms)
    }
}