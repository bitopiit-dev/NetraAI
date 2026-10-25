package com.factory.netra.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.factory.netra.container
import com.factory.netra.ui.components.isWideLayout
import com.factory.netra.data.db.CameraEntity
import com.factory.netra.data.db.CameraWithDevice
import com.factory.netra.data.db.DeviceEntity
import com.factory.netra.data.db.DeviceType
import com.factory.netra.ui.components.EmptyState
import com.factory.netra.ui.components.FocusCard
import com.factory.netra.ui.components.IconLabel
import com.factory.netra.ui.components.StatusDot
import com.factory.netra.ui.components.formatWhen
import com.factory.netra.ui.theme.NetraColors

@Composable
fun DevicesScreen() {
    val c = LocalContext.current.container
    val vm: DevicesViewModel = viewModel { DevicesViewModel(c) }
    val devices by vm.devices.collectAsStateWithLifecycle()
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    val wide = isWideLayout()
    val side = if (wide) 28.dp else 16.dp

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showScan by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CameraEntity?>(null) }
    var removing by remember { mutableStateOf<DeviceEntity?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = side, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Devices", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = { showScan = true }) {
                    Icon(Icons.Outlined.Radar, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan network")
                }
                OutlinedButton(onClick = { showAdd = true }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add by IP")
                }
            }
            vm.busy?.let {
                Column(Modifier.padding(horizontal = side)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = NetraColors.Chalk, trackColor = NetraColors.Seam)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim, modifier = Modifier.padding(top = 4.dp))
                }
            }

            val nvrCount = devices.count { it.type == DeviceType.NVR }
            TabRow(
                selectedTabIndex = tab,
                containerColor = NetraColors.Night,
                contentColor = NetraColors.Text,
                modifier = Modifier.padding(horizontal = side),
                indicator = { pos ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = NetraColors.Chalk)
                },
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recorders and IP cameras (${devices.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Camera channels (${cameras.size})") })
            }

            if (devices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Outlined.Router, "No devices yet",
                        "Scan 192.168.39.1 to 254. Add your 7 NVRs and all 110 cameras come in through their channels.",
                    ) { Button(onClick = { showScan = true }) { Text("Scan network") } }
                }
            } else if (tab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(side, 14.dp, side, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "$nvrCount recorders · ${devices.size - nvrCount} direct cameras",
                            color = NetraColors.TextDim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    items(devices, key = { it.id }) { d ->
                        DeviceRow(
                            d, cameras.count { it.camera.deviceId == d.id },
                            onResync = { vm.resync(d) }, onRemove = { removing = d },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search by camera, zone or recorder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = side, vertical = 12.dp),
                )
                val shown = remember(cameras, query) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) cameras else cameras.filter {
                        it.camera.name.lowercase().contains(q) || it.camera.zone.lowercase().contains(q) ||
                            it.device.name.lowercase().contains(q) || it.device.host.contains(q)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(side, 0.dp, side, 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.camera.id }) { cam ->
                        CameraRow(cam, wide, onChange = { vm.update(it) }, onEdit = { editing = cam.camera })
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (showScan) ScanDialog(vm) { showScan = false }
    if (showAdd) AddDeviceDialog(vm) { showAdd = false }
    editing?.let { cam ->
        EditCameraDialog(
            cam,
            knownZones = cameras.map { it.camera.zone }.distinct(),
            onDismiss = { editing = null },
            onSave = { vm.update(it); editing = null },
        )
    }
    removing?.let { d ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = NetraColors.Panel,
            title = { Text("Remove ${d.name}?") },
            text = { Text("Its cameras leave Netra. Recordings on the device are not touched.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(d); removing = null }) { Text("Remove", color = NetraColors.Alarm) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun DeviceRow(d: DeviceEntity, cams: Int, onResync: () -> Unit, onRemove: () -> Unit) {
    FocusCard(onClick = onResync, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(d.online, size = 10.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${if (d.type == DeviceType.NVR) "Recorder" else "IP camera"} · ${d.model} · ${d.host}",
                    style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$cams cameras in Netra" + (if (d.type == DeviceType.NVR) " of ${d.channelCount} channels" else "") +
                        if (d.online) "" else " · offline since ${if (d.lastSeen > 0) formatWhen(d.lastSeen) else "added"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (d.online) NetraColors.TextDim else NetraColors.Alarm,
                )
            }
            IconButton(onClick = onResync) { Icon(Icons.Outlined.Sync, "Refresh channels") }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove") }
        }
    }
}

@Composable
private fun CameraRow(
    cam: CameraWithDevice,
    wide: Boolean,
    onChange: (CameraEntity) -> Unit,
    onEdit: () -> Unit,
) {
    val c = cam.camera
    FocusCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(c.online)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${c.zone} · ${cam.device.name} ch ${c.channel}",
                        style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim, maxLines = 1,
                    )
                }
                if (wide) Toggles(c, onChange)
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit camera") }
            }
            if (!wide) Toggles(c, onChange)
        }
    }
}

@Composable
private fun Toggles(c: CameraEntity, onChange: (CameraEntity) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LabeledSwitch(Icons.Outlined.Psychology, "AI", c.aiEnabled) { onChange(c.copy(aiEnabled = it)) }
        LabeledSwitch(Icons.Outlined.Lock, "Restricted", c.restricted) { onChange(c.copy(restricted = it)) }
        LabeledSwitch(Icons.Outlined.Timer, "Idle watch", c.monitorIdle) { onChange(c.copy(monitorIdle = it)) }
    }
}

@Composable
private fun LabeledSwitch(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconLabel(icon, label)
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NetraColors.Chalk,
                checkedThumbColor = NetraColors.Night,
            ),
        )
    }
}
