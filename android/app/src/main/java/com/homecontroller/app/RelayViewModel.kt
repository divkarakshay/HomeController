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
        val error: String? = null
    )

    private val manager = BleRelayManager(app)

    private val _state = MutableStateFlow(UiState(bluetoothOn = manager.isEnabled))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var reconnectAttempts = 0

    init {
        manager.listener = object : BleRelayManager.Listener {
            override fun onConnected() {
                reconnectAttempts = 0
                _state.update {
                    it.copy(connected = true, connecting = false, bluetoothOn = true, error = null)
                }
                manager.refresh()
            }

            override fun onDisconnected() {
                _state.update { it.copy(connected = false, connecting = false) }
                if (reconnectAttempts < MAX_RECONNECTS) {
                    reconnectAttempts++
                    viewModelScope.launch {
                        delay(RECONNECT_DELAY_MS)
                        if (!_state.value.connected) {
                            _state.update { it.copy(connecting = true) }
                            manager.reconnect()
                        }
                    }
                }
            }

            override fun onStates(states: List<Boolean>) {
                _state.update { it.copy(states = states) }
            }

            override fun onStateChanged(channel: Int, on: Boolean) {
                _state.update { s ->
                    val list = s.states.toMutableList()
                    list[channel] = on
                    s.copy(states = list)
                }
            }

            override fun onError(message: String) {
                _state.update {
                    it.copy(connecting = false, error = message)
                }
            }
        }
    }

    fun connect() {
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
        reconnectAttempts = MAX_RECONNECTS
        manager.disconnect()
        _state.update { it.copy(connected = false, connecting = false) }
    }

    fun hasPermissions(): Boolean = manager.hasPermissions()

    fun permissionResult() {
        _state.update { it.copy(bluetoothOn = manager.isEnabled) }
        if (manager.hasPermissions()) connect()
    }

    fun bluetoothCheck() {
        _state.update { it.copy(bluetoothOn = manager.isEnabled) }
    }

    companion object {
        private const val MAX_RECONNECTS = 8
        private const val RECONNECT_DELAY_MS = 2_000L
    }
}