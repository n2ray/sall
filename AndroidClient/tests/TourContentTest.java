package io.github.n2ray.sall;

import java.util.List;

public final class TourContentTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static boolean has(TourContent.Copy copy, String text) {
        return copy.rows().stream().anyMatch(row -> (row.emphasis() + row.text()).contains(text));
    }
    public static void main(String[] args) {
        var all = new TourContent.Features(true, true, true, true, true, false, true, true, true);
        var none = new TourContent.Features(false, false, false, false, false, true, false, false, false);
        var touch = TourPolicy.run(List.of(TourPolicy.Topic.TOUCH));
        check(has(TourContent.forStep(touch, TourPolicy.Layout.PHONE, false, "Mac", all), "三指"), "supported gestures explained");
        check(!has(TourContent.forStep(touch, TourPolicy.Layout.PHONE, false, "Mac", none), "三指"), "unavailable gestures omitted");
        var bar = TourPolicy.run(List.of(TourPolicy.Topic.BAR));
        var accessible = TourContent.forStep(bar, TourPolicy.Layout.PHONE, true, "Mac", all);
        check(has(accessible, "TalkBack") && !has(accessible, "长按窗口"), "accessible window actions");
        check(has(accessible, "虚拟显示器") && has(accessible, "已开启"), "virtual display on");
        var compact = TourContent.forStep(bar, TourPolicy.Layout.PHONE, false, "Mac", none);
        check(has(compact, "应用") && !has(compact, "虚拟显示器") && !has(compact, "菜单"), "only available controls shown");
        var sharing = TourPolicy.run(List.of(TourPolicy.Topic.SHARING));
        var copy = TourContent.forStep(sharing, TourPolicy.Layout.LANDSCAPE, false, "Mac", all);
        check(has(copy, "只发送到 Mac 剪贴板") && has(copy, "声音测试"), "clipboard mode and sound");
        var empty = TourContent.forStep(sharing, TourPolicy.Layout.PHONE, false, "Mac", none);
        check(!has(empty, "声音测试") && has(empty, "未报告"), "unknown host capabilities do not promise support");
        var laptop = TourPolicy.run(List.of(TourPolicy.Topic.LAPTOP));
        check(!has(TourContent.forStep(laptop, TourPolicy.Layout.PHONE, true, "Mac", all), "双指点击"), "avoid touchpad gestures with TalkBack");
        System.out.println("Tour content checks passed");
    }
}
