package com.homecontroller.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import java.util.TimeZone

class BleRelayManager(private val context: Context) {

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onStates(states: List<Boolean>)
        fun onStateChanged(channel: Int, on: Boolean) {}
        fun onRestoreMode(mode: Int) {}
        fun onTimer(timer: Protocol.TimerInfo) {}
        fun onTimers(timers: Map<Int, Protocol.TimerInfo>) {}
        fun onFanSpeed(level: Int) {}
        fun onFanBusy(busy: Boolean) {}
        fun onRules(rules: List<Protocol.TimeRule>) {}
        fun onTime(epochSeconds: Long, synced: Boolean) {}
        fun onOtaStatus(status: String, crcMismatch: Boolean) {}
        fun onOtaAck(seq: Int) {}
        fun onOtaResendFrom(seq: Int) {}
        fun onOtaStarted() {}
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
    private var connected = false
    private var waitingForNotifications = false

    val isEnabled: Boolean
        get() = adapter.isEnabled

    val isReady: Boolean
        get() = writeChar != null && gatt != null

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
        if (isConnected) return
        if (connecting) return
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
        if (lastDevice != null) {
            connectToDevice(lastDevice!!)
            return
        }
        startScan()
    }

    val isConnected: Boolean
        get() = gatt != null && connected

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
        connecting = true
        val old = gatt
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        old?.disconnect()
        old?.close()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        connecting = false
        connected = false
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
    fun reconnect() {
        if (lastDevice != null) {
            connectToDevice(lastDevice!!)
        } else {
            startScan()
        }
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
    fun setRestoreMode(mode: Int) {
        writeChar?.let { write(it, Protocol.setModePacket(mode)) }
    }

    @SuppressLint("MissingPermission")
    fun requestRestoreMode() {
        writeChar?.let { write(it, Protocol.getModePacket) }
    }

    @SuppressLint("MissingPermission")
    fun setTimer(channel: Int, minutes: Int, active: Boolean = minutes > 0) {
        writeChar?.let { write(it, Protocol.timerPacket(channel, minutes, active)) }
    }

    @SuppressLint("MissingPermission")
    fun setFanSpeed(level: Int) {
        writeChar?.let { write(it, Protocol.setFanSpeedPacket(level)) }
    }

    @SuppressLint("MissingPermission")
    fun requestFanSpeed() {
        writeChar?.let { write(it, Protocol.getFanSpeedPacket) }
    }

    @SuppressLint("MissingPermission")
    fun syncEpoch() {
        // Local-time epoch: the controller compares rule times against its own
        // wall clock, so hand it our local HH:MM (UTC + current tz offset).
        val now = System.currentTimeMillis()
        val tzOffset = TimeZone.getDefault().getOffset(now)
        val localEpoch = (now + tzOffset) / 1000
        writeChar?.let { write(it, Protocol.epochPacket(localEpoch)) }
    }

    @SuppressLint("MissingPermission")
    fun requestRules() {
        writeChar?.let { write(it, Protocol.getRulesPacket) }
    }

    @SuppressLint("MissingPermission")
    fun setRule(rule: Protocol.TimeRule) {
        writeChar?.let { write(it, Protocol.setRulePacket(rule)) }
    }

    fun deleteRule(rule: Protocol.TimeRule) {
        writeChar?.let { write(it, Protocol.deleteRulePacket(rule)) }
    }

    fun requestTimers() {
        writeChar?.let { write(it, Protocol.getTimersPacket) }
    }

    @SuppressLint("MissingPermission")
    fun requestTime() {
        writeChar?.let { write(it, Protocol.getTimePacket) }
    }

    @SuppressLint("MissingPermission")
    fun startOta(size: Int) {
        writeChar?.let { write(it, Protocol.otaBeginPacket(size)) }
    }

    @SuppressLint("MissingPermission")
    fun writeOtaChunk(seq: Int, data: ByteArray): Boolean {
        val g = gatt ?: return false
        val char = writeChar ?: return false
        val packet = Protocol.otaWritePacket(seq, data)
        // writeCharacteristic returns false while the GATT write queue is
        // full. Block (with a small sleep) until the controller drains it
        // instead of silently dropping chunks.
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            @Suppress("DEPRECATION")
            char.value = packet
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            val ok: Boolean = runCatching { g.writeCharacteristic(char) }.getOrDefault(true)
            if (ok) return true
            Thread.sleep(2)
        }
        return false
    }

    @SuppressLint("MissingPermission")
    fun endOta(crc: Long) {
        writeChar?.let { write(it, Protocol.otaEndPacket(crc)) }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, char: BluetoothGattCharacteristic) {
        waitingForNotifications = true
        runCatching { g.setCharacteristicNotification(char, true) }
        val cccd = char.getDescriptor(Protocol.CCCD_UUID)
        if (cccd == null) {
            onNotificationsEnabled()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                return
            } catch (e: NoSuchMethodError) {
                // Fall through to the deprecated path (older 33-35 devices).
            }
        }
        @Suppress("DEPRECATION")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        g.writeDescriptor(cccd)
    }

    private fun onNotificationsEnabled() {
        if (!waitingForNotifications) return
        waitingForNotifications = false
        listener?.onConnected()
    }

    @SuppressLint("MissingPermission")
    private fun write(char: BluetoothGattCharacteristic, bytes: ByteArray) {
        val g = gatt ?: return
        Log.d(TAG, "Sending " + bytes.joinToString(" ") { "%02X".format(it) })
        // writeCharacteristic returns false while the OS GATT write queue is
        // full. Bursts of quick writes (connect-time sync, timers, rules) are
        // silently dropped otherwise, so retry until the write is accepted.
        val deadline = System.currentTimeMillis() + 2000
        while (true) {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val ok: Boolean = runCatching { g.writeCharacteristic(char) }.getOrDefault(true)
            if (ok) return
            if (System.currentTimeMillis() >= deadline) return
            Thread.sleep(5)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (g !== gatt) return
                    Log.d(TAG, "Connected")
                    connected = true
                    runCatching { g.discoverServices() }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (g !== gatt && gatt != null) return
                    connecting = false
                    connected = false
                    waitingForNotifications = false
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
            connecting = false
            enableNotifications(g, sc)
            // The rules list reply is 49 bytes; the default 23-byte MTU caps a
            // notification at 20 bytes and fragments the payload. Negotiate a
            // larger MTU so replies arrive whole.
            runCatching { g.requestMtu(517) }
        }

        @Deprecated("Deprecated in Java")
        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == Protocol.CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    onNotificationsEnabled()
                } else {
                    listener?.onError("Failed to enable notifications")
                }
            }
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
            Protocol.RSP_MODE -> {
                Log.d(TAG, "Restore mode received")
                if (data.size == 2) listener?.onRestoreMode(data[1].toInt())
            }
            Protocol.RSP_TIMER_OK -> {
                Log.d(TAG, "Timer received")
                Protocol.parseTimer(data)?.let { listener?.onTimer(it) }
            }
            Protocol.RSP_FAN_SPEED -> {
                Log.d(TAG, "Fan speed received")
                Protocol.parseFanSpeed(data)?.let { listener?.onFanSpeed(it) }
            }
            Protocol.RSP_FAN_BUSY -> {
                Log.d(TAG, "Fan busy received")
                Protocol.parseFanBusy(data)?.let { listener?.onFanBusy(it) }
            }
            Protocol.RSP_RULES -> {
                Log.d(TAG, "Rules received")
                Protocol.parseRules(data)?.let { listener?.onRules(it) }
            }
            Protocol.RSP_TIME -> {
                Log.d(TAG, "Time received")
                Protocol.parseTime(data)?.let { (epoch, synced) -> listener?.onTime(epoch, synced) }
            }
            Protocol.RSP_TIMERS -> {
                Log.d(TAG, "Timers received")
                Protocol.parseTimers(data)?.let { listener?.onTimers(it) }
            }
            Protocol.RSP_OTA_BEGIN -> {
                Log.d(TAG, "OTA upload started")
                listener?.onOtaStarted()
            }
            Protocol.RSP_OTA_ACK -> {
                if (data.size >= 3) {
                    val seq = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    Log.d(TAG, "OTA chunk acked: $seq")
                    listener?.onOtaAck(seq)
                }
            }
            Protocol.RSP_OTA_RESEND -> {
                if (data.size >= 3) {
                    val seq = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
                    Log.d(TAG, "OTA resend from: $seq")
                    listener?.onOtaResendFrom(seq)
                }
            }
            Protocol.RSP_OTA_END -> listener?.onOtaStatus("Firmware sent — controller is restarting", false)
            Protocol.RSP_OTA_CRC_ERR -> listener?.onOtaStatus("CRC mismatch — update rejected", true)
            Protocol.RSP_SET_OK -> Log.d(TAG, "Set applied")
        }
    }

    companion object {
        private const val TAG = "BleRelayManager"
        private const val SCAN_TIMEOUT_MS = 10_000L
    }
}