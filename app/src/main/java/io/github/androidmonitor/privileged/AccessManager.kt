package io.github.androidmonitor.privileged

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.androidmonitor.BuildConfig
import io.github.androidmonitor.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

enum class AccessMode { STANDARD, SHIZUKU, ROOT }

enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    OUTDATED,
    PERMISSION_NEEDED,
    PERMISSION_DENIED,
    READY,
    CONNECTING,
    CONNECTED,
}

enum class RootStatus { UNKNOWN, CHECKING, AVAILABLE, UNAVAILABLE }

data class AccessState(
    val mode: AccessMode = AccessMode.STANDARD,
    val shizuku: ShizukuStatus = ShizukuStatus.NOT_RUNNING,
    val root: RootStatus = RootStatus.UNKNOWN,
)

/**
 * Decides how the app reads system data. Standard mode needs nothing; Shizuku and root
 * give us a [PrivilegedShell] that can read /proc and sysfs files Android hides from apps.
 */
class AccessManager(private val context: Context, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AccessState())
    val state: StateFlow<AccessState> = _state.asStateFlow()

    /** The active privileged shell, or null in standard mode. */
    @Volatile
    var shell: PrivilegedShell? = null
        private set

    private var shellService: IShellService? = null
    private var binding = false

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder == null || !binder.pingBinder()) {
                refreshShizukuStatus()
                return
            }
            val service = IShellService.Stub.asInterface(binder)
            shellService = service
            if (settings.preferredAccess == AccessMode.SHIZUKU) {
                activate(AccessMode.SHIZUKU, ShizukuShell(service))
            }
            refreshShizukuStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) = onShizukuLost()
    }

    private val onBinderReceived = Shizuku.OnBinderReceivedListener {
        if (settings.preferredAccess == AccessMode.SHIZUKU && hasShizukuPermission()) bindShizuku()
        refreshShizukuStatus()
    }

    private val onBinderDead = Shizuku.OnBinderDeadListener { onShizukuLost() }

    private val onPermissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            settings.preferredAccess = AccessMode.SHIZUKU
            bindShizuku()
        }
        refreshShizukuStatus()
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
        Shizuku.addBinderDeadListener(onBinderDead)
        Shizuku.addRequestPermissionResultListener(onPermissionResult)
        refreshShizukuStatus()
        if (settings.preferredAccess == AccessMode.ROOT) useRoot()
    }

    /** Re-checks Shizuku, e.g. when the user comes back from starting it. */
    fun refresh() = refreshShizukuStatus()

    fun useShizuku() {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> Unit
            hasShizukuPermission() -> {
                settings.preferredAccess = AccessMode.SHIZUKU
                bindShizuku()
            }
            else -> Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
        refreshShizukuStatus()
    }

    fun useRoot() {
        _state.update { it.copy(root = RootStatus.CHECKING) }
        scope.launch {
            val available = withContext(Dispatchers.IO) { RootShell.isAvailable() }
            _state.update { it.copy(root = if (available) RootStatus.AVAILABLE else RootStatus.UNAVAILABLE) }
            if (available) {
                settings.preferredAccess = AccessMode.ROOT
                unbindShizuku()
                activate(AccessMode.ROOT, RootShell())
            } else if (_state.value.mode == AccessMode.ROOT) {
                deactivate()
            }
        }
    }

    fun useStandard() {
        settings.preferredAccess = AccessMode.STANDARD
        unbindShizuku()
        deactivate()
    }

    fun shizukuLaunchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

    private fun hasShizukuPermission(): Boolean =
        Shizuku.pingBinder() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private fun bindShizuku() {
        if (shellService != null || binding) return
        binding = runCatching { Shizuku.bindUserService(serviceArgs, connection) }.isSuccess
    }

    private fun unbindShizuku() {
        if (shellService != null || binding) {
            runCatching { Shizuku.unbindUserService(serviceArgs, connection, true) }
        }
        shellService = null
        binding = false
        refreshShizukuStatus()
    }

    private fun onShizukuLost() {
        shellService = null
        binding = false
        if (_state.value.mode == AccessMode.SHIZUKU) deactivate()
        refreshShizukuStatus()
    }

    private fun activate(mode: AccessMode, shell: PrivilegedShell) {
        this.shell = shell
        _state.update { it.copy(mode = mode) }
    }

    private fun deactivate() {
        shell = null
        _state.update { it.copy(mode = AccessMode.STANDARD) }
    }

    private fun refreshShizukuStatus() {
        val status = when {
            shellService != null -> ShizukuStatus.CONNECTED
            !Shizuku.pingBinder() ->
                if (isShizukuInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
            Shizuku.isPreV11() -> ShizukuStatus.OUTDATED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                if (binding) ShizukuStatus.CONNECTING else ShizukuStatus.READY
            Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.PERMISSION_DENIED
            else -> ShizukuStatus.PERMISSION_NEEDED
        }
        _state.update { it.copy(shizuku = status) }
    }

    private fun isShizukuInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_REQUEST_CODE = 1001
    }
}
