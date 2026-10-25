package com.factory.netra.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.factory.netra.container
import com.factory.netra.ui.components.isWideLayout
import com.factory.netra.data.db.AlertEntity
import com.factory.netra.data.db.AlertSource
import com.factory.netra.data.db.Severity
import com.factory.netra.ui.components.EmptyState
import com.factory.netra.ui.components.FocusCard
import com.factory.netra.ui.components.SeverityTag
import com.factory.netra.ui.components.formatWhen
import com.factory.netra.ui.theme.NetraColors
import com.factory.netra.ui.theme.color
import kotlinx.coroutines.launch
import java.io.File

private enum class Filter(val label: String) { Open("Needs review"), High("High priority"), All("All") }

@Composable
fun AlertsScreen(onOpenCamera: (Long) -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val alerts by remember { c.alerts.observeRecent(500) }.collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableStateOf(Filter.Open) }
    var detail by remember { mutableStateOf<AlertEntity?>(null) }
    val wide = isWideLayout()

    val shown = remember(alerts, filter) {
        when (filter) {
            Filter.Open -> alerts.filter { !it.acknowledged }
            Filter.High -> alerts.filter { it.severity == Severity.HIGH }
            Filter.All -> alerts
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Alerts", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = NetraColors.Panel,
                        selectedContainerColor = NetraColors.Chalk,
                        selectedLabelColor = NetraColors.Night,
                    ),
                )
            }
            if (wide) {
                OutlinedButton(onClick = { scope.launch { c.alerts.acknowledgeAll() } }) {
                    Icon(Icons.Outlined.DoneAll, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark all reviewed")
                }
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Outlined.TaskAlt,
                    if (filter == Filter.Open) "All caught up" else "No alerts",
                    "New AI and recorder alerts appear here and as notifications.",
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = if (wide) 28.dp else 16.dp, end = if (wide) 28.dp else 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(shown, key = { it.id }) { a -> AlertRow(a) { detail = a } }
        }
    }

    detail?.let { a ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = NetraColors.Panel,
            title = { Text(a.type.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Snapshot(a.snapshotPath, Modifier.widthIn(max = 760.dp).fillMaxWidth().aspectRatio(16f / 9f))
                    Text("${a.cameraName} · ${a.zone}", style = MaterialTheme.typography.titleSmall)
                    Text(a.message, color = NetraColors.TextDim)
                    Text(
                        "${formatWhen(a.createdAt)} · " +
                            if (a.source == AlertSource.AI) "Detected by Netra AI" else "Reported by the recorder",
                        style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.alerts.acknowledge(a.id) }
                    detail = null
                }) { Text(if (a.acknowledged) "Close" else "Mark reviewed") }
            },
            dismissButton = {
                if (a.cameraId != null) {
                    TextButton(onClick = {
                        detail = null
                        onOpenCamera(a.cameraId)
                    }) { Text("Watch live") }
                }
            },
        )
    }
}

@Composable
private fun AlertRow(a: AlertEntity, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 72.dp).background(a.severity.color()))
            Spacer(Modifier.width(12.dp))
            Snapshot(a.snapshotPath, Modifier.width(128.dp).aspectRatio(16f / 9f))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    a.type.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (a.acknowledged) NetraColors.TextDim else NetraColors.Text,
                )
                Text(
                    "${a.cameraName} · ${a.zone}", style = MaterialTheme.typography.bodyMedium,
                    color = NetraColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    a.message, style = MaterialTheme.typography.bodySmall,
                    color = NetraColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SeverityTag(a.severity)
                Text(formatWhen(a.createdAt), style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim)
            }
        }
    }
}

@Composable
private fun Snapshot(path: String?, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(NetraColors.Video),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = "Alert snapshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Outlined.ImageNotSupported, null, tint = NetraColors.TextDim)
        }
    }
}
