package com.serialscope.rs485.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.core.ScopePalette
import com.serialscope.rs485.dsp.Fft
import com.serialscope.rs485.engine.ConnectionState
import com.serialscope.rs485.engine.EngineState
import com.serialscope.rs485.engine.ScopeEngine
import com.serialscope.rs485.ui.components.collectAsStateValue
import com.serialscope.rs485.ui.format.Fmt
import com.serialscope.rs485.ui.panels.ConnectionPanel
import com.serialscope.rs485.ui.panels.FilterPanel
import com.serialscope.rs485.ui.panels.MonitorPanel
import com.serialscope.rs485.ui.panels.SendPanel
import com.serialscope.rs485.ui.scope.SpectrumCanvas
import com.serialscope.rs485.ui.scope.WaveformCanvas
import com.serialscope.rs485.ui.theme.MonoSmallStyle
import com.serialscope.rs485.ui.theme.ScopeColors
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.isActive

/** Mutable holder so the spectrum canvas always reads the freshest FFT result. */
private class SpectrumHolder {
    val bins = FloatArray(TraceRenderer.MAX_WINDOW / 2 + 2)
    var binCount = 0
    var sampleRate = 1000.0
}

private val TAB_TITLES = listOf("连接", "发送", "滤波", "监视")

@Composable
fun ScopeScreen(vm: ScopeViewModel) {
    val state by vm.engineState.collectAsStateValue()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }

    val renderer = remember { TraceRenderer(vm.engine) }
    val tick = remember { mutableStateOf(0L) }
    val spectrum = remember { SpectrumHolder() }
    val windowCache = remember { mutableMapOf<Int, FloatArray>() }
    val colors = remember { IntArray(ScopeEngine.MAX_CHANNELS) { ScopePalette.colorFor(it) } }
    val visible = remember { BooleanArray(ScopeEngine.MAX_CHANNELS) { true } }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(vm.pendingAutoConnectDeviceId) {
        val deviceId = vm.pendingAutoConnectDeviceId ?: return@LaunchedEffect
        vm.pendingAutoConnectDeviceId = null
        vm.connect(deviceId)
    }

    LaunchedEffect(Unit) {
        var frameCounter = 0
        while (isActive) {
            withFrameNanos { }
            frameCounter++

            val flags = vm.visibilityFlags()
            for (i in flags.indices) visible[i] = flags[i]

            if (!vm.paused) {
                val measured = vm.engineState.value.sampleRateHz
                val rate = vm.effectiveSampleRate(measured)
                renderer.update(vm.windowSamples, vm.chainsFor(rate), visible, vm.showRawOverlay)

                if (vm.showSpectrum && frameCounter % 4 == 0) {
                    val channelIndex = vm.filterChannel.coerceIn(0, ScopeEngine.MAX_CHANNELS - 1)
                    val count = renderer.lengths[channelIndex]
                    if (count > 32) {
                        val size = Fft.floorPow2(minOf(count, 4096))
                        val window = windowCache.getOrPut(size) { Fft.hannWindow(size) }
                        spectrum.binCount = Fft.spectrumDb(
                            renderer.traces[channelIndex], 0, count, spectrum.bins, window
                        )
                        spectrum.sampleRate = rate
                    } else {
                        spectrum.binCount = 0
                    }
                }
            }
            tick.value++
        }
    }

    Scaffold(
        containerColor = ScopeColors.Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(ScopeColors.Background)
                .padding(innerPadding)
        ) {
            TopStrip(state = state, vm = vm)

            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 150.dp)
            ) {
                WaveformCanvas(
                    tick = tick,
                    traces = renderer.traces,
                    lengths = renderer.lengths,
                    rawTraces = renderer.rawTraces,
                    rawLengths = renderer.rawLengths,
                    colors = colors,
                    visible = visible,
                    stats = renderer.stats,
                    autoScale = vm.autoScale,
                    manualMin = vm.manualMin,
                    manualMax = vm.manualMax,
                    showGrid = vm.showGrid,
                    modifier = Modifier.fillMaxSize()
                )
                if (vm.paused) {
                    Text(
                        "已暂停",
                        style = MonoSmallStyle,
                        color = ScopeColors.Warning,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }

            if (vm.showSpectrum) {
                SpectrumCanvas(
                    tick = tick,
                    bins = spectrum.bins,
                    binCount = spectrum.binCount,
                    sampleRate = spectrum.sampleRate,
                    traceColor = Color(colors[vm.filterChannel.coerceIn(0, ScopeEngine.MAX_CHANNELS - 1)]),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(horizontal = 4.dp)
                )
            }

            TimebaseBar(vm = vm, state = state)

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TAB_TITLES.forEachIndexed { index, title ->
                    FilterChip(
                        selected = tab == index,
                        onClick = { tab = index },
                        label = { Text(title, style = MaterialTheme.typography.bodySmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScopeColors.AccentDim
                        )
                    )
                }
            }

            Box(Modifier.fillMaxWidth().weight(1.3f)) {
                when (tab) {
                    0 -> ConnectionPanel(vm, state)
                    1 -> SendPanel(vm, state)
                    2 -> FilterPanel(vm, state)
                    else -> MonitorPanel(vm, state)
                }
            }
        }
    }
}

