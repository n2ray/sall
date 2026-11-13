package io.github.n2ray.sall;

final class HardwareKeyGate {
    static boolean sendRemote(boolean connected, boolean windowFocused, boolean sessionVisible, boolean localEditorFocused) {
        return connected && windowFocused && sessionVisible && !localEditorFocused;
    }
}
