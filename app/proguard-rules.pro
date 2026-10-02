# Shizuku creates the shell service by class name in its own process.
-keep class io.github.androidmonitor.privileged.ShellService { <init>(...); }
-keep class io.github.androidmonitor.privileged.IShellService** { *; }
