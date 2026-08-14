package com.homecontroller.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide owner of the single BLE connection.
 *  Both the app UI and the home-screen widget talk to this holder so only
 *  one GATT link to the controller ever exists. */
object RelayHolder {

    data class DeviceState(
        val bluetoothOn: Boolean = true,
        val connected: Boolean = false,
        val states: List<Boolean> = List(Protocol.RELAY_COUNT) { false },
        val restoreMode: Int = Protocol.MODE_RESTORE_LAST,
        val timers: Map<Int, Protocol.TimerInfo> = emptyMap(),
        val fanLevel: Int = 1,
        val fanBusy: Boolean = false,
        val rules: List<Protocol.TimeRule> = (0 until Protocol.RULE_COUNT).map {
            Protocol.TimeRule(it, false, 0, Protocol.RULE_ACTION_OFF, 0, 0, 0)
        },
        val deviceEpoch: Long = 0,
        val timeSynced: Boolean = false
    )

    interface Events {
        fun onConnected() {}
        fun onDisconnected() {}
        fun onOtaStatus(status: String, crcMismatch: Boolean) {}
        fun onOtaAck(seq: Int) {}
        fun onOtaResendFrom(seq: Int) {}
        fun onOtaStarted() {}
        fun onError(message: String)
    }

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    var manager: BleRelayManager? = null
        private set

    private var appContext: Context? = null

    /** Extra consumer (e.g. the ViewModel) notified of connection events. */
    var events: Events? = null

    private fun refreshWidgets() {
        appContext?.let { runCatching { RelaysWidgetProvider.refresh(it) } }
    }

    fun init(context: Context) {
        if (manager != null) return
        val app = context.applicationContext
        appContext = app
        manager = BleRelayManager(app).apply {
            listener = object : BleRelayManager.Listener {
                override fun onConnected() {
                    Log.d(TAG, "Holder connected")
                    _state.update { it.copy(connected = true, bluetoothOn = true) }
                    refresh()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        requestRestoreMode()
                        syncEpoch()
                        requestFanSpeed()
                        requestRules()
                        requestTime()
                    }, 150)
                    refreshWidgets()
                    events?.onConnected()
                }

                override fun onDisconnected() {
                    _state.update { it.copy(connected = false) }
                    refreshWidgets()
                    events?.onDisconnected()
                }

                override fun onStates(states: List<Boolean>) {
                    _state.update { it.copy(states = states) }
                    refreshWidgets()
                }

                override fun onStateChanged(channel: Int, on: Boolean) {
                    _state.update { s ->
                        val list = s.states.toMutableList()
                        list[channel] = on
                        val timers = if (!on) s.timers - channel else s.timers
                        s.copy(states = list, timers = timers)
                    }
                    refreshWidgets()
                }

                override fun onRestoreMode(mode: Int) {
                    _state.update { it.copy(restoreMode = mode) }
                    refreshWidgets()
                }

                override fun onTimer(timer: Protocol.TimerInfo) {
                    _state.update { s ->
                        if (timer.active) s.copy(timers = s.timers + (timer.channel to timer))
                        else s.copy(timers = s.timers - timer.channel)
                    }
                    refreshWidgets()
                }

                override fun onFanSpeed(level: Int) {
                    _state.update { it.copy(fanLevel = level, fanBusy = false) }
                    refreshWidgets()
                }

                override fun onFanBusy(busy: Boolean) {
                    _state.update { it.copy(fanBusy = busy) }
                    refreshWidgets()
                }

                override fun onRules(rules: List<Protocol.TimeRule>) {
                    _state.update { it.copy(rules = rules) }
                    refreshWidgets()
                }

                override fun onTime(epochSeconds: Long, synced: Boolean) {
                    _state.update { it.copy(deviceEpoch = epochSeconds, timeSynced = synced) }
                }

                override fun onOtaStatus(status: String, crcMismatch: Boolean) {
                    events?.onOtaStatus(status, crcMismatch)
                }

                override fun onOtaAck(seq: Int) {
                    events?.onOtaAck(seq)
                }

                override fun onOtaResendFrom(seq: Int) {
                    events?.onOtaResendFrom(seq)
                }

                override fun onOtaStarted() {
                    events?.onOtaStarted()
                }

                override fun onError(message: String) {
                    events?.onError(message)
                }
            }
        }
    }

    fun bluetoothCheck() {
        _state.update { it.copy(bluetoothOn = manager?.isEnabled ?: true) }
    }

    fun connect() {
        manager?.connect()
    }

    fun disconnect() {
        manager?.disconnect()
        _state.update { it.copy(connected = false) }
    }

    fun toggleRelay(channel: Int, on: Boolean) {
        if (_state.value.connected) manager?.setRelay(channel, on)
    }

    fun refresh() {
        manager?.refresh()
    }

    fun setRestoreMode(mode: Int) {
        manager?.setRestoreMode(mode)
    }

    fun setTimer(channel: Int, minutes: Int, active: Boolean = minutes > 0) {
        manager?.setTimer(channel, minutes, active)
    }

    fun setFanSpeed(level: Int) {
        manager?.setFanSpeed(level)
    }

    fun requestFanSpeed() {
        manager?.requestFanSpeed()
    }

    fun requestRules() {
        manager?.requestRules()
    }

    fun syncEpoch() {
        manager?.syncEpoch()
    }

    fun setRule(rule: Protocol.TimeRule) {
        manager?.setRule(rule)
    }

    fun requestTime() {
        manager?.requestTime()
    }

    fun startOta(size: Int) = manager?.startOta(size)

    fun writeOtaChunk(seq: Int, data: ByteArray): Boolean =
        manager?.writeOtaChunk(seq, data) ?: false

    fun endOta(crc: Long) = manager?.endOta(crc)

    fun hasPermissions(): Boolean = manager?.hasPermissions() ?: false

    val isReady: Boolean
        get() = manager?.isReady ?: false

    private const val TAG = "RelayHolder"
}