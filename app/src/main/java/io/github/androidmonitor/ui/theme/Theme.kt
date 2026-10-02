package io.github.androidmonitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** One accent per metric, like Task Manager's colored graphs. */
@Immutable
data class MetricPalette(
    val cpu: Color,
    val memory: Color,
    val gpu: Color,
    val network: Color,
    val thermal: Color,
    /** Highlight behind busy cells in the process list. */
    val heat: Color,
)

private val DarkMetrics = MetricPalette(
    cpu = Color(0xFF5AAEFF),
    memory = Color(0xFFC08CFF),
    gpu = Color(0xFF3FD69B),
    network = Color(0xFFFFAD5C),
    thermal = Color(0xFFFF7A90),
    heat = Color(0xFFFFC145),
)

private val LightMetrics = MetricPalette(
    cpu = Color(0xFF1A6FD6),
    memory = Color(0xFF8A3FD1),
    gpu = Color(0xFF0E8A5A),
    network = Color(0xFFD06A00),
    thermal = Color(0xFFD0384F),
    heat = Color(0xFFF2A900),
)

val LocalMetricPalette = staticCompositionLocalOf { DarkMetrics }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CC4FF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF1A3A5C),
    onPrimaryContainer = Color(0xFFD3E6FF),
    secondaryContainer = Color(0xFF253241),
    onSecondaryContainer = Color(0xFFD7E3F2),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF22272E),
    onSurfaceVariant = Color(0xFF9AA3AF),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF13161B),
    surfaceContainer = Color(0xFF171A20),
    surfaceContainerHigh = Color(0xFF1E2229),
    surfaceContainerHighest = Color(0xFF262B33),
    outline = Color(0xFF3B424C),
    outlineVariant = Color(0xFF262B33),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6FB),
    onPrimaryContainer = Color(0xFF0B3460),
    secondaryContainer = Color(0xFFDCE6F3),
    onSecondaryContainer = Color(0xFF1B2A3A),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF16191D),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF16191D),
    surfaceVariant = Color(0xFFE3E7EC),
    onSurfaceVariant = Color(0xFF5A626D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF0F3),
    surfaceContainerHigh = Color(0xFFE7EAEE),
    surfaceContainerHighest = Color(0xFFE0E4E9),
    outline = Color(0xFFC3C9D1),
    outlineVariant = Color(0xFFDDE1E6),
)

@Composable
fun AndroidMonitorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMetricPalette provides if (darkTheme) DarkMetrics else LightMetrics) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

/** Fixed-width digits so live numbers don't jitter. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
