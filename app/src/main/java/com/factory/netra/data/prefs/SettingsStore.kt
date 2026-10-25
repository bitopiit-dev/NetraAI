package com.factory.netra.data.prefs

import android.content.Context
import androidx.core.content.edit
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which video player library draws the live streams. */
object VideoEngine {
    const val AUTO = 0     // Android player, falls back to VLC per camera on failure
    @Suppress("unused")
    const val ANDROID = 1  // Media3 / ExoPlayer only
    const val VLC = 2      // libVLC for every camera
    val labels = listOf("Auto", "Android player", "VLC")
}

data class AppSettings(
    val factoryName: String = "Garments Factory",
    val aiEnabled: Boolean = true,
    val recorderEvents: Boolean = true,
    val workStartHour: Int = 8,
    val workEndHour: Int = 20,
    /** Bangladesh garments weekly holiday is usually Friday. */
    val weeklyOffDay: Int = Calendar.FRIDAY,
    val crowdThreshold: Int = 10,
    val confidence: Float = 0.5f,
    val idleMinutes: Int = 20,
    val workers: Int = 4,
    val cycleDelaySec: Int = 5,
    val cooldownMin: Int = 5,
    val hdFullscreen: Boolean = true,
    val defaultGrid: Int = 9,
    val startOnBoot: Boolean = true,
    val retentionDays: Int = 30,
    val videoEngine: Int = VideoEngine.AUTO,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("netra_settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(current)
        write(next)
        state.value = next
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            factoryName = prefs.getString("factoryName", d.factoryName) ?: d.factoryName,
            aiEnabled = prefs.getBoolean("aiEnabled", d.aiEnabled),
            recorderEvents = prefs.getBoolean("recorderEvents", d.recorderEvents),
            workStartHour = prefs.getInt("workStartHour", d.workStartHour),
            workEndHour = prefs.getInt("workEndHour", d.workEndHour),
            weeklyOffDay = prefs.getInt("weeklyOffDay", d.weeklyOffDay),
            crowdThreshold = prefs.getInt("crowdThreshold", d.crowdThreshold),
            confidence = prefs.getFloat("confidence", d.confidence),
            idleMinutes = prefs.getInt("idleMinutes", d.idleMinutes),
            workers = prefs.getInt("workers", d.workers),
            cycleDelaySec = prefs.getInt("cycleDelaySec", d.cycleDelaySec),
            cooldownMin = prefs.getInt("cooldownMin", d.cooldownMin),
            hdFullscreen = prefs.getBoolean("hdFullscreen", d.hdFullscreen),
            defaultGrid = prefs.getInt("defaultGrid", d.defaultGrid),
            startOnBoot = prefs.getBoolean("startOnBoot", d.startOnBoot),
            retentionDays = prefs.getInt("retentionDays", d.retentionDays),
            videoEngine = prefs.getInt("videoEngine", d.videoEngine),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit {
            putString("factoryName", s.factoryName)
            putBoolean("aiEnabled", s.aiEnabled)
            putBoolean("recorderEvents", s.recorderEvents)
            putInt("workStartHour", s.workStartHour)
            putInt("workEndHour", s.workEndHour)
            putInt("weeklyOffDay", s.weeklyOffDay)
            putInt("crowdThreshold", s.crowdThreshold)
            putFloat("confidence", s.confidence)
            putInt("idleMinutes", s.idleMinutes)
            putInt("workers", s.workers)
            putInt("cycleDelaySec", s.cycleDelaySec)
            putInt("cooldownMin", s.cooldownMin)
            putBoolean("hdFullscreen", s.hdFullscreen)
            putInt("defaultGrid", s.defaultGrid)
            putBoolean("startOnBoot", s.startOnBoot)
            putInt("retentionDays", s.retentionDays)
            putInt("videoEngine", s.videoEngine)
        }
    }
}

/** True during the shift (handles night shifts that cross midnight). */
fun AppSettings.isWorkingTime(now: Calendar = Calendar.getInstance()): Boolean {
    if (now[Calendar.DAY_OF_WEEK] == weeklyOffDay) return false
    val h = now[Calendar.HOUR_OF_DAY]
    return if (workStartHour <= workEndHour) (h in (workStartHour until workEndHour))
    else ((h >= workStartHour) || (h < workEndHour))
}
