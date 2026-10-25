package com.factory.netra.ui.live

import android.os.Environment
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Hd
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Sd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factory.netra.ai.Detection
import com.factory.netra.ai.ObjectDetectorEngine
import com.factory.netra.ai.people
import com.factory.netra.ai.vehicles
import com.factory.netra.container
import com.factory.netra.data.db.CameraWithDevice
import com.factory.netra.ui.components.RtspVideo
import com.factory.netra.ui.theme.NetraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FullscreenCamera(cameraId: Long, onClose: () -> Unit, onSwitch: (Long) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val cams by remember { c.devices.observeCameras() }
        .collectAsStateWithLifecycle<List<CameraWithDevice>?>(initialValue = null)
    val settings by c.settings.flow.collectAsStateWithLifecycle()

    val list = cams
    if (list == null) {
        Box(Modifier.fillMaxSize().background(NetraColors.Video), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = NetraColors.Chalk)
        }
        return
    }
    val enabled = list.filter { it.camera.enabled }
    val index = enabled.indexOfFirst { it.camera.id == cameraId }
    if (index < 0) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val cam = enabled[index]

    val isPhone = LocalConfiguration.current.screenWidthDp < 600
    var hd by rememberSaveable { mutableStateOf(settings.hdFullscreen && !isPhone) }
    var aiOn by rememberSaveable { mutableStateOf(true) }
    var texture by remember(cameraId, hd) { mutableStateOf<TextureView?>(null) }
    var detections by remember(cameraId) { mutableStateOf<List<Detection>>(emptyList()) }
    var frame by remember(cameraId) { mutableStateOf(IntSize.Zero) }
    val url = remember(cameraId, hd) { c.devices.rtspUrl(cam, hd) }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(cameraId) { runCatching { backFocus.requestFocus() } }

    // Live AI overlay: ~3 frames per second from the playing stream.
    LaunchedEffect(texture, aiOn) {
        detections = emptyList()
        val tv = texture ?: return@LaunchedEffect
        if (!aiOn || !c.detector.available) return@LaunchedEffect
        while (isActive) {
            if (tv.isAvailable && tv.width > 0 && tv.height > 0) {
                val w = 640
                val h = (640f * tv.height / tv.width).toInt().coerceAtLeast(1)
                val bmp = tv.getBitmap(w, h)
                if (bmp != null) {
                    val result = withContext(Dispatchers.Default) {
                        c.detector.detect(bmp, settings.confidence).also { bmp.recycle() }
                    }
                    frame = IntSize(w, h)
                    detections = result
                }
            }
            delay(300)
        }
    }

    Box(Modifier.fillMaxSize().background(NetraColors.Video)) {
        RtspVideo(
            url = url,
            modifier = Modifier.fillMaxSize(),
            useTexture = true,
            onTexture = { texture = it },
            overlay = { if (aiOn) DetectionOverlay(detections, frame) },
        )

        // Top bar: one row on TV/tablet, two rows on a phone (so the name is readable)
        val controls: @Composable () -> Unit = {
            ControlButton(
                Icons.Outlined.Psychology,
                if (aiOn) "Hide AI boxes" else "Show AI boxes",
                active = aiOn,
            ) { aiOn = !aiOn }
            ControlButton(if (hd) Icons.Outlined.Hd else Icons.Outlined.Sd, if (hd) "Switch to SD" else "Switch to HD") {
                hd = !hd
            }
            ControlButton(Icons.Outlined.CameraAlt, "Save snapshot") {
                val bmp = texture?.bitmap
                if (bmp == null) {
                    Toast.makeText(context, "No picture yet", Toast.LENGTH_SHORT).show()
                } else {
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val file = File(dir, "Netra_${cam.camera.name.replace(Regex("[^A-Za-z0-9]+"), "_")}_$stamp.jpg")
                    runCatching {
                        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
                    }
                    bmp.recycle()
                    Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
                }
            }
            ControlButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous camera") {
                onSwitch(enabled[(index - 1 + enabled.size) % enabled.size].camera.id)
            }
            ControlButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next camera") {
                onSwitch(enabled[(index + 1) % enabled.size].camera.id)
            }
        }
        val title: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Text(
                    cam.camera.name, style = MaterialTheme.typography.titleLarge, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${cam.camera.zone} · ${cam.device.name} channel ${cam.camera.channel}",
                    style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val narrow = LocalConfiguration.current.screenWidthDp < 600
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", Modifier.focusRequester(backFocus), onClick = onClose)
                title(Modifier.weight(1f))
                if (!narrow) controls()
            }
            if (narrow) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { controls() }
            }
        }

        // Live counts
        if (aiOn) {
            val p = detections.people()
            val v = detections.vehicles()
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    when {
                        !c.detector.available -> "AI model not installed: Settings → Download AI model"
                        else -> "$p people · $v vehicles in view"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = NetraColors.Chalk,
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (active) NetraColors.Chalk else Color.White.copy(alpha = 0.12f),
            contentColor = if (active) NetraColors.Night else Color.White,
        ),
    ) { Icon(icon, description) }
}

@Composable
private fun DetectionOverlay(detections: List<Detection>, frame: IntSize) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxSize()) {
        if (frame.width == 0 || frame.height == 0) return@Canvas
        val sx = size.width / frame.width
        val sy = size.height / frame.height
        val pad = 4.dp.toPx()
        for (d in detections) {
            val color = if (d.label == ObjectDetectorEngine.PERSON) NetraColors.Chalk else NetraColors.Thread
            val topLeft = Offset(d.box.left * sx, d.box.top * sy)
            drawRect(
                color = color,
                topLeft = topLeft,
                size = Size(d.box.width() * sx, d.box.height() * sy),
                style = Stroke(width = 2.dp.toPx()),
            )
            val text = measurer.measure(
                "${d.label} ${(d.score * 100).toInt()}%",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            val y = (topLeft.y - text.size.height).coerceAtLeast(0f)
            drawRect(color, Offset(topLeft.x, y), Size(text.size.width + pad * 2, text.size.height.toFloat()))
            drawText(text, color = NetraColors.Night, topLeft = Offset(topLeft.x + pad, y))
        }
    }
}

