package com.serialscope.rs485.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.engine.EngineState
import com.serialscope.rs485.ui.LineEnding
import com.serialscope.rs485.ui.ScopeViewModel
import com.serialscope.rs485.ui.components.ChoiceChips
import com.serialscope.rs485.ui.components.LabeledSlider
import com.serialscope.rs485.ui.components.PanelCard
import com.serialscope.rs485.ui.components.ToggleRow
import com.serialscope.rs485.ui.theme.MonoSmallStyle
import com.serialscope.rs485.ui.theme.ScopeColors
import com.serialscope.rs485.util.Hex

@Composable
fun SendPanel(vm: ScopeViewModel, state: EngineState) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PanelCard(title = "发送数据") {
                ChoiceChips(
                    options = listOf("文本 (UTF-8)", "十六进制"),
                    selectedIndex = if (vm.sendAsHex) 1 else 0,
                    onSelect = { vm.sendAsHex = it == 1 }
                )

                OutlinedTextField(
                    value = vm.sendText,
                    onValueChange = { vm.sendText = it },
                    label = {
                        Text(if (vm.sendAsHex) "例如 AA 55 01 00 FF" else "要发送的文本")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (vm.sendAsHex) KeyboardType.Ascii else KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
                )

                if (!vm.sendAsHex) {
                    Text("行尾", style = MaterialTheme.typography.bodySmall)
                    ChoiceChips(
                        options = LineEnding.entries.map { it.label },
                        selectedIndex = vm.lineEnding.ordinal,
                        onSelect = { vm.lineEnding = LineEnding.entries[it] }
                    )
                }

                val preview = previewText(vm)
                Text(
                    text = preview ?: "HEX 格式无效",
                    style = MonoSmallStyle,
                    color = if (preview == null) ScopeColors.Danger else ScopeColors.TextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.sendCurrent() },
                        enabled = state.connected,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("发送")
                    }
                    OutlinedButton(onClick = { vm.sendText = "" }) { Text("清空") }
                }
                if (!state.connected) {
                    Text("串口未连接，无法发送", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            PanelCard(title = "快捷发送") {
                vm.quickSend.forEachIndexed { index, text ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { vm.sendBytes(encodeQuick(vm, text)) },
                            enabled = state.connected,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text, maxLines = 1)
                        }
                        TextButton(onClick = { vm.quickSend.removeAt(index) }) { Text("删除") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val text = vm.sendText.ifBlank { "AT" }
                            if (!vm.quickSend.contains(text)) vm.quickSend.add(text)
                        }
                    ) {
                        Text("用当前内容新增")
                    }
                    TextButton(onClick = { vm.quickSend.clear() }) { Text("全部清空") }
                }
            }
        }

        item {
            PanelCard(title = "周期发送") {
                ToggleRow(
                    label = "启用周期发送",
                    supporting = "按固定间隔重复发送当前内容，用于轮询从机寄存器",
                    checked = vm.repeatEnabled,
                    onCheckedChange = { vm.updateRepeatEnabled(it) }
                )
                LabeledSlider(
                    label = "间隔",
                    value = vm.repeatIntervalMs.toFloat(),
                    range = 50f..5000f,
                    valueText = "${vm.repeatIntervalMs} ms",
                    onChange = {
                        vm.repeatIntervalMs = it.toInt()
                        vm.restartRepeat()
                    }
                )
            }
        }
    }
}

private fun encodeQuick(vm: ScopeViewModel, text: String): ByteArray =
    if (vm.sendAsHex) Hex.parse(text) ?: ByteArray(0)
    else Hex.textToBytes(text, "UTF-8", vm.lineEnding.value)

private fun previewText(vm: ScopeViewModel): String? {
    val bytes = if (vm.sendAsHex) Hex.parse(vm.sendText) else {
        Hex.textToBytes(vm.sendText, "UTF-8", vm.lineEnding.value)
    } ?: return null
    val shown = Hex.encode(bytes, 0, minOf(bytes.size, 24)) + if (bytes.size > 24) " …" else ""
    return "共 ${bytes.size} 字节 · $shown"
}