package io.github.n2ray.sall;

public final class HostSettingsOptionsTest {
    public static void main(String[] args) {
        assert HostSettingsOptions.canChangeVirtualDisplay(false, true);
        assert !HostSettingsOptions.canChangeVirtualDisplay(false, false);
        assert HostSettingsOptions.canChangeVirtualDisplay(true, false);
        assert HostSettingsOptions.canChangeVirtualDisplay(false, null);
        assert HostSettingsOptions.sendAudioLabel(null).contains("更新 Mac 上的 Sill");
        assert HostSettingsOptions.sendAudioLabel(false).contains("关");
        assert HostSettingsOptions.sendAudioLabel(true).contains("开");
        assert HostSettingsOptions.clipboardNote(false, true, true).contains("未报告");
        assert HostSettingsOptions.clipboardNote(true, false, true).contains("开启后");
        assert HostSettingsOptions.clipboardNote(true, true, true).contains("立即粘贴");
        assert HostSettingsOptions.clipboardNote(true, true, false).contains("自行粘贴");
        assert HostSettingsOptions.gesturesNote(true, 0).contains("未报告");
        assert HostSettingsOptions.gesturesNote(true, 1).contains("快捷键");
        assert HostSettingsOptions.gesturesNote(false, 1).contains("关闭时");
        System.out.println("Host settings availability checks passed");
    }
}
