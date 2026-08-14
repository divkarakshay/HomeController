package com.homecontroller.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RelayViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val connected: Boolean = false,
        val connecting: Boolean = false,
        val bluetoothOn: Boolean = true,
        val states: List<Boolean> = List(Protocol.RELAY_COUNT) { false },
        val restoreMode: Int = Protocol.MODE_RESTORE_LAST,
        val timers: Map<Int, Protocol.TimerInfo> = emptyMap(),
        val fanLevel: Int = 1,
        val fanBusy: Boolean = false,
        val rules: List<Protocol.TimeRule> = (0 until Protocol.RULE_COUNT).map {
            Protocol.TimeRule(it, false, 0, Protocol.RULE_ACTION_OFF, 0, 0, 0)
        },
        val deviceEpoch: Long = 0,
        val timeSynced: Boolean = false,
        val otaProgress: Float? = null,
        val otaStatus: String? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState(bluetoothOn = RelayHolder.state.value.bluetoothOn))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var reconnectAttempts = 0

    init {
        RelayHolder.init(app)
        RelayService.start(app)
        RelayHolder.bluetoothCheck()

        RelayHolder.events = object : RelayHolder.Events {
            override fun onConnected() {
                reconnectAttempts = 0
                _state.update { it.copy(connecting = false, error = null) }
            }

            override fun onDisconnected() {
                _state.update { it.copy(connecting = false) }
                if (reconnectAttempts < MAX_RECONNECTS) {
                    reconnectAttempts++
                    viewModelScope.launch {
                        delay(RECONNECT_DELAY_MS)
                        if (!_state.value.connected) {
                            _state.update { it.copy(connecting = true) }
                            RelayHolder.connect()
                        }
                    }
                }
            }

            override fun onError(message: String) {
                _state.update { it.copy(connecting = false, error = message) }
            }

            override fun onOtaStatus(status: String, crcMismatch: Boolean) {
                _state.update {
                    it.copy(otaProgress = if (crcMismatch) null else it.otaProgress, otaStatus = status)
                }
            }

            override fun onOtaAck(seq: Int) {
                otaAckedSeq = maxOf(otaAckedSeq, seq)
                otaStallDeadline = System.currentTimeMillis() + OTA_STALL_TIMEOUT_MS
            }

            override fun onOtaResendFrom(seq: Int) {
                // The controller is waiting for a chunk we never delivered
                // (NR writes can be dropped). Rewind the window there.
                otaAckedSeq = minOf(otaAckedSeq, seq - 1)
                otaResendNeeded = true
            }

            override fun onOtaStarted() {
                _state.update { it.copy(otaStatus = "Controller accepted the upload") }
            }
        }

        viewModelScope.launch {
            RelayHolder.state.collect { s ->
                _state.update {
                    it.copy(
                        connected = s.connected,
                        bluetoothOn = s.bluetoothOn,
                        states = s.states,
                        restoreMode = s.restoreMode,
                        timers = s.timers,
                        fanLevel = s.fanLevel,
                        fanBusy = s.fanBusy,
                        rules = s.rules,
                        deviceEpoch = s.deviceEpoch,
                        timeSynced = s.timeSynced
                    )
                }
            }
        }
    }

    fun connect() {
        reconnectAttempts = 0
        _state.update { it.copy(connecting = true, error = null) }
        RelayHolder.connect()
    }

    fun toggleRelay(channel: Int, on: Boolean) {
        _state.update { s ->
            val newStates = s.states.toMutableList().apply { this[channel] = on }
            s.copy(states = newStates)
        }
        if (_state.value.connected) {
            RelayHolder.toggleRelay(channel, on)
        } else {
            _state.update { it.copy(error = "Not connected to ${Protocol.DEVICE_NAME}") }
        }
    }

    fun refresh() {
        if (_state.value.connected) {
            RelayHolder.refresh()
        } else {
            _state.update { it.copy(error = "Not connected") }
        }
    }

    fun setRestoreMode(mode: Int) {
        if (!_state.value.connected) {
            _state.update { it.copy(error = "Connect to ${Protocol.DEVICE_NAME} to change this setting") }
            return
        }
        _state.update { it.copy(restoreMode = mode) }
        RelayHolder.setRestoreMode(mode)
    }

    fun setTimer(channel: Int, minutes: Int, active: Boolean = minutes > 0) {
        if (!_state.value.connected) {
            _state.update { it.copy(error = "Connect to ${Protocol.DEVICE_NAME} to set a timer") }
            return
        }
        _state.update { s ->
            val timers = if (active) s.timers + (channel to Protocol.TimerInfo(channel, minutes, true))
            else s.timers - channel
            s.copy(timers = timers)
        }
        RelayHolder.setTimer(channel, minutes, active)
    }

    fun setFanSpeed(level: Int) {
        if (!_state.value.connected) {
            _state.update { it.copy(error = "Connect to ${Protocol.DEVICE_NAME} to control the fan") }
            return
        }
        _state.update { it.copy(fanLevel = level, fanBusy = true) }
        RelayHolder.setFanSpeed(level)
    }

    fun setRule(rule: Protocol.TimeRule) {
        if (!_state.value.connected) {
            _state.update { it.copy(error = "Connect to ${Protocol.DEVICE_NAME} to set rules") }
            return
        }
        // The controller answers with the full list (0xB0), which reconciles
        // any optimistic edit.
        RelayHolder.setRule(rule)
    }

    fun refreshRules() {
        RelayHolder.requestRules()
    }

    fun syncClock() {
        if (!_state.value.connected) {
            _state.update { it.copy(error = "Connect to ${Protocol.DEVICE_NAME} to sync the clock") }
            return
        }
        RelayHolder.syncEpoch()
        RelayHolder.requestTime()
        RelayHolder.requestRules()
    }

    fun disconnect() {
        reconnectAttempts = MAX_RECONNECTS
        RelayHolder.disconnect()
        _state.update { it.copy(connected = false, connecting = false) }
    }

    fun uploadFirmware(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                if (!_state.value.connected) {
                    _state.update { it.copy(connecting = true) }
                    RelayHolder.connect()
                    repeat(30) {
                        if (_state.value.connected) return@repeat
                        delay(500)
                    }
                }
                repeat(20) {
                    if (RelayHolder.isReady) return@repeat
                    delay(250)
                }
                if (!_state.value.connected || !RelayHolder.isReady) {
                    throw IllegalStateException("Could not connect to ${Protocol.DEVICE_NAME} — try again")
                }
                val bytes = getApplication<Application>().contentResolver
                    .openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read file")
                if (bytes.size !in 0x1000..Protocol.OTA_MAX_SIZE) {
                    throw IllegalStateException(
                        "Firmware is ${bytes.size / 1024} KB — need 4 KB to ${Protocol.OTA_MAX_SIZE / 1024} KB"
                    )
                }
                val crc = java.util.zip.CRC32().apply { update(bytes) }.value
                otaAckedSeq = -1
                otaResendNeeded = false
                otaStallDeadline = System.currentTimeMillis() + OTA_STALL_TIMEOUT_MS
                RelayHolder.startOta(bytes.size)
                _state.update { it.copy(otaStatus = "Uploading…", otaProgress = 0f) }
                var seq = 0
                var offset = 0
                var lastProgressAt = System.currentTimeMillis()
                val window = OTA_WINDOW_CHUNKS
                while (offset < bytes.size) {
                    // Wait until the controller has caught up to keep the
                    // radio queue small enough that packets are never dropped.
                    if (seq > otaAckedSeq + window) {
                        if (System.currentTimeMillis() > otaStallDeadline) {
                            throw IllegalStateException(
                                "Update stalled on chunk $seq — controller stopped acknowledging. Retry."
                            )
                        }
                        delay(OTA_ACK_POLL_MS)
                        continue
                    }
                    if (otaResendNeeded) {
                        otaResendNeeded = false
                        val from = otaAckedSeq + 1
                        seq = maxOf(0, from)
                        offset = seq * Protocol.OTA_CHUNK_SIZE
                        otaStallDeadline = System.currentTimeMillis() + OTA_STALL_TIMEOUT_MS
                        delay(OTA_RESEND_BACKOFF_MS)
                        continue
                    }
                    val chunk = bytes.copyOfRange(offset, minOf(offset + Protocol.OTA_CHUNK_SIZE, bytes.size))
                    if (!RelayHolder.writeOtaChunk(seq, chunk)) {
                        throw IllegalStateException("Bluetooth write queue is stuck — retry.")
                    }
                    offset += chunk.size
                    seq++
                    val now = System.currentTimeMillis()
                    if (now - lastProgressAt >= 250) {
                        lastProgressAt = now
                        _state.update { s -> s.copy(otaProgress = offset.toFloat() / bytes.size) }
                    }
                }
                val endDeadline = System.currentTimeMillis() + OTA_STALL_TIMEOUT_MS
                while (otaAckedSeq + 1 < seq) {
                    if (System.currentTimeMillis() > endDeadline) {
                        throw IllegalStateException("Last chunks were not acknowledged — retry.")
                    }
                    delay(OTA_ACK_POLL_MS)
                }
                RelayHolder.endOta(crc)
                _state.update { it.copy(otaStatus = "Firmware sent — controller is restarting") }
            }.onFailure { e ->
                _state.update {
                    it.copy(otaProgress = null, otaStatus = null, error = e.message ?: "Update failed")
                }
            }
        }
    }

    fun clearOtaStatus() {
        _state.update { it.copy(otaProgress = null, otaStatus = null) }
    }

    fun hasPermissions(): Boolean = RelayHolder.hasPermissions()

    fun permissionResult() {
        RelayHolder.bluetoothCheck()
        if (RelayHolder.hasPermissions()) connect()
    }

    fun bluetoothCheck() {
        RelayHolder.bluetoothCheck()
    }

    companion object {
        private const val MAX_RECONNECTS = 8
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val OTA_WINDOW_CHUNKS = 24
        private const val OTA_ACK_POLL_MS = 25L
        private const val OTA_STALL_TIMEOUT_MS = 5_000L
        private const val OTA_RESEND_BACKOFF_MS = 25L
        @Volatile
        private var otaAckedSeq = -1
        @Volatile
        private var otaResendNeeded = false
        @Volatile
        private var otaStallDeadline = 0L
    }
}