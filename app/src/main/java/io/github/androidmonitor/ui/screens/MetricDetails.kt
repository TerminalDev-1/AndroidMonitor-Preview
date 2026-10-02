package io.github.androidmonitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.androidmonitor.data.CoreInfo
import io.github.androidmonitor.data.CpuUsageSource
import io.github.androidmonitor.data.MemorySnapshot
import io.github.androidmonitor.data.MonitorState
import io.github.androidmonitor.data.ThermalSensor
import io.github.androidmonitor.privileged.AccessMode
import io.github.androidmonitor.ui.components.ChartBlock
import io.github.androidmonitor.ui.components.ChartSeries
import io.github.androidmonitor.ui.components.DetailHeader
import io.github.androidmonitor.ui.components.LegendItem
import io.github.androidmonitor.ui.components.LineChart
import io.github.androidmonitor.ui.components.NoticeCard
import io.github.androidmonitor.ui.components.SectionLabel
import io.github.androidmonitor.ui.components.Stat
import io.github.androidmonitor.ui.components.StatGrid
import io.github.androidmonitor.ui.theme.tabular
import io.github.androidmonitor.ui.util.formatBytes
import io.github.androidmonitor.ui.util.formatDuration
import io.github.androidmonitor.ui.util.formatFrequency
import io.github.androidmonitor.ui.util.formatPercent
import io.github.androidmonitor.ui.util.formatPower
import io.github.androidmonitor.ui.util.formatRate
import io.github.androidmonitor.ui.util.formatTemperature
import io.github.androidmonitor.ui.util.niceCeil
import kotlin.math.roundToInt

@Composable
fun MetricDetail(metric: Metric, state: MonitorState, onOpenAccess: () -> Unit, modifier: Modifier = Modifier) {
    key(metric) {
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (metric) {
                Metric.CPU -> CpuDetail(state, onOpenAccess)
                Metric.MEMORY -> MemoryDetail(state)
                Metric.GPU -> GpuDetail(state, onOpenAccess)
                Metric.NETWORK -> NetworkDetail(state)
                Metric.THERMAL -> ThermalDetail(state, onOpenAccess)
            }
        }
    }
}

private const val CHART_HEIGHT = 220

@Composable
private fun CpuDetail(state: MonitorState, onOpenAccess: () -> Unit) {
    val cpu = state.snapshot.cpu
    val color = Metric.CPU.color()
    DetailHeader("CPU", cpu.socName)
    if (cpu.source == CpuUsageSource.ESTIMATED) {
        NoticeCard(
            icon = Icons.Outlined.Info,
            title = "Estimated from clock speeds",
            body = "Android doesn't let regular apps read real CPU usage. Connect Shizuku or root " +
                "for exact numbers, per-core load, and the process list.",
            actionLabel = "Set up access",
            onAction = onOpenAccess,
        )
    }
    ChartBlock("% Utilization over 60 seconds", "100%") {
        LineChart(
            listOf(ChartSeries(state.history.cpu, color)),
            Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
        )
    }
    SectionLabel(if (cpu.source == CpuUsageSource.KERNEL) "Per-core utilization" else "Per-core load (estimated)")
    CoreGrid(cpu.cores, state.history.cores, color)
    val online = cpu.cores.count { it.online }
    StatGrid(
        listOf(
            Stat("Utilization", if (cpu.warmingUp) "…" else formatPercent(cpu.usage), big = true),
            Stat("Speed", formatFrequency(cpu.peakFreqMhz), big = true),
            Stat("Average speed", formatFrequency(cpu.avgFreqMhz)),
            Stat("Max speed", formatFrequency(cpu.maxFreqMhz)),
            Stat("Cores", "$online of ${cpu.cores.size} online"),
            Stat("Up time", formatDuration(state.snapshot.uptimeMs)),
            Stat(
                "Data source",
                when {
                    cpu.source == CpuUsageSource.ESTIMATED -> "Clock-speed estimate"
                    state.access == AccessMode.ROOT -> "Kernel (root)"
                    else -> "Kernel (Shizuku)"
                },
            ),
        ),
    )
}

