package io.github.androidmonitor

import android.app.Application
import io.github.androidmonitor.data.SettingsStore
import io.github.androidmonitor.privileged.AccessManager

class MonitorApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var access: AccessManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        access = AccessManager(this, settings)
    }
}
