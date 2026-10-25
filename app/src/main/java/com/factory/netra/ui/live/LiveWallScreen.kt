package com.factory.netra.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factory.netra.container
import com.factory.netra.ui.components.isWideLayout
import com.factory.netra.data.db.CameraWithDevice
import com.factory.netra.ui.components.EmptyState
import com.factory.netra.ui.components.FocusCard
import com.factory.netra.ui.components.RtspVideo
import com.factory.netra.ui.components.StreamState
import com.factory.netra.ui.components.StatusDot
import com.factory.netra.ui.theme.NetraColors
import kotlinx.coroutines.delay

private val GRID_SIZES = listOf(1, 4, 9, 16)

@Composable
fun LiveWallScreen(
    zone: String?,
    onZoneChange: (String?) -> Unit,
    onOpenCamera: (Long) -> Unit,
) {
    val c = LocalContext.current.container
    val cams by remember { c.devices.observeCameras() }.collectAsStateWithLifecycle(emptyList())
    val settings by c.settings.flow.collectAsStateWithLifecycle()
    val wide = isWideLayout()

    var grid by rememberSaveable { mutableIntStateOf(if (wide) settings.defaultGrid else 4) }
    var page by rememberSaveable(zone, grid) { mutableIntStateOf(0) }
    var tour by rememberSaveable { mutableStateOf(false) }

    val zones = remember(cams) { cams.map { it.camera.zone }.distinct().sorted() }
    val visible = remember(cams, zone) {
        cams.filter { it.camera.enabled && (zone == null || it.camera.zone == zone) }
    }
    val pages = ((visible.size + grid - 1) / grid).coerceAtLeast(1)
    val current = if (page < pages) page else 0
    val pageCams = visible.drop(current * grid).take(grid)

    // Guard tour: flips through every page automatically on the control-room TV.
    LaunchedEffect(tour, pages, grid) {
        while (tour && pages > 1) {
            delay(20_000)
            page = (page.coerceAtMost(pages - 1) + 1) % pages
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ZoneChip("All zones", zone == null) { onZoneChange(null) }
                zones.forEach { z -> ZoneChip(z, zone == z) { onZoneChange(z) } }
            }
            if (wide) {
                GRID_SIZES.forEach { g -> ZoneChip("$g", grid == g) { grid = g } }
                ZoneChip("Tour", tour) { tour = !tour }
            } else {
                ZoneChip(if (grid == 1) "1 view" else "4 views", false) { grid = if (grid == 1) 4 else 1 }
            }
            IconButton(onClick = { page = (current - 1 + pages) % pages }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous page")
            }
            Text("${current + 1} / $pages", style = MaterialTheme.typography.labelMedium, color = NetraColors.TextDim)
            IconButton(onClick = { page = (current + 1) % pages }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next page")
            }
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Outlined.VideocamOff, "No cameras here",
                    "Add a recorder in Devices, or pick another zone.",
                )
            }
            return@Column
        }

        val cols = when {
            grid == 1 -> 1
            !wide -> 2
            grid == 4 -> 2
            grid == 9 -> 3
            else -> 4
        }
        val rows = pageCams.chunked(cols)
        val hd = grid == 1

        if (wide) {
            Column(
                Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val totalRows = (grid + cols - 1) / cols
                for (r in 0 until totalRows) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0 until cols) {
                            val cam = rows.getOrNull(r)?.getOrNull(col)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (cam != null) CameraTile(cam, hd, Modifier.fillMaxSize()) { onOpenCamera(cam.camera.id) }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { cam ->
                            CameraTile(cam, hd, Modifier.weight(1f).aspectRatio(16f / 9f)) { onOpenCamera(cam.camera.id) }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun ZoneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = NetraColors.Panel,
            labelColor = NetraColors.TextDim,
            selectedContainerColor = NetraColors.Chalk,
            selectedLabelColor = NetraColors.Night,
        ),
    )
}

@Composable
private fun CameraTile(cam: CameraWithDevice, hd: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val c = LocalContext.current.container
    val url = remember(cam.camera.id, cam.device.passwordEnc, hd) { c.devices.rtspUrl(cam, hd) }
    FocusCard(onClick = onOpen, modifier = modifier, corner = 10.dp, container = NetraColors.Video) {
        var playing by remember(url) { mutableStateOf(false) }
        RtspVideo(
            url = url,
            modifier = Modifier.fillMaxSize(),
            onState = { playing = it == StreamState.Playing },
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(playing)
            if (cam.camera.restricted) {
                Icon(Icons.Outlined.Lock, "Restricted", tint = NetraColors.Chalk, modifier = Modifier.size(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    cam.camera.name, style = MaterialTheme.typography.titleSmall, color = Color.White,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    cam.camera.zone, style = MaterialTheme.typography.labelSmall, color = NetraColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (cam.camera.lastPeople > 0) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NetraColors.Chalk.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Groups, null, tint = NetraColors.Chalk, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${cam.camera.lastPeople}", style = MaterialTheme.typography.labelSmall, color = NetraColors.Chalk)
                }
            }
        }
    }
}
