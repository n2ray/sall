package io.github.n2ray.sill;

import java.util.List;

public final class AppDrawerModelTest {
    public static void main(String[] args) {
        var windows = List.of(new AppDrawerModel.Window(5, "收件箱", "邮件", "mail"),
                new AppDrawerModel.Window(6, "Draft", "邮件", "mail"),
                new AppDrawerModel.Window(7, "Code", "Chrome", "browser"));
        var apps = List.of(new AppDrawerModel.App("Chrome", "browser"), new AppDrawerModel.App("Zoom", "zoom"),
                new AppDrawerModel.App("邮件", "mail"), new AppDrawerModel.App("Alpha", "alpha"));
        var all = AppDrawerModel.filter(windows, apps, "");
        assert all.open().size() == 2 && all.open().get(0).windows().size() == 2;
        assert all.closed().size() == 2 && all.closed().get(0).name().equals("Alpha");
        var title = AppDrawerModel.filter(windows, apps, " DRAFT ");
        assert title.open().size() == 1 && title.open().get(0).windows().get(0).id() == 6 && title.closed().isEmpty();
        var app = AppDrawerModel.filter(windows, apps, "邮件");
        assert app.open().size() == 1 && app.open().get(0).windows().size() == 2;
        assert AppDrawerModel.filter(windows, apps, "not found").open().isEmpty();
        assert AppDrawerModel.filter(List.of(), List.of(), "").closed().isEmpty();
        var unknown = AppDrawerModel.filter(List.of(new AppDrawerModel.Window(10, "窗口", "工具", "")), apps, "工具");
        assert unknown.open().size() == 1 && unknown.open().get(0).windows().get(0).id() == 10;
        System.out.println("App drawer grouping and search checks passed");
    }
}
