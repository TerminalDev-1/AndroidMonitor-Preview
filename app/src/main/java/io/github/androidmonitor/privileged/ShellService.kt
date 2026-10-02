package io.github.androidmonitor.privileged

import kotlin.system.exitProcess

/**
 * Runs in a separate process that Shizuku starts with the shell user's (ADB) permissions.
 * The app talks to it over Binder through [IShellService].
 */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = runProcess("sh", "-c", command).orEmpty()
}
