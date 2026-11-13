package io.github.n2ray.sall;

final class HostSettingsOptions {
    private HostSettingsOptions() { }

    static String sendAudioLabel(Boolean sendAudio) {
        if (sendAudio == null) return "Mac 发送声音 · 关（测试版）\n更新 Mac 上的 Sill 以使用声音";
        return "Mac 发送声音 · " + (sendAudio ? "开" : "关") + "（测试版）";
    }

    static boolean canChangeVirtualDisplay(boolean enabled, Boolean available) {
        return enabled || !Boolean.FALSE.equals(available);
    }

    static String clipboardNote(boolean available, boolean enabled, boolean pasteRightAway) {
        if (!available) return "Mac 当前未报告支持剪贴板共享。";
        if (!enabled) return "开启后可在手机与 Mac 之间共享剪贴板文本。";
        return "Mac 的剪贴板文本可发送到手机；手机文本需主动确认发送到 Mac。"
                + (pasteRightAway ? "确认后会在 Mac 上立即粘贴。" : "发送后可在 Mac 上自行粘贴。");
    }

    static String gesturesNote(boolean enabled, int generation) {
        if (!enabled) return "关闭时，多指 Mac 手势不发送到 Mac。";
        if (generation < 1) return "Mac 当前未报告支持多指手势，请检查 Mac 上的 Sill 版本。";
        return "在画面或触控板上使用多指手势；Mac 使用自己的键盘快捷键执行操作，已关闭的快捷键不会生效。";
    }
}
