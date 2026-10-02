package io.github.androidmonitor.ui.util

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private fun format(pattern: String, vararg args: Any): String = String.format(Locale.getDefault(), pattern, *args)

fun formatPercent(value: Float?): String = value?.let { "${it.roundToInt()}%" } ?: "—"

/** One decimal for small values, like Task Manager's process list. */
fun formatPercentFine(value: Float): String = when {
    value < 0.05f -> "0%"
    value < 10f -> format("%.1f%%", value)
    else -> "${value.roundToInt()}%"
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return format(if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatFrequency(mhz: Int?): String = when {
    mhz == null -> "—"
    mhz >= 1000 -> format("%.2f GHz", mhz / 1000f)
    else -> "$mhz MHz"
}

fun formatTemperature(celsius: Float?): String = celsius?.let { format("%.1f °C", it) } ?: "—"

fun formatPower(watts: Float?): String = watts?.let { format("%.2f W", it) } ?: "—"

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return String.format(
        Locale.US, "%d:%02d:%02d:%02d",
        total / 86_400, total % 86_400 / 3_600, total % 3_600 / 60, total % 60,
    )
}

/** Rounds up to 1, 2 or 5 × 10ⁿ so auto-scaled graphs get tidy labels. */
fun niceCeil(value: Float): Float {
    if (value <= 0f || value.isNaN()) return 1f
    val magnitude = 10.0.pow(floor(log10(value.toDouble())))
    for (step in doubleArrayOf(1.0, 2.0, 5.0, 10.0)) {
        val candidate = step * magnitude
        if (candidate >= value) return candidate.toFloat()
    }
    return (10 * magnitude).toFloat()
}
