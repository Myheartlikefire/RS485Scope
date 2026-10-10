package com.serialscope.rs485.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serialscope.rs485.engine.EngineState
import com.serialscope.rs485.ui.ScopeViewModel
import com.serialscope.rs485.ui.components.ChoiceChips
import com.serialscope.rs485.ui.components.KeyValueRow
import com.serialscope.rs485.ui.components.PanelCard
import com.serialscope.rs485.ui.components.ToggleRow
import com.serialscope.rs485.ui.format.Fmt
import com.serialscope.rs485.ui.theme.ScopeColors
import com.serialscope.rs485.util.Hex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MonitorPanel(vm: ScopeViewModel, state: EngineState) {
    var dumpText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(vm.monitorAsHex, vm.monitorPaused) {
        val out = ByteArray(6144)
        while (isActive) {
            if (!vm.monitorPaused) {
                val n = vm.engine.snapshotMonitor(out)
                dumpText = if (n > 0) format(out, n, vm.monitorAsHex) else ""
                if (vm.monitorWrap) {
                    runCatching { scrollState.scrollTo(scrollState.maxValue) }
                }
            }
            delay(250)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PanelCard(title = "原始数据监视") {
                ChoiceChips(
                    options = listOf("十六进制", "ASCII 文本"),
                    selectedIndex = if (vm.monitorAsHex) 0 else 1,
                    onSelect = { vm.monitorAsHex = it == 0 }
                )
                ToggleRow(
                    label = "暂停刷新",
                    checked = vm.monitorPaused,
                    onCheckedChange = { vm.monitorPaused = it }
                )
                ToggleRow(
                    label = "自动滚动到底部",
                    checked = vm.monitorWrap,
                    onCheckedChange = { vm.monitorWrap = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.clearData() }) { Text("清空缓冲") }
                    OutlinedButton(onClick = { dumpText = "" }) { Text("清空显示") }
                }
                KeyValueRow("累计接收", Fmt.bytes(state.bytesReceived))
                KeyValueRow("缓冲区容量", Fmt.bytes(vm.engine.monitorLog.capacity.toLong()))
            }
        }
        item {
            PanelCard {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = dumpText.ifEmpty { "（暂无数据）" },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = ScopeColors.TextPrimary,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }
}

/** Bytes per line in the hex dump view. */
private const val BYTES_PER_LINE = 16

private fun format(bytes: ByteArray, length: Int, asHex: Boolean): String {
    if (!asHex) return Hex.toPrintableAscii(bytes, 0, length)
    val sb = StringBuilder(length * 3 + length / BYTES_PER_LINE + 4)
    var index = 0
    while (index < length) {
        val lineEnd = minOf(index + BYTES_PER_LINE, length)
        for (k in index until lineEnd) {
            val b = bytes[k].toInt() and 0xFF
            sb.append(HEX[(b ushr 4)]).append(HEX[b and 0x0F])
            sb.append(if (k == lineEnd - 1) "" else " ")
        }
        sb.append('\n')
        index = lineEnd
    }
    return sb.toString()
}

private const val HEX = "0123456789ABCDEF"