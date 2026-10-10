package com.serialscope.rs485.dsp

/** A single streaming (stateful) filter stage. */
interface SampleFilter {
    fun reset()
    fun process(x: Float): Float
}

/** Blends the wrapped filter output with the dry signal; mix = 1 -> full effect. */
class BlendFilter(private val inner: SampleFilter, private val mix: Float) : SampleFilter {
    private val m = mix.coerceIn(0f, 1f)
    override fun reset() = inner.reset()
    override fun process(x: Float): Float {
        if (m >= 1f) return inner.process(x)
        val y = inner.process(x)
        return x + (y - x) * m
    }
}

/** One pole DC blocker: y[n] = x[n] - x[n-1] + r*y[n-1]. */
class DcBlocker(private val r: Float = 0.9995f) : SampleFilter {
    private var x1 = 0f
    private var y1 = 0f

    override fun reset() {
        x1 = 0f
        y1 = 0f
    }

    override fun process(x: Float): Float {
        val y = x - x1 + r * y1
        x1 = x
        y1 = y
        return y
    }
}

/** Boxcar moving average implemented as a running sum, O(1) per sample. */
class MovingAverage(taps: Int) : SampleFilter {
    private val window = taps.coerceAtLeast(1)
    private val buf = FloatArray(window)
    private var index = 0
    private var sum = 0.0

    override fun reset() {
        buf.fill(0f)
        index = 0
        sum = 0.0
    }

    override fun process(x: Float): Float {
        sum += (x - buf[index]).toDouble()
        buf[index] = x
        index++
        if (index == window) index = 0
        return (sum / window).toFloat()
    }
}

/** Running median, removes isolated spikes while keeping edges. */
class MedianFilter(taps: Int) : SampleFilter {
    private val window = if (taps % 2 == 1) taps.coerceAtLeast(1) else taps.coerceAtLeast(2) + 1
    private val buf = FloatArray(window)
    private val scratch = FloatArray(window)
    private var index = 0
    private var count = 0

    override fun reset() {
        buf.fill(0f)
        index = 0
        count = 0
    }

    override fun process(x: Float): Float {
        buf[index] = x
        index++
        if (index == window) index = 0
        if (count < window) count++

        System.arraycopy(buf, 0, scratch, 0, count)
        // insertion sort, `count` is small (<= 31)
        for (i in 1 until count) {
            val v = scratch[i]
            var j = i - 1
            while (j >= 0 && scratch[j] > v) {
                scratch[j + 1] = scratch[j]
                j--
            }
            scratch[j + 1] = v
        }
        return scratch[count / 2]
    }
}