package com.serialscope.rs485.ui

import com.serialscope.rs485.core.SignalStats
import com.serialscope.rs485.dsp.FilterChain
import com.serialscope.rs485.engine.ScopeEngine

/**
 * Per frame render pipeline.
 *
 * Each UI frame the raw ring buffers are snapshotted and pushed through the
 * channel filter chains, warming the filters up with the samples just before the
 * visible window. Rebuilding from the raw history on every frame keeps the trace
 * correct after the user changes a filter parameter mid stream.
 */
class TraceRenderer(private val engine: ScopeEngine) {

    /** Filtered samples (raw when no filter is active), oldest first. */
    val traces: Array<FloatArray> = Array(ScopeEngine.MAX_CHANNELS) { FloatArray(MAX_WINDOW) }
    val lengths = IntArray(ScopeEngine.MAX_CHANNELS)
    val stats: Array<SignalStats> = Array(ScopeEngine.MAX_CHANNELS) { SignalStats.EMPTY }

    /** Unfiltered copies of the same window, only filled when the overlay is on. */
    val rawTraces: Array<FloatArray> = Array(ScopeEngine.MAX_CHANNELS) { FloatArray(MAX_WINDOW) }
    val rawLengths = IntArray(ScopeEngine.MAX_CHANNELS)

    private val scratch = FloatArray(MAX_WINDOW + WARMUP)
    private val lastTotal = LongArray(ScopeEngine.MAX_CHANNELS) { -1L }
    private val lastChainSignature = arrayOfNulls<String>(ScopeEngine.MAX_CHANNELS)
    private var lastWindow = -1

    /**
     * Refreshes every visible channel. [chains] must hold one chain per channel
     * index, [visible] one flag per channel.
     */
    fun update(
        windowSamples: Int,
        chains: List<FilterChain>,
        visible: BooleanArray,
        captureRaw: Boolean
    ) {
        val window = windowSamples.coerceIn(MIN_WINDOW, MAX_WINDOW)
        val want = window + WARMUP
        // A timebase or filter change must repaint even when no new sample arrived.
        val windowChanged = window != lastWindow
        lastWindow = window
        for (i in traces.indices) {
            val channel = engine.channels[i]
            if (!(visible.getOrNull(i) ?: false)) {
                lengths[i] = 0
                rawLengths[i] = 0
                continue
            }
            val chain = chains.getOrElse(i) { FilterChain.EMPTY }
            val signature = chain.signature()
            val chainChanged = lastChainSignature[i] != signature
            lastChainSignature[i] = signature

            val total = channel.buffer.totalWritten()
            if (!windowChanged && !chainChanged && total == lastTotal[i]) continue
            lastTotal[i] = total

            val got = channel.buffer.newest(scratch)
            if (got <= 0) {
                lengths[i] = 0
                rawLengths[i] = 0
                stats[i] = SignalStats.EMPTY
                continue
            }
            val use = minOf(got, want)
            val offset = got - use
            val warmup = (use - window).coerceAtLeast(0)

            val produced = chain.applyStream(scratch, offset, use, warmup, traces[i], window)
            lengths[i] = produced
            stats[i] = SignalStats.of(traces[i], produced)
            channel.stats = stats[i]

            if (captureRaw && !chain.isEmpty) {
                rawLengths[i] = FilterChain.EMPTY.applyStream(
                    scratch, offset, use, warmup, rawTraces[i], window
                )
            } else {
                rawLengths[i] = 0
            }
        }
    }

    fun invalidate() {
        lastTotal.fill(-1L)
        lastChainSignature.fill(null)
        lastWindow = -1
    }

    companion object {
        const val MAX_WINDOW = 8192
        const val MIN_WINDOW = 128
        const val WARMUP = 1024
    }
}