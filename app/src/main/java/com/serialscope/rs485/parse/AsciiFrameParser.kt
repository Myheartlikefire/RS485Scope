package com.serialscope.rs485.parse

/**
 * Line oriented parser: `1.23,4.56,7.89\n` -> one frame with three channels.
 *
 * Tokens may be separated by a configurable delimiter as well as spaces/tabs,
 * so both `a,b,c` and `a b c` work. Lines that contain no number at all are
 * silently skipped, which makes CSV headers harmless.
 */
class AsciiFrameParser(
    private val delimiter: Char = ',',
    private val maxChannels: Int = 16
) : FrameParser() {

    override val label: String get() = "文本/CSV"

    private val delimiterByte: Byte = delimiter.code.toByte()
    private val lineBuffer = ByteArray(MAX_LINE)
    private var lineLength = 0
    private var skipNextLf = false

    private val values = FloatArray(maxChannels)
    private var lastFlushMillis = 0L

    override fun reset() {
        super.reset()
        lineLength = 0
        skipNextLf = false
    }

    override fun feed(data: ByteArray, offset: Int, length: Int, sink: SampleSink) {
        val end = offset + length
        for (i in offset until end) {
            val b = data[i]
            when (b) {
                LF -> {
                    if (skipNextLf) {
                        skipNextLf = false
                    } else {
                        emitLine(sink)
                    }
                }
                CR -> {
                    emitLine(sink)
                    skipNextLf = true
                }
                else -> {
                    skipNextLf = false
                    if (lineLength < lineBuffer.size) {
                        lineBuffer[lineLength++] = b
                    } else {
                        // Overlong line: treat it as a frame boundary so we resynchronise.
                        emitLine(sink)
                    }
                }
            }
        }
        lastFlushMillis = System.currentTimeMillis()
    }

    override fun flush(sink: SampleSink) {
        if (lineLength > 0 && System.currentTimeMillis() - lastFlushMillis > IDLE_FLUSH_MILLIS) {
            emitLine(sink)
        }
    }

    private fun emitLine(sink: SampleSink) {
        val count = parseNumbers(lineBuffer, 0, lineLength, values)
        lineLength = 0
        if (count > 0) {
            sink.onFrame(values, count)
        }
    }

    /** Parses whitespace/delimiter separated decimal numbers, returns how many. */
    private fun parseNumbers(src: ByteArray, from: Int, to: Int, out: FloatArray): Int {
        var count = 0
        var i = from
        while (i < to && count < out.size) {
            // skip separators
            while (i < to && (src[i] == delimiterByte || isSpace(src[i]))) i++
            if (i >= to) break
            val start = i
            while (i < to && src[i] != delimiterByte && !isSpace(src[i])) i++
            val v = parseFloat(src, start, i)
            if (!v.isNaN()) {
                out[count++] = v
            }
        }
        return count
    }

    companion object {
        private const val MAX_LINE = 4096
        private const val LF: Byte = 10
        private const val CR: Byte = 13
        private const val IDLE_FLUSH_MILLIS = 80L

        private fun isSpace(b: Byte): Boolean =
            b == ' '.code.toByte() || b == '\t'.code.toByte()

        /** Allocation free float scanner; returns NaN when the token is not a number. */
        fun parseFloat(src: ByteArray, from: Int, to: Int): Float {
            var i = from
            if (i >= to) return Float.NaN
            var negative = false
            if (src[i] == '-'.code.toByte()) {
                negative = true
                i++
            } else if (src[i] == '+'.code.toByte()) {
                i++
            }
            var mantissa = 0.0
            var digits = 0
            while (i < to) {
                val c = src[i].toInt()
                if (c < '0'.code || c > '9'.code) break
                mantissa = mantissa * 10.0 + (c - '0'.code)
                digits++
                i++
            }
            var exponent = 0
            if (i < to && src[i] == '.'.code.toByte()) {
                i++
                while (i < to) {
                    val c = src[i].toInt()
                    if (c < '0'.code || c > '9'.code) break
                    mantissa = mantissa * 10.0 + (c - '0'.code)
                    exponent--
                    digits++
                    i++
                }
            }
            if (digits == 0) return Float.NaN
            if (i < to && (src[i] == 'e'.code.toByte() || src[i] == 'E'.code.toByte())) {
                i++
                var expNegative = false
                if (i < to && src[i] == '-'.code.toByte()) {
                    expNegative = true
                    i++
                } else if (i < to && src[i] == '+'.code.toByte()) {
                    i++
                }
                var e = 0
                var expDigits = 0
                while (i < to) {
                    val c = src[i].toInt()
                    if (c < '0'.code || c > '9'.code) break
                    e = e * 10 + (c - '0'.code)
                    expDigits++
                    i++
                }
                if (expDigits == 0) return Float.NaN
                exponent += if (expNegative) -e else e
            }
            var value = mantissa
            if (exponent != 0) {
                value *= Math.pow(10.0, exponent.toDouble())
            }
            return (if (negative) -value else value).toFloat()
        }
    }
}