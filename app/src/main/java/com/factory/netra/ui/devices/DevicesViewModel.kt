package com.factory.netra.ui.devices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.factory.netra.AppContainer
import com.factory.netra.dahua.ScanEvent
import com.factory.netra.dahua.ScanHit
import com.factory.netra.data.db.CameraEntity
import com.factory.netra.data.db.DeviceEntity
import com.factory.netra.data.db.DeviceType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ImportMode(val label: String) {
    RecordersOnly("Recorders only (recommended)"),
    CamerasOnly("Cameras only"),
}

class DevicesViewModel(private val c: AppContainer) : ViewModel() {
    val devices = c.devices.observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cameras = c.devices.observeCameras()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-line feedback shown in a snackbar. */
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
        private set

    // ---- Network scan ----
    var scanning by mutableStateOf(false)
        private set
    var scanProgress by mutableStateOf(0f)
        private set
    val hits = mutableStateListOf<ScanHit>()
    var importLog by mutableStateOf<List<String>>(emptyList())
        private set
    private var scanJob: Job? = null

    fun startScan(prefix: String) {
        scanJob?.cancel()
        hits.clear()
        importLog = emptyList()
        scanning = true
        scanProgress = 0f
        scanJob = viewModelScope.launch {
            c.scanner.scan(prefix).collect { e ->
                when (e) {
                    is ScanEvent.Progress -> scanProgress = e.done / e.total.toFloat()
                    is ScanEvent.Found -> {
                        hits.add(e.hit)
                        hits.sortBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
                    }
                }
            }
            scanning = false
        }
    }

    /**
     * Adds every selected address. Recorders bring in all of their connected
     * channels; cameras that are already recorded by an NVR are best left out
     * (otherwise each camera would appear twice).
     */
    fun importSelected(ips: List<String>, user: String, pass: String, mode: ImportMode) {
        if (ips.isEmpty()) return
        viewModelScope.launch {
            val log = mutableListOf<String>()
            var added = 0
            ips.forEachIndexed { i, ip ->
                busy = "Adding ${i + 1} of ${ips.size}: $ip"
                val type = if (mode == ImportMode.CamerasOnly) DeviceType.CAMERA else null
                val r = c.devices.addDevice("", ip, 80, 554, user, pass, "", forceType = type)
                r.onSuccess { n ->
                    added += n
                    log += "$ip: $n cameras added"
                }.onFailure { log += "$ip: ${it.message ?: "failed"}" }
                importLog = log.toList()
                if (r.exceptionOrNull() is com.factory.netra.dahua.AuthException) {
                    log += "Stopped: wrong password. Dahua locks the account after repeated failures."
                    importLog = log.toList()
                    busy = null
                    return@launch
                }
            }
            busy = null
            message = "$added cameras added from ${ips.size} devices"
        }
    }

    fun addManual(
        name: String, host: String, httpPort: Int, rtspPort: Int,
        user: String, pass: String, zone: String, type: DeviceType,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            busy = "Connecting to $host"
            val r = c.devices.addDevice(name, host, httpPort, rtspPort, user, pass, zone, forceType = type)
            busy = null
            r.onSuccess {
                message = "Added ${if (type == DeviceType.NVR) "recorder" else "camera"} at $host with $it cameras"
                onDone()
            }.onFailure { message = it.message ?: "Could not reach $host" }
        }
    }

    fun resync(d: DeviceEntity) = viewModelScope.launch {
        busy = "Reading channels from ${d.name}"
        val r = c.devices.resync(d)
        busy = null
        message = r.fold({ if (it == 0) "${d.name} is up to date" else "$it new cameras from ${d.name}" },
            { it.message ?: "Could not reach ${d.host}" })
    }

    fun delete(d: DeviceEntity) = viewModelScope.launch {
        c.devices.delete(d)
        message = "Removed ${d.name} and its cameras"
    }

    fun update(cam: CameraEntity) = viewModelScope.launch { c.devices.updateCamera(cam) }
}
