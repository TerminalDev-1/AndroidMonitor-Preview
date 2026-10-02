package io.github.androidmonitor.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.androidmonitor.ui.theme.LocalMetricPalette

enum class Metric(val title: String, val icon: ImageVector) {
    CPU("CPU", Icons.Outlined.DeveloperBoard),
    MEMORY("Memory", Icons.Outlined.Memory),
    GPU("GPU", Icons.Outlined.VideogameAsset),
    NETWORK("Network", Icons.Outlined.SwapVert),
    THERMAL("Battery & thermal", Icons.Outlined.Thermostat),
}

@Composable
@ReadOnlyComposable
fun Metric.color(): Color {
    val palette = LocalMetricPalette.current
    return when (this) {
        Metric.CPU -> palette.cpu
        Metric.MEMORY -> palette.memory
        Metric.GPU -> palette.gpu
        Metric.NETWORK -> palette.network
        Metric.THERMAL -> palette.thermal
    }
}