@Composable
private fun CoreGrid(cores: List<CoreInfo>, history: List<List<Float>>, color: Color) {
    val columns = if (cores.size <= 4) cores.size.coerceAtLeast(1) else 4
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cores.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { i, core ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LineChart(
                            listOf(ChartSeries(history.getOrElse(rowIndex * columns + i) { emptyList() }, color)),
                            Modifier.fillMaxWidth().height(56.dp),
                            frameColor = color.copy(alpha = 0.6f),
                            gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            "CPU ${core.id}  " + if (core.online) formatFrequency(core.curMhz) else "Offline",
                            style = MaterialTheme.typography.labelSmall.tabular,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MemoryDetail(state: MonitorState) {
    val memory = state.snapshot.memory
    val color = Metric.MEMORY.color()
    DetailHeader("Memory", "${formatBytes(memory.totalBytes)} total")
    ChartBlock("Memory usage", formatBytes(memory.totalBytes)) {
        LineChart(
            listOf(ChartSeries(state.history.memory, color)),
            Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
        )
    }
    SectionLabel("Memory composition")
    CompositionBar(memory, color)
    val swapUsed = memory.swapTotalBytes?.let { total -> total - (memory.swapFreeBytes ?: total) }
    StatGrid(
        listOfNotNull(
            Stat("In use", formatBytes(memory.usedBytes), big = true),
            Stat("Available", formatBytes(memory.availableBytes), big = true),
            memory.cachedBytes?.let { Stat("Cached", formatBytes(it)) },
            if (swapUsed != null && (memory.swapTotalBytes ?: 0) > 0) {
                Stat("Swap (zRAM)", "${formatBytes(swapUsed)} / ${formatBytes(memory.swapTotalBytes ?: 0)}")
            } else {
                null
            },
            Stat(
                "Storage",
                "${formatBytes(memory.storageTotalBytes - memory.storageFreeBytes)} / ${formatBytes(memory.storageTotalBytes)}",
            ),
            Stat("Memory pressure", if (memory.lowMemory) "Low memory" else "Normal"),
        ),
    )
}

@Composable
private fun CompositionBar(memory: MemorySnapshot, color: Color) {
    val total = memory.totalBytes.coerceAtLeast(1)
    val used = memory.usedBytes.toFloat() / total
    val cached = (memory.cachedBytes ?: 0L).coerceAtMost(memory.availableBytes).toFloat() / total
    val free = (1f - used - cached).coerceAtLeast(0f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(36.dp).border(1.dp, color, RectangleShape)) {
            if (used > 0f) Box(Modifier.weight(used).fillMaxHeight().background(color.copy(alpha = 0.55f)))
            if (cached > 0f) Box(Modifier.weight(cached).fillMaxHeight().background(color.copy(alpha = 0.2f)))
            if (free > 0f) Box(Modifier.weight(free).fillMaxHeight())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem("In use", color.copy(alpha = 0.8f))
            LegendItem("Cached", color.copy(alpha = 0.35f))
            LegendItem("Free", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun GpuDetail(state: MonitorState, onOpenAccess: () -> Unit) {
    val gpu = state.snapshot.gpu
    val color = Metric.GPU.color()
    DetailHeader("GPU", gpu.model ?: "GPU")
    if (!gpu.available) {
        val standard = state.access == AccessMode.STANDARD
        NoticeCard(
            icon = Icons.Outlined.Info,
            title = "GPU usage isn't readable",
            body = if (standard) {
                "This device hides GPU counters from regular apps. Connecting Shizuku or root " +
                    "usually unlocks them on Snapdragon (Adreno) devices."
            } else {
                "This GPU driver doesn't expose a usage counter Android Monitor can read yet. " +
                    "Please open an issue with your device model so we can add support."
            },
            actionLabel = if (standard) "Set up access" else null,
            onAction = if (standard) onOpenAccess else null,
        )
    }
    ChartBlock("% Utilization over 60 seconds", "100%") {
        LineChart(
            listOf(ChartSeries(state.history.gpu, color)),
            Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
        )
    }
    StatGrid(
        listOf(
            Stat("Utilization", formatPercent(gpu.usage), big = true),
            Stat("Frequency", formatFrequency(gpu.freqMhz), big = true),
            Stat("Max frequency", formatFrequency(gpu.maxFreqMhz)),
            Stat("Temperature", formatTemperature(gpu.tempC)),
        ),
    )
}

@Composable
private fun NetworkDetail(state: MonitorState) {
    val network = state.snapshot.network
    val history = state.history
    val color = Metric.NETWORK.color()
    val top = niceCeil(maxOf(history.netRx.maxOrNull() ?: 0f, history.netTx.maxOrNull() ?: 0f))
    DetailHeader("Network", network.connection)
    ChartBlock("Throughput", formatRate(top.toLong())) {
        LineChart(
            listOf(
                ChartSeries(history.netRx, color),
                ChartSeries(history.netTx, color, fill = false, dashed = true),
            ),
            Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
            maxValue = top,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendItem("Receive", color)
        LegendItem("Send", color, dashed = true)
    }
    StatGrid(
        listOf(
            Stat("Receive", formatRate(network.rxBytesPerSec), big = true),
            Stat("Send", formatRate(network.txBytesPerSec), big = true),
            Stat("Received since boot", formatBytes(network.totalRxBytes)),
            Stat("Sent since boot", formatBytes(network.totalTxBytes)),
        ),
    )
}

@Composable
private fun ThermalDetail(state: MonitorState, onOpenAccess: () -> Unit) {
    val battery = state.snapshot.battery
    val thermal = state.snapshot.thermal
    val history = state.history
    val color = Metric.THERMAL.color()
    val hasCpuTemp = history.cpuTemp.isNotEmpty()
    val series = buildList {
        if (hasCpuTemp) add(ChartSeries(history.cpuTemp, color))
        add(ChartSeries(history.batteryTemp, color, fill = !hasCpuTemp, dashed = hasCpuTemp))
    }
    val top = niceCeil(series.maxOfOrNull { s -> s.values.maxOrNull() ?: 0f } ?: 0f)

    DetailHeader("Battery & thermal", "${battery.level}% · ${battery.status}")
    ChartBlock("Temperature over 60 seconds", "${top.roundToInt()} °C", footerRight = "0 °C") {
        LineChart(series, Modifier.fillMaxWidth().height(CHART_HEIGHT.dp), maxValue = top)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (hasCpuTemp) LegendItem("CPU", color)
        LegendItem("Battery", color, dashed = hasCpuTemp)
    }
    StatGrid(
        listOfNotNull(
            Stat("Charge", "${battery.level}%", big = true),
            Stat(if (battery.charging) "Charging power" else "Power draw", formatPower(battery.powerW), big = true),
            Stat("Battery", formatTemperature(battery.tempC)),
            Stat("CPU", formatTemperature(thermal.cpuTempC)),
            Stat("GPU", formatTemperature(thermal.gpuTempC)),
            thermal.skinTempC?.let { Stat("Skin", formatTemperature(it)) },
            Stat("Voltage", battery.voltageV?.let { "%.2f V".format(it) } ?: "—"),
            Stat("Current", battery.currentMa?.let { "$it mA" } ?: "—"),
            Stat("Health", battery.health),
            thermal.status?.let { Stat("Thermal status", it) },
        ),
    )
    SensorList(thermal.sensors, state.access, onOpenAccess)
}

@Composable
private fun SensorList(sensors: List<ThermalSensor>, access: AccessMode, onOpenAccess: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Thermal sensors")
        if (sensors.isEmpty()) {
            if (access == AccessMode.STANDARD) {
                NoticeCard(
                    icon = Icons.Outlined.Info,
                    title = "Sensors are hidden",
                    body = "This device hides its temperature sensors from regular apps. Connect Shizuku or root to see them.",
                    actionLabel = "Set up access",
                    onAction = onOpenAccess,
                )
            } else {
                Text(
                    "No thermal sensors found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SensorTable(sensors)
        }
    }
}

@Composable
private fun SensorTable(sensors: List<ThermalSensor>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) sensors else sensors.take(8)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            shown.forEach { sensor ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        sensor.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatTemperature(sensor.tempC), style = MaterialTheme.typography.bodyMedium.tabular)
                }
            }
            if (sensors.size > 8) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text(if (expanded) "Show fewer" else "Show all ${sensors.size}")
                }
            }
        }
    }
}
