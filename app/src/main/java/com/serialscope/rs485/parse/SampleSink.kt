package com.serialscope.rs485.parse

/** Receives decoded multi channel frames from a [FrameParser]. */
fun interface SampleSink {
    fun onFrame(values: FloatArray, count: Int)
}