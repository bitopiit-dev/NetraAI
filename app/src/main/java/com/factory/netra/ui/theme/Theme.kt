package com.factory.netra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.factory.netra.data.db.Severity

/**
 * "Denim & chalk" palette.
 * Background is indigo-dyed denim; the one loud colour is tailor's chalk yellow,
 * used for focus and primary actions. Teal thread = healthy / online.
 */
object NetraColors {
    val Night = Color(0xFF0F1626)
    val Panel = Color(0xFF172036)
    val PanelHigh = Color(0xFF1F2A45)
    val Seam = Color(0xFF2C3A5C)
    val Chalk = Color(0xFFF2D06B)
    val Thread = Color(0xFF4FD1B5)
    val Alarm = Color(0xFFFF5D5D)
    val Amber = Color(0xFFFFA24C)
    val Slate = Color(0xFF8FA3D9)
    val Text = Color(0xFFE9EDF6)
    val TextDim = Color(0xFF97A3BF)
    val Video = Color(0xFF060A12)
}

fun Severity.color(): Color = when (this) {
    Severity.HIGH -> NetraColors.Alarm
    Severity.MEDIUM -> NetraColors.Amber
    Severity.LOW -> NetraColors.Slate
    Severity.INFO -> NetraColors.TextDim
}

private val scheme = darkColorScheme(
    primary = NetraColors.Chalk,
    onPrimary = NetraColors.Night,
    secondary = NetraColors.Thread,
    onSecondary = NetraColors.Night,
    tertiary = NetraColors.Slate,
    background = NetraColors.Night,
    onBackground = NetraColors.Text,
    surface = NetraColors.Panel,
    onSurface = NetraColors.Text,
    surfaceVariant = NetraColors.PanelHigh,
    onSurfaceVariant = NetraColors.TextDim,
    surfaceContainer = NetraColors.Panel,
    surfaceContainerHigh = NetraColors.PanelHigh,
    surfaceContainerHighest = NetraColors.PanelHigh,
    surfaceContainerLow = NetraColors.Panel,
    outline = NetraColors.Seam,
    outlineVariant = NetraColors.Seam,
    error = NetraColors.Alarm,
)

private val tnum = "tnum"

private val type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFeatureSettings = tnum),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = tnum),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = tnum),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = tnum),
)

@Composable
fun NetraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
