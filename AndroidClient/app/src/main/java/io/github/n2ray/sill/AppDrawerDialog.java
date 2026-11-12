package io.github.n2ray.sill;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local search and selection UI. Call all methods on the activity's main thread. */
final class AppDrawerDialog {
    interface Listener { void selectWindow(long id); void launchApp(String bundleId); }

    private final Activity activity;
    private final Listener listener;
    private FrameLayout overlay;
    private android.window.OnBackInvokedCallback overlayBack;
    private EditText search;
    private ScrollView scroll;
    private LinearLayout rows;
    private List<AppDrawerModel.Window> windows = java.util.Collections.emptyList();
    private List<AppDrawerModel.App> apps = java.util.Collections.emptyList();
    private Map<String, Bitmap> icons = java.util.Collections.emptyMap();
    private final Map<String, List<ImageView>> badgeViews = new HashMap<>();
    private final Map<View, Integer> accessibility = new HashMap<>();
    private long activeWindow = -1;
    private boolean activeDesktop;
    private int presentationVersion;

    AppDrawerDialog(Activity activity, Listener listener) { this.activity = activity; this.listener = listener; }

    void show(JSONArray windowList, JSONArray appList, Map<String, Bitmap> appIcons, long active,
              boolean desktop, FrameLayout picture, FrameLayout sessionLayer, LinearLayout sessionPanel,
              LinearLayout bar, boolean landscape, boolean largerPicture) {
        if (isShowing()) { dismiss(); return; }
        presentationVersion++;
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14)); root.setBackground(background(0xff181a1f, 20));
        root.setFocusableInTouchMode(true);
        search = new EditText(activity); search.setSingleLine(true); search.setHint("搜索应用");
        search.setTextColor(Color.WHITE); search.setHintTextColor(0xffadb3bd);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setContentDescription("搜索 Mac 上的应用");
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        scroll = new ScrollView(activity); scroll.setFillViewport(false);
        rows = new LinearLayout(activity); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(false); }
            public void afterTextChanged(Editable value) { }
        });
        update(windowList, appList, appIcons, active, desktop);
        overlay = new FrameLayout(activity);
        if (landscape) {
            overlay.setBackgroundColor(0x94000000);
            overlay.setOnClickListener(v -> dismiss());
            FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(Math.min(dp(380), Math.max(dp(160), picture.getWidth() - dp(44))), -1, Gravity.TOP | Gravity.START);
            panel.setMargins(dp(22), dp(8), dp(22), dp(14));
            root.setOnClickListener(v -> { });
            overlay.addView(root, panel);
            picture.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // Keep the button row live while the remaining session area dismisses the drawer.
            AppDrawerModel.PortraitPlacement placement = AppDrawerModel.portraitPlacement(
                    sessionLayer.getHeight(), bar.getTop(), bar.getBottom(), dp(8), largerPicture);
            View topDim = new View(activity), bottomDim = new View(activity);
            topDim.setBackgroundColor(0x94000000); bottomDim.setBackgroundColor(0x94000000);
            topDim.setOnClickListener(v -> dismiss()); bottomDim.setOnClickListener(v -> dismiss());
            overlay.addView(topDim, new FrameLayout.LayoutParams(-1, placement.topDim(), Gravity.TOP));
            FrameLayout.LayoutParams bottom = new FrameLayout.LayoutParams(-1, placement.bottomDim(), Gravity.TOP);
            bottom.topMargin = placement.bottomStart(); overlay.addView(bottomDim, bottom);
            FrameLayout.LayoutParams panel = new FrameLayout.LayoutParams(Math.max(0, sessionLayer.getWidth() - dp(28)), placement.panelHeight(), Gravity.TOP | Gravity.START);
            panel.leftMargin = dp(14); panel.topMargin = placement.panelTop();
            root.setOnClickListener(v -> { }); overlay.addView(root, panel);
            for (int i = 0; i < sessionPanel.getChildCount(); i++) {
                View child = sessionPanel.getChildAt(i);
                if (child == bar) continue;
                accessibility.put(child, child.getImportantForAccessibility());
                child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            sessionLayer.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout current = overlay;
            current.setOnApplyWindowInsetsListener((view, insets) -> {
                // shortcut: Android 10 lacks reliable IME insets with adjustNothing; use a compatible inset source when available.
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    boolean visible = insets.isVisible(android.view.WindowInsets.Type.ime());
                    int imeBottom = visible ? insets.getInsets(android.view.WindowInsets.Type.ime()).bottom : 0;
                    current.post(() -> {
                        if (overlay != current || root.getParent() != current) return;
                        int visibleBottom = sessionLayer.getHeight();
                        if (visible) {
                            int[] windowPos = new int[2], layerPos = new int[2];
                            View decor = activity.getWindow().getDecorView();
                            decor.getLocationOnScreen(windowPos); sessionLayer.getLocationOnScreen(layerPos);
                            visibleBottom = windowPos[1] + decor.getHeight() - imeBottom - layerPos[1];
                        }
                        AppDrawerModel.PortraitPlacement fit = AppDrawerModel.portraitPlacement(
                                sessionLayer.getHeight(), bar.getTop(), bar.getBottom(), dp(8),
                                largerPicture, visibleBottom, visible ? dp(120) : 0);
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) root.getLayoutParams();
                        if (params.topMargin != fit.panelTop() || params.height != fit.panelHeight()) {
                            params.topMargin = fit.panelTop(); params.height = fit.panelHeight(); root.setLayoutParams(params);
                        }
                    });
                }
                return insets;
            });
            current.requestApplyInsets();
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            overlayBack = this::handleBack;
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, overlayBack);
        }
        root.requestFocus();
    }

    void update(JSONArray windowList, JSONArray appList, Map<String, Bitmap> appIcons, long active, boolean desktop) {
        boolean sourceChanged = isShowing() && AppDrawerModel.sourceChanged(activeWindow, activeDesktop, active, desktop);
        List<AppDrawerModel.Window> nextWindows = new ArrayList<>();
        if (windowList != null) for (int i = 0; i < windowList.length(); i++) {
            JSONObject item = windowList.optJSONObject(i); if (item == null) continue;
            long id = item.optLong("id", -1);
            if (id < 0 || id > 0xffffffffL) continue;
            nextWindows.add(new AppDrawerModel.Window(id, item.optString("title", ""), item.optString("appName", ""), item.optString("bundleID", "")));
        }
        List<AppDrawerModel.App> nextApps = new ArrayList<>();
        if (appList != null) for (int i = 0; i < appList.length(); i++) {
            JSONObject item = appList.optJSONObject(i); if (item != null)
                nextApps.add(new AppDrawerModel.App(item.optString("name", ""), item.optString("bundleID", "")));
        }
        Map<String, Bitmap> nextIcons = appIcons == null ? java.util.Collections.emptyMap() : new HashMap<>(appIcons);
        boolean changed = !windows.equals(nextWindows) || !apps.equals(nextApps) || activeWindow != active;
        if (!changed && rows != null) for (var entry : badgeViews.entrySet()) {
            Bitmap before = icons.get(entry.getKey()), after = nextIcons.get(entry.getKey());
            if (before != after) for (ImageView badge : entry.getValue()) badge.setImageBitmap(after);
        }
        windows = java.util.Collections.unmodifiableList(nextWindows); apps = java.util.Collections.unmodifiableList(nextApps);
        icons = nextIcons; activeWindow = active; activeDesktop = desktop;
        if (sourceChanged) { dismiss(); return; }
        if (rows != null && (changed || rows.getChildCount() == 0)) render(true);
    }

    boolean isShowing() { return overlay != null; }
    int presentationVersion() { return presentationVersion; }
    void reanchor(int expectedVersion, JSONArray windowList, JSONArray appList, Map<String, Bitmap> appIcons,
                  long active, boolean desktop, FrameLayout picture, FrameLayout sessionLayer, LinearLayout sessionPanel,
                  LinearLayout bar, boolean landscape, boolean largerPicture) {
        if (!isShowing() || presentationVersion != expectedVersion) return;
        String query = search.getText().toString();
        int selectionStart = search.getSelectionStart(), selectionEnd = search.getSelectionEnd();
        int scrollY = scroll.getScrollY();
        boolean focused = search.hasFocus();
        android.view.WindowInsets insets = search.getRootWindowInsets();
        boolean imeVisible = android.os.Build.VERSION.SDK_INT >= 30 && insets != null
                ? insets.isVisible(android.view.WindowInsets.Type.ime()) : focused;
        dismiss();
        show(windowList, appList, appIcons, active, desktop, picture, sessionLayer, sessionPanel, bar, landscape, largerPicture);
        EditText restored = search;
        restored.setText(query);
        if (selectionStart >= 0 && selectionEnd >= 0)
            restored.setSelection(Math.min(selectionStart, query.length()), Math.min(selectionEnd, query.length()));
        ScrollView restoredScroll = scroll;
        restoredScroll.post(() -> { if (search == restored && isShowing()) restoredScroll.scrollTo(0, scrollY); });
        if (focused) {
            restored.requestFocus();
            if (imeVisible) restored.post(() -> {
                if (search == restored && isShowing() && restored.hasFocus())
                    ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
                            .showSoftInput(restored, 0);
            });
        }
    }
    void handleBack() {
        if (overlay == null) { dismiss(); return; }
        android.view.WindowInsets insets = overlay.getRootWindowInsets();
        boolean imeVisible = android.os.Build.VERSION.SDK_INT >= 30 && insets != null
                ? insets.isVisible(android.view.WindowInsets.Type.ime()) : search != null && search.hasFocus();
        if (imeVisible && search != null && search.hasFocus()) {
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
        } else dismiss();
    }
    void dismiss() {
        presentationVersion++;
        if (android.os.Build.VERSION.SDK_INT >= 33 && overlayBack != null) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(overlayBack);
            overlayBack = null;
        }
        if (overlay != null) {
            if (search != null && search.hasFocus()) {
                ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus();
            }
            if (overlay.getParent() instanceof android.view.ViewGroup parent) parent.removeView(overlay);
            overlay = null;
        }
        for (var entry : accessibility.entrySet()) entry.getKey().setImportantForAccessibility(entry.getValue());
        accessibility.clear();
    }

    private void render(boolean keepScroll) {
        if (rows == null) return;
        int y = keepScroll ? scroll.getScrollY() : 0;
        AppDrawerModel.Results result = AppDrawerModel.filter(windows, apps, search.getText().toString());
        rows.removeAllViews(); badgeViews.clear();
        if (!result.open().isEmpty()) {
            header("正在运行");
            for (AppDrawerModel.Group group : result.open()) {
                boolean selected = group.windows().stream().anyMatch(window -> window.id() == activeWindow);
                LinearLayout label = row(group.name(), group.bundleId(), true);
                label.setBackground(background(selected ? 0xff39485e : 0xff24262c));
                TextView count = text(group.windows().size() + " 个窗口", 12, 0xffadb3bd); label.addView(count);
                label.setContentDescription(group.name() + "，" + group.windows().size() + " 个窗口" + (selected ? "，当前显示" : ""));
                rows.addView(label);
                label.setOnClickListener(v -> {
                    AppDrawerModel.Group current = AppDrawerModel.filter(windows, apps, "").open().stream()
                            .filter(g -> g.bundleId().equals(group.bundleId()) && g.name().equals(group.name())).findFirst().orElse(null);
                    if (current == null) { render(true); return; }
                    long id = AppDrawerModel.nextWindow(current, activeWindow);
                    if (id < 0) { render(true); return; }
                    dismiss(); listener.selectWindow(id);
                });
            }
        }
        if (!result.closed().isEmpty()) {
            header("所有应用");
            for (AppDrawerModel.App app : result.closed()) {
                LinearLayout entry = row(app.name(), app.bundleId(), true); rows.addView(entry);
                entry.setContentDescription("打开 " + app.name());
                entry.setOnClickListener(v -> {
                    if (apps.stream().noneMatch(a -> a.bundleId().equals(app.bundleId()))) { render(true); return; }
                    listener.launchApp(app.bundleId());
                });
            }
        }
        if (result.open().isEmpty() && result.closed().isEmpty()) {
            TextView empty = text(search.getText().toString().trim().isEmpty() ? "Mac 暂无可用应用" : "没有匹配的应用", 14, 0xffadb3bd);
            empty.setPadding(dp(10), dp(24), dp(10), dp(24)); rows.addView(empty);
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void header(String value) {
        TextView label = text(value, 13, 0xffadb3bd); label.setPadding(dp(10), dp(12), dp(10), dp(4)); rows.addView(label);
    }
    private LinearLayout row(String name, String bundle, boolean clickable) {
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(3), dp(10), dp(3)); row.setMinimumHeight(dp(48));
        if (clickable) row.setBackground(background(0xff24262c));
        ImageView icon = new ImageView(activity); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackground(background(0xff353942)); icon.setImageBitmap(icons.get(bundle));
        badgeViews.computeIfAbsent(bundle, ignored -> new ArrayList<>()).add(icon);
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView label = text(name, 15, Color.WHITE); label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(12), 0, dp(6), 0); row.addView(label, p);
        return row;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private GradientDrawable background(int color) { return background(color, 9); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
