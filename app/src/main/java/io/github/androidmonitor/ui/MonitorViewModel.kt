package io.github.androidmonitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.androidmonitor.MonitorApp
import io.github.androidmonitor.data.AppInfoCache
import io.github.androidmonitor.data.MonitorState
import io.github.androidmonitor.data.ProcessGroup
import io.github.androidmonitor.data.ProcessListState
import io.github.androidmonitor.data.ProcessSampler
import io.github.androidmonitor.data.SettingsStore
import io.github.androidmonitor.data.SystemSampler
import io.github.androidmonitor.privileged.AccessManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MonitorApp
    val access: AccessManager = app.access
    val settings: SettingsStore = app.settings

    private val systemSampler = SystemSampler(application, access)
    private val processSampler = ProcessSampler(application, access)
    val apps: AppInfoCache get() = processSampler.apps

    /** Live readings. Polling pauses while the app is in the background. */
    val monitor: StateFlow<MonitorState?> = poll { systemSampler.sample() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Only polled while the Processes screen is on screen. */
    val processes: StateFlow<ProcessListState> = poll(minIntervalMs = 1_000) { processSampler.sample() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), ProcessListState.Loading)

    fun endTask(group: ProcessGroup, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(processSampler.endTask(group)) }
    }

    private fun <T> poll(minIntervalMs: Long = 0, sample: suspend () -> T): Flow<T> = flow {
        while (true) {
            emit(sample())
            delay(maxOf(settings.refreshMs.value, minIntervalMs))
        }
    }.flowOn(Dispatchers.Default)
}
