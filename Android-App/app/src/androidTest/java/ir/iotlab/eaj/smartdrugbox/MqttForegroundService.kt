package ir.iotlab.eaj.smartdrugbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

class MqttForegroundService : Service() {
    private lateinit var mqttClient: MqttClient
    private lateinit var brokerUrl: String
    private val clientId = "AndroidClient-${System.currentTimeMillis()}"
    private val username = "AJ_IoT"
    private val password = "hgsde32993004"

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val NOTIFICATION_ID = 123
        private const val CHANNEL_ID = "mqtt_service_channel"
        private const val PILL_REMINDER_CHANNEL_ID = "pill_reminder_channel"
        const val ACTION_STOP_ALARM = "ir.iotlab.eaj.smartdrugbox.STOP_ALARM"

        fun startService(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun stopService(context: Context) {
            val intent = Intent(context, MqttForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("MqttPrefs", Context.MODE_PRIVATE)
        val url = prefs.getString("broker_url", "87.248.152.126") ?: "87.248.152.126"
        val port = prefs.getString("broker_port", "1883") ?: "1883"
        brokerUrl = "tcp://$url:$port"

        createNotificationChannels()
        startMqttClient()
        startPillCheckLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopAlarmSound()
            val pillId = intent.getIntExtra("pill_id", -1)
            if (pillId != -1) {
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(pillId)
            }
            return START_NOT_STICKY
        }

        if (intent?.action == "COMMAND") {
            when (intent.getStringExtra("command")) {
                "start_tracking" -> {
                    val part = intent.getIntExtra("part", -1)
                    if (part != -1) sendEsp32Command("start_tracking", part)
                }
            }
        }
        return START_STICKY
    }

    private fun startPillCheckLoop() {
        serviceScope.launch {
            while (true) {
                val pillBoxes = PillBoxDataManager.loadPillBoxes(this@MqttForegroundService)
                val now = System.currentTimeMillis()
                var dataChanged = false

                pillBoxes.forEachIndexed { index, pillBox ->
                    if (pillBox.isTracking) {
                        pillBox.lastActivation?.let { lastActivation ->
                            val periodMillis = pillBox.period * 1000L
                            val reminderTime = periodMillis / 50
                            val nextActivation = lastActivation + periodMillis

                            val reminderPoint = nextActivation - reminderTime
                            if (now >= reminderPoint && !pillBox.reminderSent) {
                                showReminderNotification(this@MqttForegroundService, pillBox)
                                updatePillBoxState(index) { it.copy(reminderSent = true) }
                                dataChanged = true
                            }

                            if (now >= nextActivation && !pillBox.mainNotificationSent) {
                                showPillNotification(this@MqttForegroundService, pillBox)
                                sendEsp32Command("alarm_on", index + 1)
                                updatePillBoxState(index) {
                                    it.copy(isActive = true, mainNotificationSent = true)
                                }
                                dataChanged = true
                            }

                            val followUpPoint = nextActivation + reminderTime
                            if (now >= followUpPoint && pillBox.isActive && !pillBox.followUpSent) {
                                showFollowUpNotification(this@MqttForegroundService, pillBox)
                                updatePillBoxState(index) { it.copy(followUpSent = true) }
                                dataChanged = true
                            }
                        }
                    }
                }

                if (dataChanged) {
                    sendBroadcast(Intent("ir.iotlab.eaj.smartdrugbox.DATA_CHANGED"))
                }
                delay(1000)
            }
        }
    }

    private fun handleMedicationTaken(payload: String) {
        try {
            val json = JSONObject(payload)
            val partNumber = json.getInt("part")
            val boxIndex = partNumber - 1

            val pillBoxes = PillBoxDataManager.loadPillBoxes(this)

            if (boxIndex in pillBoxes.indices && pillBoxes[boxIndex].isTracking) {
                Log.d("PillTaken", "Received confirmation that pill for part #${partNumber} was taken.")

                // --- MODIFIED: Check if the dose was on-time or early ---
                val wasAlarmActive = pillBoxes[boxIndex].isActive

                stopAlarmSound()
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(pillBoxes[boxIndex].id)

                sendEsp32Command("alarm_off", partNumber)

                // --- MODIFIED: Show a different notification based on whether the alarm was active ---
                if (wasAlarmActive) {
                    showTakenNotification(this, pillBoxes[boxIndex])
                } else {
                    showEarlyTakenNotification(this, pillBoxes[boxIndex])
                }

                updatePillBoxState(boxIndex) {
                    it.copy(
                        isActive = false,
                        lastActivation = System.currentTimeMillis(),
                        reminderSent = false,
                        mainNotificationSent = false,
                        followUpSent = false
                    )
                }
                sendBroadcast(Intent("ir.iotlab.eaj.smartdrugbox.DATA_CHANGED"))
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Error parsing 'taken' message", e)
        }
    }

    private fun startMqttClient() {
        try {
            mqttClient = MqttClient(brokerUrl, clientId, null)
            val options = MqttConnectOptions().apply {
                userName = username
                password = this@MqttForegroundService.password.toCharArray()
                isCleanSession = false
                connectionTimeout = 10
                keepAliveInterval = 60
                setAutomaticReconnect(true)
            }

            mqttClient.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String) {
                    Log.d("MQTT", "Connected to $serverURI")
                    subscribeToTopics()
                }
                override fun connectionLost(cause: Throwable?) { Log.e("MQTT", "Connection lost") }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    val payload = String(message.payload)
                    Log.d("MQTT", "Message arrived on $topic: $payload")
                    if (topic == "esp32/medication/taken") {
                        handleMedicationTaken(payload)
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) { Log.d("MQTT", "Delivery complete") }
            })
            mqttClient.connect(options)
            startForeground(NOTIFICATION_ID, getForegroundNotification())
        } catch (e: Exception) {
            Log.e("MQTT", "Error starting MQTT client", e)
            stopSelf()
        }
    }

    private fun subscribeToTopics() {
        try {
            mqttClient.subscribe("esp32/medication/taken", 1)
            Log.d("MQTT", "Subscribed to esp32/medication/taken")
        } catch (e: Exception) {
            Log.e("MQTT", "Subscription error", e)
        }
    }

    private fun sendEsp32Command(command: String, partNumber: Int) {
        val payload = "{\"command\":\"$command\",\"part\":$partNumber}"
        publishMessage("esp32/commands", payload)
    }

    private fun updatePillBoxState(index: Int, update: (PillBox) -> PillBox) {
        val currentBoxes = PillBoxDataManager.loadPillBoxes(this)
        if (index in currentBoxes.indices) {
            val updatedBoxes = currentBoxes.toMutableList()
            updatedBoxes[index] = update(updatedBoxes[index])
            PillBoxDataManager.savePillBoxes(this, updatedBoxes)
        }
    }

    private fun playAlarmSound(soundResourceName: String) {
        stopAlarmSound()
        try {
            val resourceId = resources.getIdentifier(soundResourceName, "raw", packageName)
            if (resourceId != 0) {
                val soundUri = Uri.parse("android.resource://$packageName/$resourceId")
                mediaPlayer = MediaPlayer.create(this, soundUri).apply {
                    isLooping = true
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e("MediaPlayer", "Error playing sound", e)
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun showPillNotification(context: Context, pillBox: PillBox) {
        playAlarmSound(pillBox.alarmSound)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val stopIntent = Intent(context, MqttForegroundService::class.java).apply {
            action = ACTION_STOP_ALARM
            putExtra("pill_id", pillBox.id)
        }
        val stopPendingIntent = PendingIntent.getService(
            context, pillBox.id, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, PILL_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to take ${pillBox.name}!")
            .setContentText("Please take your medication now")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false) // User cannot swipe away the alarm
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_lock_silent_mode_off, "Dismiss", stopPendingIntent)
            .build()

        notificationManager.notify(pillBox.id, notification)
    }

    private fun showReminderNotification(context: Context, pillBox: PillBox) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminderMinutes = (pillBox.period / 50) / 60
        val reminderText = if (reminderMinutes > 0) {
            "Prepare to take ${pillBox.name} in about $reminderMinutes minute(s)"
        } else {
            "Prepare to take ${pillBox.name} soon"
        }

        val notification = NotificationCompat.Builder(context, PILL_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Upcoming: ${pillBox.name}")
            .setContentText(reminderText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(pillBox.id + 1000, notification)
    }

    private fun showFollowUpNotification(context: Context, pillBox: PillBox) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, PILL_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder: ${pillBox.name}")
            .setContentText("You haven't taken ${pillBox.name} yet. Please take it now.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(pillBox.id + 2000, notification)
    }

    private fun showTakenNotification(context: Context, pillBox: PillBox) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, PILL_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${pillBox.name} Taken!")
            .setContentText("Great! Your next dose is scheduled.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(pillBox.id + 3000, notification)
    }

    // --- NEW: Notification for when a pill is taken early ---
    private fun showEarlyTakenNotification(context: Context, pillBox: PillBox) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, PILL_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${pillBox.name} Taken Early")
            .setContentText("Dose taken early. Timer has been reset.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(pillBox.id + 4000, notification) // Use a new ID to avoid conflicts
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps MQTT connection alive"
            }

            val reminderChannel = NotificationChannel(
                PILL_REMINDER_CHANNEL_ID,
                "Pill Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for pill reminder notifications"
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(reminderChannel)
        }
    }

    private fun getForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Drug Box Service")
            .setContentText("Monitoring your medication schedule.")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    private fun publishMessage(topic: String, payload: String) {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                val mqttMessage = MqttMessage(payload.toByteArray())
                mqttMessage.qos = 1
                mqttClient.publish(topic, mqttMessage)
                Log.d("MQTT", "Published to $topic: $payload")
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Publish error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        stopAlarmSound()
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
            }
        } catch (e: Exception) {
            Log.e("MQTT", "Disconnection error", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
