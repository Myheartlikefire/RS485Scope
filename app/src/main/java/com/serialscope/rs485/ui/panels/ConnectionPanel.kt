package com.serialscope.rs485.ui.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.serialscope.rs485.engine.ConnectionState
import com.serialscope.rs485.engine.EngineState
import com.serialscope.rs485.ui.ScopeViewModel
import com.serialscope.rs485.ui.components.ChoiceChips
import com.serialscope.rs485.ui.components.collectAsStateValue
import com.serialscope.rs485.ui.components.KeyValueRow
import com.serialscope.rs485.ui.components.PanelCard
import com.serialscope.rs485.ui.components.SectionLabel
import com.serialscope.rs485.ui.components.ToggleRow
import com.serialscope.rs485.ui.format.Fmt
import com.serialscope.rs485.ui.theme.MonoNumberStyle
import com.serialscope.rs485.ui.theme.ScopeColors
import com.serialscope.rs485.usb.SerialFlowControl
import com.serialscope.rs485.usb.SerialParity
import com.serialscope.rs485.usb.SerialPortConfig
import com.serialscope.rs485.usb.UsbSerialDevice

@Composable
fun ConnectionPanel(vm: ScopeViewModel, state: EngineState) {
    val devices by vm.devices.collectAsStateValue()
    var customBaud by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PanelCard(title = "USB 串口设备") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { vm.refreshDevices() }, modifier = Modifier.weight(1f)) {
                        Text("刷新设备")
                    }
                    if (state.connected) {
                        OutlinedButton(onClick = { vm.disconnect() }, modifier = Modifier.weight(1f)) {
                            Text("断开")
                        }
                    }
                }

                if (devices.isEmpty()) {
                    Text(
                        "未发现 USB 设备。请确认 OTG 转接头已插好，且开发板供电正常。",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    devices.forEach { device -> DeviceRow(vm, device, state) }
                }
            }
        }

        item { ParserCard(vm) }

        item {
            PanelCard(title = "串口参数") {
                SectionLabel("波特率")
                ChoiceChips(
                    options = SerialPortConfig.BAUD_RATES.map { it.toString() },
                    selectedIndex = SerialPortConfig.BAUD_RATES.indexOf(vm.portConfig.baudRate),
                    onSelect = { index ->
                        customBaud = ""
                        vm.updatePortConfig { it.copy(baudRate = SerialPortConfig.BAUD_RATES[index]) }
                    }
                )
                OutlinedTextField(
                    value = customBaud,
                    onValueChange = { text ->
                        customBaud = text.filter { c -> c.isDigit() }.take(9)
                        val parsed = customBaud.toIntOrNull()
                        if (parsed != null && parsed in 50..10_000_000) {
                            vm.updatePortConfig { it.copy(baudRate = parsed) }
                        }
                    },
                    label = { Text("自定义波特率") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                KeyValueRow("当前", vm.portConfig.label)

                HorizontalDivider(color = ScopeColors.Outline)

                SectionLabel("数据位")
                ChoiceChips(
                    options = SerialPortConfig.DATA_BITS.map { "$it" },
                    selectedIndex = SerialPortConfig.DATA_BITS.indexOf(vm.portConfig.dataBits),
                    onSelect = { index ->
                        vm.updatePortConfig { it.copy(dataBits = SerialPortConfig.DATA_BITS[index]) }
                    }
                )

                SectionLabel("停止位")
                ChoiceChips(
                    options = SerialPortConfig.STOP_BITS.map { "$it" },
                    selectedIndex = SerialPortConfig.STOP_BITS.indexOf(vm.portConfig.stopBits),
                    onSelect = { index ->
                        vm.updatePortConfig { it.copy(stopBits = SerialPortConfig.STOP_BITS[index]) }
                    }
                )

                SectionLabel("校验位")
                ChoiceChips(
                    options = SerialParity.entries.map { it.label },
                    selectedIndex = vm.portConfig.parity.ordinal,
                    onSelect = { index ->
                        vm.updatePortConfig { it.copy(parity = SerialParity.entries[index]) }
                    }
                )

                SectionLabel("流控")
                ChoiceChips(
                    options = SerialFlowControl.entries.map { it.label },
                    selectedIndex = vm.portConfig.flowControl.ordinal,
                    onSelect = { index ->
                        vm.updatePortConfig { it.copy(flowControl = SerialFlowControl.entries[index]) }
                    }
                )

                HorizontalDivider(color = ScopeColors.Outline)

                ToggleRow(
                    label = "用 RTS 控制 RS485 收发方向",
                    supporting = "半双工收发器把 DE/RE 接到 RTS 时开启；自动换向的模块请关闭",
                    checked = vm.portConfig.rtsForDirection,
                    onCheckedChange = { value ->
                        vm.updatePortConfig { it.copy(rtsForDirection = value) }
                    }
                )
            }
        }

        item {
            PanelCard(title = "链路状态") {
                KeyValueRow(
                    "连接",
                    when (state.connection) {
                        ConnectionState.DISCONNECTED -> "未连接"
                        ConnectionState.CONNECTING -> "连接中…"
                        ConnectionState.CONNECTED -> "已连接"
                        ConnectionState.ERROR -> "错误"
                    }
                )
                state.deviceLabel?.let { KeyValueRow("设备", it) }
                state.portLabel?.let { KeyValueRow("参数", it) }
                state.errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = ScopeColors.Danger)
                }
                HorizontalDivider(color = ScopeColors.Outline)
                KeyValueRow("接收", Fmt.bytes(state.bytesReceived))
                KeyValueRow("发送", Fmt.bytes(state.bytesSent))
                KeyValueRow("有效帧", Fmt.count(state.framesReceived))
                KeyValueRow("丢帧", Fmt.count(state.discardedFrames))
                KeyValueRow("丢字节", Fmt.bytes(state.discardedBytes))
                KeyValueRow("帧率 / 采样率", Fmt.rate(state.sampleRateHz))
                KeyValueRow("吞吐", Fmt.bytes(state.rxBytesPerSecond.toLong()) + "/s")
                KeyValueRow("活动通道", "${state.activeChannels}")
            }
        }
    }
}

@Composable
private fun DeviceRow(vm: ScopeViewModel, device: UsbSerialDevice, state: EngineState) {
    val isConnected = state.connected && state.deviceId == device.deviceId

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(device.displayName, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = buildString {
                append(if (device.supported) "驱动：${device.driverName}" else "无匹配驱动")
                if (!device.hasPermission) append(" · 未授权")
            },
            style = MaterialTheme.typography.labelSmall
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.connect(device.deviceId) },
                enabled = device.supported && !isConnected
            ) {
                Text(if (isConnected) "已连接" else "连接")
            }
            if (!device.supported) {
                Text(
                    "该 VID/PID 未被 usb-serial-for-android 支持",
                    style = MonoNumberStyle,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}