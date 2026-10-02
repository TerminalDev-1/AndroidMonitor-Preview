package io.github.androidmonitor.data

import android.os.Build

class CpuReader {
    private val coreIds: List<Int> = parseCpuList(SysFs.read("$CPU_DIR/possible"))
        ?: List(Runtime.getRuntime().availableProcessors()) { it }

    private var minKhz: List<Long?> = emptyList()
    private var maxKhz: List<Long?> = emptyList()
    private var previous: Map<String, CpuTimes>? = null

    private var socName: String = readSocName(null)

    /** Forgets kernel counters, e.g. after the access mode changes. */
    fun reset() {
        previous = null
    }

    suspend fun sample(reader: SysReader): CpuSnapshot {
        if (maxKhz.all { it == null }) {
            loadLimits(reader)
            socName = readSocName(maxKhz.filterNotNull().maxOrNull())
        }

        val values = reader.read(coreIds.map(::curFreqPath) + PROC_STAT)
        val curKhz = coreIds.map { values[curFreqPath(it)]?.toLongOrNull() }
        val times = values[PROC_STAT]?.let(::parseProcStat)?.takeIf { "cpu" in it }
        val before = previous
        previous = times

        val cores: List<CoreInfo>
        val usage: Float
        val source: CpuUsageSource
        if (times != null) {
            source = CpuUsageSource.KERNEL
            cores = coreIds.mapIndexed { i, id ->
                CoreInfo(
                    id = id,
                    online = "cpu$id" in times,
                    usage = before?.let { usageBetween(it["cpu$id"], times["cpu$id"]) } ?: 0f,
                    curMhz = curKhz[i]?.let { (it / 1000).toInt() },
                    maxMhz = maxKhz.getOrNull(i)?.let { (it / 1000).toInt() },
                )
            }
            usage = before?.let { usageBetween(it["cpu"], times["cpu"]) } ?: 0f
        } else {
            source = CpuUsageSource.ESTIMATED
            cores = coreIds.mapIndexed { i, id ->
                val cur = curKhz[i]
                val min = minKhz.getOrNull(i)
                val max = maxKhz.getOrNull(i)
                val estimate = if (cur != null && min != null && max != null && max > min) {
                    ((cur - min).toFloat() / (max - min) * 100f).coerceIn(0f, 100f)
                } else {
                    0f
                }
                CoreInfo(
                    id = id,
                    online = cur != null,
                    usage = estimate,
                    curMhz = cur?.let { (it / 1000).toInt() },
                    maxMhz = max?.let { (it / 1000).toInt() },
                )
            }
            val online = cores.filter { it.online }
            usage = if (online.isEmpty()) 0f else online.map { it.usage }.average().toFloat()
        }

        val freqs = cores.mapNotNull { it.curMhz }
        return CpuSnapshot(
            usage = usage,
            source = source,
            warmingUp = source == CpuUsageSource.KERNEL && before == null,
            cores = cores,
            socName = socName,
            avgFreqMhz = freqs.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            peakFreqMhz = freqs.maxOrNull(),
            maxFreqMhz = cores.mapNotNull { it.maxMhz }.maxOrNull(),
        )
    }

    private suspend fun loadLimits(reader: SysReader) {
        val limits = reader.read(coreIds.flatMap { listOf(minFreqPath(it), maxFreqPath(it)) })
        minKhz = coreIds.map { limits[minFreqPath(it)]?.toLongOrNull() }
        maxKhz = coreIds.map { limits[maxFreqPath(it)]?.toLongOrNull() }
    }

    private data class CpuTimes(val idle: Long, val total: Long)

    private fun parseProcStat(text: String): Map<String, CpuTimes> =
        text.lineSequence()
            .filter { it.startsWith("cpu") }
            .mapNotNull { line ->
                val parts = line.trim().split(WHITESPACE)
                val numbers = parts.drop(1).take(8).map { it.toLongOrNull() ?: 0L }
                if (numbers.size < 5) null
                else parts[0] to CpuTimes(idle = numbers[3] + numbers[4], total = numbers.sum())
            }
            .toMap()

    private fun usageBetween(old: CpuTimes?, new: CpuTimes?): Float? {
        if (old == null || new == null) return null
        val total = new.total - old.total
        if (total <= 0) return null
        val idle = (new.idle - old.idle).coerceIn(0, total)
        return ((1f - idle.toFloat() / total) * 100f).coerceIn(0f, 100f)
    }

    companion object {
        private const val CPU_DIR = "/sys/devices/system/cpu"
        private const val PROC_STAT = "/proc/stat"
        private val WHITESPACE = Regex("\\s+")

        private fun curFreqPath(core: Int) = "$CPU_DIR/cpu$core/cpufreq/scaling_cur_freq"
        private fun minFreqPath(core: Int) = "$CPU_DIR/cpu$core/cpufreq/cpuinfo_min_freq"
        private fun maxFreqPath(core: Int) = "$CPU_DIR/cpu$core/cpufreq/cpuinfo_max_freq"

        /** Parses kernel CPU lists like "0-7" or "0-3,6". */
        internal fun parseCpuList(text: String?): List<Int>? {
            if (text.isNullOrBlank()) return null
            return text.split(',').flatMap { part ->
                val bounds = part.trim().split('-').mapNotNull { it.toIntOrNull() }
                when (bounds.size) {
                    1 -> listOf(bounds[0])
                    2 -> (bounds[0]..bounds[1]).toList()
                    else -> emptyList()
                }
            }.takeIf { it.isNotEmpty() }
        }

        private val SNAPDRAGON_NAMES = mapOf(
            "SM8750" to "Snapdragon 8 Elite",
            "SM8650" to "Snapdragon 8 Gen 3",
            "SM8635" to "Snapdragon 8s Gen 3",
            "SM8550" to "Snapdragon 8 Gen 2",
            "SM8475" to "Snapdragon 8+ Gen 1",
            "SM8450" to "Snapdragon 8 Gen 1",
            "SM8350" to "Snapdragon 888",
            "SM8250" to "Snapdragon 865",
            "SM8150" to "Snapdragon 855",
            "SM7675" to "Snapdragon 7+ Gen 3",
            "SM7550" to "Snapdragon 7 Gen 3",
            "SM7475" to "Snapdragon 7+ Gen 2",
            "SM7450" to "Snapdragon 7 Gen 1",
            "SM7325" to "Snapdragon 778G",
            "SM7150" to "Snapdragon 730",
            "SM6375" to "Snapdragon 695",
            "SM6225" to "Snapdragon 680",
        )

        /** [maxKhz] tells apart chips that share a model number, like the 865, 865+ and 870. */
        private fun readSocName(maxKhz: Long?): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val model = Build.SOC_MODEL
                if (model.isNotBlank() && model != Build.UNKNOWN) {
                    if (model.equals("SM8250", ignoreCase = true) && maxKhz != null) {
                        when {
                            maxKhz >= 3_150_000 -> return "Snapdragon 870"
                            maxKhz >= 3_000_000 -> return "Snapdragon 865+"
                        }
                    }
                    SNAPDRAGON_NAMES[model.uppercase()]?.let { return it }
                    val maker = Build.SOC_MANUFACTURER.let { if (it == "QTI") "Qualcomm" else it }
                    return listOf(maker, model)
                        .filter { it.isNotBlank() && it != Build.UNKNOWN }
                        .joinToString(" ")
                }
            }
            return Build.HARDWARE
        }
    }
}
