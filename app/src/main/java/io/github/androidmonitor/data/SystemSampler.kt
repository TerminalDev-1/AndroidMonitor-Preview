package io.github.androidmonitor.data

import android.content.Context
import android.os.SystemClock
import io.github.androidmonitor.privileged.AccessManager
import io.github.androidmonitor.privileged.PrivilegedShell

/** Takes one reading of everything shown on the Performance screen and keeps graph history. */
class SystemSampler(context: Context, private val access: AccessManager) {
    private val reader = SysReader { access.shell }
    private val cpu = CpuReader()
    private val gpu = GpuReader()
    private val thermal = ThermalReader(context)
    private val memory = MemoryReader(context)
    private val battery = BatteryReader(context)
    private val network = NetworkReader(context)
    private val history = HistoryBuffer()
    private var lastShell: PrivilegedShell? = null

    suspend fun sample(): MonitorState {
        val shell = access.shell
        if (shell !== lastShell) {
            lastShell = shell
            cpu.reset()
            thermal.reset()
        }
        val thermalSnapshot = thermal.sample(reader, shell)
        val snapshot = Snapshot(
            cpu = cpu.sample(reader),
            memory = memory.sample(),
            gpu = gpu.sample(reader, thermalSnapshot.gpuTempC),
            network = network.sample(),
            battery = battery.sample(),
            thermal = thermalSnapshot,
            uptimeMs = SystemClock.elapsedRealtime(),
        )
        return MonitorState(snapshot, history.push(snapshot), access.state.value.mode)
    }
}

private class HistoryBuffer {
    private class Series {
        private val values = ArrayDeque<Float>(HISTORY_SIZE + 1)

        fun push(value: Float) {
            values.addLast(value)
            while (values.size > HISTORY_SIZE) values.removeFirst()
        }

        fun toList(): List<Float> = values.toList()
    }

    private val cpu = Series()
    private var cores: List<Series> = emptyList()
    private val memory = Series()
    private val gpu = Series()
    private val netRx = Series()
    private val netTx = Series()
    private val cpuTemp = Series()
    private val batteryTemp = Series()

    fun push(s: Snapshot): History {
        if (cores.size != s.cpu.cores.size) cores = List(s.cpu.cores.size) { Series() }
        cpu.push(s.cpu.usage)
        s.cpu.cores.forEachIndexed { i, core -> cores[i].push(core.usage) }
        memory.push(s.memory.usedPercent)
        gpu.push(s.gpu.usage ?: 0f)
        netRx.push(s.network.rxBytesPerSec.toFloat())
        netTx.push(s.network.txBytesPerSec.toFloat())
        s.thermal.cpuTempC?.let(cpuTemp::push)
        s.battery.tempC?.let(batteryTemp::push)
        return History(
            cpu = cpu.toList(),
            cores = cores.map { it.toList() },
            memory = memory.toList(),
            gpu = gpu.toList(),
            netRx = netRx.toList(),
            netTx = netTx.toList(),
            cpuTemp = cpuTemp.toList(),
            batteryTemp = batteryTemp.toList(),
        )
    }
}
