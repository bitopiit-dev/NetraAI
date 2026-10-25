package com.factory.netra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factory.netra.container
import com.factory.netra.ui.components.isWideLayout
import com.factory.netra.ui.alerts.AlertsScreen
import com.factory.netra.ui.components.FocusCard
import com.factory.netra.ui.dashboard.DashboardScreen
import com.factory.netra.ui.devices.DevicesScreen
import com.factory.netra.ui.live.FullscreenCamera
import com.factory.netra.ui.live.LiveWallScreen
import com.factory.netra.ui.settings.SettingsScreen
import com.factory.netra.ui.theme.NetraColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Dest(val label: String, val icon: ImageVector) {
    Overview("Overview", Icons.Outlined.SpaceDashboard),
    Live("Live", Icons.Outlined.Videocam),
    Alerts("Alerts", Icons.Outlined.NotificationsActive),
    Devices("Devices", Icons.Outlined.Router),
    Settings("Settings", Icons.Outlined.Tune),
}

@Composable
fun NetraShell(openRequest: Dest?, onOpenHandled: () -> Unit) {
    var dest by rememberSaveable { mutableStateOf(Dest.Overview) }
    var liveZone by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf<Long?>(null) }
    val c = LocalContext.current.container
    val unack by c.alerts.observeUnackCount().collectAsStateWithLifecycle(0)

    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            dest = openRequest
            fullscreen = null
            onOpenHandled()
        }
    }
    BackHandler(enabled = (fullscreen != null) || (dest != Dest.Overview)) {
        if (fullscreen != null) fullscreen = null else dest = Dest.Overview
    }

    val open = fullscreen
    if (open != null) {
        FullscreenCamera(cameraId = open, onClose = { fullscreen = null }) { fullscreen = it }
        return
    }

    val screen: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            when (dest) {
                Dest.Overview -> DashboardScreen(
                    onOpenZone = { liveZone = it; dest = Dest.Live },
                    onOpenAlerts = { dest = Dest.Alerts },
                    onAddDevices = { dest = Dest.Devices },
                )
                Dest.Live -> LiveWallScreen(
                    zone = liveZone,
                    onZoneChange = { liveZone = it },
                    onOpenCamera = { fullscreen = it },
                )
                Dest.Alerts -> AlertsScreen(onOpenCamera = { fullscreen = it })
                Dest.Devices -> DevicesScreen()
                Dest.Settings -> SettingsScreen()
            }
        }
    }

    val wide = isWideLayout()
    if (wide) {
        Row(Modifier.fillMaxSize().background(NetraColors.Night)) {
            NavRail(dest, unack) { dest = it }
            screen(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Scaffold(
            containerColor = NetraColors.Night,
            bottomBar = {
                NavigationBar(containerColor = NetraColors.Panel) {
                    Dest.entries.forEach { d ->
                        NavigationBarItem(
                            selected = d == dest,
                            onClick = { dest = d },
                            icon = { NavIcon(d, unack) },
                            label = { Text(d.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NetraColors.Night,
                                indicatorColor = NetraColors.Chalk,
                                selectedTextColor = NetraColors.Chalk,
                                unselectedIconColor = NetraColors.TextDim,
                                unselectedTextColor = NetraColors.TextDim,
                            ),
                        )
                    }
                }
            },
        ) { pad -> screen(Modifier.padding(pad).fillMaxSize()) }
    }
}

@Composable
private fun NavIcon(d: Dest, unack: Int, tint: Color = Color.Unspecified) {
    BadgedBox(
        badge = {
            if ((d == Dest.Alerts) && (unack > 0)) {
                Badge(containerColor = NetraColors.Alarm) {
                    Text(if (unack > 99) "99+" else unack.toString())
                }
            }
        },
    ) {
        if (tint == Color.Unspecified) Icon(d.icon, d.label) else Icon(d.icon, d.label, tint = tint)
    }
}

@Composable
private fun NavRail(current: Dest, unack: Int, onSelect: (Dest) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(NetraColors.Panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Visibility, null, tint = NetraColors.Chalk, modifier = Modifier.size(30.dp))
        Text("Netra", style = MaterialTheme.typography.titleMedium, color = NetraColors.Text)
        Spacer(Modifier.height(18.dp))
        Dest.entries.forEach { d ->
            val selected = d == current
            FocusCard(
                onClick = { onSelect(d) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selected) Modifier.focusRequester(focus) else Modifier),
                container = if (selected) NetraColors.PanelHigh else NetraColors.Panel,
                borderColor = Color.Transparent,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NavIcon(d, unack, tint = if (selected) NetraColors.Chalk else NetraColors.TextDim)
                    Text(
                        d.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) NetraColors.Text else NetraColors.TextDim,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Clock()
    }
}

@Composable
private fun Clock() {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)) }
    val day = remember(now) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)) }
    Text(time, style = MaterialTheme.typography.titleLarge, color = NetraColors.Text)
    Text(day, style = MaterialTheme.typography.labelSmall, color = NetraColors.TextDim)
}
