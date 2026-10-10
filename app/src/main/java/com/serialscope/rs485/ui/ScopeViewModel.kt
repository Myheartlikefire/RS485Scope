package com.serialscope.rs485.ui

import android.app.Application
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serialscope.rs485.dsp.BiquadSpec
import com.serialscope.rs485.dsp.BiquadType
import com.serialscope.rs485.dsp.FilterChain
import com.serialscope.rs485.dsp.FilterSpec
import com.serialscope.rs485.dsp.FilterStage
import com.serialscope.rs485.dsp.MovingAverageSpec
import com.serialscope.rs485.dsp.NotchSpec
import com.serialscope.rs485.engine.ConnectionState
import com.serialscope.rs485.engine.EngineState
import com.serialscope.rs485.engine.ParserMode
import com.serialscope.rs485.engine.ScopeEngine
import com.serialscope.rs485.parse.BinaryFrameConfig
import com.serialscope.rs485.usb.SerialFlowControl
import com.serialscope.rs485.usb.SerialParity
import com.serialscope.rs485.usb.SerialPortConfig
import com.serialscope.rs485.usb.UsbSerialDevice
import com.serialscope.rs485.util.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LineEnding(val label: String, val value: String) {
    NONE("无", ""),
    LF("LF", "\n"),
    CR("CR", "\r"),
    CRLF("CRLF", "\r\n")
}

/** Snapshot of one channel used by the UI list (plain fields are not observable). */
data class ChannelSummary(
    val index: Int,
    val last: Float,
    val peakToPeak: Float,
    val rms: Float,
    val mean: Float,
    val visible: Boolean
)

class ScopeViewModel(application: Application) : AndroidViewModel(application) {

    val engine = ScopeEngine(application)

    val engineState: StateFlow<EngineState> = engine.state

    private val _devices = MutableStateFlow<List<UsbSerialDevice>>(emptyList())
    val devices: StateFlow<List<UsbSerialDevice>> = _devices.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    // ------------------------------------------------------------ port settings
    var portConfig by mutableStateOf(SerialPortConfig())
        private set

    var parserMode by mutableStateOf(ParserMode.ASCII)
        private set

    var asciiDelimiter by mutableStateOf(ScopeEngine.DEFAULT_DELIMITER)
        private set

    var binaryConfig by mutableStateOf(BinaryFrameConfig())
        private set

    // ------------------------------------------------------------ view settings
    var windowSamples by mutableStateOf(2048)
    var autoScale by mutableStateOf(true)
    var manualMin by mutableStateOf(-1f)
    var manualMax by mutableStateOf(1f)
    var showGrid by mutableStateOf(true)
    var showRawOverlay by mutableStateOf(false)
    var paused by mutableStateOf(false)
    var showSpectrum by mutableStateOf(false)
    var useManualSampleRate by mutableStateOf(false)
    var manualSampleRate by mutableStateOf(1000f)

    // ---------------------------------------------------------------- channels
    private val channelVisible = mutableStateMapOf<Int, Boolean>()

    var channelSummaries by mutableStateOf<List<ChannelSummary>>(emptyList())
        private set

    fun isChannelVisible(index: Int): Boolean = channelVisible[index] ?: true

    fun setChannelVisible(index: Int, visible: Boolean) {
        channelVisible[index] = visible
    }

    /** Visibility flags handed to the render loop, one entry per channel index. */
    fun visibilityFlags(): BooleanArray =
        BooleanArray(ScopeEngine.MAX_CHANNELS) { isChannelVisible(it) }

    // ----------------------------------------------------------------- filters
    val filterStages = mutableStateMapOf<Int, List<FilterStage>>()
    var filterChannel by mutableStateOf(0)

    // -------------------------------------------------------------------- send
    var sendText by mutableStateOf("")
    var sendAsHex by mutableStateOf(false)
    var lineEnding by mutableStateOf(LineEnding.CRLF)
    var repeatEnabled by mutableStateOf(false)
    var repeatIntervalMs by mutableStateOf(1000)
    val quickSend = mutableStateListOf("AT", "READ?", "START")

