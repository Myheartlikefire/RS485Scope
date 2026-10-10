package com.serialscope.rs485.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.core.ScopePalette
import com.serialscope.rs485.dsp.BiquadSpec
import com.serialscope.rs485.dsp.BiquadType
import com.serialscope.rs485.dsp.DcBlockerSpec
import com.serialscope.rs485.dsp.FilterSpec
import com.serialscope.rs485.dsp.FilterStage
import com.serialscope.rs485.dsp.MedianSpec
import com.serialscope.rs485.dsp.MovingAverageSpec
import com.serialscope.rs485.dsp.NotchSpec
import com.serialscope.rs485.engine.EngineState
import com.serialscope.rs485.engine.ScopeEngine
import com.serialscope.rs485.ui.ScopeViewModel
import com.serialscope.rs485.ui.components.ChoiceChips
import com.serialscope.rs485.ui.components.LabeledSlider
import com.serialscope.rs485.ui.components.PanelCard
import com.serialscope.rs485.ui.components.ToggleRow
import com.serialscope.rs485.ui.format.Fmt
import com.serialscope.rs485.ui.theme.MonoNumberStyle
import com.serialscope.rs485.ui.theme.ScopeColors
import kotlin.math.ln
import kotlin.math.pow

private const val MIN_FILTER_FREQ = 0.5f
private const val MAX_FILTER_FREQ = 20000f

@Composable
fun FilterPanel(vm: ScopeViewModel, state: EngineState) {
    val sampleRate = vm.effectiveSampleRate(state.sampleRateHz)
    val nyquist = (sampleRate / 2.0).toFloat()
    val maxFreq = minOf(MAX_FILTER_FREQ, nyquist * 0.95f).coerceAtLeast(MIN_FILTER_FREQ * 2f)
    val visibleChannels = maxOf(state.activeChannels, 2).coerceAtMost(ScopeEngine.MAX_CHANNELS)
    val channel = vm.filterChannel.coerceIn(0, ScopeEngine.MAX_CHANNELS - 1)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PanelCard(title = "通道") {
                val summaries = vm.channelSummaries
                if (summaries.isEmpty()) {
                    Text("等待数据…", style = MaterialTheme.typography.bodySmall)
                } else {
                    summaries.forEach { summary ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(Color(ScopePalette.colorFor(summary.index)), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("CH${summary.index + 1}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "当前 ${Fmt.sample(summary.last)}   峰峰 ${Fmt.sample(summary.peakToPeak)}   RMS ${Fmt.sample(summary.rms)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Switch(
                                checked = summary.visible,
                                onCheckedChange = { vm.setChannelVisible(summary.index, it) }
                            )
                        }
                    }
                }
            }
        }

        item {
            PanelCard(title = "采样率") {
                Text(
                    "测得的帧率 / 采样率：${Fmt.rate(state.sampleRateHz)}",
                    style = MaterialTheme.typography.bodySmall
                )
                ToggleRow(
                    label = "手动指定采样率",
                    supporting = "滤波器系数按此频率计算，波形时间轴也用它换算",
                    checked = vm.useManualSampleRate,
                    onCheckedChange = { vm.useManualSampleRate = it }
                )
                if (vm.useManualSampleRate) {
                    var text by remember { mutableStateOf(vm.manualSampleRate.toInt().toString()) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { input ->
                            text = input.filter { c -> c.isDigit() }.take(8)
                            text.toFloatOrNull()?.let { if (it >= 1f) vm.manualSampleRate = it }
                        },
                        label = { Text("采样率 (Hz)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "生效采样率：%.1f Hz   奈奎斯特 %.1f Hz".format(sampleRate, nyquist),
                    style = MonoNumberStyle
                )
            }
        }

        item {
            PanelCard(title = "常用滤波器（加到 CH${channel + 1}）") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.addPresetMainsNotch(channel, 50f) }) { Text("50 Hz 陷波") }
                    OutlinedButton(onClick = { vm.addPresetMainsNotch(channel, 60f) }) { Text("60 Hz 陷波") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.addPresetLowPass(channel, maxFreq / 4f) }) { Text("低通") }
                    OutlinedButton(onClick = { vm.addPresetSmoothing(channel) }) { Text("平滑") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.addStage(channel, BiquadSpec(BiquadType.HIGH_PASS, 1f, 0.7071f)) }
                    ) { Text("去直流(高通)") }
                    OutlinedButton(
                        onClick = { vm.addStage(channel, BiquadSpec(BiquadType.BAND_PASS, maxFreq / 4f, 2f)) }
                    ) { Text("带通") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.addStage(channel, BiquadSpec(BiquadType.LOW_SHELF, 100f, 0.7071f, -6f)) }
                    ) { Text("低架") }
                    OutlinedButton(
                        onClick = { vm.addStage(channel, MedianSpec(5)) }
                    ) { Text("中值去毛刺") }
                }
            }
        }

        item {
            PanelCard(title = "滤波链") {
                Text("选择要编辑的通道", style = MaterialTheme.typography.bodySmall)
                ChoiceChips(
                    options = (0 until visibleChannels).map { "CH${it + 1}" },
                    selectedIndex = channel,
                    onSelect = { vm.filterChannel = it }
                )
                HorizontalDivider(color = ScopeColors.Outline)
                val stages = vm.stagesFor(channel)
                if (stages.isEmpty()) {
                    Text("该通道没有滤波器，波形即原始采样。", style = MaterialTheme.typography.bodySmall)
                } else {
                    stages.forEachIndexed { index, stage ->
                        StageEditor(
                            vm = vm,
                            channel = channel,
                            index = index,
                            stage = stage,
                            maxFreq = maxFreq,
                            sampleRate = sampleRate
                        )
                    }
                    TextButton(onClick = { vm.clearStages(channel) }) { Text("清空该通道滤波链") }
                }
            }
        }
    }
}

