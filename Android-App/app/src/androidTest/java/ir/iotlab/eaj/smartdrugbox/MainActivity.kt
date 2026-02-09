package ir.iotlab.eaj.smartdrugbox

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.iotlab.eaj.smartdrugbox.ui.theme.SmartDrugBoxTheme
import kotlinx.coroutines.delay
import android.content.BroadcastReceiver
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MqttForegroundService.startService(this)
        setContent {
            SmartDrugBoxTheme {
                DrugBoxApp(
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}

// --- NEW: Animated Gradient Background ---
@Composable
fun AnimatedGradientBackground(content: @Composable BoxScope.() -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "background_animation")

    val color1 by infiniteTransition.animateColor(
        initialValue = Color(0xFFE1BEE7),
        targetValue = Color(0xFFC5CAE9),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "color1"
    )
    val color2 by infiniteTransition.animateColor(
        initialValue = Color(0xFFC5CAE9),
        targetValue = Color(0xFFB3E5FC),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "color2"
    )

    val brush = Brush.verticalGradient(listOf(color1, color2))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush),
        content = content
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugBoxApp(onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    var pillBoxes by remember { mutableStateOf(PillBoxDataManager.loadPillBoxes(context)) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted: Boolean -> }
        )
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    DisposableEffect(context) {
        val dataChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "ir.iotlab.eaj.smartdrugbox.DATA_CHANGED") {
                    pillBoxes = PillBoxDataManager.loadPillBoxes(context)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(dataChangeReceiver, IntentFilter("ir.iotlab.eaj.smartdrugbox.DATA_CHANGED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(dataChangeReceiver, IntentFilter("ir.iotlab.eaj.smartdrugbox.DATA_CHANGED"))
        }
        onDispose { context.unregisterReceiver(dataChangeReceiver) }
    }

    val onPeriodChange = { index: Int, newPeriodInSeconds: Int ->
        val updatedBoxes = pillBoxes.toMutableList()
        val oldBox = updatedBoxes[index]
        updatedBoxes[index] = oldBox.copy(
            period = newPeriodInSeconds,
            lastActivation = if (oldBox.isTracking) System.currentTimeMillis() else null,
            reminderSent = false, mainNotificationSent = false, followUpSent = false
        )
        PillBoxDataManager.savePillBoxes(context, updatedBoxes)
        pillBoxes = updatedBoxes
        sendPeriodCommand(context, updatedBoxes[index].ledPin, newPeriodInSeconds)
    }

    val onStartTracking = { index: Int ->
        val updatedBoxes = pillBoxes.toMutableList()
        updatedBoxes[index] = updatedBoxes[index].copy(
            isTracking = true, lastActivation = System.currentTimeMillis(),
            isActive = false, reminderSent = false, mainNotificationSent = false, followUpSent = false
        )
        PillBoxDataManager.savePillBoxes(context, updatedBoxes)
        pillBoxes = updatedBoxes
        sendStartTrackingCommand(context, index + 1)
    }

    val onStopTracking = { index: Int ->
        val updatedBoxes = pillBoxes.toMutableList()
        updatedBoxes[index] = updatedBoxes[index].copy(
            isTracking = false, isActive = false, lastActivation = null,
            reminderSent = false, mainNotificationSent = false, followUpSent = false
        )
        PillBoxDataManager.savePillBoxes(context, updatedBoxes)
        pillBoxes = updatedBoxes
        sendResetCommand(context, updatedBoxes[index].ledPin)
    }

    val onEdit = { index: Int, newPillBox: PillBox ->
        val updatedBoxes = pillBoxes.toMutableList()
        updatedBoxes[index] = newPillBox
        PillBoxDataManager.savePillBoxes(context, updatedBoxes)
        pillBoxes = updatedBoxes
    }

    AnimatedGradientBackground {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Smart Drug Box", fontWeight = FontWeight.Bold) },
                    actions = { IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "Settings") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            PillBoxScreen(
                modifier = Modifier.padding(padding),
                pillBoxes = pillBoxes,
                onEdit = onEdit,
                onPeriodChange = onPeriodChange,
                onStartTracking = onStartTracking,
                onStopTracking = onStopTracking
            )
        }
    }
}

@Composable
fun PillBoxScreen(
    modifier: Modifier = Modifier,
    pillBoxes: List<PillBox>,
    onEdit: (Int, PillBox) -> Unit,
    onPeriodChange: (Int, Int) -> Unit,
    onStartTracking: (Int) -> Unit,
    onStopTracking: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Add a spacer at the top for better padding
        Spacer(modifier = Modifier.height(8.dp))

        pillBoxes.forEachIndexed { index, pillBox ->
            var showDialog by remember { mutableStateOf(false) }
            var showPeriodDialog by remember { mutableStateOf(false) }

            // --- NEW: Card Animation ---
            AnimatedVisibility(
                visible = true, // Always visible, but triggers animation on first composition
                enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = index * 100)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(durationMillis = 500, delayMillis = index * 100)
                        )
            ) {
                PillCard(
                    pillBox = pillBox,
                    onEdit = { showDialog = true },
                    onSetPeriod = { showPeriodDialog = true },
                    onStartTracking = { onStartTracking(index) },
                    onStopTracking = { onStopTracking(index) }
                )
            }

            if (showDialog) {
                EditPillDialog(
                    initial = pillBox,
                    onConfirm = { onEdit(index, it) },
                    onDismiss = { showDialog = false }
                )
            }
            if (showPeriodDialog) {
                SetPeriodDialog(
                    initialPeriodInSeconds = pillBox.period,
                    onConfirm = { newPeriod -> onPeriodChange(index, newPeriod) },
                    onDismiss = { showPeriodDialog = false }
                )
            }
        }
        // Add a spacer at the bottom
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatPeriod(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return buildString {
        if (hours > 0) append("$hours hr ")
        if (minutes > 0) append("$minutes min ")
        if (secs > 0 || isEmpty()) append("$secs sec")
    }.trim()
}

