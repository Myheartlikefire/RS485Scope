package com.serialscope.rs485.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.engine.ParserMode
import com.serialscope.rs485.parse.ChecksumMode
import com.serialscope.rs485.parse.SampleType
import com.serialscope.rs485.ui.ScopeViewModel
import com.serialscope.rs485.ui.components.ChoiceChips
import com.serialscope.rs485.ui.components.LabeledSlider
import com.serialscope.rs485.ui.components.PanelCard
import com.serialscope.rs485.ui.components.SectionLabel
import com.serialscope.rs485.ui.theme.MonoSmallStyle
import com.serialscope.rs485.ui.theme.ScopeColors
import com.serialscope.rs485.util.Hex

private val DELIMITERS = listOf(',', ';', ' ', '\t')
private val DELIMITER_LABELS = listOf("逗号 ,", "分号 ;", "空格", "制表符")

/**
 * Chooses how the incoming byte stream is turned into channels.
 *
 * Text mode is convenient for `printf("a,b,c\n")` style firmware; the binary
 * mode decodes fixed length frames which is what most MCU DAQ code emits.
 */
@Composable
fun ParserCard(vm: ScopeViewModel) {
    PanelCard(title = "数据解析") {
        ChoiceChips(
            options = ParserMode.entries.map { it.label },
            selectedIndex = vm.parserMode.ordinal,
            onSelect = { vm.selectParserMode(ParserMode.entries[it]) }
        )

        when (vm.parserMode) {
            ParserMode.ASCII -> {
                SectionLabel("字段分隔符")
                ChoiceChips(
                    options = DELIMITER_LABELS,
                    selectedIndex = DELIMITERS.indexOf(vm.asciiDelimiter).coerceAtLeast(0),
                    onSelect = { vm.selectAsciiDelimiter(DELIMITERS[it]) }
                )
                Text(
                    "每行一帧，例如 12.3,45.6,78.9 — 空格与制表符也会被当作分隔符；" +
                        "不含数字的行会被自动忽略。",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ParserMode.BINARY -> {
                var headerText by remember {
                    mutableStateOf(Hex.encode(vm.binaryConfig.header))
                }
                val headerValid = Hex.parse(headerText) != null
                OutlinedTextField(
                    value = headerText,
                    onValueChange = { input ->
                        headerText = input
                        Hex.parse(input)?.let { bytes ->
                            vm.updateBinaryConfig { cfg -> cfg.copy(header = bytes) }
                        }
                    },
                    label = { Text("帧头 (十六进制，可留空)") },
                    isError = !headerValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "当前解析：" + describeBinary(vm),
                    style = MonoSmallStyle,
                    color = ScopeColors.TextSecondary
                )

                LabeledSlider(
                    label = "通道数",
                    value = vm.binaryConfig.channelCount.toFloat(),
                    range = 1f..8f,
                    steps = 6,
                    valueText = "${vm.binaryConfig.channelCount}",
                    onChange = { vm.updateBinaryConfig { cfg -> cfg.copy(channelCount = it.toInt()) } }
                )

                SectionLabel("数据类型")
                ChoiceChips(
                    options = SampleType.entries.map { it.label },
                    selectedIndex = vm.binaryConfig.sampleType.ordinal,
                    onSelect = { vm.updateBinaryConfig { cfg -> cfg.copy(sampleType = SampleType.entries[it]) } }
                )

                SectionLabel("字节序")
                ChoiceChips(
                    options = listOf("小端 (little)", "大端 (big)"),
                    selectedIndex = if (vm.binaryConfig.bigEndian) 1 else 0,
                    onSelect = { vm.updateBinaryConfig { cfg -> cfg.copy(bigEndian = it == 1) } }
                )

                SectionLabel("校验")
                ChoiceChips(
                    options = ChecksumMode.entries.map { it.label },
                    selectedIndex = vm.binaryConfig.checksum.ordinal,
                    onSelect = { vm.updateBinaryConfig { cfg -> cfg.copy(checksum = ChecksumMode.entries[it]) } }
                )

                LabeledSlider(
                    label = "帧尾额外字节",
                    value = vm.binaryConfig.tailBytes.toFloat(),
                    range = 0f..8f,
                    steps = 7,
                    valueText = "${vm.binaryConfig.tailBytes}",
                    onChange = { vm.updateBinaryConfig { cfg -> cfg.copy(tailBytes = it.toInt()) } }
                )

                HorizontalDivider(color = ScopeColors.Outline)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NumberField(
                        label = "缩放系数",
                        value = vm.binaryConfig.scale,
                        modifier = Modifier.weight(1f),
                        onChange = { vm.updateBinaryConfig { cfg -> cfg.copy(scale = it) } }
                    )
                    NumberField(
                        label = "偏置",
                        value = vm.binaryConfig.offset,
                        modifier = Modifier.weight(1f),
                        onChange = { vm.updateBinaryConfig { cfg -> cfg.copy(offset = it) } }
                    )
                }
                Text(
                    "换算值 = 原始值 × 缩放系数 + 偏置，例如 3.3/65535 可把 uint16 变成电压。",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ParserMode.MONITOR_ONLY -> Text(
                "不解析通道，只把收到的字节原样送进「监视」页，适合先确认链路是否通畅。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    var text by remember { mutableStateOf(trimNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.take(16)
            text.toFloatOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        modifier = modifier
    )
}

private fun trimNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

private fun describeBinary(vm: ScopeViewModel): String {
    val cfg = vm.binaryConfig
    return "头 ${cfg.header.size}B + ${cfg.channelCount}×${cfg.sampleType.byteSize}B" +
        (if (cfg.checksumBytes > 0) " + 校验1B" else "") +
        (if (cfg.tailBytes > 0) " + 尾${cfg.tailBytes}B" else "") +
        " = ${cfg.frameBytes}B/帧"
}