package com.serialscope.rs485.parse

/**
 * Fixed length binary frame decoder, e.g.
 *
 *     AA 55 | ch0 int16 | ch1 int16 | checksum | tail
 *
 * The parser synchronises on the header, validates the optional checksum and
 * emits one frame per valid packet.
 */
class BinaryFrameParser(private val config: BinaryFrameConfig) : FrameParser() {

    override val label: String get() = "二进制帧"

    private val header = config.header
    private val hasHeader = header.isNotEmpty()
    private val frameBytes = config.frameBytes
    private val values = FloatArray(config.channelCount.coerceAtLeast(1))
    private val buffer = ByteArray(frameBytes.coerceAtLeast(1))

    private var bufferLength = 0
    private var headerMatched = 0
    private var readingPayload = !hasHeader

    override fun reset() {
        super.reset()
        bufferLength = 0
        headerMatched = 0
        readingPayload = !hasHeader
    }

    override fun feed(data: ByteArray, offset: Int, length: Int, sink: SampleSink) {
        if (frameBytes <= 0) return
        val end = offset + length
        var i = offset
        while (i < end) {
            if (!readingPayload) {
                val b = data[i]
                i++
                if (b == header[headerMatched]) {
                    headerMatched++
                    if (headerMatched == header.size) {
                        headerMatched = 0
                        readingPayload = true
                        bufferLength = 0
                    }
                } else {
                    headerMatched = if (b == header[0]) 1 else 0
                    discardedBytes++
                }
                continue
            }

            val need = frameBytes - bufferLength
            val available = end - i
            val take = if (available < need) available else need
            System.arraycopy(data, i, buffer, bufferLength, take)
            bufferLength += take
            i += take

            if (bufferLength == frameBytes) {
                decode(sink)
                bufferLength = 0
                readingPayload = !hasHeader
            }
        }
    }

    private fun decode(sink: SampleSink) {
        val payloadBytes = config.payloadBytes
        if (config.checksumBytes == 1) {
            val given = buffer[payloadBytes].toInt() and 0xFF
            val computed = when (config.checksum) {
                ChecksumMode.SUM8 -> {
                    var s = 0
                    for (k in 0 until payloadBytes) s += buffer[k].toInt() and 0xFF
                    s and 0xFF
                }
                ChecksumMode.XOR8 -> {
                    var s = 0
                    for (k in 0 until payloadBytes) s = s xor (buffer[k].toInt() and 0xFF)
                    s and 0xFF
                }
                ChecksumMode.NONE -> given
            }
            if (given != computed) {
                discardedFrames++
                discardedBytes += frameBytes
                return
            }
        }

        var p = 0
        for (c in values.indices) {
            val raw = readValue(p)
            values[c] = raw * config.scale + config.offset
            p += config.sampleType.byteSize
        }
        sink.onFrame(values, values.size)
    }

    private fun readValue(offset: Int): Float {
        val be = config.bigEndian
        return when (config.sampleType) {
            SampleType.INT16 -> {
                val lo = buffer[offset].toInt() and 0xFF
                val hi = buffer[offset + 1].toInt() and 0xFF
                val v = if (be) (lo shl 8) or hi else lo or (hi shl 8)
                v.toShort().toInt().toFloat()
            }
            SampleType.UINT16 -> {
                val lo = buffer[offset].toInt() and 0xFF
                val hi = buffer[offset + 1].toInt() and 0xFF
                val v = if (be) (lo shl 8) or hi else lo or (hi shl 8)
                v.toFloat()
            }
            SampleType.INT32 -> {
                var v = 0
                if (be) {
                    for (k in 0 until 4) v = (v shl 8) or (buffer[offset + k].toInt() and 0xFF)
                } else {
                    for (k in 3 downTo 0) v = (v shl 8) or (buffer[offset + k].toInt() and 0xFF)
                }
                v.toFloat()
            }
            SampleType.FLOAT32 -> {
                var bits = 0
                if (be) {
                    for (k in 0 until 4) bits = (bits shl 8) or (buffer[offset + k].toInt() and 0xFF)
                } else {
                    for (k in 3 downTo 0) bits = (bits shl 8) or (buffer[offset + k].toInt() and 0xFF)
                }
                Float.fromBits(bits)
            }
        }
    }
}