    // ----------------------------------------------------------------- monitor
    var monitorAsHex by mutableStateOf(true)
    var monitorWrap by mutableStateOf(true)
    var monitorPaused by mutableStateOf(false)

    private var nextStageId = 1L
    private var repeatJob: Job? = null
    private var deviceJob: Job? = null

    private var chainCacheKey: String = ""
    private var chainCache: List<FilterChain> =
        List(ScopeEngine.MAX_CHANNELS) { FilterChain.EMPTY }

    init {
        engine.startTicker(viewModelScope)
        engine.configureParser(parserMode, asciiDelimiter, binaryConfig)
        refreshDevices()
        startSummaryTicker()
        deviceJob = viewModelScope.launch {
            while (isActive) {
                delay(2000)
                if (!engine.state.value.connected) refreshDevices()
            }
        }
    }

    /** Publishes channel statistics at a UI friendly rate (plain fields are not observable). */
    private fun startSummaryTicker() {
        viewModelScope.launch {
            while (isActive) {
                channelSummaries = engine.channels.map { ch ->
                    ChannelSummary(
                        index = ch.index,
                        last = ch.lastValue,
                        peakToPeak = ch.stats.peakToPeak,
                        rms = ch.stats.rms,
                        mean = ch.stats.mean,
                        visible = isChannelVisible(ch.index)
                    )
                }
                delay(200)
            }
        }
    }

    // -------------------------------------------------------------- device list

