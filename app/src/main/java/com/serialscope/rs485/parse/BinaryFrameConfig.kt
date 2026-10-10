package com.serialscope.rs485.parse

enum class SampleType(val byteSize: Int, val label: String) {
    INT16(2, "int16 (有符号)"),
    UINT16(2, "uint16 (无符号)"),
    INT32(4, "int32 (有符号)"),
    FLOAT32(4, "float32 (IEEE754)")
}

enum class ChecksumMode(val label: String) {
    NONE("无"),
    SUM8("累加和 (1 字节)"),
    XOR8("异或 (1 字节)")
}

/** Wire format of a fixed length binary frame. */
data class BinaryFrameConfig(
    val header: ByteArray = byteArrayOf(0xAA.toByte(), 0x55.toByte()),
    val channelCount: Int = 2,
    val sampleType: SampleType = SampleType.INT16,
    val bigEndian: Boolean = false,
    val scale: Float = 1f,
    val offset: Float = 0f,
    val checksum: ChecksumMode = ChecksumMode.NONE,
    val tailBytes: Int = 0
) {
    val payloadBytes: Int get() = channelCount * sampleType.byteSize
    val checksumBytes: Int get() = if (checksum == ChecksumMode.NONE) 0 else 1
    val frameBytes: Int get() = payloadBytes + checksumBytes + tailBytes

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryFrameConfig) return false
        return header.contentEquals(other.header) &&
            channelCount == other.channelCount &&
            sampleType == other.sampleType &&
            bigEndian == other.bigEndian &&
            scale == other.scale &&
            offset == other.offset &&
            checksum == other.checksum &&
            tailBytes == other.tailBytes
    }

    override fun hashCode(): Int {
        var result = header.contentHashCode()
        result = 31 * result + channelCount
        result = 31 * result + sampleType.hashCode()
        result = 31 * result + bigEndian.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + offset.hashCode()
        result = 31 * result + checksum.hashCode()
        result = 31 * result + tailBytes
        return result
    }
}