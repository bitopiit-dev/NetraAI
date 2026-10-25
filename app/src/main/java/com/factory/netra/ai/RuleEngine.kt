package com.factory.netra.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.factory.netra.AppContainer
import com.factory.netra.data.db.AlertSource
import com.factory.netra.data.db.AlertType
import com.factory.netra.data.db.CameraWithDevice
import com.factory.netra.data.db.DeviceEntity
import com.factory.netra.data.db.Severity
import com.factory.netra.data.prefs.isWorkingTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Garments-factory rules evaluated on every AI frame:
 *  - Restricted area: fabric/accessories store, finished-goods store, server room
 *  - After hours: anyone on the floor outside the shift or on the weekly holiday
 *  - Crowd: unusual gathering (N+ people in one view)
 *  - Workstation unattended: sewing line / QC table empty for X minutes in the shift
 */
class RuleEngine(private val c: AppContainer) {
    private val lastAlert = ConcurrentHashMap<String, Long>()
    private val lastPersonSeen = ConcurrentHashMap<Long, Long>()

    private data class Finding(val type: AlertType, val severity: Severity, val message: String)

    suspend fun evaluate(cam: CameraWithDevice, detections: List<Detection>, frame: Bitmap) {
        val s = c.settings.current
        val now = System.currentTimeMillis()
        val id = cam.camera.id
        val people = detections.people()
        val working = s.isWorkingTime()
        val findings = mutableListOf<Finding>()

        if (people > 0) lastPersonSeen[id] = now

        if ((cam.camera.restricted) && (people > 0)) {
            findings += Finding(AlertType.RESTRICTED_AREA, Severity.HIGH, "$people person(s) inside a restricted area")
        } else if ((!working) && (people > 0)) {
            findings += Finding(AlertType.AFTER_HOURS, Severity.HIGH, "$people person(s) on site outside working hours")
        }
        if (people >= s.crowdThreshold) {
            findings += Finding(AlertType.CROWD, Severity.MEDIUM, "$people people gathered in one area")
        }
        if (cam.camera.monitorIdle && working) {
            val last = lastPersonSeen.getOrPut(id) { now }
            val idleMin = (now - last) / 60_000
            if ((people == 0) && (idleMin >= s.idleMinutes)) {
                findings += Finding(AlertType.LINE_IDLE, Severity.LOW, "No operator seen for $idleMin min")
            }
        }

        for (f in findings) {
            if (!cooldownPassed("$id:${f.type}", s.cooldownMin)) continue
            val annotated = annotate(frame, detections)
            val alert = c.alerts.record(
                cameraId = id, cameraName = cam.camera.name, zone = cam.camera.zone,
                type = f.type, severity = f.severity, source = AlertSource.AI,
                message = f.message, frame = annotated,
            )
            annotated.recycle()
            c.notifier.notify(alert)
        }
    }

    /** Smart events pushed by the Dahua NVR / camera itself. */
    suspend fun raiseRecorderAlert(
        device: DeviceEntity,
        cam: CameraWithDevice?,
        type: AlertType,
        severity: Severity,
        code: String,
    ) {
        val key = "${device.id}:${cam?.camera?.channel ?: 0}:$type"
        if (!cooldownPassed(key, c.settings.current.cooldownMin)) return
        val jpeg = cam?.let { c.dahua.snapshot(c.devices.endpoint(device), it.camera.channel) }
        val alert = c.alerts.record(
            cameraId = cam?.camera?.id,
            cameraName = cam?.camera?.name ?: device.name,
            zone = cam?.camera?.zone ?: "Recorder",
            type = type, severity = severity, source = AlertSource.RECORDER,
            message = "${device.name}: $code", frame = null, jpeg = jpeg,
        )
        c.notifier.notify(alert)
    }

    private fun cooldownPassed(key: String, minutes: Int): Boolean {
        val now = System.currentTimeMillis()
        val last = lastAlert[key] ?: 0L
        if ((now - last) < (minutes * 60_000L)) return false
        lastAlert[key] = now
        return true
    }

    private fun annotate(frame: Bitmap, detections: List<Detection>): Bitmap {
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val stroke = (out.width / 220f).coerceAtLeast(2f)
        val box = Paint().apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        val text = Paint().apply { color = Color.BLACK; textSize = stroke * 7; isAntiAlias = true }
        val tag = Paint().apply { style = Paint.Style.FILL }
        for (d in detections) {
            val color = if (d.label == ObjectDetectorEngine.PERSON) PERSON_COLOR else VEHICLE_COLOR
            box.color = color
            tag.color = color
            canvas.drawRect(d.box, box)
            val label = "${d.label} ${(d.score * 100).toInt()}%"
            val w = text.measureText(label) + (stroke * 2)
            canvas.drawRect(d.box.left, d.box.top - text.textSize - stroke, d.box.left + w, d.box.top, tag)
            canvas.drawText(label, d.box.left + stroke, d.box.top - stroke, text)
        }
        return out
    }

    companion object {
        private val PERSON_COLOR = Color.rgb(242, 208, 107)
        private val VEHICLE_COLOR = Color.rgb(79, 209, 181)
    }
}
