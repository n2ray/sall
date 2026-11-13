package io.github.n2ray.sill;

import java.util.ArrayList;
import java.util.List;

/** The tour teaches capabilities currently offered by this Mac and device. */
final class TourContent {
    record Features(boolean menus, boolean gestures, boolean virtualDisplayAvailable, boolean virtualDisplayOn,
                    boolean clipboard, boolean pasteRightAway, boolean sound, boolean largerPicture, boolean thumbnails) { }
    record Row(String symbol, String emphasis, String text, boolean added) { }
    record Copy(String title, String subtitle, List<Row> rows, String hint) {
        Copy { rows = List.copyOf(rows); }
    }
    private static void add(List<Row> rows, String icon, String emphasis, String text, boolean added) {
        rows.add(new Row(icon, emphasis, text, added));
    }
    static Copy forStep(TourPolicy.Run run, TourPolicy.Layout layout, boolean accessibility, String macName, Features f) {
        String mac = macName == null || macName.isBlank() ? "Mac" : macName;
        boolean landscape = layout == TourPolicy.Layout.LANDSCAPE;
        List<Row> rows = new ArrayList<>();
        String title, subtitle = null, hint;
        switch (run.at()) {
            case TOUCH -> {
                title = "点按、长按、拖动与缩放"; subtitle = "在这里的操作会发送到 " + mac + "。";
                add(rows, "☝", "点按", "可点击。", false);
                add(rows, "◉", "长按", "可右键点击。", false);
                add(rows, "↕", "拖动", "可滚动。", false);
                add(rows, "⊕", "双指缩放", "可放大画面，最多 5 倍；放大后双指移动画面，单指仍可操作 Mac。", true);
                if (f.gestures()) add(rows, "↔", "三指滑动", "向上打开调度中心，向下打开应用 Exposé，左右切换空间。", true);
                hint = landscape ? "Mac 画面在顶部工具栏下方。" : "Mac 画面在屏幕上方。";
            }
            case BAR -> {
                title = f.menus() ? "窗口与菜单" : "窗口";
                if (!f.thumbnails()) add(rows, "⌕", "应用", "列出 Mac 上的应用；再次选择已打开的应用可切换到它的下一个窗口。", false);
                else if (accessibility) add(rows, "▣", "每个窗口", "都有关闭、最小化、全屏和左右移动操作。使用 TalkBack 的操作菜单选择。", false);
                else add(rows, "▣", "长按窗口", "可关闭、最小化或全屏；继续按住并拖动可调整顺序。", false);
                if (f.menus()) add(rows, "☰", "菜单", "显示 Mac 当前应用的菜单栏。", true);
                if (f.virtualDisplayAvailable()) add(rows, "▢", "虚拟显示器", (f.virtualDisplayOn() ? "已开启；" : "可在 Mac 画质和帧率设置中开启；") + "窗口在专用显示器上继续更新，即使被遮挡。", true);
                add(rows, "Aa", accessibility ? "文字大小" : "Aa", accessibility ? "可通过无障碍操作调大或调小窗口文字。" : "按住并滑动调整窗口文字大小；桌面不支持此功能。", true);
                add(rows, "⌨", "键盘", "打开屏幕键盘，在 Mac 上输入。也可以使用外接键盘。", false);
                hint = landscape ? "位于顶部工具栏中。" : f.thumbnails() ? "画面下方第一行是 Aa 和键盘，第二行是窗口。" : "位于画面下方的按钮行中。";
            }
            case SHARING -> {
                title = f.clipboard() && f.sound() ? "剪贴板与声音" : f.clipboard() ? "剪贴板" : f.sound() ? "声音" : "剪贴板与声音";
                if (f.clipboard()) {
                    add(rows, "▤", "剪贴板共享", "开启后，可在 Mac 和手机之间传送文本。", true);
                    add(rows, "▤", f.pasteRightAway() ? "粘贴到 Mac" : "发送到 Mac", f.pasteRightAway() ? "会在 Mac 确认后粘贴到光标位置。" : "只发送到 Mac 剪贴板。", true);
                }
                if (f.sound()) add(rows, "♫", "声音测试", "可播放 Mac 的声音；本机静音只影响手机。", true);
                if (!f.clipboard() && !f.sound()) add(rows, "↓", "更新 Sill", "此 Mac 当前未报告剪贴板或声音能力。", false);
                hint = "在设置中，画面工具栏的最后一个按钮。";
            }
            case SETTINGS -> {
                title = "设置";
                add(rows, "×", "断开连接", "位于设置底部。", false);
                add(rows, "?", "使用引导", "可在设置中重新打开。", false);
                if (!accessibility) {
                    add(rows, "↕", "滚动方向", "可反转画面拖动和触控板双指滚动的方向。", true);
                    add(rows, "◷", "窗口控制长按时间", "设置长按缩略图多久后显示窗口控制。", true);
                }
                if (landscape) add(rows, "▯", "竖起手机", "可使用按键和触控板。", false);
                hint = landscape ? "工具栏最后一个按钮。" : "画面下方按钮行的最后一个按钮。";
            }
            case LAPTOP -> {
                title = "按键与触控板";
                if (run.index() == 0) subtitle = "竖屏时可以使用按键和触控板。";
                add(rows, "⌘", "cmd、opt、ctrl 和 shift", "用于下一次按键或触控板点击：先点 cmd，再点 C 即可复制。", false);
                if (!accessibility) {
                    add(rows, "☝", "双指点击触控板", "可右键。", false);
                    add(rows, "↔", "长按后拖动", "可移动窗口或选择文字。", false);
                }
                if (f.largerPicture()) add(rows, "▣", "大画面", "可在设置中开启，让窗口画面占用缩略图的位置。", true);
                hint = f.thumbnails() ? "在窗口缩略图下方：先是按键，再是触控板。" : "在按钮行下方：先是按键，再是触控板。";
            }
            default -> throw new IllegalStateException();
        }
        return new Copy(title, subtitle, rows, hint);
    }
}