@Composable
private fun StageEditor(
    vm: ScopeViewModel,
    channel: Int,
    index: Int,
    stage: FilterStage,
    maxFreq: Float,
    sampleRate: Double
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#${index + 1}  ${stage.spec.kindLabel}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = stage.enabled,
                onCheckedChange = { vm.setStageEnabled(channel, stage.id, it) }
            )
        }
        Text(stage.spec.describe(sampleRate), style = MaterialTheme.typography.labelSmall)

        when (val spec = stage.spec) {
            is BiquadSpec -> {
                LogFreqSlider(
                    label = "截止 / 中心频率",
                    value = spec.freqHz,
                    minHz = MIN_FILTER_FREQ,
                    maxHz = maxFreq,
                    onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(freqHz = it)) }
                )
                if (spec.type != BiquadType.LOW_SHELF && spec.type != BiquadType.HIGH_SHELF) {
                    LabeledSlider(
                        label = "Q 值",
                        value = spec.q,
                        range = 0.1f..20f,
                        valueText = "%.2f".format(spec.q),
                        onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(q = it)) }
                    )
                }
                when (spec.type) {
                    BiquadType.PEAKING, BiquadType.LOW_SHELF, BiquadType.HIGH_SHELF -> LabeledSlider(
                        label = "增益",
                        value = spec.gainDb,
                        range = -40f..40f,
                        valueText = "%+.1f dB".format(spec.gainDb),
                        onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(gainDb = it)) }
                    )
                    else -> Unit
                }
            }

            is NotchSpec -> {
                LogFreqSlider(
                    label = "陷波中心频率",
                    value = spec.freqHz,
                    minHz = MIN_FILTER_FREQ,
                    maxHz = maxFreq,
                    onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(freqHz = it)) }
                )
                LabeledSlider(
                    label = "带宽 (-3dB)",
                    value = spec.bandwidthHz,
                    range = 0.1f..maxFreq / 2f,
                    valueText = "%.2f Hz".format(spec.bandwidthHz),
                    onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(bandwidthHz = it)) }
                )
                LabeledSlider(
                    label = "陷波深度",
                    value = spec.depth,
                    range = 0f..1f,
                    valueText = "%.0f%%".format(spec.depth * 100f),
                    onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(depth = it)) }
                )
            }

            is MovingAverageSpec -> LabeledSlider(
                label = "窗口点数",
                value = spec.taps.toFloat(),
                range = 2f..101f,
                valueText = "${spec.taps} 点",
                onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(taps = it.toInt())) }
            )

            is MedianSpec -> LabeledSlider(
                label = "窗口点数",
                value = spec.taps.toFloat(),
                range = 3f..31f,
                valueText = "${spec.taps} 点",
                onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(taps = it.toInt())) }
            )

            is DcBlockerSpec -> LabeledSlider(
                label = "极点 r",
                value = spec.pole,
                range = 0.9f..0.9999f,
                valueText = "%.4f".format(spec.pole),
                onChange = { vm.updateStageSpec(channel, stage.id, spec.copy(pole = it)) }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.moveStage(channel, stage.id, -1) }) { Text("上移") }
            TextButton(onClick = { vm.moveStage(channel, stage.id, 1) }) { Text("下移") }
            TextButton(onClick = { vm.removeStage(channel, stage.id) }) { Text("删除") }
        }
        HorizontalDivider(color = ScopeColors.Outline)
    }
}

/** Frequency slider with a logarithmic response so 1 Hz and 10 kHz are both reachable. */
@Composable
private fun LogFreqSlider(
    label: String,
    value: Float,
    minHz: Float,
    maxHz: Float,
    onChange: (Float) -> Unit
) {
    val safeMin = minHz.coerceAtLeast(0.001f)
    val safeMax = maxHz.coerceAtLeast(safeMin * 2f)
    val t = (ln(value.coerceIn(safeMin, safeMax) / safeMin) / ln(safeMax / safeMin))
        .coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("%.2f Hz".format(value), style = MonoNumberStyle)
        }
        Slider(
            value = t,
            onValueChange = { onChange((safeMin * (safeMax / safeMin).pow(it)).coerceIn(safeMin, safeMax)) }
        )
    }
}

@Suppress("unused")
private val UNUSED_SPEC: FilterSpec? = null