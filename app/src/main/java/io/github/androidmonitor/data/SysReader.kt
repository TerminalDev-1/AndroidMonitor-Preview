package io.github.androidmonitor.data

import io.github.androidmonitor.privileged.PrivilegedShell
import io.github.androidmonitor.privileged.readFiles
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object SysFs {
    fun read(path: String): String? = when (val result = tryRead(path)) {
        is ReadResult.Found -> result.text
        else -> null
    }

    internal fun tryRead(path: String): ReadResult = try {
        val text = File(path).readText().trim()
        if (text.isEmpty()) ReadResult.Missing else ReadResult.Found(text)
    } catch (e: FileNotFoundException) {
        val message = e.message.orEmpty()
        if ("EACCES" in message || "Permission denied" in message) ReadResult.Denied else ReadResult.Missing
    } catch (e: SecurityException) {
        ReadResult.Denied
    } catch (e: IOException) {
        ReadResult.Missing
    }

    internal sealed interface ReadResult {
        data class Found(val text: String) : ReadResult
        data object Denied : ReadResult
        data object Missing : ReadResult
    }
}

/**
 * Reads /proc and sysfs files directly when Android allows it, and falls back to the
 * privileged shell (when connected) for files the app isn't allowed to open.
 */
class SysReader(private val shell: () -> PrivilegedShell?) {
    private val denied = ConcurrentHashMap.newKeySet<String>()

    suspend fun read(paths: Collection<String>): Map<String, String> {
        val result = HashMap<String, String>()
        val viaShell = ArrayList<String>()
        for (path in paths) {
            if (path in denied) {
                viaShell += path
                continue
            }
            when (val read = SysFs.tryRead(path)) {
                is SysFs.ReadResult.Found -> result[path] = read.text
                SysFs.ReadResult.Denied -> {
                    denied += path
                    viaShell += path
                }
                SysFs.ReadResult.Missing -> Unit
            }
        }
        val privileged = shell()
        if (privileged != null && viaShell.isNotEmpty()) result += privileged.readFiles(viaShell)
        return result
    }
}

internal fun normalizeTemperature(raw: Float): Float = when {
    kotlin.math.abs(raw) >= 1000f -> raw / 1000f // millidegrees (most devices)
    kotlin.math.abs(raw) >= 200f -> raw / 10f // decidegrees
    else -> raw
}

internal fun leadingNumber(text: String): Float? =
    Regex("""-?\d+(\.\d+)?""").find(text)?.value?.toFloatOrNull()
