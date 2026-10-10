package com.serialscope.rs485.usb

import android.hardware.usb.UsbDevice

/** One USB device seen by the host controller, annotated with prober support. */
data class UsbSerialDevice(
    val device: UsbDevice,
    val driverName: String?,
    val hasPermission: Boolean
) {
    val deviceId: Int get() = device.deviceId

    val supported: Boolean get() = driverName != null

    val displayName: String
        get() = buildString {
            append(device.productName?.takeIf { it.isNotBlank() } ?: "USB 串口设备")
            append("  [")
            append(hex(device.vendorId))
            append(':')
            append(hex(device.productId))
            append(']')
        }

    private fun hex(value: Int): String = "%04X".format(value)
}