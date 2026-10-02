package io.github.androidmonitor.privileged

import java.io.File

/** Runs shell commands with more rights than a normal app: Shizuku's shell user, or root. */
interface PrivilegedShell {
    /** Returns the command's stdout, or null if it couldn't be run. */
    suspend fun exec(command: String): String?
}

private const val MARKER = "@@"

/** Reads several files in one shell round trip. Missing or unreadable files are left out. */
suspend fun PrivilegedShell.readFiles(paths: Collection<String>): Map<String, String> {
    if (paths.isEmpty()) return emptyMap()
    val script = paths.joinToString("; ") { "printf '\\n$MARKER%s\\n' '$it'; cat '$it' 2>/dev/null" }
    val output = exec(script) ?: return emptyMap()
    return output.split("\n$MARKER").drop(1).mapNotNull { chunk ->
        val path = chunk.substringBefore('\n')
        val value = chunk.substringAfter('\n', "").trim()
        if (value.isEmpty()) null else path to value
    }.toMap()
}

/** Runs a process and returns its stdout, or null if it couldn't be started. */
internal fun runProcess(vararg argv: String): String? = try {
    val process = ProcessBuilder(*argv)
        .redirectError(File("/dev/null"))
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    output
} catch (e: Exception) {
    null
}
