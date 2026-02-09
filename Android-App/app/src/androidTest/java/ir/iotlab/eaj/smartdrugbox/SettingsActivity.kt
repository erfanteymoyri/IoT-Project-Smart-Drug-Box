package ir.iotlab.eaj.smartdrugbox

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.iotlab.eaj.smartdrugbox.ui.theme.SmartDrugBoxTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartDrugBoxTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("MqttPrefs", Context.MODE_PRIVATE)

    var brokerUrl by remember { mutableStateOf(prefs.getString("broker_url", "tcp://87.248.152.126") ?: "") }
    var brokerPort by remember { mutableStateOf(prefs.getString("broker_port", "1883") ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MQTT Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = brokerUrl,
                onValueChange = { brokerUrl = it },
                label = { Text("Broker URL") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = brokerPort,
                onValueChange = { brokerPort = it },
                label = { Text("Broker Port") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    // Save the new settings
                    prefs.edit()
                        .putString("broker_url", brokerUrl)
                        .putString("broker_port", brokerPort)
                        .apply()

                    // Stop and restart the service to apply new settings
                    MqttForegroundService.stopService(context)
                    MqttForegroundService.startService(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Restart Service")
            }
        }
    }
}
