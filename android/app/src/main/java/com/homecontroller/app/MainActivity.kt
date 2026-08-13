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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val AppGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
)

private val IosGreen = Color(0xFF34C759)
private val IosOrange = Color(0xFFFF9F0A)
private val IosRed = Color(0xFFFF453A)

private val White = Color.White
private val White70 = White.copy(alpha = 0.70f)
private val White50 = White.copy(alpha = 0.50f)
private val White30 = White.copy(alpha = 0.30f)
private val GlassFill = White.copy(alpha = 0.08f)
private val GlassBorder = White.copy(alpha = 0.16f)
private val IosMenu = Color(0xFF232E36)

class MainActivity : ComponentActivity() {

    private val viewModel: RelayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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

@Composable
fun HomeControllerApp(viewModel: RelayViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.bluetoothCheck() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.permissionResult() }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.bluetoothCheck()
        if (viewModel.hasPermissions()) {
            viewModel.connect()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppGradient)
    ) {
        if (showSettings) {
            SettingsScreen(
                connected = state.connected,
                restoreMode = state.restoreMode,
                onModeChange = viewModel::setRestoreMode,
                onBack = { showSettings = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Header(
                    connected = state.connected,
                    connecting = state.connecting,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onSettingsClick = {
                        menuOpen = false
                        showSettings = true
                    }
                )

                if (!state.bluetoothOn) {
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Bluetooth is off",
                                color = White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Enable Bluetooth in Settings to control your lights and fan.",
                                color = White70,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    bluetoothLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                border = BorderStroke(1.dp, GlassBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
                            ) {
                                Text("Open Bluetooth Settings")
                            }
                        }
                    }
                }

                state.error?.let { error ->
                    GlassCard {
                        Text(
                            text = error,
                            color = IosOrange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                RelaysGrid(
                    states = state.states,
                    enabled = state.connected,
                    onToggle = viewModel::toggleRelay
                )

                when {
                    state.connected -> ConnectedActions(
                        onRefresh = viewModel::refresh,
                        onDisconnect = viewModel::disconnect
                    )
                    else -> ConnectCard(state.connecting, viewModel::connect)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    connected: Boolean,
    restoreMode: Int,
    onModeChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Settings",
                color = White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        PowerRestoreCard(
            mode = restoreMode,
            enabled = connected,
            onModeChange = onModeChange
        )
    }
}

@Composable
private fun Header(
    connected: Boolean,
    connecting: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(GlassFill, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(White.copy(alpha = 0.20f), CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Protocol.DEVICE_NAME,
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Living Room · 8 channels",
                color = White50,
                fontSize = 13.sp
            )
        }
        StatusPill(connected, connecting)
        Spacer(Modifier.width(4.dp))
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = White
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                containerColor = IosMenu
            ) {
                DropdownMenuItem(
                    text = {
                        Text("Settings", color = White, fontWeight = FontWeight.Medium)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = White
                        )
                    },
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun StatusPill(connected: Boolean, connecting: Boolean) {
    val (label, color) = when {
        connected -> "Connected" to IosGreen
        connecting -> "Connecting…" to IosOrange
        else -> "Offline" to IosRed
    }
    Row(
        modifier = Modifier
            .background(GlassFill, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GlassFill),
        border = BorderStroke(1.dp, GlassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
private fun RelaysGrid(
    states: List<Boolean>,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until Protocol.RELAY_COUNT step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RelayTile(row, states, enabled, onToggle)
                RelayTile(row + 1, states, enabled, onToggle)
            }
        }
    }
}

@Composable
private fun RowScope.RelayTile(
    channel: Int,
    states: List<Boolean>,
    enabled: Boolean,
    onToggle: (Int, Boolean) -> Unit
) {
    val on = states.getOrElse(channel) { false }
    GlassCard(
        modifier = Modifier
            .weight(1f)
            .height(128.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = Protocol.RELAY_NAMES[channel],
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (on) "ON" else "OFF",
                    color = if (on) IosGreen else White50,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { onToggle(channel, it) },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = White,
                    checkedTrackColor = IosGreen,
                    uncheckedThumbColor = White,
                    uncheckedTrackColor = White.copy(alpha = 0.25f),
                    uncheckedBorderColor = Color.Transparent,
                    disabledCheckedThumbColor = White.copy(alpha = 0.60f),
                    disabledCheckedTrackColor = IosGreen.copy(alpha = 0.45f),
                    disabledUncheckedThumbColor = White.copy(alpha = 0.50f),
                    disabledUncheckedTrackColor = White.copy(alpha = 0.12f)
                )
            )
        }
    }
}

@Composable
private fun PowerRestoreCard(
    mode: Int,
    enabled: Boolean,
    onModeChange: (Int) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Power Restore",
                color = White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (enabled) {
                    "What happens to app-set states when power returns"
                } else {
                    "Connect to the device to change this setting"
                },
                color = White50,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == Protocol.MODE_RESTORE_LAST,
                    onClick = { onModeChange(Protocol.MODE_RESTORE_LAST) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = White.copy(alpha = 0.28f),
                        activeContentColor = White,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = White70,
                        disabledActiveContainerColor = White.copy(alpha = 0.16f),
                        disabledActiveContentColor = White50,
                        disabledInactiveContentColor = White30
                    )
                ) {
                    Text("Last state", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                SegmentedButton(
                    selected = mode == Protocol.MODE_ALL_OFF,
                    onClick = { onModeChange(Protocol.MODE_ALL_OFF) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = White.copy(alpha = 0.28f),
                        activeContentColor = White,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = White70,
                        disabledActiveContainerColor = White.copy(alpha = 0.16f),
                        disabledActiveContentColor = White50,
                        disabledInactiveContentColor = White30
                    )
                ) {
                    Text("All off", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ConnectCard(connecting: Boolean, onConnect: () -> Unit) {
    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (connecting) "Looking for your device…" else "Not Connected",
                color = White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap Connect to scan for HomeController over Bluetooth",
                color = White70,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onConnect,
                enabled = !connecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, White.copy(alpha = 0.35f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = White.copy(alpha = 0.14f),
                    contentColor = White
                )
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Connect", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ConnectedActions(onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
        ) {
            Text("Refresh", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White70)
        ) {
            Text("Disconnect", fontWeight = FontWeight.SemiBold)
        }
    }
}