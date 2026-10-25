package com.factory.netra.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.factory.netra.data.db.Severity
import com.factory.netra.ui.theme.NetraColors
import com.factory.netra.ui.theme.color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Netra's signature: a stitched seam in tailor's-chalk yellow that runs just
 * inside the edge of whatever has D-pad / keyboard focus.
 */
fun Modifier.stitchFocus(focused: Boolean, corner: Dp = 12.dp): Modifier = drawWithContent {
    drawContent()
    if (focused) {
        val inset = 4.dp.toPx()
        val r = (corner.toPx() - inset).coerceAtLeast(2f)
        drawRoundRect(
            color = NetraColors.Chalk,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 5.dp.toPx())),
            ),
        )
    }
}

/** Focusable, clickable panel used for every interactive card (TV remote friendly). */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    container: Color = NetraColors.Panel,
    selected: Boolean = false,
    borderColor: Color = NetraColors.Seam,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "focusScale")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(container)
            .border(1.dp, if (selected) NetraColors.Chalk else borderColor, shape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .stitchFocus(focused, corner),
        content = content,
    )
}

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (online) NetraColors.Thread else NetraColors.Alarm)
    )
}

@Composable
fun SeverityTag(severity: Severity, modifier: Modifier = Modifier) {
    val c = severity.color()
    Text(
        severity.label,
        style = MaterialTheme.typography.labelSmall,
        color = c,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = NetraColors.Text,
    detail: String? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NetraColors.Panel)
            .border(1.dp, NetraColors.Seam, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(value, style = MaterialTheme.typography.displaySmall, color = accent)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NetraColors.TextDim)
        if (detail != null) {
            Spacer(Modifier.padding(top = 2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim, maxLines = 1)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun IconLabel(icon: ImageVector, text: String, color: Color = NetraColors.TextDim) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = NetraColors.Chalk, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = NetraColors.TextDim, modifier = Modifier.width(380.dp))
        action()
    }
}

private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dayFmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

fun formatWhen(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> timeFmt.format(Date(ts))
        else -> dayFmt.format(Date(ts))
    }
}

/**
 * TV / tablet layout (side rail, filled video grid) only when the screen is both
 * wide AND tall enough. A phone in landscape (~850×390 dp) uses the phone layout,
 * otherwise the rail gets cut off and grid tiles become tiny.
 */
@Composable
fun isWideLayout(): Boolean {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return cfg.screenWidthDp >= 720 && cfg.screenHeightDp >= 520
}
