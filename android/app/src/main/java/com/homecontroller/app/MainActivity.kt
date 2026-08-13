package com.homecontroller.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: RelayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeControllerApp(viewModel)
            }
        }
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeControllerApp(viewModel: RelayViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.bluetoothCheck() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.permissionResult() }

    LaunchedEffect(Unit) {
        if (viewModel.hasPermissions()) {
            viewModel.connect()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(Protocol.DEVICE_NAME) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ConnectionCard(state) { viewModel.connect() }

            if (!state.bluetoothOn) {
                OutlinedButton(
                    onClick = {
                        bluetoothLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bluetooth is off — open Settings")
                }
            }

            PinField(state.pin, state.connected) { viewModel.setPin(it) }

            state.error?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            RelaysCard(state.states, viewModel::toggleRelay)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = viewModel::refresh, enabled = state.connected) {
                    Text("Refresh")
                }
                OutlinedButton(onClick = viewModel::disconnect, enabled = state.connected) {
                    Text("Disconnect")
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: RelayViewModel.UiState, onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    state.connected -> StatusDot(Color(0xFF2E7D32))
                    state.connecting -> CircularProgressIndicator(
                        modifier = Modifier.width(12.dp).height(12.dp),
                        strokeWidth = 2.dp
                    )
                    else -> StatusDot(Color(0xFFC62828))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        state.connected -> "Connected"
                        state.connecting -> "Connecting…"
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.connecting
            ) {
                Text(if (state.connected) "Connected — tap to disconnect" else "Scan & Connect")
            }
        }
    }
}

@Composable
private fun PinField(pin: String, connected: Boolean, onPinChange: (String) -> Unit) {
    OutlinedTextField(
        value = pin,
        onValueChange = { onPinChange(it.filter { c -> c.isDigit() }.take(6)) },
        label = { Text("Device PIN") },
        singleLine = true,
        enabled = !connected,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RelaysCard(
    states: List<Boolean>,
    onToggle: (Int, Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Protocol.RELAY_NAMES.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (index == Protocol.RELAY_CH_FAN) "Fan channel" else "Light channel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (states.getOrElse(index) { false }) "ON" else "OFF",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (states.getOrElse(index) { false }) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = states.getOrElse(index) { false },
                        onCheckedChange = { onToggle(index, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .width(12.dp)
            .height(12.dp)
            .background(color, CircleShape)
    )
}