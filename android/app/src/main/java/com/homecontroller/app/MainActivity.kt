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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val IosGreen = Color(0xFF34C759)
private val IosOrange = Color(0xFFFF9F0A)
private val IosRed = Color(0xFFFF453A)

data class AppColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val menuBackground: Color,
    val connectedGradient: List<Color>,
    val disconnectedGradient: List<Color>,
    val switchOffThumb: Color,
    val switchOffTrack: Color,
    val segmentedActiveFill: Color,
    val buttonFill: Color
)

private val DarkAppColors = AppColors(
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.70f),
    textTertiary = Color.White.copy(alpha = 0.50f),
    glassFill = Color.White.copy(alpha = 0.08f),
    glassBorder = Color.White.copy(alpha = 0.16f),
    menuBackground = Color(0xFF232E36),
    connectedGradient = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
    disconnectedGradient = listOf(Color(0xFF17171B), Color(0xFF1F2127), Color(0xFF292C33)),
    switchOffThumb = Color.White,
    switchOffTrack = Color.White.copy(alpha = 0.25f),
    segmentedActiveFill = Color.White.copy(alpha = 0.28f),
    buttonFill = Color.White.copy(alpha = 0.14f)
)

private val LightAppColors = AppColors(
    textPrimary = Color(0xFF20262B),
    textSecondary = Color(0xFF46505A),
    textTertiary = Color(0xFF66717B),
    glassFill = Color.White.copy(alpha = 0.55f),
    glassBorder = Color.White.copy(alpha = 0.90f),
    menuBackground = Color(0xFFF1F4F6),
    connectedGradient = listOf(Color(0xFFD8E9EF), Color(0xFFBFD7E2), Color(0xFFA4C8D8)),
    disconnectedGradient = listOf(Color(0xFFECEDEF), Color(0xFFE3E5E8), Color(0xFFDADDE1)),
    switchOffThumb = Color.White,
    switchOffTrack = Color(0xFFB9C2C9),
    segmentedActiveFill = Color.White.copy(alpha = 0.90f),
    buttonFill = Color.White.copy(alpha = 0.70f)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

class MainActivity : ComponentActivity() {

    private val viewModel: RelayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firmwareUri = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        if (firmwareUri != null) {
            viewModel.uploadFirmware(firmwareUri)
        }
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colors = if (darkTheme) DarkAppColors else LightAppColors
            CompositionLocalProvider(LocalAppColors provides colors) {
                MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                    HomeControllerApp(viewModel, openSettingsInitially = firmwareUri != null)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            viewModel.uploadFirmware(intent.data!!)
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
fun HomeControllerApp(viewModel: RelayViewModel, openSettingsInitially: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val channelStore = remember { ChannelStore(context.applicationContext) }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.bluetoothCheck() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.permissionResult() }

    var showSettings by rememberSaveable { mutableStateOf(openSettingsInitially) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.bluetoothCheck()
        if (viewModel.hasPermissions()) {
            viewModel.connect()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    val backgroundColors = (if (state.connected) colors.connectedGradient else colors.disconnectedGradient).map { c ->
        animateColorAsState(
            targetValue = c,
            animationSpec = tween(durationMillis = 900),
            label = "bg"
        ).value
    }
    val background = Brush.linearGradient(backgroundColors)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        if (showSettings) {
            SettingsScreen(
                connected = state.connected,
                restoreMode = state.restoreMode,
                channelStore = channelStore,
                otaProgress = state.otaProgress,
                otaStatus = state.otaStatus,
                error = state.error,
                rules = state.rules,
                deviceEpoch = state.deviceEpoch,
                timeSynced = state.timeSynced,
                onModeChange = viewModel::setRestoreMode,
                onSetRule = viewModel::setRule,
                onDeleteRule = viewModel::deleteRule,
                onSyncClock = viewModel::syncClock,
                onPickFirmware = viewModel::uploadFirmware,
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
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Enable Bluetooth in Settings to control your lights and fan.",
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    bluetoothLauncher.launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                },
                                border = BorderStroke(1.dp, colors.glassBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
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

                FanCard(
                    on = state.states.getOrElse(Protocol.RELAY_CH_FAN) { false },
                    level = state.fanLevel,
                    busy = state.fanBusy,
                    enabled = state.connected,
                    name = channelStore.name(Protocol.RELAY_CH_FAN),
                    iconKey = channelStore.iconKey(Protocol.RELAY_CH_FAN),
                    timer = state.timers[Protocol.RELAY_CH_FAN],
                    onToggle = { viewModel.toggleRelay(Protocol.RELAY_CH_FAN, it) },
                    onTimerSet = { minutes, active ->
                        viewModel.setTimer(Protocol.RELAY_CH_FAN, minutes, active)
                    },
                    onSetSpeed = viewModel::setFanSpeed
                )

                RelaysGrid(
                    states = state.states,
                    timers = state.timers,
                    enabled = state.connected,
                    channelStore = channelStore,
                    onToggle = viewModel::toggleRelay,
                    onTimerSet = viewModel::setTimer
                )

                when {
                    state.connected -> ConnectedActions(
                        onRefresh = viewModel::refresh,
                        onDisconnect = viewModel::disconnect
                    )
                    else -> ConnectCard(state.connecting, viewModel::connect)
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    connected: Boolean,
    restoreMode: Int,
    channelStore: ChannelStore,
    otaProgress: Float?,
    otaStatus: String?,
    error: String?,
    rules: List<Protocol.TimeRule>,
    deviceEpoch: Long,
    timeSynced: Boolean,
    onModeChange: (Int) -> Unit,
    onSetRule: (Protocol.TimeRule) -> Unit,
    onDeleteRule: (Protocol.TimeRule) -> Unit,
    onSyncClock: () -> Unit,
    onPickFirmware: (android.net.Uri) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onPickFirmware(uri)
    }
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
                    tint = colors.textPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Settings",
                color = colors.textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        ChannelsCard(channelStore = channelStore)
        TimeRulesCard(
            rules = rules,
            deviceEpoch = deviceEpoch,
            timeSynced = timeSynced,
            enabled = connected,
            channelStore = channelStore,
            onSetRule = onSetRule,
            onDeleteRule = onDeleteRule,
            onSyncClock = onSyncClock
        )
        PowerRestoreCard(
            mode = restoreMode,
            enabled = connected,
            onModeChange = onModeChange
        )
        error?.let { msg ->
            GlassCard {
                Text(
                    text = msg,
                    color = IosOrange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    fontSize = 14.sp
                )
            }
        }
        FirmwareUpdateCard(
            connected = connected,
            progress = otaProgress,
            status = otaStatus,
            onPick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) }
        )
    }
}

@Composable
private fun FirmwareUpdateCard(
    connected: Boolean,
    progress: Float?,
    status: String?,
    onPick: () -> Unit
) {
    val colors = LocalAppColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Firmware Update",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (connected) {
                    "Flash a new firmware over Bluetooth (no USB cable needed)"
                } else {
                    "Connect to the device to update"
                },
                color = colors.textTertiary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPick,
                enabled = connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.glassBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.buttonFill,
                    contentColor = colors.textPrimary
                )
            ) {
                Text(
                    text = if (progress == null && status == null) "Choose firmware (.bin)…"
                    else if (progress == null) "Choose another file"
                    else "Uploading…",
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .height(6.dp),
                    color = IosGreen,
                    trackColor = colors.glassFill
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ChannelsCard(channelStore: ChannelStore) {
    val colors = LocalAppColors.current
    var editChannel by remember { mutableStateOf<Int?>(null) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Channels",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Rename channels and pick an icon",
                color = colors.textTertiary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            for (ch in 0 until Protocol.RELAY_COUNT) {
                EditChannelRow(
                    channel = ch,
                    name = channelStore.name(ch),
                    iconKey = channelStore.iconKey(ch),
                    onEdit = { editChannel = ch },
                    onRename = { channelStore.setName(ch, it) }
                )
            }
        }
    }

    editChannel?.let { ch ->
        ChannelEditDialog(
            channel = ch,
            name = channelStore.name(ch),
            iconKey = channelStore.iconKey(ch),
            onRename = { channelStore.setName(ch, it) },
            onIcon = { channelStore.setIconKey(ch, it) },
            onDismiss = { editChannel = null }
        )
    }
}

@Composable
private fun ColumnScope.EditChannelRow(
    channel: Int,
    name: String,
    iconKey: String,
    onEdit: () -> Unit,
    onRename: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var text by remember(channel) { mutableStateOf(name) }
    val icon = ChannelCatalog.byKey(iconKey)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onRename(it) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        leadingIcon = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon.icon,
                    contentDescription = icon.label,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        placeholder = { Text(Protocol.RELAY_NAMES[channel], color = colors.textTertiary, fontSize = 14.sp) },
        textStyle = LocalTextStyle.current.copy(color = colors.textPrimary, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.glassBorder,
            unfocusedBorderColor = colors.glassBorder,
            cursorColor = colors.textPrimary
        )
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelEditDialog(
    channel: Int,
    name: String,
    iconKey: String,
    onRename: (String) -> Unit,
    onIcon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.menuBackground,
        title = { Text(Protocol.RELAY_NAMES[channel], color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pick an icon for this channel",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChannelCatalog.options.forEach { option ->
                        val selected = option.key == iconKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) colors.segmentedActiveFill else colors.glassFill,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onIcon(option.key); onDismiss() }
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.label,
                                tint = if (selected) colors.textPrimary else colors.textSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = option.label,
                                color = if (selected) colors.textPrimary else colors.textTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = onRename,
                    singleLine = true,
                    label = { Text("Name", color = colors.textTertiary) },
                    textStyle = LocalTextStyle.current.copy(color = colors.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.glassBorder,
                        unfocusedBorderColor = colors.glassBorder,
                        cursorColor = colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun Header(
    connected: Boolean,
    connecting: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Protocol.DEVICE_NAME,
                color = colors.textPrimary,
                fontSize = 24.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Living Room · 8 channels",
                color = colors.textTertiary,
                fontSize = 13.sp,
                lineHeight = 15.sp
            )
        }
        StatusPill(connected, connecting)
        Spacer(Modifier.width(4.dp))
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = colors.textPrimary
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                containerColor = colors.menuBackground
            ) {
                DropdownMenuItem(
                    text = {
                        Text("Settings", color = colors.textPrimary, fontWeight = FontWeight.Medium)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = colors.textPrimary
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
    val colors = LocalAppColors.current
    val (label, color) = when {
        connected -> "Connected" to IosGreen
        connecting -> "Connecting…" to IosOrange
        else -> "Offline" to IosRed
    }
    Row(
        modifier = Modifier
            .background(colors.glassFill, RoundedCornerShape(50))
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
            color = colors.textPrimary,
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
    val colors = LocalAppColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.glassFill),
        border = BorderStroke(1.dp, colors.glassBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
private fun RelaysGrid(
    states: List<Boolean>,
    timers: Map<Int, Protocol.TimerInfo>,
    enabled: Boolean,
    channelStore: ChannelStore,
    onToggle: (Int, Boolean) -> Unit,
    onTimerSet: (Int, Int, Boolean) -> Unit
) {
    val channels = (0 until Protocol.RELAY_COUNT).filter { it != Protocol.RELAY_CH_FAN }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in channels.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RelayTile(
                    channels[i], states, timers, enabled, channelStore,
                    onToggle,
                    onTimerSet = { minutes, active -> onTimerSet(channels[i], minutes, active) }
                )
                if (i + 1 < channels.size) {
                    RelayTile(
                        channels[i + 1], states, timers, enabled, channelStore,
                        onToggle,
                        onTimerSet = { minutes, active -> onTimerSet(channels[i + 1], minutes, active) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.RelayTile(
    channel: Int,
    states: List<Boolean>,
    timers: Map<Int, Protocol.TimerInfo>,
    enabled: Boolean,
    channelStore: ChannelStore,
    onToggle: (Int, Boolean) -> Unit,
    onTimerSet: (Int, Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    val on = states.getOrElse(channel) { false }
    val icon = ChannelCatalog.byKey(channelStore.iconKey(channel))
    val timer = timers[channel]
    var timerMenuOpen by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier
            .weight(1f)
            .height(112.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon.icon,
                        contentDescription = icon.label,
                        tint = if (on) IosGreen else colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = channelStore.name(channel),
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (on) "ON" else "OFF",
                    color = if (on) IosGreen else colors.textTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box {
                    if (timer != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.glassFill)
                                .clickable(enabled = enabled) { timerMenuOpen = true }
                                .padding(start = 8.dp, end = 10.dp, top = 3.dp, bottom = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Timer",
                                tint = IosOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${timer.minutes}m",
                                color = IosOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { timerMenuOpen = true },
                            enabled = enabled
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Set timer",
                                tint = if (enabled) colors.textTertiary else colors.textTertiary.copy(alpha = 0.40f)
                            )
                        }
                    }
                    TimerQuickAddMenu(
                        expanded = timerMenuOpen,
                        onDismiss = { timerMenuOpen = false },
                        channelName = channelStore.name(channel),
                        timer = timer,
                        enabled = enabled,
                        onSet = onTimerSet
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { onToggle(channel, it) },
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = IosGreen,
                        uncheckedThumbColor = colors.switchOffThumb,
                        uncheckedTrackColor = colors.switchOffTrack,
                        uncheckedBorderColor = Color.Transparent,
                        disabledCheckedThumbColor = Color.White.copy(alpha = 0.60f),
                        disabledCheckedTrackColor = IosGreen.copy(alpha = 0.45f),
                        disabledUncheckedThumbColor = colors.switchOffThumb.copy(alpha = 0.50f),
                        disabledUncheckedTrackColor = colors.switchOffTrack.copy(alpha = 0.50f)
                    )
                )
            }
        }
    }
}

// ---- Fan control (Atomberg-style rotary dial) ----
// Six levels on a 60°-spaced circle: Speed 1-5 plus Boost. Rotating the dial
// (or tapping a notch) commits a speed, which the controller applies by
// cycling the fan relay (OFF ~1 s, ON ~2.5 s per step). The dial hub doubles
// as the fan's power button.

private val FanMarkAngles = (0 until Protocol.FAN_LEVELS).map { i -> (270 + i * 60) % 360 }

private fun fanAngleToLevel(angleDeg: Float): Int {
    var best = 0
    var bestDist = 360f
    for (i in 0 until Protocol.FAN_LEVELS) {
        var d = abs(angleDeg - FanMarkAngles[i])
        if (d > 180f) d = 360f - d
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best + 1
}

private fun fanLevelName(level: Int): String =
    if (level == Protocol.FAN_LEVEL_BOOST) "Boost" else "Speed $level"

@Composable
private fun FanCard(
    on: Boolean,
    level: Int,
    busy: Boolean,
    enabled: Boolean,
    name: String,
    iconKey: String,
    timer: Protocol.TimerInfo?,
    onToggle: (Boolean) -> Unit,
    onTimerSet: (Int, Boolean) -> Unit,
    onSetSpeed: (Int) -> Unit
) {
    val colors = LocalAppColors.current
    val icon = ChannelCatalog.byKey(iconKey)
    var timerMenuOpen by remember { mutableStateOf(false) }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon.icon,
                        contentDescription = icon.label,
                        tint = if (on) IosGreen else colors.textTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = name,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = when {
                        busy -> "Setting ${fanLevelName(level)}…"
                        on -> fanLevelName(level)
                        else -> "Off · tap the hub to turn on"
                    },
                    color = when {
                        busy -> IosOrange
                        on -> IosGreen
                        else -> colors.textTertiary
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Box {
                    if (timer != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(colors.glassFill)
                                .clickable(enabled = enabled) { timerMenuOpen = true }
                                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Timer",
                                tint = IosOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${timer.minutes}m",
                                color = IosOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { timerMenuOpen = true },
                            enabled = enabled,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Set fan timer",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    TimerQuickAddMenu(
                        expanded = timerMenuOpen,
                        onDismiss = { timerMenuOpen = false },
                        channelName = name,
                        timer = timer,
                        enabled = enabled,
                        onSet = onTimerSet
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            CompactFanDial(
                on = on,
                level = level,
                busy = busy,
                enabled = enabled,
                onToggle = { onToggle(!on) },
                onSetLevel = onSetSpeed
            )
        }
    }
}

@Composable
private fun CompactFanDial(
    on: Boolean,
    level: Int,
    busy: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSetLevel: (Int) -> Unit
) {
    val colors = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    var dragPreview by remember { mutableStateOf<Int?>(null) }
    val shownLevel = dragPreview ?: level
    val dialSize = 128.dp

    fun pick(offset: Offset, boxSize: Size) {
        val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
        val raw = Math.toDegrees(
            atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())
        ).toFloat()
        val angle = ((raw % 360f) + 360f) % 360f
        val target = fanAngleToLevel(angle)
        if (dragPreview == target) return
        dragPreview = target
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(
        modifier = Modifier
            .size(dialSize)
            .pointerInput(enabled && !busy) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        pick(change.position, size.toSize())
                        change.consume()
                    },
                    onDragEnd = {
                        dragPreview?.let(onSetLevel)
                        dragPreview = null
                    },
                    onDragCancel = { dragPreview = null }
                )
            }
            .pointerInput(enabled && !busy) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    // Taps inside the hub are handled by the power button below.
                    if ((offset - center).getDistance() <= 28.dp.toPx()) return@detectTapGestures
                    pick(offset, size.toSize())
                    dragPreview?.let(onSetLevel)
                    dragPreview = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                color = IosOrange,
                strokeWidth = 3.dp,
                trackColor = colors.glassBorder
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawFanDial(shownLevel, busy, on, colors, textMeasurer)
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    when {
                        busy -> IosOrange.copy(alpha = 0.18f)
                        on -> IosGreen.copy(alpha = 0.16f)
                        else -> colors.segmentedActiveFill
                    }
                )
                .border(1.dp, colors.glassBorder, CircleShape)
                .clickable(enabled = enabled && !busy) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Power,
                contentDescription = if (on) "Turn fan off" else "Turn fan on",
                tint = if (on) IosGreen else colors.textTertiary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun DrawScope.drawFanDial(
    level: Int,
    busy: Boolean,
    on: Boolean,
    colors: AppColors,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = minOf(size.width, size.height) / 2f - 8.dp.toPx()
    val boost = level == Protocol.FAN_LEVEL_BOOST

    drawCircle(
        color = colors.glassBorder,
        radius = outer,
        center = Offset(cx, cy),
        style = Stroke(2.dp.toPx())
    )

    // LED dots around the ring — lit like the fan's indicator disk. When the
    // fan is off, the dots stay dim to show the dial is idle.
    for (i in 0 until Protocol.FAN_LEVELS) {
        val angle = Math.toRadians(FanMarkAngles[i].toDouble())
        val pos = Offset(
            cx + cos(angle).toFloat() * outer,
            cy + sin(angle).toFloat() * outer
        )
        val lit = i < level && on
        val ledColor = when {
            lit && boost -> IosOrange
            lit -> IosGreen
            else -> colors.textTertiary.copy(alpha = 0.35f)
        }
        drawCircle(color = ledColor, radius = 5.dp.toPx(), center = pos)
    }

    // Level labels inside the LED ring.
    val labelRadius = outer - 19.dp.toPx()
    for (i in 0 until Protocol.FAN_LEVELS) {
        val angle = Math.toRadians(FanMarkAngles[i].toDouble())
        val pos = Offset(
            cx + cos(angle).toFloat() * labelRadius,
            cy + sin(angle).toFloat() * labelRadius
        )
        val text = if (i == Protocol.FAN_LEVEL_BOOST - 1) "B" else "${i + 1}"
        val layout = textMeasurer.measure(
            AnnotatedString(text),
            style = TextStyle(
                color = if (i + 1 == level) colors.textPrimary else colors.textTertiary.copy(alpha = 0.75f),
                fontSize = 8.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        drawText(
            layout,
            topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f)
        )
    }

    // Needle pointing at the selected level.
    val sel = level - 1
    val selAngle = Math.toRadians(FanMarkAngles[sel].toDouble())
    val needleStart = Offset(
        cx + cos(selAngle).toFloat() * 34.dp.toPx(),
        cy + sin(selAngle).toFloat() * 34.dp.toPx()
    )
    val needleEnd = Offset(
        cx + cos(selAngle).toFloat() * (outer - 1.dp.toPx()),
        cy + sin(selAngle).toFloat() * (outer - 1.dp.toPx())
    )
    drawLine(
        color = when {
            busy -> IosOrange.copy(alpha = 0.65f)
            on -> colors.textPrimary
            else -> colors.textTertiary.copy(alpha = 0.45f)
        },
        start = needleStart,
        end = needleEnd,
        strokeWidth = 4.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Center hub (the power button lives on top as a composable overlay).
    drawCircle(color = colors.segmentedActiveFill, radius = 27.dp.toPx(), center = Offset(cx, cy))
    drawCircle(
        color = colors.glassBorder,
        radius = 27.dp.toPx(),
        center = Offset(cx, cy),
        style = Stroke(1.dp.toPx())
    )
}

@Composable
private fun TimerQuickAddMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    channelName: String,
    timer: Protocol.TimerInfo?,
    enabled: Boolean,
    onSet: (Int, Boolean) -> Unit
) {
    val colors = LocalAppColors.current
    val current = timer?.minutes ?: 0
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.menuBackground,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Off timer — $channelName",
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (timer != null) {
                    "Turns off in ${timer.minutes} min — tap to add more"
                } else {
                    "No timer running — tap to add"
                },
                color = if (timer != null) IosOrange else colors.textTertiary,
                fontSize = 12.sp
            )
        }
        QuickAddItem("+5 min", enabled && current + 5 <= 9999) { onSet(current + 5, true) }
        QuickAddItem("+15 min", enabled && current + 15 <= 9999) { onSet(current + 15, true) }
        QuickAddItem("+30 min", enabled && current + 30 <= 9999) { onSet(current + 30, true) }
        QuickAddItem("+1 hr", enabled && current + 60 <= 9999) { onSet(current + 60, true) }
        if (timer != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.glassBorder.copy(alpha = 0.50f))
            )
            DropdownMenuItem(
                onClick = {
                    onSet(0, false)
                    onDismiss()
                },
                text = {
                    Text(
                        text = "Cancel timer",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            )
        }
        DropdownMenuItem(
            onClick = onDismiss,
            text = {
                Text(
                    text = "Done",
                    color = colors.textTertiary,
                    fontSize = 14.sp
                )
            }
        )
    }
}

@Composable
private fun QuickAddItem(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = IosGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
private fun PowerRestoreCard(
    mode: Int,
    enabled: Boolean,
    onModeChange: (Int) -> Unit
) {
    val colors = LocalAppColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Power Restore",
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (enabled) {
                    "What happens to app-set states when power returns"
                } else {
                    "Connect to the device to change this setting"
                },
                color = colors.textTertiary,
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
                        activeContainerColor = colors.segmentedActiveFill,
                        activeContentColor = colors.textPrimary,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = colors.textSecondary,
                        disabledActiveContainerColor = colors.segmentedActiveFill.copy(alpha = 0.50f),
                        disabledActiveContentColor = colors.textTertiary,
                        disabledInactiveContentColor = colors.textTertiary.copy(alpha = 0.60f)
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
                        activeContainerColor = colors.segmentedActiveFill,
                        activeContentColor = colors.textPrimary,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = colors.textSecondary,
                        disabledActiveContainerColor = colors.segmentedActiveFill.copy(alpha = 0.50f),
                        disabledActiveContentColor = colors.textTertiary,
                        disabledInactiveContentColor = colors.textTertiary.copy(alpha = 0.60f)
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
    val colors = LocalAppColors.current
    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (connecting) "Looking for your device…" else "Not Connected",
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap Connect to scan for HomeController over Bluetooth",
                color = colors.textSecondary,
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
                border = BorderStroke(1.dp, colors.glassBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.buttonFill,
                    contentColor = colors.textPrimary
                )
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.textPrimary,
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
private fun TimeRulesCard(
    rules: List<Protocol.TimeRule>,
    deviceEpoch: Long,
    timeSynced: Boolean,
    enabled: Boolean,
    channelStore: ChannelStore,
    onSetRule: (Protocol.TimeRule) -> Unit,
    onDeleteRule: (Protocol.TimeRule) -> Unit,
    onSyncClock: () -> Unit
) {
    val colors = LocalAppColors.current
    val used = rules.filter { !it.isDefault() }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Protocol.TimeRule?>(null) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Time Rules",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (!timeSynced) {
                                "Clock not synced — rules fire after the phone connects"
                            } else {
                                "Controller time " +
                                    "%02d:%02d".format(deviceEpoch / 3600 % 24, deviceEpoch % 3600 / 60)
                            },
                            color = if (timeSynced) colors.textTertiary else IosOrange,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (enabled) {
                            TextButton(
                                onClick = onSyncClock,
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Sync clock",
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                if (enabled && used.size < Protocol.RULE_COUNT) {
                    OutlinedButton(
                        onClick = { showAdd = true },
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colors.buttonFill,
                            contentColor = colors.textPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = IosGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Add rule", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (used.isEmpty()) {
                Text(
                    text = "No rules yet. Example: turn Light 1 OFF at 9:00 — e.g. for the " +
                        "morning sunlight once the sun is up.",
                    color = colors.textTertiary,
                    fontSize = 12.sp
                )
            } else {
                used.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        enabled = enabled,
                        channelStore = channelStore,
                        onSetRule = onSetRule,
                        onDeleteRule = onDeleteRule,
                        onEdit = { editing = rule }
                    )
                }
            }
        }
    }
    if (showAdd) {
        AddRuleDialog(
            channelStore = channelStore,
            initial = null,
            onSave = { draft ->
                val slot = rules.indexOfFirst { it.isDefault() }
                if (slot in 0 until Protocol.RULE_COUNT) {
                    onSetRule(draft.copy(index = slot))
                }
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }
    editing?.let { rule ->
        AddRuleDialog(
            channelStore = channelStore,
            initial = rule,
            onSave = { draft ->
                onSetRule(draft.copy(index = rule.index, enabled = rule.enabled))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

private fun ruleDaysLabel(days: Int): String = when (days) {
    0 -> "Daily"
    else -> {
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        when (days) {
            0b0011111 -> "Mon–Fri"
            0b1100000 -> "Weekends"
            else -> names.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" ")
        }
    }
}

@Composable
private fun RuleRow(
    rule: Protocol.TimeRule,
    enabled: Boolean,
    channelStore: ChannelStore,
    onSetRule: (Protocol.TimeRule) -> Unit,
    onDeleteRule: (Protocol.TimeRule) -> Unit,
    onEdit: () -> Unit
) {
    val colors = LocalAppColors.current
    val on = rule.action == Protocol.RULE_ACTION_ON
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ChannelCatalog.byKey(channelStore.iconKey(rule.channel)).icon,
            contentDescription = null,
            tint = if (on) IosGreen else colors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channelStore.name(rule.channel),
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${if (on) "ON" else "OFF"} at %02d:%02d · %s"
                    .format(rule.hour, rule.minute, ruleDaysLabel(rule.days)),
                color = if (rule.enabled) colors.textSecondary else colors.textTertiary,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = rule.enabled,
            onCheckedChange = { onSetRule(rule.copy(enabled = it)) },
            enabled = enabled,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = IosGreen,
                uncheckedThumbColor = colors.switchOffThumb,
                uncheckedTrackColor = colors.switchOffTrack,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.60f),
                disabledCheckedTrackColor = IosGreen.copy(alpha = 0.45f),
                disabledUncheckedThumbColor = colors.switchOffThumb.copy(alpha = 0.50f),
                disabledUncheckedTrackColor = colors.switchOffTrack.copy(alpha = 0.50f)
            )
        )
        IconButton(
            onClick = onEdit,
            enabled = enabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit rule",
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        IconButton(
            onClick = { onDeleteRule(rule) },
            enabled = enabled,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete rule",
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.segmentedActiveFill else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(
    channelStore: ChannelStore,
    initial: Protocol.TimeRule?,
    onSave: (Protocol.TimeRule) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    var channel by remember(initial) { mutableStateOf(initial?.channel ?: 1) }
    var action by remember(initial) { mutableStateOf(initial?.action ?: Protocol.RULE_ACTION_OFF) }
    var hourStr by remember(initial) { mutableStateOf("%02d".format(initial?.hour ?: 9)) }
    var minStr by remember(initial) { mutableStateOf("%02d".format(initial?.minute ?: 0)) }
    var days by remember(initial) { mutableStateOf(initial?.days ?: 0) }
    val hour = hourStr.toIntOrNull()
    val minute = minStr.toIntOrNull()
    val valid = hour != null && hour in 0..23 && minute != null && minute in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.menuBackground,
        title = { Text(if (initial == null) "New rule" else "Edit rule", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "At the set time the controller turns the channel on/off. " +
                        "Rules live on the controller, survive reboots, and only fire " +
                        "when the clock is synced.",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0 until Protocol.RELAY_COUNT).forEach { ch ->
                        Chip(
                            label = channelStore.name(ch),
                            selected = channel == ch,
                            onClick = { channel = ch }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Action",
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(
                            selected = action == Protocol.RULE_ACTION_OFF,
                            onClick = { action = Protocol.RULE_ACTION_OFF },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = colors.segmentedActiveFill,
                                activeContentColor = colors.textPrimary,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = colors.textSecondary
                            )
                        ) { Text("OFF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        SegmentedButton(
                            selected = action == Protocol.RULE_ACTION_ON,
                            onClick = { action = Protocol.RULE_ACTION_ON },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = colors.segmentedActiveFill,
                                activeContentColor = colors.textPrimary,
                                inactiveContainerColor = Color.Transparent,
                                inactiveContentColor = colors.textSecondary
                            )
                        ) { Text("ON", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Time",
                        color = colors.textTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hourStr,
                        onValueChange = { hourStr = it.filter { c -> c.isDigit() }.take(2) },
                        singleLine = true,
                        modifier = Modifier.width(64.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.glassBorder,
                            unfocusedBorderColor = colors.glassBorder,
                            cursorColor = colors.textPrimary
                        )
                    )
                    Text(
                        text = ":",
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    OutlinedTextField(
                        value = minStr,
                        onValueChange = { minStr = it.filter { c -> c.isDigit() }.take(2) },
                        singleLine = true,
                        modifier = Modifier.width(64.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.glassBorder,
                            unfocusedBorderColor = colors.glassBorder,
                            cursorColor = colors.textPrimary
                        )
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Chip("Every day", selected = days == 0, onClick = { days = 0 })
                    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                    dayNames.forEachIndexed { i, name ->
                        Chip(
                            label = name,
                            selected = days and (1 shl i) != 0,
                            onClick = { days = days xor (1 shl i) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Protocol.TimeRule(
                            index = 0,
                            enabled = true,
                            channel = channel,
                            action = action,
                            hour = hour!!,
                            minute = minute!!,
                            days = days
                        )
                    )
                },
                enabled = valid,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textPrimary)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary)
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun ConnectedActions(onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    val colors = LocalAppColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.glassBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary)
        ) {
            Text("Refresh", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.glassBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
        ) {
            Text("Disconnect", fontWeight = FontWeight.SemiBold)
        }
    }
}