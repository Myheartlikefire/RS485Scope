package com.serialscope.rs485.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Thin wrapper around usb-serial-for-android: resolves a driver, opens the first
 * port and exposes blocking read/write plus optional RS485 direction control.
 *
 * The library maps the standard setter pairs (setDTR/getDTR, setRTS/getRTS) onto
 * Kotlin properties, so `port.rts = true` drives the DE/RE pin of adapter boards
 * that wire the transceiver direction to RTS.
 */
class SerialLink(private val context: Context) {

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var connection: UsbDeviceConnection? = null
    private var port: UsbSerialPort? = null
    private var config: SerialPortConfig = SerialPortConfig()

    val isOpen: Boolean get() = port?.isOpen == true

    val manager: UsbManager get() = usbManager

    /** USB devices currently attached, annotated with driver support. */
    fun listDevices(): List<UsbSerialDevice> {
        val prober = UsbSerialProber.getDefaultProber()
        val supportedIds = try {
            prober.findAllDrivers(usbManager).associate { driver ->
                driver.device.deviceId to driver.javaClass.simpleName
            }
        } catch (t: Throwable) {
            Log.w(TAG, "findAllDrivers failed", t)
            emptyMap()
        }
        return try {
            usbManager.deviceList.values
                .map { device ->
                    UsbSerialDevice(
                        device = device,
                        driverName = supportedIds[device.deviceId],
                        hasPermission = usbManager.hasPermission(device)
                    )
                }
                .sortedWith(compareByDescending<UsbSerialDevice> { it.supported }.thenBy { it.deviceId })
        } catch (t: Throwable) {
            Log.w(TAG, "deviceList failed", t)
            emptyList()
        }
    }

    fun findDevice(deviceId: Int): UsbDevice? =
        try {
            usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }
        } catch (t: Throwable) {
            null
        }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun open(device: UsbDevice, portConfig: SerialPortConfig): Result<Unit> {
        close()

        val driver = try {
            UsbSerialProber.getDefaultProber().probeDevice(device)
        } catch (t: Throwable) {
            Log.w(TAG, "probeDevice failed", t)
            null
        } ?: return Result.failure(
            IllegalStateException("没有匹配的 USB 串口驱动 (VID/PID 不受支持)")
        )

        val conn = usbManager.openDevice(device)
            ?: return Result.failure(IllegalStateException("打开 USB 设备失败，请确认已授予 USB 权限"))

        val p = driver.ports.firstOrNull()
        if (p == null) {
            runCatching { conn.close() }
            return Result.failure(IllegalStateException("该设备没有可用的串口"))
        }

        return try {
            p.open(conn)
            p.setParameters(
                portConfig.baudRate,
                portConfig.dataBits,
                stopBitConstant(portConfig.stopBits),
                parityConstant(portConfig.parity)
            )
            runCatching { p.flowControl = flowControlConstant(portConfig.flowControl) }
            runCatching { p.setReadQueue(4, 16 * 1024) }
            if (portConfig.rtsForDirection) {
                // Idle in receive mode; the writer flips RTS around every burst.
                p.rts = false
                p.dtr = portConfig.dtr
            } else {
                p.dtr = portConfig.dtr
                p.rts = portConfig.rts
            }
            connection = conn
            port = p
            config = portConfig
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.w(TAG, "open failed", t)
            runCatching { p.close() }
            runCatching { conn.close() }
            connection = null
            port = null
            Result.failure(t)
        }
    }

    /** Blocking read; returns the number of bytes read, or 0 on timeout. */
    fun read(buffer: ByteArray, timeoutMillis: Int): Int {
        val p = port ?: return 0
        return p.read(buffer, timeoutMillis)
    }

    /** Blocking write, toggling RTS around the burst when direction control is on. */
    fun write(bytes: ByteArray, timeoutMillis: Int = 500) {
        val p = port ?: throw IllegalStateException("串口未连接")
        if (config.rtsForDirection) {
            p.rts = true
            try {
                p.write(bytes, timeoutMillis)
            } finally {
                p.rts = false
            }
        } else {
            p.write(bytes, timeoutMillis)
        }
    }

    fun close() {
        runCatching { port?.close() }
        runCatching { connection?.close() }
        port = null
        connection = null
    }

    private fun stopBitConstant(stopBits: Int): Int =
        if (stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1

    private fun parityConstant(parity: SerialParity): Int = when (parity) {
        SerialParity.NONE -> UsbSerialPort.PARITY_NONE
        SerialParity.ODD -> UsbSerialPort.PARITY_ODD
        SerialParity.EVEN -> UsbSerialPort.PARITY_EVEN
        SerialParity.MARK -> UsbSerialPort.PARITY_MARK
        SerialParity.SPACE -> UsbSerialPort.PARITY_SPACE
    }

    private fun flowControlConstant(flow: SerialFlowControl): UsbSerialPort.FlowControl = when (flow) {
        SerialFlowControl.NONE -> UsbSerialPort.FlowControl.NONE
        SerialFlowControl.RTS_CTS -> UsbSerialPort.FlowControl.RTS_CTS
        SerialFlowControl.DTR_DSR -> UsbSerialPort.FlowControl.DTR_DSR
        SerialFlowControl.XON_XOFF -> UsbSerialPort.FlowControl.XON_XOFF
    }

    companion object {
        private const val TAG = "SerialLink"
    }
}