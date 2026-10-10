package com.serialscope.rs485.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.serialscope.rs485.core.ByteRingLog
import com.serialscope.rs485.core.SampleRateEstimator
import com.serialscope.rs485.parse.AsciiFrameParser
import com.serialscope.rs485.parse.BinaryFrameConfig
import com.serialscope.rs485.parse.FrameParser
import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.serialscope.rs485.parse.SampleSink
import com.serialscope.rs485.usb.SerialLink
import com.serialscope.rs485.usb.SerialPortConfig
import com.serialscope.rs485.usb.UsbPermissionHelper
import com.serialscope.rs485.usb.UsbSerialDevice
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the whole acquisition path:
 *
 *   USB bulk endpoint -> [SerialLink] -> [FrameParser] -> per channel ring buffers
 *
 * Decoding runs on a dedicated high priority thread; the UI only ever reads
 * snapshots, so rendering never blocks reception.
 */
class ScopeEngine(context: Context) {

    private val appContext = context.applicationContext
    private val link = SerialLink(appContext)
    private val permissionHelper = UsbPermissionHelper(appContext)

    val channels: List<Channel> = List(MAX_CHANNELS) { Channel(it, RING_CAPACITY) }
    val monitorLog = ByteRingLog(MONITOR_CAPACITY)

    private val bytesReceived = AtomicLong()
    private val bytesSent = AtomicLong()
    private val framesReceived = AtomicLong()
    private val frameRate = SampleRateEstimator()
    private val byteRate = SampleRateEstimator()

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    private var parser: FrameParser = AsciiFrameParser(DEFAULT_DELIMITER, MAX_CHANNELS)

    @Volatile
    private var parserSignature: String = ""

    @Volatile
    private var running: Boolean = false

    @Volatile
    private var activeChannels: Int = 0

    @Volatile
    private var connectedDeviceId: Int = -1

    private var readerThread: Thread? = null
    private var tickerJob: Job? = null
    private var detachReceiver: BroadcastReceiver? = null
    private val writeLock = Any()

    /** Latest sample decoded for each channel, refreshed every UI frame. */
    private val sink = SampleSink { values, count ->
        val n = minOf(count, channels.size)
        val now = System.currentTimeMillis()
        for (i in 0 until n) {
            val ch = channels[i]
            ch.buffer.add(values[i])
            ch.lastValue = values[i]
            ch.lastValueMillis = now
        }
        if (n > activeChannels) activeChannels = n
        framesReceived.incrementAndGet()
        frameRate.onSamples(1, now)
    }

    init {
        registerDetachReceiver()
    }

    // ---------------------------------------------------------------- discovery

    fun listDevices(): List<UsbSerialDevice> = link.listDevices()

    fun findDevice(deviceId: Int): UsbDevice? = link.findDevice(deviceId)

