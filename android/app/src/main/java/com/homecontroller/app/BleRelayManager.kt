package com.homecontroller.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BleRelayManager(private val context: Context) {

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onStates(states: List<Boolean>)
        fun onStateChanged(channel: Int, on: Boolean) {}
        fun onError(message: String)
    }

    var listener: Listener? = null

    private val adapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var lastDevice: BluetoothDevice? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var connecting = false

    val isEnabled: Boolean
        get() = adapter.isEnabled

    val deviceName: String?
        @SuppressLint("MissingPermission")
        get() = lastDevice?.name

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    fun connect(target: BluetoothDevice? = null) {
        if (!adapter.isEnabled) {
            listener?.onError("Bluetooth is off. Enable it in Settings.")
            return
        }
        if (!hasPermissions()) {
            listener?.onError("Bluetooth permissions are missing.")
            return
        }
        if (target != null) {
            lastDevice = target
            connectToDevice(target)
            return
        }
        if (lastDevice != null && !connecting) {
            connectToDevice(lastDevice!!)
            return
        }
        startScan()
    }

    @SuppressLint("MissingPermission")
    fun reconnect() {
        if (lastDevice != null) {
            connectToDevice(lastDevice!!)
        } else {
            startScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        connecting = true
        val scanner = adapter.bluetoothLeScanner ?: return
        this.scanner = scanner
        Log.d(TAG, "Scanning for ${Protocol.DEVICE_NAME}")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device?.name ?: return
                if (name == Protocol.DEVICE_NAME) {
                    scanner.stopScan(this)
                    handler.removeCallbacksAndMessages(null)
                    lastDevice = result.device
                    connectToDevice(result.device)
                }
            }
        }
        scanCallback = callback
        scanner.startScan(callback)
        handler.postDelayed({
            scanner.stopScan(callback)
            if (connecting) {
                connecting = false
                listener?.onError("${Protocol.DEVICE_NAME} not found. Power it on and retry.")
            }
        }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to ${device.address}")
        gatt?.disconnect()
        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        connecting = false
        handler.removeCallbacksAndMessages(null)
        stopScanSafe()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        writeChar = null
    }

    private fun stopScanSafe() {
        runCatching {
            scanCallback?.let { scanner?.stopScan(it) }
            scanCallback = null
            scanner = null
        }
    }

    @SuppressLint("MissingPermission")
    fun sendAuth(pin: Int) {
        writeChar?.let { write(it, Protocol.authPacket(pin)) }
    }

    @SuppressLint("MissingPermission")
    fun setRelay(channel: Int, on: Boolean) {
        writeChar?.let { write(it, Protocol.setPacket(channel, on)) }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        writeChar?.let { write(it, Protocol.getPacket) }
    }

    @SuppressLint("MissingPermission")
    private fun write(char: BluetoothGattCharacteristic, bytes: ByteArray) {
        val g = gatt ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(char)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected")
                    runCatching { g.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connecting = false
                    Log.d(TAG, "Disconnected")
                    listener?.onDisconnected()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onError("Service discovery failed")
                return
            }
            val service = g.getService(Protocol.SERVICE_UUID) ?: run {
                listener?.onError("HomeController service not found")
                return
            }
            val wc = service.getCharacteristic(Protocol.WRITE_CHAR)
            val sc = service.getCharacteristic(Protocol.STATUS_CHAR)
            if (wc == null || sc == null) {
                listener?.onError("Relay characteristics not found")
                return
            }
            writeChar = wc
            runCatching { g.setCharacteristicNotification(sc, true) }
            connecting = false
            listener?.onConnected()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleStatus(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleStatus(characteristic.value)
        }
    }

    private fun handleStatus(data: ByteArray) {
        if (data.isEmpty()) return
        when (data[0]) {
            Protocol.RSP_AUTH_OK -> {
                Log.d(TAG, "Auth OK")
                Protocol.parseStates(data)?.let { listener?.onStates(it) }
            }
            Protocol.RSP_GET_ALL -> {
                Log.d(TAG, "States received")
                Protocol.parseStates(data)?.let { listener?.onStates(it) }
            }
            Protocol.RSP_STATE_CHANGED -> {
                Log.d(TAG, "State changed")
                if (data.size == 3) {
                    val channel = data[1].toInt()
                    val on = data[2] == 1.toByte()
                    if (channel in 0 until Protocol.RELAY_COUNT) {
                        listener?.onStateChanged(channel, on)
                    }
                }
            }
            Protocol.RSP_ERR_PIN -> listener?.onError("Wrong PIN")
            Protocol.RSP_ERR_UNAUTH -> listener?.onError("Not authorized. Resend PIN.")
        }
    }

    companion object {
        private const val TAG = "BleRelayManager"
        private const val SCAN_TIMEOUT_MS = 10_000L
    }
}