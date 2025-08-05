package ir.iotlab.eaj.smartdrugbox
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.iotlab.eaj.smartdrugbox.ui.theme.SmartDrugBoxTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartDrugBoxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MqttSettingsScreen()
                }
            }
        }
    }
}

@Composable
fun MqttSettingsScreen() {
    var broker by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("MQTT Settings", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = broker, onValueChange = { broker = it }, label = { Text("Broker Address") })
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") })
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
        Button(onClick = {
            // TODO: Save settings using DataStore or SharedPreferences
        }) {
            Text("Save Settings")
        }
    }
}
