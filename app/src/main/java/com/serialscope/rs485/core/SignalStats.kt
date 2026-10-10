package com.serialscope.rs485.core

import kotlin.math.sqrt

/** Descriptive statistics of a sample window. */
data class SignalStats(
    val min: Float,
    val max: Float,
    val mean: Float,
    val rms: Float,
    val count: Int
) {
    val peakToPeak: Float get() = max - min

    companion object {
        val EMPTY = SignalStats(0f, 0f, 0f, 0f, 0)

        fun of(values: FloatArray, length: Int): SignalStats {
            val n = minOf(length, values.size)
            if (n <= 0) return EMPTY
            var mn = Float.MAX_VALUE
            var mx = -Float.MAX_VALUE
            var sum = 0.0
            var sq = 0.0
            var used = 0
            for (i in 0 until n) {
                val v = values[i]
                if (v.isNaN() || v.isInfinite()) continue
                if (v < mn) mn = v
                if (v > mx) mx = v
                sum += v
                sq += v.toDouble() * v
                used++
            }
            if (used == 0) return EMPTY
            return SignalStats(
                min = mn,
                max = mx,
                mean = (sum / used).toFloat(),
                rms = sqrt(sq / used).toFloat(),
                count = used
            )
        }
    }
}

/**
 * Estimates the sample rate by counting samples per one second sliding window.
 * Fed from the reader thread, read from the UI thread.
 */
class SampleRateEstimator(private val windowMillis: Long = 1000L) {

    private var windowStart = 0L
    private var samplesInWindow = 0L
    private var rate = 0f

    @Synchronized
    fun onSamples(count: Int, nowMillis: Long = System.currentTimeMillis()) {
        if (windowStart == 0L) windowStart = nowMillis
        samplesInWindow += count
        val elapsed = nowMillis - windowStart
        if (elapsed >= windowMillis) {
            rate = (samplesInWindow * 1000.0 / elapsed).toFloat()
            samplesInWindow = 0L
            windowStart = nowMillis
        }
    }

    @Synchronized
    fun ratePerSecond(): Float = rate

    @Synchronized
    fun reset() {
        windowStart = 0L
        samplesInWindow = 0L
        rate = 0f
    }
}