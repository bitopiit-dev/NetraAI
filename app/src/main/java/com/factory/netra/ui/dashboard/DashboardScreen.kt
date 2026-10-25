package com.factory.netra.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.factory.netra.AppContainer
import com.factory.netra.ai.AiStatus
import com.factory.netra.container
import com.factory.netra.ui.components.isWideLayout
import com.factory.netra.data.db.AlertEntity
import com.factory.netra.data.db.DeviceType
import com.factory.netra.data.db.Severity
import com.factory.netra.data.prefs.isWorkingTime
import com.factory.netra.ui.components.EmptyState
import com.factory.netra.ui.components.FocusCard
import com.factory.netra.ui.components.IconLabel
import com.factory.netra.ui.components.Metric
import com.factory.netra.ui.components.SectionTitle
import com.factory.netra.ui.components.SeverityTag
import com.factory.netra.ui.components.formatWhen
import com.factory.netra.ui.theme.NetraColors
import com.factory.netra.ui.theme.color
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class ZoneSummary(
    val zone: String,
    val cameras: Int,
    val online: Int,
    val people: Int,
    val alertsToday: Int,
    val worst: Severity?,
)

data class DashboardState(
    val factory: String = "",
    val working: Boolean = true,
    val camsTotal: Int = 0,
    val camsOnline: Int = 0,
    val nvrTotal: Int = 0,
    val nvrOnline: Int = 0,
    val alertsToday: Int = 0,
    val highToday: Int = 0,
    val zones: List<ZoneSummary> = emptyList(),
    val latest: List<AlertEntity> = emptyList(),
    val ai: AiStatus = AiStatus(),
    val loaded: Boolean = false,
)

class DashboardViewModel(c: AppContainer) : ViewModel() {
    private val startOfDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val state = combine(
        c.devices.observeCameras(),
        c.devices.observeDevices(),
        c.alerts.observeSince(startOfDay),
        c.ai.status,
        c.settings.flow,
    ) { cams, devices, today, ai, settings ->
        val todayByCam = today.groupBy { it.zone }
        val zones = cams.groupBy { it.camera.zone }.map { (zone, list) ->
            val zAlerts = todayByCam[zone].orEmpty()
            ZoneSummary(
                zone = zone,
                cameras = list.size,
                online = list.count { it.camera.online },
                people = list.sumOf { it.camera.lastPeople },
                alertsToday = zAlerts.size,
                worst = zAlerts.minByOrNull { it.severity.ordinal }?.severity,
            )
        }.sortedWith(compareBy<ZoneSummary> { it.worst?.ordinal ?: 9 }.thenBy { it.zone })
        val nvrs = devices.filter { it.type == DeviceType.NVR }
        DashboardState(
            factory = settings.factoryName,
            working = settings.isWorkingTime(),
            camsTotal = cams.size,
            camsOnline = cams.count { it.camera.online },
            nvrTotal = nvrs.size,
            nvrOnline = nvrs.count { it.online },
            alertsToday = today.size,
            highToday = today.count { it.severity == Severity.HIGH },
            zones = zones,
            latest = today.take(8),
            ai = ai,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}

@Composable
fun DashboardScreen(
    onOpenZone: (String?) -> Unit,
    onOpenAlerts: () -> Unit,
    onAddDevices: () -> Unit,
) {
    val c = LocalContext.current.container
    val vm: DashboardViewModel = viewModel { DashboardViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val wide = isWideLayout()

    if (s.loaded && s.camsTotal == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Videocam,
                title = "Connect your recorders",
                body = "Scan 192.168.39.x to find the Dahua NVRs. Each NVR brings in all of its cameras with their names.",
            ) {
                Button(onClick = onAddDevices) {
                    Icon(Icons.Outlined.AddCircleOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add devices")
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (wide) 220.dp else 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(if (wide) 28.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Header(s) }

        item {
            Metric(
                "${s.camsOnline}/${s.camsTotal}", "Cameras online",
                accent = if (s.camsOnline < s.camsTotal) NetraColors.Amber else NetraColors.Thread,
            )
        }
        item {
            Metric(
                "${s.nvrOnline}/${s.nvrTotal}", "Recorders online",
                accent = if (s.nvrOnline < s.nvrTotal) NetraColors.Alarm else NetraColors.Thread,
            )
        }
        item {
            Metric(
                s.ai.peopleOnSite.toString(), "People in view",
                detail = if (s.ai.lastCycleAt > 0) "Updated ${formatWhen(s.ai.lastCycleAt)}" else null,
            )
        }
        item {
            Metric(
                s.alertsToday.toString(), "Alerts today",
                accent = if (s.highToday > 0) NetraColors.Alarm else NetraColors.Text,
                detail = if (s.highToday > 0) "${s.highToday} high priority" else "None high priority",
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Floors and zones", Modifier.padding(top = 10.dp)) {
                TextButton(onClick = { onOpenZone(null) }) { Text("Open live wall") }
            }
        }
        items(s.zones, key = { it.zone }) { z -> ZoneCard(z) { onOpenZone(z.zone) } }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Latest today", Modifier.padding(top = 10.dp)) {
                TextButton(onClick = onOpenAlerts) { Text("All alerts") }
            }
        }
        if (s.latest.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Nothing has needed attention today.", color = NetraColors.TextDim)
            }
        }
        items(s.latest, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { a -> AlertLine(a, onOpenAlerts) }
    }
}

@Composable
private fun Header(s: DashboardState) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(s.factory, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconLabel(
                Icons.Outlined.ShieldMoon,
                if (s.working) "Shift running" else "Outside working hours, after-hours watch on",
                if (s.working) NetraColors.TextDim else NetraColors.Chalk,
            )
            val ai = s.ai
            IconLabel(
                Icons.Outlined.Psychology,
                when {
                    !ai.modelReady -> "AI model missing, see README"
                    !ai.running -> "AI patrol paused"
                    ai.targets == 0 -> "AI patrol waiting for cameras"
                    else -> "AI patrol: ${ai.analyzed}/${ai.targets} cameras every ${(ai.cycleMs / 1000).coerceAtLeast(1)} s"
                },
                if (ai.running && ai.modelReady) NetraColors.Thread else NetraColors.Amber,
            )
        }
    }
}

@Composable
private fun ZoneCard(z: ZoneSummary, onClick: () -> Unit) {
    val accent = z.worst?.color() ?: NetraColors.Seam
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row {
            Box(Modifier.width(4.dp).height(118.dp).background(accent))
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(z.zone, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconLabel(Icons.Outlined.Videocam, "${z.online} of ${z.cameras} cameras live")
                IconLabel(Icons.Outlined.Groups, "${z.people} people in view")
                val ratio = if (z.cameras == 0) 0f else z.online / z.cameras.toFloat()
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (ratio < 1f) NetraColors.Amber else NetraColors.Thread,
                    trackColor = NetraColors.Seam,
                )
                Text(
                    if (z.alertsToday == 0) "No alerts today" else "${z.alertsToday} alerts today",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (z.alertsToday == 0) NetraColors.TextDim else accent,
                )
            }
        }
    }
}

@Composable
private fun AlertLine(a: AlertEntity, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(a.severity.color()))
            Column(Modifier.weight(1f)) {
                Text(a.type.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${a.cameraName} · ${a.zone} · ${a.message}",
                    style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            SeverityTag(a.severity)
            Text(formatWhen(a.createdAt), style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim)
        }
    }
}
