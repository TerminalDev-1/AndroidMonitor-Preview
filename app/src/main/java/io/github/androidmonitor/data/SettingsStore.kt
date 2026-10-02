package io.github.androidmonitor.data

import android.content.Context
import androidx.core.content.edit
import io.github.androidmonitor.privileged.AccessMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _refreshMs = MutableStateFlow(prefs.getLong(KEY_REFRESH_MS, DEFAULT_REFRESH_MS))
    val refreshMs: StateFlow<Long> = _refreshMs.asStateFlow()

    fun setRefreshMs(value: Long) {
        prefs.edit { putLong(KEY_REFRESH_MS, value) }
        _refreshMs.value = value
    }

    var preferredAccess: AccessMode
        get() = prefs.getString(KEY_ACCESS, null)
            ?.let { runCatching { AccessMode.valueOf(it) }.getOrNull() }
            ?: AccessMode.STANDARD
        set(value) = prefs.edit { putString(KEY_ACCESS, value.name) }

    companion object {
        val REFRESH_OPTIONS = listOf(500L, 1_000L, 2_000L, 5_000L)
        private const val DEFAULT_REFRESH_MS = 1_000L
        private const val KEY_REFRESH_MS = "refresh_ms"
        private const val KEY_ACCESS = "access_mode"
    }
}
