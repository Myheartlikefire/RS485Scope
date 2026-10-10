package com.serialscope.rs485.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

/**
 * Wraps the "grant access to this USB device" dialog.
 *
 * The reply arrives as a broadcast addressed to our own package, so the receiver
 * is registered as NOT_EXPORTED (system broadcasts are still delivered).
 */
class UsbPermissionHelper(private val context: Context) {

    private var receiver: BroadcastReceiver? = null
    private var callback: ((Boolean) -> Unit)? = null
    private var expectedDeviceId: Int = -1

    fun isGranted(device: UsbDevice): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.hasPermission(device)
    }

    fun request(device: UsbDevice, onResult: (Boolean) -> Unit) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.hasPermission(device)) {
            onResult(true)
            return
        }
        dispose()
        callback = onResult
        expectedDeviceId = device.deviceId

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null || intent.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val replied = IntentCompat.getParcelableExtra(
                    intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java
                )
                val matches = replied == null || replied.deviceId == expectedDeviceId
                finish(granted && matches)
            }
        }
        receiver = r
        ContextCompat.registerReceiver(
            context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        manager.requestPermission(device, pending)
    }

    private fun finish(granted: Boolean) {
        val cb = callback
        dispose()
        cb?.invoke(granted)
    }

    fun dispose() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        callback = null
        expectedDeviceId = -1
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.serialscope.rs485.USB_PERMISSION"
    }
}