package io.github.androidmonitor.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import io.github.androidmonitor.privileged.PrivilegedShell
import java.io.File

class ThermalReader(context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)

    private data class Zone(val path: String, val type: String)

    private var zones: List<Zone>? = null

    /** Re-discovers sensors next time, e.g. after Shizuku or root connects. */
    fun reset() {
        zones = null
    }

    suspend fun sample(reader: SysReader, shell: PrivilegedShell?): ThermalSnapshot {
        val known = zones ?: discover(shell).also { zones = it }
        val temps = reader.read(known.map { "${it.path}/temp" })
        val sensors = known.mapNotNull { zone ->
            val raw = temps["${zone.path}/temp"]?.toFloatOrNull() ?: return@mapNotNull null
            val celsius = normalizeTemperature(raw)
            if (celsius <= 0f || celsius >= 150f) null else ThermalSensor(zone.type, celsius)
        }.sortedByDescending { it.tempC }

        fun hottest(vararg keywords: String) = sensors
            .filter { s -> keywords.any { s.name.contains(it, ignoreCase = true) } }
            .maxOfOrNull { it.tempC }

        return ThermalSnapshot(
            sensors = sensors,
            cpuTempC = hottest("cpu"),
            gpuTempC = hottest("gpu"),
            skinTempC = hottest("skin"),
            status = thermalStatus(),
        )
    }

    private suspend fun discover(shell: PrivilegedShell?): List<Zone> {
        val direct = File(THERMAL_DIR).listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            ?.mapNotNull { dir -> SysFs.read("${dir.path}/type")?.let { Zone(dir.path, it) } }
            .orEmpty()
        if (direct.isNotEmpty() || shell == null) return direct

        val script = "for z in $THERMAL_DIR/thermal_zone*; do " +
            "printf '%s|%s\\n' \"\$z\" \"\$(cat \$z/type 2>/dev/null)\"; done"
        val output = shell.exec(script) ?: return emptyList()
        return output.lines().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            if (parts.size == 2 && parts[1].isNotBlank()) Zone(parts[0].trim(), parts[1].trim()) else null
        }
    }

    private fun thermalStatus(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutting down"
            else -> null
        }
    }

    private companion object {
        const val THERMAL_DIR = "/sys/class/thermal"
    }
}
