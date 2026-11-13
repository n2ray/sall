package io.github.n2ray.sall;

import java.util.List;

public final class VideoDecoderPolicyTest {
    public static void main(String[] args) {
        var policy = new VideoDecoderPolicy();
        var available = List.of("c2.android.hevc.decoder", "OMX.google.hevc.decoder");
        assert policy.next(available).equals("");
        policy.failed("c2.unisoc.hevc.decoder");
        assert policy.softwareOnly() && policy.next(available).equals("c2.android.hevc.decoder");
        policy.failed("c2.android.hevc.decoder");
        assert policy.next(available).equals("OMX.google.hevc.decoder");
        policy.failed("OMX.google.hevc.decoder");
        assert policy.next(available) == null;
        var defaultSoftware = new VideoDecoderPolicy();
        defaultSoftware.failed("c2.android.hevc.decoder");
        assert defaultSoftware.next(List.of("c2.android.hevc.decoder")) == null;
        var createFailed = new VideoDecoderPolicy();
        createFailed.failed(null);
        assert createFailed.next(available).equals("c2.android.hevc.decoder");
        assert new VideoDecoderPolicy().next(List.of()).equals("");
        System.out.println("Video decoder default, software fallback, exhaustion and new-session checks passed");
    }
}
