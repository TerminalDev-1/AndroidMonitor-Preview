# Android Monitor

[![Build](https://github.com/TerminalDev-1/AndroidMonitor/actions/workflows/build.yml/badge.svg)](https://github.com/TerminalDev-1/AndroidMonitor/actions/workflows/build.yml)

A modern, open-source system monitor for Android tablets and phones, inspired by the Windows Task Manager.

![Android Monitor on a Xiaomi Pad 6](docs/screenshot-tablet.png)

Live graphs for **CPU, GPU, memory, network, battery and temperatures**, plus a **process list** where you can see what's using your device and end tasks.

## Features

- **Performance view** with live 60-second graphs. On tablets, a sidebar of mini-graphs sits next to a large detail view, just like Task Manager.
- **CPU:** total and per-core utilization, clock speeds, core count, up time, and the SoC name (for example "Snapdragon 8 Gen 2").
- **GPU:** utilization, frequency and temperature (Qualcomm Adreno, plus generic `/sys/kernel/gpu` drivers).
- **Memory:** usage, composition (in use / cached / free), zRAM swap, and storage.
- **Network:** receive and send throughput.
- **Battery & thermal:** charge, power draw, voltage, current, thermal status, and every thermal sensor.
- **Processes:** grouped by app, with icons, CPU and memory "heat" columns, search, sorting, and End task.
- Light and dark themes, and an adjustable refresh rate.

## Why does it need Shizuku?

Since Android 8, regular apps can't read system-wide CPU usage or see other apps' processes. That's why most monitors on the Play Store show broken or fake numbers. Android Monitor has three modes:

| Mode | Setup | What you get |
|---|---|---|
| **Standard** | None | Memory, network, battery, CPU clock speeds, GPU where readable. CPU usage is *estimated* from clock speeds and labelled as an estimate. |
| **Shizuku** (recommended) | Install [Shizuku](https://shizuku.rikka.app/) and start it with wireless debugging | Real CPU usage, per-core load, the process list, End task, all thermal sensors |
| **Root** | A rooted device | Same as Shizuku |

[Shizuku](https://github.com/RikkaApps/Shizuku) is a free, open-source app that lets other apps use ADB-level permissions without root. Android Monitor only uses it to **read** system files (`/proc`, `/sys`) and to run `am force-stop` when you tap End task.

## Building

1. Install [Android Studio](https://developer.android.com/studio) (recent stable version).
2. **File → Open** and choose this folder. Android Studio downloads Gradle and the Android SDK on the first sync.
3. Connect your device with USB debugging on, then press **Run**.

To build from the command line, generate the Gradle wrapper once (Android Studio's terminal works), then:

```bash
./gradlew assembleDebug
```

## Project layout

```
app/src/main/java/io/github/androidmonitor/
├── privileged/   Shizuku and root access (AccessManager, ShellService)
├── data/         Readers for CPU, GPU, memory, thermal, battery, network, processes
└── ui/           Jetpack Compose screens, charts and theme
```

## Contributing

Issues and pull requests are welcome. GPU and thermal file paths vary a lot between devices. If a stat shows "Not available" on your device, please open an issue with your device model and the output of:

```bash
adb shell ls /sys/class/kgsl/kgsl-3d0 /sys/kernel/gpu
```

## License

[MIT](LICENSE)
