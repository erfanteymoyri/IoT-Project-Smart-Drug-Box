package ir.iotlab.eaj.smartdrugbox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ir.iotlab.eaj.smartdrugbox.ui.theme.SmartDrugBoxTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartDrugBoxTheme {
                DrugBoxApp(onSettingsClick = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugBoxApp(onSettingsClick: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Smart Drug Box") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        PillBoxScreen(Modifier.padding(padding))
    }
}

@Composable
fun PillBoxScreen(modifier: Modifier = Modifier) {
    val pillBoxes = remember {
        List(4) { index ->
            mutableStateOf(
                PillBox(
                    name = "Pill ${index + 1}",
                    amount = index + 1,
                    color = Color(0xFF90CAF9),
                    period = 8 * (index + 1),
                    lastTaken = LocalDateTime.now()
                )
            )
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        pillBoxes.forEach { pillBox ->
            var showDialog by remember { mutableStateOf(false) }

            PillCard(
                pillBox = pillBox.value,
                onEdit = { showDialog = true },
                onReset = {
                    pillBox.value = pillBox.value.copy(lastTaken = LocalDateTime.now())
                }
            )

            if (showDialog) {
                EditPillDialog(
                    initial = pillBox.value,
                    onConfirm = {
                        pillBox.value = it
                        showDialog = false
                    },
                    onDismiss = { showDialog = false }
                )
            }
        }
    }
}

@Composable
fun PillCard(
    pillBox: PillBox,
    onEdit: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(pillBox.color.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Text("Name: ${pillBox.name}", style = MaterialTheme.typography.titleMedium)
            Text("Amount: ${pillBox.amount}")
            Text("Period: ${pillBox.period} hours")
            Text("Last Taken: ${pillBox.lastTaken.format(DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy"))}")
            Text("Next Dose: ${pillBox.lastTaken.plusHours(pillBox.period.toLong()).format(DateTimeFormatter.ofPattern("HH:mm dd-MM-yyyy"))}")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onEdit, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
                    Text("Edit", color = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onReset, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) {
                    Text("RESET", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EditPillDialog(
    initial: PillBox,
    onConfirm: (PillBox) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(TextFieldValue(initial.name)) }
    var amount by remember { mutableStateOf(TextFieldValue(initial.amount.toString())) }
    var period by remember { mutableStateOf(TextFieldValue(initial.period.toString())) }
    var color by remember { mutableStateOf(initial.color) }

    val colors = listOf(
        Color(0xFF90CAF9), Color(0xFFF48FB1), Color(0xFFA5D6A7),
        Color(0xFFFFF59D), Color(0xFFCE93D8), Color(0xFFFFAB91)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    initial.copy(
                        name = name.text,
                        amount = amount.text.toIntOrNull() ?: initial.amount,
                        period = period.text.toIntOrNull() ?: initial.period,
                        color = color
                    )
                )
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Edit Drug Box") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
                OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period (hrs)") })

                Text("Select Color:")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(c, shape = RoundedCornerShape(4.dp))
                                .clickable { color = c }
                                .border(2.dp, if (c == color) Color.Black else Color.Transparent, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    )
}

data class PillBox(
    val name: String,
    val amount: Int,
    val color: Color,
    val period: Int,
    val lastTaken: LocalDateTime
)