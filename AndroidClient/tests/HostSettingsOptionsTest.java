package io.github.n2ray.sill;

public final class HostSettingsOptionsTest {
    public static void main(String[] args) {
        assert HostSettingsOptions.canChangeVirtualDisplay(false, true);
        assert !HostSettingsOptions.canChangeVirtualDisplay(false, false);
        assert HostSettingsOptions.canChangeVirtualDisplay(true, false);
        assert HostSettingsOptions.canChangeVirtualDisplay(false, null);
        assert HostSettingsOptions.sendAudioLabel(null).contains("更新 Mac 上的 Sill");
        assert HostSettingsOptions.sendAudioLabel(false).contains("关");
        assert HostSettingsOptions.sendAudioLabel(true).contains("开");
        System.out.println("Host settings availability checks passed");
    }
}
