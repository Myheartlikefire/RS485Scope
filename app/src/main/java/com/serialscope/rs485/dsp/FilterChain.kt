package com.serialscope.rs485.dsp

/**
 * Ordered chain of filter stages applied to one channel.
 *
 * [applyStream] always resets the chain and warms it up with the first [warmup]
 * input samples, so the rendered trace is identical no matter when the user
 * changed a parameter: no transient, no stale filter state.
 */
class FilterChain private constructor(
    private val filters: Array<SampleFilter>,
    private val signature: String
) {

    val size: Int get() = filters.size

    val isEmpty: Boolean get() = filters.isEmpty()

    fun reset() {
        for (f in filters) f.reset()
    }

    fun process(x: Float): Float {
        var v = x
        for (i in filters.indices) {
            v = filters[i].process(v)
        }
        return v
    }

    /**
     * Filters `input[inOffset until inOffset + length]`. The first [warmup]
     * samples only prime the filter state; the following [outLength] results are
     * written to [out] starting at index 0. Returns the number written.
     */
    fun applyStream(
        input: FloatArray,
        inOffset: Int,
        length: Int,
        warmup: Int,
        out: FloatArray,
        outLength: Int = out.size
    ): Int {
        val wanted = minOf(outLength, out.size)
        if (wanted <= 0) return 0
        if (filters.isEmpty()) {
            val n = minOf(length, wanted)
            val start = inOffset + length - n
            System.arraycopy(input, start, out, 0, n)
            return n
        }
        reset()
        var produced = 0
        for (i in 0 until length) {
            val v = process(input[inOffset + i])
            if (i >= warmup) {
                if (produced < wanted) out[produced] = v
                produced++
            }
        }
        return minOf(produced, wanted)
    }

    /** Cheap identity of the current configuration, used to skip redundant work. */
    fun signature(): String = signature

    companion object {
        val EMPTY = FilterChain(emptyArray(), "")

        fun of(stages: List<FilterStage>, sampleRate: Double): FilterChain {
            val active = stages.filter { it.enabled }
            if (active.isEmpty()) return EMPTY
            val filters = Array<SampleFilter>(active.size) { active[it].spec.create(sampleRate) }
            val signature = active.joinToString("|") { "${it.spec::class.simpleName}:${it.spec}" }
            return FilterChain(filters, signature)
        }
    }
}