    fun hasPermission(device: UsbDevice): Boolean = link.hasPermission(device)

    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) =
        permissionHelper.request(device, onResult)

    // ------------------------------------------------------------------ connect

    fun connect(deviceId: Int, portConfig: SerialPortConfig, onResult: (Result<Unit>) -> Unit) {
        val device = link.findDevice(deviceId)
        if (device == null) {
            onResult(Result.failure(IllegalStateException("设备已断开，请重新插拔")))
            return
        }
        _state.update { it.copy(connection = ConnectionState.CONNECTING, errorMessage = null) }

        if (link.hasPermission(device)) {
            openPort(device, portConfig, onResult)
        } else {
            permissionHelper.request(device) { granted ->
                if (granted) {
                    openPort(device, portConfig, onResult)
                } else {
                    val error = Result.failure<Unit>(IllegalStateException("未授予 USB 访问权限"))
                    fail("未授予 USB 访问权限")
                    onResult(error)
                }
            }
        }
    }

    private fun openPort(
        device: UsbDevice,
        portConfig: SerialPortConfig,
        onResult: (Result<Unit>) -> Unit
    ) {
        val result = link.open(device, portConfig)
        result.onSuccess {
            connectedDeviceId = device.deviceId
            clearData()
            running = true
            startReaderThread()
            _state.update {
                it.copy(
                    connection = ConnectionState.CONNECTED,
                    deviceId = device.deviceId,
                    deviceLabel = device.productName?.takeIf { n -> n.isNotBlank() } ?: "USB 串口设备",
                    portLabel = portConfig.label,
                    errorMessage = null
                )
            }
        }.onFailure { t ->
            fail(t.message ?: "打开串口失败")
        }
        onResult(result)
    }

    fun disconnect() {
        teardown()
        _state.value = EngineState()
    }

    private fun teardown() {
        running = false
        val thread = readerThread
        readerThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(400) }
        }
        runCatching { link.close() }
        connectedDeviceId = -1
        activeChannels = 0
    }

    private fun fail(message: String) {
        teardown()
        _state.update {
            it.copy(connection = ConnectionState.ERROR, errorMessage = message, portLabel = null)
        }
    }

    // -------------------------------------------------------------------- write

    fun send(bytes: ByteArray): Result<Unit> {
        if (!link.isOpen) return Result.failure(IllegalStateException("串口未连接"))
        return try {
            synchronized(writeLock) { link.write(bytes) }
            bytesSent.addAndGet(bytes.size.toLong())
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.w(TAG, "write failed", t)
            Result.failure(t)
        }
    }

    // ------------------------------------------------------------------- parser

    fun configureParser(mode: ParserMode, delimiter: Char, binary: BinaryFrameConfig) {
        val signature = "$mode|$delimiter|$binary"
        if (signature == parserSignature) return
        parserSignature = signature
        parser = ParserFactory.create(mode, delimiter, binary, MAX_CHANNELS)
        activeChannels = 0
        clearData()
    }

    fun clearData() {
        for (ch in channels) ch.clear()
        monitorLog.clear()
        frameRate.reset()
        byteRate.reset()
    }

    // ------------------------------------------------------------------ reading

    private fun startReaderThread() {
        if (readerThread != null) return
        val thread = Thread({ readerLoop() }, "rs485-reader").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 2
        }
        readerThread = thread
        thread.start()
    }

    private fun readerLoop() {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (running) {
            val current = parser
            try {
                val read = link.read(buffer, READ_TIMEOUT_MILLIS)
                if (read > 0) {
                    bytesReceived.addAndGet(read.toLong())
                    byteRate.onSamples(read)
                    monitorLog.add(buffer, 0, read)
                    current.feed(buffer, 0, read, sink)
                } else {
                    current.flush(sink)
                }
            } catch (t: SerialTimeoutException) {
                // Normal for a quiet bus: the endpoint simply had nothing to give us.
                current.flush(sink)
            } catch (t: Throwable) {
                if (running) {
                    Log.w(TAG, "reader loop stopped", t)
                    fail("串口读取中断：${t.message ?: t.javaClass.simpleName}")
                }
                return
            }
        }
    }

    // ------------------------------------------------------------------ snapshot

    fun snapshotRaw(channelIndex: Int, out: FloatArray): Int {
        val ch = channels.getOrNull(channelIndex) ?: return 0
        return ch.buffer.newest(out)
    }

    fun snapshotMonitor(out: ByteArray): Int = monitorLog.newest(out)

    // --------------------------------------------------------------- state push

    fun startTicker(scope: CoroutineScope) {
        if (tickerJob != null) return
        tickerJob = scope.launch {
            while (isActive) {
                publishState()
                delay(STATE_INTERVAL_MILLIS)
            }
        }
    }

    private fun publishState() {
        val current = parser
        _state.update {
            it.copy(
                bytesReceived = bytesReceived.get(),
                bytesSent = bytesSent.get(),
                framesReceived = framesReceived.get(),
                discardedFrames = current.discardedFrames,
                discardedBytes = current.discardedBytes,
                sampleRateHz = frameRate.ratePerSecond(),
                rxBytesPerSecond = byteRate.ratePerSecond(),
                activeChannels = activeChannels
            )
        }
    }

    // ------------------------------------------------------------------ detach

    private fun registerDetachReceiver() {
        if (detachReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
                val device = IntentCompat.getParcelableExtra(
                    intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java
                )
                if (device != null && device.deviceId != connectedDeviceId) return
                if (connectedDeviceId == -1) return
                teardown()
                _state.update {
                    it.copy(
                        connection = ConnectionState.ERROR,
                        errorMessage = "USB 设备已拔出",
                        portLabel = null
                    )
                }
            }
        }
        detachReceiver = receiver
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun close() {
        runCatching { tickerJob?.cancel() }
        tickerJob = null
        teardown()
        detachReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        detachReceiver = null
        permissionHelper.dispose()
    }

    companion object {
        private const val TAG = "ScopeEngine"
        const val MAX_CHANNELS = 8
        const val RING_CAPACITY = 131072
        const val MONITOR_CAPACITY = 65536
        const val DEFAULT_DELIMITER = ','
        private const val READ_BUFFER_SIZE = 8192
        private const val READ_TIMEOUT_MILLIS = 25
        private const val STATE_INTERVAL_MILLIS = 250L
    }
}