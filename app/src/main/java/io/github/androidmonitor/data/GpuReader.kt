package io.github.androidmonitor.data

/**
 * Reads GPU load from the driver's sysfs files. Qualcomm Adreno (kgsl) is well supported;
 * some Exynos/Tensor/MediaTek kernels expose the generic /sys/kernel/gpu files instead.
 */
class GpuReader {
    suspend fun sample(reader: SysReader, thermalGpuTemp: Float?): GpuSnapshot {
        val v = reader.read(ALL_PATHS)
        val usage = v[KGSL_BUSY_PERCENT]?.let(::leadingNumber)
            ?: v[KGSL_GPUBUSY]?.let(::busyRatio)
            ?: v[GENERIC_BUSY]?.let(::leadingNumber)
        val freq = toMhz(v[KGSL_DEVFREQ_CUR] ?: v[KGSL_CLK] ?: v[GENERIC_CLOCK])
        val maxFreq = toMhz(v[KGSL_DEVFREQ_MAX] ?: v[KGSL_MAX_CLK] ?: v[GENERIC_MAX_CLOCK])
        val model = (v[KGSL_MODEL] ?: v[GENERIC_MODEL])?.let(::prettyModel)
        val temp = v[KGSL_TEMP]?.toFloatOrNull()?.let(::normalizeTemperature) ?: thermalGpuTemp
        return GpuSnapshot(
            available = usage != null,
            usage = usage?.coerceIn(0f, 100f),
            freqMhz = freq,
            maxFreqMhz = maxFreq,
            model = model,
            tempC = temp,
        )
    }

    /** kgsl "gpubusy" holds "busy total" cycle counts for the last sampling window. */
    private fun busyRatio(text: String): Float? {
        val numbers = text.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        if (numbers.size < 2) return null
        return if (numbers[1] > 0) numbers[0] * 100f / numbers[1] else 0f
    }

    private fun toMhz(text: String?): Int? {
        val value = text?.let(::leadingNumber)?.toLong() ?: return null
        if (value <= 0) return null
        return when {
            value > 10_000_000 -> (value / 1_000_000).toInt() // Hz
            value > 10_000 -> (value / 1_000).toInt() // kHz
            else -> value.toInt() // MHz
        }
    }

    private fun prettyModel(raw: String): String {
        Regex("""Adreno\D*(\d+)""", RegexOption.IGNORE_CASE).find(raw)?.let {
            return "Adreno ${it.groupValues[1]}"
        }
        return raw.trim()
    }

    private companion object {
        const val KGSL = "/sys/class/kgsl/kgsl-3d0"
        const val KGSL_BUSY_PERCENT = "$KGSL/gpu_busy_percentage"
        const val KGSL_GPUBUSY = "$KGSL/gpubusy"
        const val KGSL_DEVFREQ_CUR = "$KGSL/devfreq/cur_freq"
        const val KGSL_DEVFREQ_MAX = "$KGSL/devfreq/max_freq"
        const val KGSL_CLK = "$KGSL/gpuclk"
        const val KGSL_MAX_CLK = "$KGSL/max_gpuclk"
        const val KGSL_MODEL = "$KGSL/gpu_model"
        const val KGSL_TEMP = "$KGSL/temp"

        const val GENERIC = "/sys/kernel/gpu"
        const val GENERIC_BUSY = "$GENERIC/gpu_busy"
        const val GENERIC_CLOCK = "$GENERIC/gpu_clock"
        const val GENERIC_MAX_CLOCK = "$GENERIC/gpu_max_clock"
        const val GENERIC_MODEL = "$GENERIC/gpu_model"

        val ALL_PATHS = listOf(
            KGSL_BUSY_PERCENT, KGSL_GPUBUSY, KGSL_DEVFREQ_CUR, KGSL_DEVFREQ_MAX, KGSL_CLK,
            KGSL_MAX_CLK, KGSL_MODEL, KGSL_TEMP,
            GENERIC_BUSY, GENERIC_CLOCK, GENERIC_MAX_CLOCK, GENERIC_MODEL,
        )
    }
}
