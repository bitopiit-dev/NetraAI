package com.factory.netra.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.sync.withLock

data class Detection(val label: String, val score: Float, val box: RectF)

/**
 * On-device object detection (MediaPipe + EfficientDet-Lite0, COCO classes).
 * Runs fully offline on the TV box / phone — no video leaves the factory LAN.
 */
class ObjectDetectorEngine(private val context: Context) {
    private val mutex = Mutex()
    /** Why the model could not be loaded (shown in Settings). */
    @Volatile var loadError: String? = null
        private set
    @Volatile private var detector: ObjectDetector? = create()

    val available: Boolean get() = detector != null

    /** Model downloaded inside the app (used when it was not bundled in assets at build time). */
    val downloadedModel: File get() = File(context.filesDir, MODEL)

    private fun create(): ObjectDetector? = try {
        createInner()
    } catch (t: Throwable) { // LinkageError is not an Exception, catch it too
        loadError = "${t.javaClass.simpleName}: ${t.message}".take(300)
        Log.e("NetraAI", "AI engine failed", t)
        null
    }

    private fun createInner(): ObjectDetector? {
        val fromAsset = runCatching { BaseOptions.builder().setModelAssetPath(MODEL).build() }
            .takeIf { runCatching { context.assets.open(MODEL).close() }.isSuccess }?.getOrNull()
        val base = fromAsset ?: runCatching {
            val f = downloadedModel
            if (!f.exists() || f.length() < 100_000) return null
            val bytes = f.readBytes()
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buffer.put(bytes).rewind()
            BaseOptions.builder().setModelAssetBuffer(buffer).build()
        }.onFailure { loadError = "Model file unreadable: ${it.message}" }.getOrNull() ?: return null

        return runCatching {
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(25)
                .setScoreThreshold(0.3f)
                .setCategoryAllowlist(ALLOW)
                .build()
            ObjectDetector.createFromOptions(context, options).also { loadError = null }
        }.onFailure {
            // UnsatisfiedLinkError here = the AI engine has no native library for this CPU (e.g. some emulators)
            loadError = "${it.javaClass.simpleName}: ${it.message}".take(300)
            Log.e("NetraAI", "AI model not loaded", it)
        }.getOrNull()
    }

    /** Downloads the model over the internet into app storage and loads it. */
    suspend fun downloadAndLoad(http: OkHttpClient): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(context.filesDir, "$MODEL.part")
            http.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Download failed: HTTP ${r.code}")
                val body = r.body ?: throw IOException("Empty download")
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (tmp.length() < 100_000) throw IOException("Downloaded file is too small")
            downloadedModel.delete()
            if (!tmp.renameTo(downloadedModel)) throw IOException("Could not save the model")
            mutex.withLock {
                detector?.close()
                detector = create() ?: throw IOException("Model downloaded but the AI engine failed: $loadError")
            }
        }
    }

    suspend fun detect(bitmap: Bitmap, minScore: Float): List<Detection> {
        val d = detector ?: return emptyList()
        return mutex.withLock { infer(d, bitmap, minScore) }
    }

    private fun infer(d: ObjectDetector, bitmap: Bitmap, minScore: Float): List<Detection> {
        val input = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
        else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val result = runCatching { d.detect(BitmapImageBuilder(input).build()) }.getOrNull()
        if (input !== bitmap) input.recycle()
        val out = ArrayList<Detection>()
        for (det in result?.detections().orEmpty()) {
            val cat = det.categories().firstOrNull() ?: continue
            if (cat.score() < minScore) continue
            val label = if (cat.categoryName() == "person") PERSON else VEHICLE
            out += Detection(label, cat.score(), RectF(det.boundingBox()))
        }
        return out
    }

    companion object {
        const val MODEL = "efficientdet_lite0.tflite"
        const val MODEL_URL =
            "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"
        const val PERSON = "person"
        const val VEHICLE = "vehicle"
        private val ALLOW = listOf("person", "car", "truck", "bus", "motorcycle", "bicycle")
    }
}

fun List<Detection>.people() = count { it.label == ObjectDetectorEngine.PERSON }
fun List<Detection>.vehicles() = count { it.label == ObjectDetectorEngine.VEHICLE }
