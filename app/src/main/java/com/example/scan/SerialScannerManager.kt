package com.example.scan

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.util.concurrent.Executors

class SerialScannerManager(
    private val context: Context,
    private val settings: List<SerialDevice>,
    private val listener: OnSerialScanListener
) {

    interface OnSerialScanListener {
        fun onSerialCodeScanned(code: String)
        fun onSerialError(hasError: Boolean)
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val activeConnections = mutableMapOf<UsbDevice, DeviceConnection>()
    private var hasError = false

    private data class DeviceConnection(
        val port: UsbSerialPort,
        val settings: SerialDevice,
        var ioManager: SerialInputOutputManager? = null,
        val buffer: StringBuilder = StringBuilder()
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            if (device == null && ACTION_USB_PERMISSION != intent.action) {
                Log.w(TAG, "Received USB broadcast with no device.")
                return
            }

            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val permissionDevice: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            permissionDevice?.let {
                                Log.d(TAG, "Permission granted for device ${it.deviceName}")
                                connectToDevice(it)
                            }
                        } else {
                            Log.e(TAG, "Permission denied for device ${permissionDevice?.deviceName}")
                            updateOverallErrorState()
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    device?.let {
                        Log.d(TAG, "USB device attached: ${it.deviceName}")
                        if (settings.any { s -> s.vendorId == it.vendorId && s.productId == it.productId }) {
                            if (usbManager.hasPermission(it)) {
                                connectToDevice(it)
                            } else {
                                requestUsbPermission(it)
                            }
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    device?.let {
                        Log.d(TAG, "USB device detached: ${it.deviceName}")
                        disconnectFromDevice(it)
                    }
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbReceiver, filter)
        checkForConnectedDevices()
    }

    fun stop() {
        try {
            context.unregisterReceiver(usbReceiver)
            disconnectAll()
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore.
        }
    }

    private fun checkForConnectedDevices() {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Log.d(TAG, "No USB serial drivers available.")
            return
        }

        var deviceFound = false
        for (deviceSetting in settings) {
            for (driver in availableDrivers) {
                val device = driver.device
                if (device.vendorId == deviceSetting.vendorId && device.productId == deviceSetting.productId) {
                    deviceFound = true
                    if (usbManager.hasPermission(device)) {
                        connectToDevice(device)
                    } else {
                        requestUsbPermission(device)
                    }
                    break // Found a matching driver for this setting
                }
            }
        }
        if (!deviceFound && settings.isNotEmpty()) {
             Log.e(TAG, "No configured devices found.")
             listener.onSerialError(true)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun connectToDevice(device: UsbDevice) {
        val deviceSetting = settings.firstOrNull { it.vendorId == device.vendorId && it.productId == device.productId }
        if (deviceSetting == null) {
            Log.w(TAG, "No settings found for device ${device.deviceName}")
            return
        }

        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            Log.e(TAG, "Driver not found for device.")
            updateOverallErrorState()
            return
        }

        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e(TAG, "Failed to open device connection.")
            updateOverallErrorState()
            return
        }

        val port = driver.ports[0]
        try {
            port.open(connection)
            port.setParameters(
                deviceSetting.baudRate,
                deviceSetting.dataBits,
                deviceSetting.stopBits,
                getParityFromString(deviceSetting.parity)
            )

            val deviceConnection = DeviceConnection(port, deviceSetting)
            val listener = object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    processDataForConnection(deviceConnection, data)
                }

                override fun onRunError(e: Exception) {
                    Log.e(TAG, "Serial IO error for ${device.deviceName}", e)
                    disconnectFromDevice(device)
                }
            }
            val ioManager = SerialInputOutputManager(port, listener)
            Executors.newSingleThreadExecutor().submit(ioManager)
            deviceConnection.ioManager = ioManager
            activeConnections[device] = deviceConnection

            Log.d(TAG, "Connected successfully to ${device.deviceName}")
            updateOverallErrorState()
        } catch (e: IOException) {
            Log.e(TAG, "Error setting up serial port for ${device.deviceName}", e)
            disconnectFromDevice(device)
        }
    }

    private fun disconnectFromDevice(device: UsbDevice) {
        val connection = activeConnections.remove(device)
        connection?.let {
            try {
                it.ioManager?.stop()
                it.port.close()
                Log.d(TAG, "Disconnected from ${device.deviceName}")
            } catch (e: IOException) {
                Log.e(TAG, "Error disconnecting from ${device.deviceName}", e)
            }
        }
        updateOverallErrorState()
    }

    private fun disconnectAll() {
        activeConnections.keys.toList().forEach { device ->
            disconnectFromDevice(device)
        }
    }

    private fun updateOverallErrorState() {
        // The error state is true if any configured device is not in the active connections list.
        val configuredDevices = usbManager.deviceList.values.filter { device ->
            settings.any { it.vendorId == device.vendorId && it.productId == device.productId }
        }
        val hasErrors = configuredDevices.any { !activeConnections.containsKey(it) }

        if (hasError != hasErrors) {
            hasError = hasErrors
            listener.onSerialError(hasError)
        }
    }

    private fun processDataForConnection(connection: DeviceConnection, data: ByteArray) {
        val decodedData = String(data, Charsets.UTF_8)
        connection.buffer.append(decodedData)

        val terminator = connection.settings.terminator.replace("\\n", "\n").replace("\\r", "\r")

        var terminatorIndex = connection.buffer.indexOf(terminator)
        while (terminatorIndex != -1) {
            val code = connection.buffer.substring(0, terminatorIndex).trim()
            if (code.isNotEmpty()) {
                listener.onSerialCodeScanned(code)
            }
            connection.buffer.delete(0, terminatorIndex + terminator.length)
            terminatorIndex = connection.buffer.indexOf(terminator)
        }
    }

    private fun getParityFromString(parityStr: String): Int {
        return when (parityStr.uppercase()) {
            "NONE" -> UsbSerialPort.PARITY_NONE
            "ODD" -> UsbSerialPort.PARITY_ODD
            "EVEN" -> UsbSerialPort.PARITY_EVEN
            "MARK" -> UsbSerialPort.PARITY_MARK
            "SPACE" -> UsbSerialPort.PARITY_SPACE
            else -> UsbSerialPort.PARITY_NONE
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.scan.USB_PERMISSION"
        private const val TAG = "SerialScannerManager"
    }
}