    fun refreshDevices() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { engine.listDevices() }
            _devices.value = list
        }
    }

    fun onUsbIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            refreshDevices()
            val device = IntentCompat.getParcelableExtra(
                intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java
            )
            if (device != null) {
                pendingAutoConnectDeviceId = device.deviceId
            }
        } else if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
            refreshDevices()
        }
    }

    var pendingAutoConnectDeviceId: Int? by mutableStateOf(null)

    // ------------------------------------------------------------------ connect

    fun connect(deviceId: Int) {
        engine.connect(deviceId, portConfig) { result ->
            result.onFailure { emit(it.message ?: "连接失败") }
        }
    }

    fun disconnect() = engine.disconnect()

    fun updatePortConfig(transform: (SerialPortConfig) -> SerialPortConfig) {
        portConfig = transform(portConfig)
    }

    // ------------------------------------------------------------------- parser

    fun selectParserMode(mode: ParserMode) {
        parserMode = mode
        pushParserConfig()
    }

    fun selectAsciiDelimiter(delimiter: Char) {
        asciiDelimiter = delimiter
        pushParserConfig()
    }

    fun updateBinaryConfig(transform: (BinaryFrameConfig) -> BinaryFrameConfig) {
        binaryConfig = transform(binaryConfig)
        pushParserConfig()
    }

    private fun pushParserConfig() {
        engine.configureParser(parserMode, asciiDelimiter, binaryConfig)
    }

    fun clearData() {
        engine.clearData()
        chainCacheKey = ""
    }

    // ------------------------------------------------------------------ filters

    fun stagesFor(channel: Int): List<FilterStage> = filterStages[channel] ?: emptyList()

    fun addStage(channel: Int, spec: FilterSpec) {
        val current = stagesFor(channel)
        if (current.size >= MAX_STAGES_PER_CHANNEL) {
            emit("单通道最多 $MAX_STAGES_PER_CHANNEL 级滤波器")
            return
        }
        filterStages[channel] = current + FilterStage(id = nextStageId++, spec = spec)
    }

    fun removeStage(channel: Int, id: Long) {
        filterStages[channel] = stagesFor(channel).filterNot { it.id == id }
    }

    fun setStageEnabled(channel: Int, id: Long, enabled: Boolean) {
        filterStages[channel] = stagesFor(channel).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
    }

    fun updateStageSpec(channel: Int, id: Long, spec: FilterSpec) {
        filterStages[channel] = stagesFor(channel).map {
            if (it.id == id) it.copy(spec = spec) else it
        }
    }

    fun moveStage(channel: Int, id: Long, delta: Int) {
        val list = stagesFor(channel).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, list.size - 1)
        if (target == index) return
        val item = list.removeAt(index)
        list.add(target, item)
        filterStages[channel] = list
    }

    fun clearStages(channel: Int) {
        filterStages[channel] = emptyList()
    }

    fun addPresetMainsNotch(channel: Int, frequency: Float) {
        addStage(channel, NotchSpec(freqHz = frequency, bandwidthHz = 2f, depth = 1f))
    }

    fun addPresetLowPass(channel: Int, cutoff: Float) {
        addStage(channel, BiquadSpec(BiquadType.LOW_PASS, cutoff, 0.7071f))
    }

    fun addPresetSmoothing(channel: Int) {
        addStage(channel, MovingAverageSpec(15))
    }

    /** One [FilterChain] per channel, rebuilt only when something actually changed. */
    fun chainsFor(sampleRate: Double): List<FilterChain> {
        val key = buildString {
            append(sampleRate.toInt())
            for (i in 0 until ScopeEngine.MAX_CHANNELS) {
                append('#')
                stagesFor(i).forEach { stage ->
                    append(stage.id).append(stage.enabled).append(stage.spec).append(';')
                }
            }
        }
        if (key == chainCacheKey) return chainCache
        chainCacheKey = key
        chainCache = List(ScopeEngine.MAX_CHANNELS) { i ->
            FilterChain.of(stagesFor(i), sampleRate)
        }
        return chainCache
    }

    // --------------------------------------------------------------------- send

    fun sendCurrent(): Boolean {
        val bytes = try {
            if (sendAsHex) Hex.parse(sendText) else {
                Hex.textToBytes(sendText, "UTF-8", lineEnding.value)
            }
        } catch (t: Throwable) {
            emit("编码失败：${t.message}")
            return false
        }
        if (bytes == null) {
            emit("HEX 格式错误，请输入偶数个十六进制字符")
            return false
        }
        if (bytes.isEmpty()) {
            emit("发送内容为空")
            return false
        }
        return sendBytes(bytes)
    }

    fun sendBytes(bytes: ByteArray): Boolean {
        val result = engine.send(bytes)
        result.onFailure { emit(it.message ?: "发送失败") }
        return result.isSuccess
    }

    fun updateRepeatEnabled(enabled: Boolean) {
        repeatEnabled = enabled
        if (enabled) startRepeat() else stopRepeat()
    }

    fun restartRepeat() {
        if (repeatEnabled) startRepeat()
    }

    private fun startRepeat() {
        stopRepeat()
        repeatJob = viewModelScope.launch {
            while (isActive) {
                delay(repeatIntervalMs.coerceAtLeast(20).toLong())
                if (!engine.state.value.connected) continue
                val bytes = if (sendAsHex) Hex.parse(sendText) else {
                    Hex.textToBytes(sendText, "UTF-8", lineEnding.value)
                }
                if (bytes != null && bytes.isNotEmpty()) engine.send(bytes)
            }
        }
    }

    private fun stopRepeat() {
        repeatJob?.cancel()
        repeatJob = null
    }

    // ------------------------------------------------------------------ helpers

    fun effectiveSampleRate(measured: Float): Double {
        if (useManualSampleRate) return manualSampleRate.toDouble().coerceAtLeast(1.0)
        if (measured > 1f) return measured.toDouble()
        return 1000.0
    }

    fun emit(message: String) {
        _messages.tryEmit(message)
    }

    override fun onCleared() {
        stopRepeat()
        deviceJob?.cancel()
        engine.close()
        super.onCleared()
    }

    companion object {
        const val MAX_STAGES_PER_CHANNEL = 6
    }
}