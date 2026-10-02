package io.github.androidmonitor.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.androidmonitor.data.AppInfoCache
import io.github.androidmonitor.data.MonitorState
import io.github.androidmonitor.data.ProcessGroup
import io.github.androidmonitor.data.ProcessListState
import io.github.androidmonitor.ui.MonitorViewModel
import io.github.androidmonitor.ui.components.NoticeCard
import io.github.androidmonitor.ui.components.ScreenTitle
import io.github.androidmonitor.ui.theme.LocalMetricPalette
import io.github.androidmonitor.ui.theme.tabular
import io.github.androidmonitor.ui.util.formatBytes
import io.github.androidmonitor.ui.util.formatPercent
import io.github.androidmonitor.ui.util.formatPercentFine

private val CpuColumnWidth = 84.dp
private val MemoryColumnWidth = 104.dp

private enum class SortBy(val comparator: Comparator<ProcessGroup>) {
    NAME(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }),
    CPU(compareByDescending<ProcessGroup> { it.cpu }.thenByDescending { it.rssBytes }),
    MEMORY(compareByDescending { it.rssBytes }),
}

@Composable
fun ProcessesScreen(vm: MonitorViewModel, monitor: MonitorState?, onOpenAccess: () -> Unit) {
    val state by vm.processes.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Processes", Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp))
        when (val s = state) {
            ProcessListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ProcessListState.NeedsAccess -> Box(Modifier.padding(horizontal = 24.dp)) {
                NoticeCard(
                    icon = Icons.Outlined.Lock,
                    title = "The process list needs Shizuku or root",
                    body = "Android hides other apps' processes from regular apps. Connect Shizuku " +
                        "(no root needed) to see what's using your CPU and memory, and to end tasks.",
                    actionLabel = "Set up access",
                    onAction = onOpenAccess,
                )
            }
            is ProcessListState.Error -> Box(Modifier.padding(horizontal = 24.dp)) {
                NoticeCard(icon = Icons.Outlined.ErrorOutline, title = "Something went wrong", body = s.message)
            }
            is ProcessListState.Ready -> ProcessTable(s, vm, monitor)
        }
    }
}

