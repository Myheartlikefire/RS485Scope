package com.serialscope.rs485.usb

enum class SerialParity(val label: String) {
    NONE("无校验"), ODD("奇校验"), EVEN("偶校验"), MARK("标记"), SPACE("空格")
}

enum class SerialFlowControl(val label: String) {
    NONE("无"), RTS_CTS("RTS/CTS"), DTR_DSR("DTR/DSR"), XON_XOFF("XON/XOFF")
}

/**
 * Port parameters for the USB <-> UART bridge that drives the RS485 transceiver.
 *
 * RS485 half duplex adapters usually turn the driver around automatically; when
 * the board wires DE/RE to the RTS pin, enable [rtsForDirection].
 */
data class SerialPortConfig(
    val baudRate: Int = 115200,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: SerialParity = SerialParity.NONE,
    val flowControl: SerialFlowControl = SerialFlowControl.NONE,
    val rtsForDirection: Boolean = false,
    val dtr: Boolean = false,
    val rts: Boolean = false
) {
    val label: String
        get() = "$baudRate · ${dataBits}${parityLetter()}${stopBits}"

    private fun parityLetter(): String = when (parity) {
        SerialParity.NONE -> "N"
        SerialParity.ODD -> "O"
        SerialParity.EVEN -> "E"
        SerialParity.MARK -> "M"
        SerialParity.SPACE -> "S"
    }

    companion object {
        val BAUD_RATES = intArrayOf(
            1200, 2400, 4800, 9600, 19200, 38400, 57600,
            115200, 230400, 460800, 921600, 1000000, 2000000
        )
        val DATA_BITS = intArrayOf(5, 6, 7, 8)
        val STOP_BITS = intArrayOf(1, 2)
    }
}