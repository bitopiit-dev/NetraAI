package com.factory.netra.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.factory.netra.AppContainer
import com.factory.netra.data.db.CameraWithDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class AiStatus(
    val running: Boolean = false,
    val modelReady: Boolean = true,
    val targets: Int = 0,
    val analyzed: Int = 0,
    val unreachable: Int = 0,
    val peopleOnSite: Int = 0,
    val cycleMs: Long = 0,
    val lastCycleAt: Long = 0,
)

/**
 * Round-robin AI patrol over every AI-enabled camera.
 * 110 cameras cannot be decoded live on one Android device, so each cycle pulls a
 * JPEG snapshot per camera from the NVR (snapshot.cgi), runs detection and rules,
 * then moves on. With 4 workers a full sweep of 110 cameras takes roughly 30–60 s.
 */
class AiScheduler(private val c: AppContainer) {
    private val _status = MutableStateFlow(AiStatus())
    val status: StateFlow<AiStatus> = _status.asStateFlow()
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = _status.value.copy(running = false)
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val s = c.settings.current
            val ready = c.detector.available
            if (!s.aiEnabled || !ready) {
                _status.value = _status.value.copy(running = false, modelReady = ready)
                delay(5_000L)
                continue
            }
            val targets = c.db.cameraDao().aiTargets()
            if (targets.isEmpty()) {
                _status.value = AiStatus(running = true, modelReady = true)
                delay(5_000L)
                continue
            }
            val started = System.currentTimeMillis()
            val gate = Semaphore(s.workers.coerceIn(1, 8))
            val results = coroutineScope {
                targets.map { cam -> async { gate.withPermit { analyze(cam, s.confidence) } } }.awaitAll()
            }
            val ok = results.filterNotNull()
            _status.value = AiStatus(
                running = true,
                modelReady = true,
                targets = targets.size,
                analyzed = ok.size,
                unreachable = results.size - ok.size,
                peopleOnSite = ok.sum(),
                cycleMs = System.currentTimeMillis() - started,
                lastCycleAt = System.currentTimeMillis(),
            )
            c.alerts.pruneIfDue(s.retentionDays)
            delay(s.cycleDelaySec * 1_000L)
        }
    }

    /** @return number of people seen, or null if the camera could not be reached. */
    private suspend fun analyze(cam: CameraWithDevice, confidence: Float): Int? {
        val jpeg = c.dahua.snapshot(c.devices.endpoint(cam.device), cam.camera.channel)
        if (jpeg == null) {
            c.db.cameraDao().setOnline(cam.camera.id, online = false)
            return null
        }
        val bitmap = decodeScaled(jpeg, 640) ?: return null
        return try {
            val detections = c.detector.detect(bitmap, confidence)
            val people = detections.people()
            c.db.cameraDao().updateAnalysis(cam.camera.id, people, System.currentTimeMillis())
            c.rules.evaluate(cam, detections, bitmap)
            people
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaled(jpeg: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while ((bounds.outWidth / (sample * 2)) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
    }
}
