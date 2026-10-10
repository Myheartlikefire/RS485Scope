package com.serialscope.rs485.engine

import com.serialscope.rs485.parse.AsciiFrameParser
import com.serialscope.rs485.parse.BinaryFrameConfig
import com.serialscope.rs485.parse.BinaryFrameParser
import com.serialscope.rs485.parse.FrameParser
import com.serialscope.rs485.parse.RawParser

enum class ParserMode(val label: String) {
    ASCII("文本 / CSV"),
    BINARY("二进制帧"),
    MONITOR_ONLY("仅监视")
}

object ParserFactory {
    fun create(
        mode: ParserMode,
        delimiter: Char,
        binary: BinaryFrameConfig,
        maxChannels: Int
    ): FrameParser = when (mode) {
        ParserMode.ASCII -> AsciiFrameParser(delimiter, maxChannels)
        ParserMode.BINARY -> BinaryFrameParser(binary)
        ParserMode.MONITOR_ONLY -> RawParser()
    }
}