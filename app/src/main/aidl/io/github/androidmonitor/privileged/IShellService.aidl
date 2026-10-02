package io.github.androidmonitor.privileged;

interface IShellService {
    // Reserved by Shizuku: called when the service is unbound and should exit.
    void destroy() = 16777114;

    String exec(String command) = 1;
}
