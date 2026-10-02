package io.github.androidmonitor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.androidmonitor.data.CpuUsageSource
import io.github.androidmonitor.data.History
import io.github.androidmonitor.data.MonitorState
import io.github.androidmonitor.data.Snapshot
import io.github.androidmonitor.ui.components.ChartSeries
import io.github.androidmonitor.ui.components.LineChart
import io.github.androidmonitor.ui.components.ScreenTitle
import io.github.androidmonitor.ui.theme.tabular
import io.github.androidmonitor.ui.util.formatBytes
import io.github.androidmonitor.ui.util.formatFrequency
import io.github.androidmonitor.ui.util.formatPercent
import io.github.androidmonitor.ui.util.formatRate
import io.github.androidmonitor.ui.util.formatTemperature

@Composable
fun PerformanceScreen(state: MonitorState?, wide: Boolean, onOpenAccess: () -> Unit) {
    if (state == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var selected by rememberSaveable { mutableStateOf<Metric?>(null) }

    if (wide) {
        // Tablet: Task Manager layout with the metric list on the left and details on the right.
        val current = selected ?: Metric.CPU
        Row(Modifier.fillMaxSize()) {
            MetricList(
                state = state,
                selected = current,
                onSelect = { selected = it },
                modifier = Modifier.width(300.dp).fillMaxHeight(),
            )
            VerticalDivider()
            MetricDetail(current, state, onOpenAccess, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        val current = selected
        if (current == null) {
            MetricList(state = state, selected = null, onSelect = { selected = it }, modifier = Modifier.fillMaxSize())
        } else {
            BackHandler { selected = null }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text("Performance", style = MaterialTheme.typography.titleMedium)
                }
                MetricDetail(current, state, onOpenAccess, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricList(state: MonitorState, selected: Metric?, onSelect: (Metric) -> Unit, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ScreenTitle("Performance", Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
        Metric.entries.forEach { metric ->
            MetricTile(metric, state, metric == selected) { onSelect(metric) }
        }
    }
}

@Composable
private fun MetricTile(metric: Metric, state: MonitorState, selected: Boolean, onClick: () -> Unit) {
    val color = metric.color()
    val (values, max) = tileSeries(metric, state.history)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LineChart(
                series = listOf(ChartSeries(values, color)),
                modifier = Modifier.size(width = 76.dp, height = 46.dp),
                maxValue = max,
                showGrid = false,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(metric.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                tileSummary(metric, state.snapshot).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.tabular,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Values and scale for a tile's sparkline (null scale = auto). */
private fun tileSeries(metric: Metric, history: History): Pair<List<Float>, Float?> = when (metric) {
    Metric.CPU -> history.cpu to 100f
    Metric.MEMORY -> history.memory to 100f
    Metric.GPU -> history.gpu to 100f
    Metric.NETWORK -> history.netRx.zip(history.netTx) { rx, tx -> maxOf(rx, tx) } to null
    Metric.THERMAL -> history.cpuTemp.ifEmpty { history.batteryTemp } to null
}

private fun tileSummary(metric: Metric, s: Snapshot): List<String> = when (metric) {
    Metric.CPU -> listOfNotNull(
        "${formatPercent(s.cpu.usage)}  ${formatFrequency(s.cpu.peakFreqMhz)}",
        if (s.cpu.source == CpuUsageSource.ESTIMATED) "Estimated" else null,
    )
    Metric.MEMORY -> listOf(
        "${formatBytes(s.memory.usedBytes)} / ${formatBytes(s.memory.totalBytes)} (${formatPercent(s.memory.usedPercent)})",
    )
    Metric.GPU -> listOf(
        when {
            !s.gpu.available -> "Not available"
            s.gpu.freqMhz != null -> "${formatPercent(s.gpu.usage)}  ${formatFrequency(s.gpu.freqMhz)}"
            else -> formatPercent(s.gpu.usage)
        },
    )
    Metric.NETWORK -> listOf(
        "↓ ${formatRate(s.network.rxBytesPerSec)}",
        "↑ ${formatRate(s.network.txBytesPerSec)}",
    )
    Metric.THERMAL -> listOfNotNull(
        "${s.battery.level}%  ${formatTemperature(s.battery.tempC)}",
        s.thermal.cpuTempC?.let { "CPU ${formatTemperature(it)}" },
    )
}