@Composable
private fun ProcessTable(state: ProcessListState.Ready, vm: MonitorViewModel, monitor: MonitorState?) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.CPU) }
    var showKernel by rememberSaveable { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val totalMemory = monitor?.snapshot?.memory?.totalBytes ?: 0L

    val visible = remember(state, query, sortBy, showKernel) {
        val trimmed = query.trim()
        state.groups
            .filter { (showKernel || !it.isKernel) && (trimmed.isEmpty() || it.matches(trimmed)) }
            .sortedWith(sortBy.comparator)
    }
    val apps = visible.filter { it.isApp }
    val system = visible.filterNot { it.isApp }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search processes") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        FilterChip(selected = showKernel, onClick = { showKernel = !showKernel }, label = { Text("Kernel threads") })
    }
    Text(
        "${state.processCount} processes · ${state.threadCount} threads",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
    )
    HeaderRow(sortBy, { sortBy = it }, monitor)
    HorizontalDivider()
    LazyColumn(Modifier.fillMaxSize()) {
        groupSection("Apps", apps, totalMemory, vm.apps) { selectedKey = it.key }
        groupSection("System processes", system, totalMemory, vm.apps) { selectedKey = it.key }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No matching processes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }

    val selected = selectedKey?.let { key -> state.groups.firstOrNull { it.key == key } }
    if (selected != null) {
        ProcessDialog(
            group = selected,
            canEnd = selected.packageName != null && selected.packageName != context.packageName,
            onDismiss = { selectedKey = null },
            onEnd = {
                selectedKey = null
                vm.endTask(selected) { ok ->
                    val message = if (ok) "Ended ${selected.label}" else "Couldn't end ${selected.label}"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun HeaderRow(sortBy: SortBy, onSort: (SortBy) -> Unit, monitor: MonitorState?) {
    val snapshot = monitor?.snapshot
    Row(Modifier.fillMaxWidth().padding(start = 24.dp), verticalAlignment = Alignment.Bottom) {
        HeaderCell("Name", null, sortBy == SortBy.NAME, Modifier.weight(1f), Alignment.Start) { onSort(SortBy.NAME) }
        HeaderCell(
            "CPU", snapshot?.cpu?.usage?.let(::formatPercent), sortBy == SortBy.CPU,
            Modifier.width(CpuColumnWidth), Alignment.End,
        ) { onSort(SortBy.CPU) }
        HeaderCell(
            "Memory", snapshot?.memory?.usedPercent?.let(::formatPercent), sortBy == SortBy.MEMORY,
            Modifier.width(MemoryColumnWidth), Alignment.End,
        ) { onSort(SortBy.MEMORY) }
    }
}

@Composable
private fun HeaderCell(
    label: String,
    total: String?,
    active: Boolean,
    modifier: Modifier,
    alignment: Alignment.Horizontal,
    onClick: () -> Unit,
) {
    Column(
        modifier.clickable(onClick = onClick).padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
        horizontalAlignment = alignment,
    ) {
        if (total != null) Text(total, style = MaterialTheme.typography.titleMedium.tabular)
        Text(
            if (active) "$label ${if (label == "Name") "↑" else "↓"}" else label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.groupSection(
    title: String,
    groups: List<ProcessGroup>,
    totalMemory: Long,
    apps: AppInfoCache,
    onClick: (ProcessGroup) -> Unit,
) {
    if (groups.isEmpty()) return
    item(key = "section-$title") {
        Text(
            "$title (${groups.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
        )
    }
    items(groups, key = { it.key }) { group -> ProcessRow(group, totalMemory, apps, onClick) }
}

@Composable
private fun ProcessRow(group: ProcessGroup, totalMemory: Long, apps: AppInfoCache, onClick: (ProcessGroup) -> Unit) {
    val heat = LocalMetricPalette.current.heat
    val subtitle = if (group.processes.size > 1) {
        "${group.packageName ?: group.processes.first().name} · ${group.processes.size} processes"
    } else {
        "${group.processes.first().name} · PID ${group.processes.first().pid}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onClick(group) }
            .padding(start = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProcessIcon(group, apps)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(group.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Like Task Manager, busier cells get a warmer background.
        HeatCell(formatPercentFine(group.cpu), heat.copy(alpha = heatAlpha(group.cpu / 25f)), CpuColumnWidth)
        val memoryShare = if (totalMemory > 0) group.rssBytes.toFloat() / totalMemory else 0f
        HeatCell(formatBytes(group.rssBytes), heat.copy(alpha = heatAlpha(memoryShare * 8f)), MemoryColumnWidth)
    }
}

private fun heatAlpha(intensity: Float): Float = 0.05f + 0.40f * intensity.coerceIn(0f, 1f)

@Composable
private fun HeatCell(text: String, background: Color, width: Dp) {
    Box(
        Modifier.width(width).fillMaxHeight().background(background),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium.tabular, modifier = Modifier.padding(end = 16.dp))
    }
}

@Composable
private fun ProcessIcon(group: ProcessGroup, apps: AppInfoCache) {
    val bitmap = remember(group.packageName) { group.packageName?.let(apps::icon) }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(32.dp))
    } else {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (group.isKernel) Icons.Outlined.Memory else Icons.Outlined.Terminal,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProcessDialog(group: ProcessGroup, canEnd: Boolean, onDismiss: () -> Unit, onEnd: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.packageName?.let { KeyValue("Package", it) }
                KeyValue("CPU", formatPercentFine(group.cpu))
                KeyValue("Memory", formatBytes(group.rssBytes))
                KeyValue("Threads", group.threads.toString())
                KeyValue("User", group.processes.first().user)
                Spacer(Modifier.height(4.dp))
                Text("Processes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                group.processes.take(12).forEach { p ->
                    Text(
                        "${p.pid}  ${p.name}",
                        style = MaterialTheme.typography.bodySmall.tabular,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (group.processes.size > 12) {
                    Text("and ${group.processes.size - 12} more", style = MaterialTheme.typography.bodySmall)
                }
                if (canEnd) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "End task force-stops the app. Unsaved work in it may be lost.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (canEnd) {
                TextButton(onClick = onEnd) { Text("End task") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (canEnd) TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium.tabular)
    }
}
