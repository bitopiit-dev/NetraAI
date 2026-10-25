package com.factory.netra.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factory.netra.BuildConfig
import com.factory.netra.container
import com.factory.netra.ui.components.isWideLayout
import com.factory.netra.data.prefs.AppSettings
import com.factory.netra.data.prefs.VideoEngine
import com.factory.netra.service.SurveillanceService
import com.factory.netra.ui.theme.NetraColors
import kotlinx.coroutines.launch
import java.util.Calendar

private val DAYS = listOf(
    Calendar.SATURDAY to "Saturday", Calendar.SUNDAY to "Sunday", Calendar.MONDAY to "Monday",
    Calendar.TUESDAY to "Tuesday", Calendar.WEDNESDAY to "Wednesday", Calendar.THURSDAY to "Thursday",
    Calendar.FRIDAY to "Friday", 0 to "No weekly holiday",
)

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val s by c.settings.flow.collectAsStateWithLifecycle()
    val ai by c.ai.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val wide = isWideLayout()
    fun set(block: (AppSettings) -> AppSettings) = c.settings.update(block)
    var serviceOn by remember { mutableStateOf(SurveillanceService.running) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Section("Factory") {
            var name by remember { mutableStateOf(s.factoryName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; set { st -> st.copy(factoryName = it.ifBlank { "Garments Factory" }) } },
                label = { Text("Factory name shown on the overview") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper("Shift starts", hourLabel(s.workStartHour)) { d -> set { it.copy(workStartHour = (it.workStartHour + d + 24) % 24) } }
            Stepper("Shift ends", hourLabel(s.workEndHour)) { d -> set { it.copy(workEndHour = (it.workEndHour + d + 24) % 24) } }
            Stepper("Weekly holiday", DAYS.first { it.first == s.weeklyOffDay }.second) { d ->
                set {
                    val i = DAYS.indexOfFirst { p -> p.first == it.weeklyOffDay }
                    it.copy(weeklyOffDay = DAYS[(i + d + DAYS.size) % DAYS.size].first)
                }
            }
            Hint("Anyone seen outside these hours, or on the holiday, raises an after-hours alert.")
        }

        Section("AI patrol") {
            Toggle("AI detection", "People and vehicle detection on every AI-enabled camera", s.aiEnabled) { v -> set { it.copy(aiEnabled = v) } }
            Text(
                when {
                    !ai.modelReady -> "AI model is not installed on this device yet."
                    ai.targets == 0 -> "No AI cameras yet."
                    else -> "Last sweep: ${ai.analyzed} of ${ai.targets} cameras in ${ai.cycleMs / 1000} s, ${ai.unreachable} unreachable."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ai.modelReady) NetraColors.TextDim else NetraColors.Alarm,
            )
            if (!c.detector.available) {
                var downloading by remember { mutableStateOf(false) }
                Button(
                    enabled = !downloading,
                    onClick = {
                        downloading = true
                        scope.launch {
                            val r = c.detector.downloadAndLoad(c.http)
                            downloading = false
                            Toast.makeText(
                                context,
                                r.fold({ "AI model installed. Detection starts in a few seconds." },
                                    { "Could not download: ${it.message}. Check internet on this device." }),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                ) { Text(if (downloading) "Downloading AI model…" else "Download AI model (about 4 MB)") }
                c.detector.loadError?.let { Text("Reason: $it", style = MaterialTheme.typography.bodySmall, color = NetraColors.Amber) }
                Hint("Needs internet once. After that AI works fully offline.")
            }
            Stepper("Crowd alert at", "${s.crowdThreshold} people") { d -> set { it.copy(crowdThreshold = (it.crowdThreshold + d).coerceIn(3, 40)) } }
            Stepper("Unattended line after", "${s.idleMinutes} min") { d -> set { it.copy(idleMinutes = (it.idleMinutes + d * 5).coerceIn(5, 120)) } }
            Stepper("Repeat the same alert after", "${s.cooldownMin} min") { d -> set { it.copy(cooldownMin = (it.cooldownMin + d).coerceIn(1, 60)) } }
            Stepper("Parallel cameras", "${s.workers}") { d -> set { it.copy(workers = (it.workers + d).coerceIn(1, 8)) } }
            Stepper("Pause between sweeps", "${s.cycleDelaySec} s") { d -> set { it.copy(cycleDelaySec = (it.cycleDelaySec + d * 5).coerceIn(0, 300)) } }
            Column {
                Text("Detection confidence: ${(s.confidence * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = s.confidence,
                    onValueChange = { v -> set { it.copy(confidence = (v * 20).toInt() / 20f) } },
                    valueRange = 0.3f..0.85f,
                    colors = SliderDefaults.colors(thumbColor = NetraColors.Chalk, activeTrackColor = NetraColors.Chalk),
                    modifier = Modifier.widthIn(max = 520.dp),
                )
                Hint("Higher means fewer false alarms but may miss people far from the camera.")
            }
        }

        Section("Recorder events") {
            Toggle(
                "Use Dahua smart events",
                "Tripwire, intrusion, tampering, video loss, object removed and HDD errors configured on the NVRs",
                s.recorderEvents,
            ) { v -> set { it.copy(recorderEvents = v) } }
        }

        Section("Display") {
            Stepper("Default live grid", "${s.defaultGrid} cameras") { d ->
                val sizes = listOf(1, 4, 9, 16)
                set { val i = sizes.indexOf(it.defaultGrid).coerceAtLeast(0); it.copy(defaultGrid = sizes[(i + d + sizes.size) % sizes.size]) }
            }
            Stepper("Video engine", VideoEngine.labels[s.videoEngine.coerceIn(0, 2)]) { d ->
                set { it.copy(videoEngine = (it.videoEngine + d + 3) % 3) }
            }
            Hint("Auto uses the Android player and switches a camera to VLC if it cannot play it. Choose VLC for older Dahua or OEM firmware.")
            Toggle("HD in full screen", "Main stream when one camera is open; sub stream on the wall", s.hdFullscreen) { v -> set { it.copy(hdFullscreen = v) } }
        }

        Section("Service and data") {
            Toggle("Start after power cut", "Begin monitoring when the TV box or phone boots", s.startOnBoot) { v -> set { it.copy(startOnBoot = v) } }
            Stepper("Keep alerts for", "${s.retentionDays} days") { d -> set { it.copy(retentionDays = (it.retentionDays + d * 5).coerceIn(5, 180)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (serviceOn) SurveillanceService.stop(context) else SurveillanceService.start(context)
                    serviceOn = !serviceOn
                }) { Text(if (serviceOn) "Stop background monitoring" else "Start background monitoring") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val n = c.alerts.pruneIfDue(s.retentionDays, force = true)
                        Toast.makeText(context, "Deleted $n old alerts", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Delete old alerts now") }
            }
        }

        Text(
            "Netra AI ${BuildConfig.VERSION_NAME} · video and AI stay on this device and your LAN",
            style = MaterialTheme.typography.bodySmall,
            color = NetraColors.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 900.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NetraColors.Panel)
            .border(1.dp, NetraColors.Seam, RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(color = NetraColors.Seam)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim)
}

@Composable
private fun Toggle(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = NetraColors.Chalk, checkedThumbColor = NetraColors.Night),
        )
    }
}

@Composable
private fun Stepper(title: String, value: String, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { onStep(-1) }) { Icon(Icons.Outlined.Remove, "Decrease $title") }
        Text(
            value, style = MaterialTheme.typography.labelLarge, color = NetraColors.Chalk,
            textAlign = TextAlign.Center, modifier = Modifier.width(150.dp),
        )
        IconButton(onClick = { onStep(1) }) { Icon(Icons.Outlined.Add, "Increase $title") }
    }
}
