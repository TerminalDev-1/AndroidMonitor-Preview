package io.github.androidmonitor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.androidmonitor.ui.screens.AccessScreen
import io.github.androidmonitor.ui.screens.PerformanceScreen
import io.github.androidmonitor.ui.screens.ProcessesScreen

enum class Destination(val label: String, val icon: ImageVector) {
    PERFORMANCE("Performance", Icons.Outlined.Speed),
    PROCESSES("Processes", Icons.AutoMirrored.Outlined.ViewList),
    ACCESS("Access", Icons.Outlined.AdminPanelSettings),
}

@Composable
fun AppRoot(vm: MonitorViewModel = viewModel()) {
    var destination by rememberSaveable { mutableStateOf(Destination.PERFORMANCE) }
    // Collected here so graphs keep their history while other tabs are open.
    val monitor by vm.monitor.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.access.refresh() }
    BackHandler(enabled = destination != Destination.PERFORMANCE) { destination = Destination.PERFORMANCE }

    // Surface (not a plain background) so text defaults to onBackground instead of black.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val openAccess = { destination = Destination.ACCESS }
            val content: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    when (destination) {
                        Destination.PERFORMANCE -> PerformanceScreen(monitor, wide, openAccess)
                        Destination.PROCESSES -> ProcessesScreen(vm, monitor, openAccess)
                        Destination.ACCESS -> AccessScreen(vm)
                    }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Spacer(Modifier.height(12.dp))
                        Destination.entries.forEach { d ->
                            NavigationRailItem(
                                selected = destination == d,
                                onClick = { destination = d },
                                icon = { Icon(d.icon, contentDescription = null) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                    content(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom,
                                ),
                            ),
                    )
                }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Destination.entries.forEach { d ->
                                NavigationBarItem(
                                    selected = destination == d,
                                    onClick = { destination = d },
                                    icon = { Icon(d.icon, contentDescription = null) },
                                    label = { Text(d.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
                }
            }
        }
    }
}
