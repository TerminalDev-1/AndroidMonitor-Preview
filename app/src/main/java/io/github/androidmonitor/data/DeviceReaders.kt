package io.github.androidmonitor.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import kotlin.math.abs

class MemoryReader(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    fun sample(): MemorySnapshot {
        val info = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val meminfo = SysFs.read("/proc/meminfo")?.let(::parseMeminfo).orEmpty()
        val storage = StatFs(Environment.getDataDirectory().path)
        return MemorySnapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            cachedBytes = meminfo["Cached"]?.times(1024),
            swapTotalBytes = meminfo["SwapTotal"]?.times(1024),
            swapFreeBytes = meminfo["SwapFree"]?.times(1024),
            lowMemory = info.lowMemory,
            storageTotalBytes = storage.totalBytes,
            storageFreeBytes = storage.availableBytes,
        )
    }

    /** Parses "Key:   1234 kB" lines into kB values. */
    private fun parseMeminfo(text: String): Map<String, Long> =
        text.lineSequence().mapNotNull { line ->
            val key = line.substringBefore(':', "").trim()
            val value = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
            if (key.isEmpty() || value == null) null else key to value
        }.toMap()
}

class BatteryReader(private val context: Context) {
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    fun sample(): BatterySnapshot {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale
        else batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it > 0 }?.let { if (it > 100) it / 1000f else it.toFloat() }

        // Most devices report microamps, some report milliamps.
        val rawCurrent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE && it != 0 }
        val currentMa = rawCurrent?.let { if (abs(it) >= 10_000) it / 1000 else it }
        val powerW = if (voltage != null && currentMa != null) voltage * abs(currentMa) / 1000f else null

        return BatterySnapshot(
            level = percent.coerceIn(0, 100),
            status = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                else -> "Unknown"
            },
            charging = charging,
            tempC = temp,
            voltageV = voltage,
            currentMa = currentMa?.let(::abs),
            powerW = powerW,
            health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                else -> "Unknown"
            },
        )
    }
}

class NetworkReader(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTime = 0L

    fun sample(): NetworkSnapshot {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastTime

        fun rate(current: Long, last: Long): Long =
            if (lastTime > 0 && elapsed > 0 && last >= 0 && current >= last) (current - last) * 1000 / elapsed else 0L

        val snapshot = NetworkSnapshot(
            rxBytesPerSec = rate(rx, lastRx),
            txBytesPerSec = rate(tx, lastTx),
            totalRxBytes = rx.coerceAtLeast(0),
            totalTxBytes = tx.coerceAtLeast(0),
            connection = connectionType(),
        )
        lastRx = rx
        lastTx = tx
        lastTime = now
        return snapshot
    }

    private fun connectionType(): String {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return "Offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
    }
}
