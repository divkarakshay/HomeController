package com.homecontroller.app

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class RelayViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val connected: Boolean = false,
        val connecting: Boolean = false,
        val bluetoothOn: Boolean = true,
        val pin: String = "",
        val states: List<Boolean> = List(Protocol.RELAY_COUNT) { false },
        val error: String? = null
    )

    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val manager = BleRelayManager(app)

    private val _state = MutableStateFlow(
        UiState(
            bluetoothOn = manager.isEnabled,
            pin = prefs.getString(KEY_PIN, "") ?: ""
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pendingCommand: ByteArray? = null
    private var reconnectAttempts = 0

    init {
        manager.listener = object : BleRelayManager.Listener {
            override fun onConnected() {
                reconnectAttempts = 0
                _state.update {
                    it.copy(connected = true, connecting = false, bluetoothOn = true, error = null)
                }
                val pin = currentPin()
                if (pin != null) {
                    if (pendingCommand != null) {
                        pendingCommand = null
                    }
                    manager.sendAuth(pin)
                } else {
                    _state.update { it.copy(error = "Enter the PIN, then tap Connect") }
                }
            }

            override fun onDisconnected() {
                _state.update {
                    it.copy(connected = false, connecting = false, states = it.states)
                }
                if (reconnectAttempts < MAX_RECONNECTS) {
                    reconnectAttempts++
                    viewModelScope.launch {
                        delay(RECONNECT_DELAY_MS)
                        if (!_state.value.connected) manager.reconnect()
                    }
                }
            }

            override fun onStates(states: List<Boolean>) {
                _state.update { it.copy(states = states) }
            }

            override fun onError(message: String) {
                _state.update {
                    it.copy(connecting = false, error = message)
                }
            }
        }
    }

    fun connect() {
        val pin = currentPin()
        if (pin == null) {
            _state.update { it.copy(error = "Enter the PIN, then tap Connect") }
            return
        }
        if (_state.value.connected) {
            disconnect()
            return
        }
        reconnectAttempts = 0
        _state.update { it.copy(connecting = true, error = null) }
        manager.connect()
    }

    fun toggleRelay(channel: Int, on: Boolean) {
        _state.update { s ->
            val newStates = s.states.toMutableList().apply { this[channel] = on }
            s.copy(states = newStates)
        }
        if (_state.value.connected) {
            manager.setRelay(channel, on)
        } else {
            _state.update { it.copy(error = "Not connected to ${Protocol.DEVICE_NAME}") }
        }
    }

    fun refresh() {
        if (_state.value.connected) {
            manager.refresh()
        } else {
            _state.update { it.copy(error = "Not connected") }
        }
    }

    fun disconnect() {
        manager.disconnect()
        _state.update { it.copy(connected = false, connecting = false) }
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_PIN, pin).apply()
        _state.update { it.copy(pin = pin) }
    }

    fun hasPermissions(): Boolean = manager.hasPermissions()

    fun permissionResult() {
        _state.update { it.copy(bluetoothOn = manager.isEnabled) }
        if (manager.hasPermissions()) connect()
    }

    fun bluetoothCheck() {
        _state.update { it.copy(bluetoothOn = manager.isEnabled) }
    }

    private fun currentPin(): Int? =
        _state.value.pin.trim().toIntOrNull()

    companion object {
        private const val PREFS_NAME = "relay_prefs"
        private const val KEY_PIN = "pin"
        private const val MAX_RECONNECTS = 3
        private const val RECONNECT_DELAY_MS = 2_000L
    }
}