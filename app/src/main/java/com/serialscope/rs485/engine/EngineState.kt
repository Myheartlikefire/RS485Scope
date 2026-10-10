package com.serialscope.rs485.engine

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

data class EngineState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceId: Int = -1,
    val deviceLabel: String? = null,
    val portLabel: String? = null,
    val errorMessage: String? = null,
    val bytesReceived: Long = 0L,
    val bytesSent: Long = 0L,
    val framesReceived: Long = 0L,
    val discardedFrames: Long = 0L,
    val discardedBytes: Long = 0L,
    val sampleRateHz: Float = 0f,
    val rxBytesPerSecond: Float = 0f,
    val activeChannels: Int = 0
) {
    val connected: Boolean get() = connection == ConnectionState.CONNECTED
}