@Composable
fun PillCard(
    pillBox: PillBox,
    onEdit: () -> Unit,
    onSetPeriod: () -> Unit,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (pillBox.isActive) Color.Green else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            ),
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(pillBox.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            if (pillBox.isActive) Color.Green else Color.Red,
                            RoundedCornerShape(50)
                        )
                        .border(1.dp, Color.White, RoundedCornerShape(50))
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Info Section
            InfoRow(label = "Status", value = if (pillBox.isTracking) "ACTIVE" else "INACTIVE", valueColor = if (pillBox.isTracking) Color(0xFF4CAF50) else Color.Red)
            InfoRow(label = "Period", value = formatPeriod(pillBox.period))
            InfoRow(label = "Last Taken", value = pillBox.lastActivationFormatted)
            InfoRow(label = "Next Dose", value = pillBox.nextActivation)

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    Button(onClick = onSetPeriod) {
                        Icon(Icons.Default.Timer, contentDescription = "Set Period")
                    }
                }

                if (pillBox.isTracking) {
                    Button(onClick = onStopTracking, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("Stop")
                    }
                } else {
                    Button(onClick = onStartTracking, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = LocalContentColor.current) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, color = LocalContentColor.current.copy(alpha = 0.7f))
        Text(text = value, fontWeight = FontWeight.Bold, color = valueColor)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetPeriodDialog(
    initialPeriodInSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timeUnits = listOf("Hours", "Minutes", "Seconds")
    var selectedUnit by remember { mutableStateOf("Hours") }
    var periodValue by remember { mutableStateOf((initialPeriodInSeconds / 3600).toString()) }
    var isUnitMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Period") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = periodValue,
                    onValueChange = { periodValue = it },
                    label = { Text("Time") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuBox(
                    expanded = isUnitMenuExpanded,
                    onExpandedChange = { isUnitMenuExpanded = !isUnitMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedUnit,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isUnitMenuExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isUnitMenuExpanded,
                        onDismissRequest = { isUnitMenuExpanded = false }
                    ) {
                        timeUnits.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = {
                                    selectedUnit = unit
                                    isUnitMenuExpanded = false
                                    periodValue = when (unit) {
                                        "Hours" -> (initialPeriodInSeconds / 3600).toString()
                                        "Minutes" -> (initialPeriodInSeconds / 60).toString()
                                        else -> initialPeriodInSeconds.toString()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val numericValue = periodValue.toIntOrNull() ?: 0
                val totalSeconds = when (selectedUnit) {
                    "Hours" -> numericValue * 3600
                    "Minutes" -> numericValue * 60
                    else -> numericValue
                }
                onConfirm(totalSeconds)
                onDismiss()
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPillDialog(
    initial: PillBox,
    onConfirm: (PillBox) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial.name)) }
    var amount by remember { mutableStateOf(TextFieldValue(initial.amount.toString())) }
    var threshold by remember { mutableStateOf(TextFieldValue(initial.weightThreshold.toString())) }
    var color by remember { mutableStateOf(initial.color) }

    val colors = listOf(
        Color(0xFF90CAF9), Color(0xFFF48FB1), Color(0xFFA5D6A7),
        Color(0xFFFFF5D8), Color(0xFFCE93D8), Color(0xFFFFAB91)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    initial.copy(
                        name = name.text,
                        amount = amount.text.toIntOrNull() ?: initial.amount,
                        weightThreshold = threshold.text.toIntOrNull() ?: initial.weightThreshold,
                        color = color
                    )
                )
                onDismiss()
            }) { Text("Confirm") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit Drug Box") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (grams)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = threshold, onValueChange = { threshold = it }, label = { Text("Weight Threshold (grams)") }, modifier = Modifier.fillMaxWidth())

                Text("Select Color:")
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    colors.forEach { c ->
                        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)).background(c).clickable { color = c }.border(2.dp, if (c == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(50)))
                    }
                }
            }
        }
    )
}

private fun sendPeriodCommand(context: Context, pin: Int, period: Int) {
    val intent = Intent(context, MqttForegroundService::class.java).apply {
        action = "COMMAND"
        putExtra("command", "period")
        putExtra("pin", pin)
        putExtra("period", period)
    }
    context.startService(intent)
}

private fun sendResetCommand(context: Context, pin: Int) {
    val intent = Intent(context, MqttForegroundService::class.java).apply {
        action = "COMMAND"
        putExtra("command", "reset")
        putExtra("pin", pin)
    }
    context.startService(intent)
}

private fun sendStartTrackingCommand(context: Context, partNumber: Int) {
    val intent = Intent(context, MqttForegroundService::class.java).apply {
        action = "COMMAND"
        putExtra("command", "start_tracking")
        putExtra("part", partNumber)
    }
    context.startService(intent)
}
