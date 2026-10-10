package com.serialscope.rs485.dsp

/** Declarative description of one filter stage, editable from the UI. */
sealed interface FilterSpec {
    val kindLabel: String
    fun create(sampleRate: Double): SampleFilter
    fun describe(sampleRate: Double): String
}

data class BiquadSpec(
    val type: BiquadType,
    val freqHz: Float,
    val q: Float = 0.7071f,
    val gainDb: Float = 0f
) : FilterSpec {

    override val kindLabel: String get() = type.label

    override fun create(sampleRate: Double): SampleFilter =
        Biquad.design(type, freqHz.toDouble(), sampleRate, q.toDouble(), gainDb.toDouble())
            ?: PassThrough

    override fun describe(sampleRate: Double): String = when (type) {
        BiquadType.PEAKING, BiquadType.LOW_SHELF, BiquadType.HIGH_SHELF ->
            "%.1f Hz  %+.1f dB  Q=%.2f".format(freqHz, gainDb, q)
        else ->
            "%.1f Hz  Q=%.2f".format(freqHz, q)
    }
}

/** Mains style notch: centre frequency, bandwidth and how deep the notch goes. */
data class NotchSpec(
    val freqHz: Float,
    val bandwidthHz: Float,
    val depth: Float = 1f
) : FilterSpec {

    override val kindLabel: String get() = "陷波"

    override fun create(sampleRate: Double): SampleFilter {
        val f0 = freqHz.toDouble()
        val bw = bandwidthHz.toDouble().coerceAtLeast(0.01)
        val q = (f0 / bw).coerceIn(0.05, 30.0)
        val notch = Biquad.design(BiquadType.NOTCH, f0, sampleRate, q) ?: return PassThrough
        return if (depth >= 0.999f) notch else BlendFilter(notch, depth)
    }

    override fun describe(sampleRate: Double): String =
        "%.1f Hz  带宽 %.1f Hz  深度 %.0f%%".format(freqHz, bandwidthHz, depth * 100f)
}

data class MovingAverageSpec(val taps: Int) : FilterSpec {
    override val kindLabel: String get() = "滑动平均"
    override fun create(sampleRate: Double): SampleFilter = MovingAverage(taps)
    override fun describe(sampleRate: Double): String {
        val ms = if (sampleRate > 0) taps * 1000.0 / sampleRate else 0.0
        return "$taps 点  ≈ %.1f ms".format(ms)
    }
}

data class MedianSpec(val taps: Int) : FilterSpec {
    override val kindLabel: String get() = "中值"
    override fun create(sampleRate: Double): SampleFilter = MedianFilter(taps)
    override fun describe(sampleRate: Double): String = "$taps 点"
}

data class DcBlockerSpec(val pole: Float = 0.9995f) : FilterSpec {
    override val kindLabel: String get() = "去直流"
    override fun create(sampleRate: Double): SampleFilter = DcBlocker(pole)
    override fun describe(sampleRate: Double): String = "一阶高通 r=%.4f".format(pole)
}

/** Used when a filter cannot be designed (e.g. frequency above Nyquist). */
object PassThrough : SampleFilter {
    override fun reset() = Unit
    override fun process(x: Float): Float = x
}

/** One entry in a channel filter chain. [id] is stable so the UI list keeps its keys. */
data class FilterStage(
    val id: Long,
    val enabled: Boolean = true,
    val spec: FilterSpec
)