package com.factory.netra.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.factory.netra.data.db.CameraEntity
import com.factory.netra.data.db.DeviceType
import com.factory.netra.ui.theme.NetraColors

val FACTORY_ZONES = listOf(
    "Main Gate", "Perimeter", "Cutting", "Sewing", "Finishing", "Ironing", "Packing",
    "Quality Control", "Fabric Store", "Accessories Store", "Finished Goods", "Washing",
    "Loading Dock", "Canteen", "Office", "Server Room",
)

private const val SUBNET = "192.168.39."

@Composable
fun ScanDialog(vm: DevicesViewModel, onDismiss: () -> Unit) {
    var prefix by rememberSaveable { mutableStateOf(SUBNET) }
    var user by rememberSaveable { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(ImportMode.RecordersOnly) }
    val unselected = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NetraColors.Panel)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Find Dahua devices on the network", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = prefix, onValueChange = { prefix = it },
                    label = { Text("Subnet (first three parts)") }, singleLine = true,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
                Button(onClick = { unselected.clear(); vm.startScan(prefix.trim().let { if (it.endsWith(".")) it else "$it." }) },
                    enabled = !vm.scanning) {
                    Text(if (vm.scanning) "Scanning…" else "Scan 1 to 254")
                }
            }
            if (vm.scanning || vm.scanProgress > 0f) {
                LinearProgressIndicator(
                    progress = { vm.scanProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = NetraColors.Chalk, trackColor = NetraColors.Seam,
                )
                Text(
                    "${vm.hits.size} devices found, ${vm.hits.count { it.dahuaPort }} answer on Dahua port 37777",
                    style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                )
            }

            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(vm.hits, key = { it.ip }) { hit ->
                        val checked = hit.ip !in unselected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { if (checked) unselected.add(hit.ip) else unselected.remove(hit.ip) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = NetraColors.Chalk, checkmarkColor = NetraColors.Night),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(hit.ip, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(140.dp))
                            Text(
                                listOfNotNull(
                                    if (hit.dahuaPort) "Dahua" else null,
                                    if (hit.http) "web" else null,
                                    if (hit.rtsp) "RTSP" else null,
                                ).joinToString(", "),
                                style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                            )
                        }
                    }
                }
                Column(
                    Modifier.width(340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Login used for every selected device", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    ImportMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { mode = m },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == m, onClick = { mode = m })
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text(
                        "Recorders only: each NVR adds its connected channels, so cameras are not listed twice. " +
                            "Use a view-only Dahua user for Netra rather than admin.",
                        style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                    )
                    val selected = vm.hits.map { it.ip }.filter { it !in unselected }
                    Button(
                        onClick = { vm.importSelected(selected, user.trim(), pass, mode) },
                        enabled = selected.isNotEmpty() && pass.isNotEmpty() && vm.busy == null && !vm.scanning,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add ${selected.size} devices") }
                    vm.busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NetraColors.Chalk) }
                    vm.importLog.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
fun AddDeviceDialog(vm: DevicesViewModel, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(DeviceType.NVR) }
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf(SUBNET) }
    var http by rememberSaveable { mutableStateOf("80") }
    var rtsp by rememberSaveable { mutableStateOf("554") }
    var user by rememberSaveable { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetraColors.Panel,
        title = { Text("Add by IP address") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Recorder (NVR)", type == DeviceType.NVR) { type = DeviceType.NVR }
                    Choice("IP camera", type == DeviceType.CAMERA) { type = DeviceType.CAMERA }
                }
                OutlinedTextField(host, { host = it }, label = { Text("IP address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(http, { http = it.filter(Char::isDigit) }, label = { Text("HTTP port") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(rtsp, { rtsp = it.filter(Char::isDigit) }, label = { Text("RTSP port") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (type == DeviceType.CAMERA) {
                    OutlinedTextField(zone, { zone = it }, label = { Text("Zone") }, singleLine = true)
                    ZonePicker(zone) { zone = it }
                }
                vm.busy?.let { Text(it, color = NetraColors.Chalk, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.count { it == '.' } == 3 && pass.isNotEmpty() && vm.busy == null,
                onClick = {
                    vm.addManual(
                        name.trim(), host.trim(), http.toIntOrNull() ?: 80, rtsp.toIntOrNull() ?: 554,
                        user.trim(), pass, zone.trim(), type, onDone = onDismiss,
                    )
                },
            ) { Text("Connect and add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EditCameraDialog(
    cam: CameraEntity,
    knownZones: List<String>,
    onDismiss: () -> Unit,
    onSave: (CameraEntity) -> Unit,
) {
    var name by remember { mutableStateOf(cam.name) }
    var zone by remember { mutableStateOf(cam.zone) }
    var enabled by remember { mutableStateOf(cam.enabled) }
    var ai by remember { mutableStateOf(cam.aiEnabled) }
    var restricted by remember { mutableStateOf(cam.restricted) }
    var idle by remember { mutableStateOf(cam.monitorIdle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetraColors.Panel,
        title = { Text("Camera settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(zone, { zone = it }, label = { Text("Zone / floor") }, singleLine = true)
                ZonePicker(zone, (knownZones + FACTORY_ZONES).distinct().filter { it != "Unassigned" }) { zone = it }
                Spacer(Modifier.height(4.dp))
                SettingSwitch("Show on live wall", "Turn off for cameras that are not in use", enabled) { enabled = it }
                SettingSwitch("AI patrol", "Count people and raise rule alerts", ai) { ai = it }
                SettingSwitch("Restricted area", "Any person seen here raises a high alert", restricted) { restricted = it }
                SettingSwitch("Idle watch", "Alert when a line or table is unattended during the shift", idle) { idle = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    cam.copy(
                        name = name.trim().ifEmpty { cam.name },
                        zone = zone.trim().ifEmpty { "Unassigned" },
                        enabled = enabled, aiEnabled = ai, restricted = restricted, monitorIdle = idle,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZonePicker(current: String, zones: List<String> = FACTORY_ZONES, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        zones.forEach { z ->
            if (z == current) {
                Choice(z, true) { onPick(z) }
            } else {
                AssistChip(onClick = { onPick(z) }, label = { Text(z) })
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NetraColors.Chalk, selectedLabelColor = NetraColors.Night,
        ),
    )
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim)
        }
        Box(Modifier.padding(start = 12.dp)) {
            Switch(
                checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = NetraColors.Chalk, checkedThumbColor = NetraColors.Night),
            )
        }
    }
}
