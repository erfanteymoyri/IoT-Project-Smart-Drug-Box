package ir.iotlab.eaj.smartdrugbox

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PillBox(
    val id: Int,
    val name: String,
    val ledPin: Int,
    val period: Int,
    val lastActivation: Long?,
    val isActive: Boolean,
    val color: Color,
    val amount: Int = 1,
    val weightThreshold: Int = 2,
    val isTracking: Boolean = false,
    val reminderSent: Boolean = false,
    val mainNotificationSent: Boolean = false,
    val followUpSent: Boolean = false,
    val alarmSound: String // The sound is now non-nullable and assigned at creation
) {
    private fun formatTimestamp(timestamp: Long): String {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    }

    val nextActivation: String
        get() = lastActivation?.let {
            formatTimestamp(it + (period * 1000L))
        } ?: "Not set"

    val lastActivationFormatted: String
        get() = lastActivation?.let {
            formatTimestamp(it)
        } ?: "Never"
}

private class ColorAdapter : JsonSerializer<Color>, JsonDeserializer<Color> {
    override fun serialize(src: Color?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src?.value?.toLong())
    }
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Color {
        return Color(json?.asLong?.toULong() ?: Color.White.value)
    }
}

object PillBoxDataManager {
    private const val PREFS_NAME = "PillBoxPrefs"
    private const val PILL_BOXES_KEY = "pill_boxes"

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Color::class.java, ColorAdapter())
        .create()

    fun savePillBoxes(context: Context, pillBoxes: List<PillBox>) {
        val json = gson.toJson(pillBoxes)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PILL_BOXES_KEY, json)
            .apply()
    }

    fun loadPillBoxes(context: Context): List<PillBox> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(PILL_BOXES_KEY, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<PillBox>>() {}.type
                gson.fromJson(json, type) ?: getDefaultPillBoxes()
            } catch (e: Exception) {
                getDefaultPillBoxes()
            }
        } else {
            getDefaultPillBoxes()
        }
    }

    private fun getDefaultPillBoxes(): List<PillBox> {
        return List(4) { index ->
            PillBox(
                id = index,
                name = "Pill ${index + 1}",
                ledPin = when(index) {
                    0 -> 19; 1 -> 21; 2 -> 22; else -> 23
                },
                period = 3600,
                lastActivation = null,
                isActive = false,
                color = when(index) {
                    0 -> Color(0xFF90CAF9); 1 -> Color(0xFFF48FB1)
                    2 -> Color(0xFFA5D6A7); else -> Color(0xFFFFF59D)
                },
                amount = 10,
                weightThreshold = 2,
                // CORRECTED: Assign a unique, fixed sound to each compartment
                alarmSound = when(index) {
                    0 -> "alarm_one"
                    1 -> "alarm_two"
                    2 -> "alarm_three"
                    else -> "alarm_four"
                }
            )
        }
    }
}
