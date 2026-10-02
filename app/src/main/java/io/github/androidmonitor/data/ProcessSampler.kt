package io.github.androidmonitor.data

import android.content.Context
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import io.github.androidmonitor.privileged.AccessManager

/**
 * Builds the process list from /proc/<pid>/stat (CPU time, memory, threads) and `ps`
 * (user and full process name). Needs the privileged shell; Android hides other
 * processes from regular apps.
 */
class ProcessSampler(context: Context, private val access: AccessManager) {
    val apps = AppInfoCache(context)

    private val clockTicks = Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0 } ?: 100L
    private val pageSize = Os.sysconf(OsConstants._SC_PAGESIZE).takeIf { it > 0 } ?: 4096L
    private val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private data class CpuTicks(val startTime: Long, val ticks: Long)

    private var previous: Map<Int, CpuTicks> = emptyMap()
    private var previousAt = 0L

    suspend fun sample(): ProcessListState {
        val shell = access.shell
        if (shell == null) {
            previous = emptyMap()
            previousAt = 0L
            return ProcessListState.NeedsAccess
        }
        val output = shell.exec(COMMAND) ?: return ProcessListState.Error("Couldn't read the process list.")
        val now = SystemClock.elapsedRealtime()
        val names = parsePs(output.substringAfter(PS_MARKER, ""))

        // CPU time available across all cores since the last sample, in clock ticks.
        val elapsedTicks = if (previousAt > 0) (now - previousAt) / 1000.0 * clockTicks * cpuCount else 0.0
        val current = HashMap<Int, CpuTicks>()
        val processes = output.substringBefore(PS_MARKER).lineSequence().mapNotNull(::parseStat).map { stat ->
            val ticks = CpuTicks(stat.startTime, stat.utime + stat.stime)
            current[stat.pid] = ticks
            val before = previous[stat.pid]?.takeIf { it.startTime == stat.startTime }
            val cpu = if (before != null && elapsedTicks > 0) {
                ((ticks.ticks - before.ticks) / elapsedTicks * 100).toFloat().coerceIn(0f, 100f)
            } else {
                0f
            }
            val (user, name) = names[stat.pid] ?: ("?" to stat.comm)
            ProcessInfo(
                pid = stat.pid,
                name = name,
                user = user,
                cpu = cpu,
                rssBytes = stat.rssPages * pageSize,
                threads = stat.threads,
                isKernel = stat.pid == 2 || stat.ppid == 2,
            )
        }.toList()
        previous = current
        previousAt = now

        if (processes.isEmpty()) return ProcessListState.Error("The process list came back empty.")
        return ProcessListState.Ready(
            groups = group(processes),
            processCount = processes.size,
            threadCount = processes.sumOf { it.threads },
        )
    }

    /** Force-stops an app. Returns true if the command succeeded. */
    suspend fun endTask(group: ProcessGroup): Boolean {
        val shell = access.shell ?: return false
        val packageName = group.packageName?.takeIf { PACKAGE_NAME.matches(it) } ?: return false
        return shell.exec("am force-stop $packageName; echo \"exit:\$?\"")?.contains("exit:0") == true
    }

    private fun group(processes: List<ProcessInfo>): List<ProcessGroup> =
        processes.groupBy { proc -> packageOf(proc)?.let { "pkg:$it" } ?: "pid:${proc.pid}" }
            .map { (key, procs) ->
                val packageName = if (key.startsWith("pkg:")) key.removePrefix("pkg:") else null
                ProcessGroup(
                    key = key,
                    label = packageName?.let(apps::label) ?: displayName(procs.first()),
                    packageName = packageName,
                    processes = procs.sortedByDescending { it.cpu },
                    isApp = packageName != null && procs.any { APP_USER.matches(it.user) },
                )
            }

    private fun packageOf(process: ProcessInfo): String? {
        if (process.isKernel) return null
        val candidate = process.name.substringBefore(':')
        return candidate.takeIf { PACKAGE_NAME.matches(it) && apps.label(it) != null }
    }

    private fun displayName(process: ProcessInfo): String =
        if (process.isKernel) process.name else process.name.substringAfterLast('/')

    private data class StatLine(
        val pid: Int,
        val comm: String,
        val ppid: Int,
        val utime: Long,
        val stime: Long,
        val threads: Int,
        val startTime: Long,
        val rssPages: Long,
    )

    /** Parses one /proc/<pid>/stat line. The name is in parentheses and may contain spaces. */
    private fun parseStat(line: String): StatLine? {
        val open = line.indexOf(" (")
        val close = line.lastIndexOf(") ")
        if (open <= 0 || close < open) return null
        val pid = line.substring(0, open).trim().toIntOrNull() ?: return null
        // fields[0] is field 3 (state) in `man proc`, so field N is fields[N - 3].
        val fields = line.substring(close + 2).trim().split(' ')
        if (fields.size < 22) return null
        return StatLine(
            pid = pid,
            comm = line.substring(open + 2, close),
            ppid = fields[1].toIntOrNull() ?: 0,
            utime = fields[11].toLongOrNull() ?: 0,
            stime = fields[12].toLongOrNull() ?: 0,
            threads = fields[17].toIntOrNull() ?: 1,
            startTime = fields[19].toLongOrNull() ?: 0,
            rssPages = fields[21].toLongOrNull() ?: 0,
        )
    }

    /** Parses `ps -A -o PID,USER,NAME` into pid -> (user, name). */
    private fun parsePs(text: String): Map<Int, Pair<String, String>> =
        text.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(WHITESPACE, limit = 3)
            val pid = parts.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
            if (parts.size < 3) null else pid to (parts[1] to parts[2])
        }.toMap()

    private companion object {
        const val PS_MARKER = "@@PS"
        const val COMMAND = "cat /proc/[0-9]*/stat 2>/dev/null; " +
            "printf '\\n$PS_MARKER\\n'; ps -A -o PID,USER,NAME 2>/dev/null"
        val WHITESPACE = Regex("\\s+")
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        val APP_USER = Regex("u\\d+_[ai]\\d+")
    }
}
