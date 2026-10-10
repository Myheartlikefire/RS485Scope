package com.serialscope.rs485.util

/** Hex / ASCII helpers shared by the send and monitor panels. */
object Hex {

    private const val HEX_UPPER = "0123456789ABCDEF"

    fun encode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String {
        if (length <= 0) return ""
        val sb = StringBuilder(length * 3)
        for (i in 0 until length) {
            val b = bytes[offset + i].toInt() and 0xFF
            if (i > 0) sb.append(' ')
            sb.append(HEX_UPPER[b ushr 4]).append(HEX_UPPER[b and 0x0F])
        }
        return sb.toString()
    }

    /** Renders a byte window as printable ASCII, replacing control bytes with '.'. */
    fun toPrintableAscii(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset
    ): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val c = bytes[offset + i].toInt() and 0xFF
            sb.append(if (c in 32..126) c.toChar() else '.')
        }
        return sb.toString()
    }

    /**
     * Parses hex text tolerating spaces, commas, dashes and `0x` prefixes.
     * Returns null when an odd nibble or an invalid character is found.
     */
    fun parse(text: String): ByteArray? {
        val cleaned = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '0' && i + 1 < text.length && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
                i += 2
                continue
            }
            if (c.isWhitespace() || c == ',' || c == '-' || c == ':' || c == '_') {
                i++
                continue
            }
            if (!isHexDigit(c)) return null
            cleaned.append(c)
            i++
        }
        if (cleaned.length == 0) return ByteArray(0)
        if (cleaned.length % 2 != 0) return null
        val out = ByteArray(cleaned.length / 2)
        for (k in out.indices) {
            val hi = hexValue(cleaned[k * 2])
            val lo = hexValue(cleaned[k * 2 + 1])
            if (hi < 0 || lo < 0) return null
            out[k] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Encodes text to bytes, appending the requested line ending. */
    fun textToBytes(text: String, charsetName: String, lineEnding: String): ByteArray {
        val body = text.toByteArray(charset(charsetName))
        if (lineEnding.isEmpty()) return body
        val suffix = lineEnding.toByteArray(charset(charsetName))
        val out = ByteArray(body.size + suffix.size)
        System.arraycopy(body, 0, out, 0, body.size)
        System.arraycopy(suffix, 0, out, body.size, suffix.size)
        return out
    }

    private fun isHexDigit(c: Char): Boolean =
        (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}