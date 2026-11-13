package io.github.n2ray.sill;

final class HostSettingsOptions {
    private HostSettingsOptions() { }

    static String sendAudioLabel(Boolean sendAudio) {
        if (sendAudio == null) return "Mac 发送声音 · 关（测试版）\n更新 Mac 上的 Sill 以使用声音";
        return "Mac 发送声音 · " + (sendAudio ? "开" : "关") + "（测试版）";
    }

    static boolean canChangeVirtualDisplay(boolean enabled, Boolean available) {
        return enabled || !Boolean.FALSE.equals(available);
    }
}
