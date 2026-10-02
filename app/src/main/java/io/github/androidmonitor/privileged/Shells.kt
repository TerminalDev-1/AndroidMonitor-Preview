package io.github.androidmonitor.privileged

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuShell(private val service: IShellService) : PrivilegedShell {
    override suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        try {
            service.exec(command)
        } catch (e: Exception) {
            null
        }
    }
}

class RootShell : PrivilegedShell {
    override suspend fun exec(command: String): String? = withContext(Dispatchers.IO) {
        runProcess("su", "-c", command)
    }

    companion object {
        /** Blocks until the root manager answers, so call it off the main thread. */
        fun isAvailable(): Boolean = runProcess("su", "-c", "id")?.contains("uid=0") == true
    }
}
