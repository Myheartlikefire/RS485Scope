package com.serialscope.rs485.parse

/** Turns a raw byte stream into frames of float samples. Not thread safe. */
abstract class FrameParser {

    abstract val label: String

    /** Bytes that were dropped because they did not form a valid frame. */
    var discardedBytes: Long = 0L
        protected set

    var discardedFrames: Long = 0L
        protected set

    open fun reset() {
        discardedBytes = 0L
        discardedFrames = 0L
    }

    abstract fun feed(data: ByteArray, offset: Int, length: Int, sink: SampleSink)

    /** Called when the stream goes idle, e.g. to flush a partially received line. */
    open fun flush(sink: SampleSink) = Unit
}

/** Pass-through parser used for "monitor only" / raw byte mode. */
class RawParser : FrameParser() {
    override val label: String get() = "原始字节"
    override fun feed(data: ByteArray, offset: Int, length: Int, sink: SampleSink) = Unit
}