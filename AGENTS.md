# AGENTS.md

Instructions for AI coding agents (Claude Code, Codex, and others) working on Android Monitor. Humans can follow this too.

Android Monitor is a Task Manager style system monitor for Android, written in Kotlin with Jetpack Compose. It's a **preview project**: no maintenance guarantees and no stable API. Keep changes simple.

## Toolchain

| Tool | Version | Notes |
|---|---|---|
| JDK | **17 or 21** | Gradle 8.11 can't run on Java 24+. If the user only has a newer JDK, install a portable Temurin 21 and set `JAVA_HOME` for the build. Don't change their system Java. |
| Android SDK | `platforms;android-35`, `build-tools;35.0.0`, `platform-tools` | Install with `sdkmanager` from the command-line tools if Android Studio isn't installed. Accepting the SDK licenses is part of setup, so tell the user you're doing it. |
| Gradle | 8.11.1 (wrapper) | Always use `./gradlew`, not a system Gradle. |
| AGP / Kotlin | 8.7.3 / 2.1.0 | Pinned in `gradle/libs.versions.toml`. Don't upgrade unless asked. |

`local.properties` (gitignored) must point to the SDK. On Windows, use forward slashes:

```properties
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
```

## Commands

```bash
./gradlew assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # build and install on the connected device
./gradlew lintDebug            # Android Lint (CI runs this too)
adb devices -l                 # check a device is connected
adb shell am start -n io.github.androidmonitor/.MainActivity
adb logcat -b crash -d         # check for crashes after launching
adb exec-out screencap -p > screen.png   # screenshot to verify UI changes
```

**Windows + Git Bash:** set `MSYS_NO_PATHCONV=1` before `adb shell` commands. Otherwise Git Bash rewrites device paths like `/sys/...` into Windows paths.

After a change, build, install, launch, check the crash log, and take a screenshot. A successful compile isn't enough for UI changes.

## Testing what the app can read

The debug build is `run-as`-able, so you can test file access **with the app's own permissions** without changing code:

```bash
adb shell run-as io.github.androidmonitor cat /sys/class/kgsl/kgsl-3d0/gpubusy   # what Standard mode sees
adb shell cat /sys/class/kgsl/kgsl-3d0/gpubusy                                  # what Shizuku (shell user) sees
```

Access varies a lot by device and Android version. Always check on the real device before assuming a file is readable.

## Architecture

```
app/src/main/java/io/github/androidmonitor/
├── privileged/  AccessManager decides the mode (Standard / Shizuku / Root).
│                ShellService runs in a Shizuku-started process as the shell user;
│                the app calls it over Binder (IShellService.aidl).
├── data/        One reader per metric (CpuReader, GpuReader, ThermalReader, DeviceReaders.kt),
│                SystemSampler (one snapshot + graph history), ProcessSampler (process list).
│                SysReader reads files directly and falls back to the privileged shell
│                only for files Android denies the app.
└── ui/          Compose screens (Performance, Processes, Access), LineChart, theme.
                 MonitorViewModel polls the samplers on the refresh interval.
```

Key rules:
- **Every feature has to work, or fail gracefully, in Standard mode.** When data needs Shizuku or root, show a clear "needs access" message (see `NoticeCard`) instead of zeros or fake numbers.
- **Label estimates as estimates.** Never show a guessed number as if it were measured.
- Read new sysfs/proc files through `SysReader` so the Shizuku/root fallback works automatically.
- Keep privileged shell commands **read-only**. The only exception is `am force-stop` for End task, which is limited to app packages. Don't add commands that kill system processes, change settings, or write to `/sys`.
- Each metric has its own accent color in `ui/theme/Theme.kt`. Reuse `LineChart`, `ChartBlock`, `StatGrid`, and `NoticeCard` so new screens match.

## Adding support for a device

1. Find candidate files: `adb shell ls /sys/class/kgsl/kgsl-3d0 /sys/kernel/gpu /sys/class/devfreq /sys/class/thermal`.
2. Check readability as the app (`run-as`) and as shell (see above).
3. Add the paths to the right reader (for example `GpuReader.ALL_PATHS`) with a short comment naming the device or driver.
4. Build, install, and confirm in the app. Mention the tested device in the commit message.

## Conventions

- Kotlin official code style. Match the surrounding code's naming and comment density.
- UI text is plain and friendly. Explain *why* something is unavailable, and offer the fix.
- Commits: short imperative subject line, with a body explaining why when it isn't obvious.
- CI (`.github/workflows/build.yml`) runs `assembleDebug lintDebug` on every push and pull request, so keep it green.