@Composable
private fun TopStrip(state: EngineState, vm: ScopeViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(
                        when (state.connection) {
                            ConnectionState.CONNECTED -> ScopeColors.Accent
                            ConnectionState.CONNECTING -> ScopeColors.Warning
                            ConnectionState.ERROR -> ScopeColors.Danger
                            ConnectionState.DISCONNECTED -> ScopeColors.TextSecondary
                        }
                    )
            )
            Text(
                "  RS485 示波器",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.paused = !vm.paused }) {
                Text(if (vm.paused) "继续" else "暂停")
            }
            TextButton(onClick = { vm.showSpectrum = !vm.showSpectrum }) {
                Text(if (vm.showSpectrum) "隐藏频谱" else "频谱")
            }
            TextButton(onClick = { vm.clearData() }) { Text("清空") }
        }
        Text(
            text = buildString {
                append("采样率 ").append(Fmt.rate(state.sampleRateHz))
                append("  ·  帧 ").append(Fmt.count(state.framesReceived))
                append("  ·  接收 ").append(Fmt.bytes(state.bytesReceived))
                append("  ·  发送 ").append(Fmt.bytes(state.bytesSent))
                append("  ·  通道 ").append(state.activeChannels)
                if (state.discardedFrames > 0) {
                    append("  ·  丢帧 ").append(Fmt.count(state.discardedFrames))
                }
            },
            style = MonoSmallStyle
        )
    }
}

@Composable
private fun TimebaseBar(vm: ScopeViewModel, state: EngineState) {
    val rate = vm.effectiveSampleRate(state.sampleRateHz)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("时基", style = MaterialTheme.typography.bodySmall)
            Text(
                "  ${vm.windowSamples} 点 · ${Fmt.msPerWindow(vm.windowSamples, rate)}",
                style = MonoSmallStyle,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.autoScale = !vm.autoScale }) {
                Text(if (vm.autoScale) "自动量程" else "手动量程")
            }
            TextButton(onClick = { vm.showGrid = !vm.showGrid }) {
                Text(if (vm.showGrid) "网格开" else "网格关")
            }
            TextButton(onClick = { vm.showRawOverlay = !vm.showRawOverlay }) {
                Text(if (vm.showRawOverlay) "原始叠加开" else "原始叠加关")
            }
        }
        Slider(
            value = windowToFraction(vm.windowSamples),
            onValueChange = { vm.windowSamples = fractionToWindow(it) },
            modifier = Modifier.fillMaxWidth()
        )
        if (!vm.autoScale) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("量程", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = vm.manualMax,
                    onValueChange = { vm.manualMax = it },
                    valueRange = -1000f..1000f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                Text("上 %.3g".format(vm.manualMax), style = MonoSmallStyle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("     ", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = vm.manualMin,
                    onValueChange = { vm.manualMin = it },
                    valueRange = -1000f..1000f,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                Text("下 %.3g".format(vm.manualMin), style = MonoSmallStyle)
            }
        }
    }
}

private const val MIN_LOG_WINDOW = 128f
private const val MAX_LOG_WINDOW = 8192f

private fun windowToFraction(samples: Int): Float {
    val v = samples.toFloat().coerceIn(MIN_LOG_WINDOW, MAX_LOG_WINDOW)
    return (ln(v / MIN_LOG_WINDOW) / ln(MAX_LOG_WINDOW / MIN_LOG_WINDOW)).coerceIn(0f, 1f)
}

private fun fractionToWindow(fraction: Float): Int {
    val value = MIN_LOG_WINDOW * (MAX_LOG_WINDOW / MIN_LOG_WINDOW).pow(fraction.coerceIn(0f, 1f))
    return value.toInt().coerceIn(128, 8192)
}