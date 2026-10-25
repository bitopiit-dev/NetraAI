package com.factory.netra.dahua

import android.util.Log
import com.factory.netra.AppContainer
import com.factory.netra.data.db.AlertType
import com.factory.netra.data.db.DeviceEntity
import com.factory.netra.data.db.Severity
import com.factory.netra.data.prefs.isWorkingTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

data class RecorderEvent(val code: String, val action: String, val index: Int)

/**
 * Subscribes to each NVR/camera's event stream
 * (/cgi-bin/eventManager.cgi?action=attach&codes=\[All\]) so the smart rules
 * already configured on the Dahua devices (tripwire, intrusion, tamper,
 * SMD human/vehicle, video loss, HDD errors) become Netra alerts instantly.
 * 7 NVRs = 7 long-lived HTTP connections, regardless of camera count.
 */
class DahuaEventService(private val c: AppContainer) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            // Restart listeners only when the device list / credentials change,
            // not on every online/offline status update.
            val devicesKey = c.devices.observeDevices()
                .map { list -> list.map { DeviceKey(it.id, it.host, it.httpPort, it.username, it.passwordEnc) } to list }
                .distinctUntilChanged { a, b -> a.first == b.first }
            combine(devicesKey, c.settings.flow.map { it.recorderEvents }.distinctUntilChanged()) { d, on -> d.second to on }
                .collectLatest { (devices, enabled) ->
                    if (!enabled) return@collectLatest
                    coroutineScope { devices.forEach { d -> launch { listen(d) } } }
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private data class DeviceKey(val id: Long, val host: String, val port: Int, val user: String, val pass: String)

    private suspend fun listen(device: DeviceEntity) {
        val ep = c.devices.endpoint(device)
        var backoff = 5_000L
        while (currentCoroutineContext().isActive) {
            try {
                val req = Request.Builder()
                    .url("${ep.base}/cgi-bin/eventManager.cgi?action=attach&codes=[All]&heartbeat=5")
                    .build()
                val call = c.dahua.client(ep, streaming = true).newCall(req)
                val cancelHook = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
                try {
                    withContext(Dispatchers.IO) {
                        call.execute().use { resp ->
                            if (resp.code == 401) throw AuthException("Event stream: wrong credentials")
                            if (!resp.isSuccessful) throw IOException("Event stream HTTP ${resp.code}")
                            c.devices.setDeviceOnline(device.id, online = true)
                            backoff = 5_000L
                            val source = resp.body?.source() ?: throw IOException("empty body")
                            while (true) {
                                val line = source.readUtf8Line() ?: break
                                parse(line)?.let { onEvent(device, it) }
                            }
                        }
                    }
                } finally {
                    cancelHook?.dispose()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                Log.w(TAG, "${device.host}: ${e.message}")
                c.devices.setDeviceOnline(device.id, online = false)
                backoff = 10 * 60_000L // do not hammer the device and lock the account
            } catch (e: Exception) {
                Log.d(TAG, "${device.host}: ${e.message}")
                c.devices.setDeviceOnline(device.id, online = false)
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(10 * 60_000L)
        }
    }

    private fun parse(line: String): RecorderEvent? {
        val m = LINE.find(line) ?: return null
        return RecorderEvent(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt())
    }

    private suspend fun onEvent(device: DeviceEntity, e: RecorderEvent) {
        val cam = c.db.cameraDao().findByChannel(device.id, e.index + 1)

        when (e.code) {
            "VideoLoss" -> cam?.let { c.db.cameraDao().setOnline(it.camera.id, e.action != "Start") }
        }
        if ((e.action != "Start") && (e.action != "Pulse")) return

        val working = c.settings.current.isWorkingTime()
        val (type, severity) = when (e.code) {
            "CrossLineDetection" -> AlertType.TRIPWIRE to Severity.HIGH
            "CrossRegionDetection" -> AlertType.INTRUSION to Severity.HIGH
            "VideoBlind" -> AlertType.TAMPERING to Severity.HIGH
            "TakenAwayDetection" -> AlertType.MISSING_OBJECT to Severity.HIGH
            "LeftDetection" -> AlertType.ABANDONED_OBJECT to Severity.MEDIUM
            "VideoLoss" -> AlertType.VIDEO_LOSS to Severity.MEDIUM
            "StorageFailure", "StorageNotExist", "StorageLowSpace" -> AlertType.STORAGE to Severity.MEDIUM
            // Human / vehicle SMD fires constantly during the shift: only after hours.
            "SmartMotionHuman" -> if (working) return else AlertType.HUMAN_MOTION to Severity.HIGH
            "SmartMotionVehicle" -> if (working) return else AlertType.VEHICLE_MOTION to Severity.MEDIUM
            else -> return
        }
        c.rules.raiseRecorderAlert(device, cam, type, severity, e.code)
    }

    companion object {
        private const val TAG = "NetraEvents"
        private val LINE = Regex("""Code=([^;]+);action=([^;]+);index=(\d+)""")
    }
}
