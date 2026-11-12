package io.github.n2ray.sill;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Initial device client: manually addressed, paired connections to Sill's remote door. */
public final class MainActivity extends Activity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final ExecutorService input = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object outputLock = new Object();
    private volatile SillConnection connection;
    private volatile boolean running;
    private volatile Surface surface;
    private LinearLayout root, connectionPanel, sessionPanel, choices, savedMacs, nearbyMacs, keyRow;
    private ScrollView connectionContainer;
    private TextView status, sessionStatus;
    private EditText host, port, code;
    private RemoteKeyboard keyboard;
    private TrackpadView trackpad;
    private android.widget.FrameLayout picture;
    private SurfaceView video;
    private android.view.View cursor;
    private final java.util.Map<Long, android.widget.ImageView> thumbnails = new java.util.HashMap<>();
    private final Object surfaceLock = new Object();
    private volatile boolean clipboardEnabled;
    private boolean audioEnabled;
    private AudioPlayer audio;
    private boolean clipboardSubscribed;
    private Double textScale = .75;
    private int desktopDisplays = -1, desktopDisplay;
    private boolean desktopActive;
    private Button desktopButton, orientationButton;
    private volatile boolean closing;
    private long modifiers;
    private JSONArray appList = new JSONArray(), macMenus = new JSONArray();
    private int menuVersion, menuToken, pendingMenuToken;
    private JSONObject hostSettings;
    private int settingsToken, pendingSettingsToken;
    private String pendingSettingsField;
    private Object pendingSettingsValue;
    private MacDiscovery discovery;
    private final java.util.Map<String, android.view.View> nearby = new java.util.HashMap<>();
    private MediaCodec decoder;
    private Surface decoderSurface;
    private int nalSize;
    private volatile int videoWidth = 16, videoHeight = 10;
    private boolean waitingForKeyframe;
    private long lastKeyframeRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad); setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                view.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom);
            } else {
                view.setPadding(pad + insets.getSystemWindowInsetLeft(), pad + insets.getSystemWindowInsetTop(),
                        pad + insets.getSystemWindowInsetRight(), pad + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.setBackgroundColor(0xff08090b);
        connectionPanel = new LinearLayout(this); connectionPanel.setOrientation(LinearLayout.VERTICAL);
        connectionContainer = new ScrollView(this); connectionContainer.setFillViewport(true); connectionContainer.addView(connectionPanel);
        root.addView(connectionContainer, new LinearLayout.LayoutParams(-1, -1));
        TextView title = new TextView(this); title.setText("Sill"); title.setTextColor(-1); title.setTextSize(30);
        connectionPanel.addView(title);
        status = new TextView(this); status.setTextColor(0xffb5bac4);
        status.setText("连接你的 Mac · 跨网络连接需启用 Remote Access"); connectionPanel.addView(status);
        savedMacs = new LinearLayout(this); savedMacs.setOrientation(LinearLayout.VERTICAL); connectionPanel.addView(savedMacs);
        nearbyMacs = new LinearLayout(this); nearbyMacs.setOrientation(LinearLayout.VERTICAL); connectionPanel.addView(nearbyMacs);
        host = field("Mac IP 或 Tailscale 地址", getPreferences(0).getString("host", ""));
        port = field("端口", getPreferences(0).getString("port", "7455")); port.setInputType(2);
        code = field("Mac 上显示的 12 位配对码", ""); code.setInputType(2);
        button(connectionPanel, "配对并连接", v -> connect());
        button(connectionPanel, "连接已配对的 Mac", v -> start(false));
        clipboardEnabled = getPreferences(0).getBoolean("shareClipboard", false);
        audioEnabled = getPreferences(0).getBoolean("experimentalAudio", false);
        refreshSavedMacs();
        buildSession();
        discovery = new MacDiscovery(this, new MacDiscovery.Listener() {
            public void found(String name, String address, int number) {
                android.view.View previous = nearby.remove(name); if (previous != null) nearbyMacs.removeView(previous);
                Button row = button(nearbyMacs, "附近 · " + name, v -> {
                    host.setText(address); port.setText(String.valueOf(number));
                    if (getPreferences(0).contains("pin:" + address + ":" + number)) start(false);
                    else show("请在 Mac 的 Sill 中打开配对窗口并输入配对码。");
                });
                nearby.put(name, row);
            }
            public void lost(String name) { android.view.View row = nearby.remove(name); if (row != null) nearbyMacs.removeView(row); }
        });
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private android.graphics.drawable.GradientDrawable shape(int color, int radius) {
        var d = new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(LinearLayout parent, String title, android.view.View.OnClickListener click) {
        Button b = new Button(this); b.setText(title); b.setTextColor(-1); b.setTextSize(12); b.setAllCaps(false);
        b.setBackground(shape(0xff272a30, 12)); b.setOnClickListener(click); parent.addView(b);
        return b;
    }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setText(value);
        e.setTextColor(-1); e.setHintTextColor(0xff9297a0); connectionPanel.addView(e); return e;
    }
    private void refreshSavedMacs() {
        savedMacs.removeAllViews();
        java.util.List<String> keys = new java.util.ArrayList<>();
        for (String key : getPreferences(0).getAll().keySet()) if (key.startsWith("pin:")) keys.add(key);
        java.util.Collections.sort(keys);
        for (String key : keys) {
            String address = key.substring(4); int divider = address.lastIndexOf(':');
            if (divider < 1 || divider == address.length() - 1) continue;
            String macHost = address.substring(0, divider), macPort = address.substring(divider + 1);
            Button row = button(savedMacs, macHost + "  ·  " + macPort, v -> {
                host.setText(macHost); port.setText(macPort); start(false);
            });
            row.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this).setTitle("移除本机记录？")
                    .setMessage("这只移除手机上的记录；如需撤销 Mac 的信任，请在 Mac 的 Sill 设置中移除设备。")
                    .setNegativeButton("取消", null).setPositiveButton("移除", (d, which) -> {
                        getPreferences(0).edit().remove(key).apply(); refreshSavedMacs();
                    }).show(); return true;
            });
        }
    }
    private void buildSession() {
        sessionPanel = new LinearLayout(this); sessionPanel.setOrientation(LinearLayout.VERTICAL);
        sessionPanel.setVisibility(android.view.View.GONE); root.addView(sessionPanel, new LinearLayout.LayoutParams(-1, -1));
        sessionStatus = new TextView(this); sessionStatus.setTextColor(0xffaeb5c0); sessionStatus.setTextSize(12);
        sessionPanel.addView(sessionStatus);
        picture = new android.widget.FrameLayout(this); picture.setBackground(shape(0xff000000, 12));
        sessionPanel.addView(picture, new LinearLayout.LayoutParams(-1, dp(230)));
        picture.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
            if ((r - l) != (oright - ol) || (b - t) != (ob - ot)) picture.post(() -> { layoutVideo(); sendViewport(); });
        });
        video = new SurfaceView(this); picture.addView(video, new android.widget.FrameLayout.LayoutParams(-1, -1));
        video.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) {
                synchronized (surfaceLock) { surface = holder.getSurface(); surfaceLock.notifyAll(); }
                SillConnection c = connection;
                if (c != null) input.execute(() -> { try { synchronized (outputLock) { if (connection == c) c.send(44, new byte[0]); } } catch (Exception ignored) { } });
            }
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { synchronized (surfaceLock) { surface = holder.getSurface(); surfaceLock.notifyAll(); } }
            public void surfaceDestroyed(SurfaceHolder holder) {
                synchronized (surfaceLock) { surface = null; }
            }
        });
        cursor = new android.view.View(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                if (trackpad == null) return;
                if (getWidth() == 0 || getHeight() == 0) return;
                FrameGeometry.Rect frame = FrameGeometry.fit(getWidth(), getHeight(), videoWidth, videoHeight);
                float x = frame.left() + trackpad.pointerX() * frame.width(), y = frame.top() + trackpad.pointerY() * frame.height();
                android.graphics.Path arrow = new android.graphics.Path(); arrow.moveTo(x, y); arrow.lineTo(x + dp(5), y + dp(16));
                arrow.lineTo(x + dp(8), y + dp(10)); arrow.lineTo(x + dp(13), y + dp(9)); arrow.close();
                paint.setColor(android.graphics.Color.BLACK); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); canvas.drawPath(arrow, paint);
                paint.setColor(android.graphics.Color.WHITE); paint.setStyle(android.graphics.Paint.Style.FILL); canvas.drawPath(arrow, paint);
            }
        };
        picture.addView(cursor, new android.widget.FrameLayout.LayoutParams(-1, -1));
        cursor.setOnTouchListener((view, event) -> {
            if (connection == null) return false;
            if (view.getWidth() == 0 || view.getHeight() == 0) return false;
            FrameGeometry.Rect frame = FrameGeometry.fit(view.getWidth(), view.getHeight(), videoWidth, videoHeight);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN &&
                    (event.getX() < frame.left() || event.getX() > frame.left() + frame.width() ||
                     event.getY() < frame.top() || event.getY() > frame.top() + frame.height())) return false;
            float x = frame.x(event.getX()), y = frame.y(event.getY());
            String action = switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> "leftDown"; case MotionEvent.ACTION_MOVE -> "move";
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> "leftUp"; default -> null;
            };
            if (action != null) { trackpad.position(x, y); cursor.invalidate(); pointer(action, x, y); }
            return true;
        });
        LinearLayout bar = new LinearLayout(this); sessionPanel.addView(bar);
        barButton(bar, "应用", v -> showWindows());
        barButton(bar, "菜单", v -> showMenu(macMenus, "Mac 菜单", menuVersion));
        barButton(bar, "Aa", v -> chooseScale());
        barButton(bar, "键盘", v -> { keyboard.requestFocus(); ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(keyboard, 0); });
        desktopButton = barButton(bar, "桌面", v -> tapDesktop());
        orientationButton = barButton(bar, "横屏", v -> setRequestedOrientation(
                getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        barButton(bar, "设置", v -> settings());
        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false); choices = new LinearLayout(this); choices.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(choices); sessionPanel.addView(strip, new LinearLayout.LayoutParams(-1, dp(72)));
        LinearLayout keys = new LinearLayout(this); keyRow = keys; sessionPanel.addView(keys);
        barButton(keys, "esc", v -> tapKey(41)); barButton(keys, "tab", v -> tapKey(43));
        modifier(keys, "ctrl", 1L << 18); modifier(keys, "opt", 1L << 19);
        modifier(keys, "cmd", 1L << 20); modifier(keys, "shift", 1L << 17);
        trackpad = new TrackpadView(this, new TrackpadView.Listener() {
            public void pointer(String action, float x, float y) { MainActivity.this.pointer(action, x, y); cursor.invalidate(); }
            public void scroll(String phase, float x, float y, float dx, float dy) { sendScroll(phase, x, y, dx, dy); }
        });
        sessionPanel.addView(trackpad, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout rightPane = new LinearLayout(this); rightPane.setOrientation(LinearLayout.VERTICAL);
        LinearLayout keysSecond = new LinearLayout(this);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
            boolean landscape = r - l > b - t;
            int visible = landscape ? android.view.View.GONE : android.view.View.VISIBLE;
            sessionStatus.setVisibility(visible); strip.setVisibility(visible);
            orientationButton.setText(landscape ? "竖屏" : "横屏");
            if (landscape && rightPane.getParent() == null) {
                sessionPanel.removeView(bar); sessionPanel.removeView(keys); sessionPanel.removeView(trackpad);
                for (int i = 0; i < 3; i++) { android.view.View key = keys.getChildAt(3); keys.removeViewAt(3); keysSecond.addView(key); }
                rightPane.addView(keys); rightPane.addView(keysSecond); rightPane.addView(trackpad, new LinearLayout.LayoutParams(-1, 0, 1));
                sessionPanel.setOrientation(LinearLayout.HORIZONTAL);
                sessionPanel.addView(bar, 1, new LinearLayout.LayoutParams(dp(65), -1));
                sessionPanel.addView(rightPane, sessionPanel.indexOfChild(strip) + 1, new LinearLayout.LayoutParams(dp(150), -1));
                bar.setOrientation(LinearLayout.VERTICAL);
                for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
            } else if (!landscape && rightPane.getParent() != null) {
                sessionPanel.removeView(bar); sessionPanel.removeView(rightPane);
                rightPane.removeView(keys); rightPane.removeView(keysSecond); rightPane.removeView(trackpad);
                while (keysSecond.getChildCount() > 0) { android.view.View key = keysSecond.getChildAt(0); keysSecond.removeViewAt(0); keys.addView(key); }
                sessionPanel.setOrientation(LinearLayout.VERTICAL); bar.setOrientation(LinearLayout.HORIZONTAL);
                sessionPanel.addView(bar, 2, new LinearLayout.LayoutParams(-1, -2));
                sessionPanel.addView(keys, sessionPanel.indexOfChild(strip) + 1, new LinearLayout.LayoutParams(-1, -2));
                sessionPanel.addView(trackpad, sessionPanel.indexOfChild(keys) + 1, new LinearLayout.LayoutParams(-1, 0, 1));
                for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(0, dp(46), 1));
            }
            int width = r - l - root.getPaddingLeft() - root.getPaddingRight();
            int height = b - t - root.getPaddingTop() - root.getPaddingBottom();
            int wanted = landscape ? -1 : Math.min(Math.round(width / 1.6f), Math.round(height * .42f));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) picture.getLayoutParams();
            int wantedWidth = landscape ? 0 : -1; float weight = landscape ? 1 : 0;
            if (params.height != wanted || params.width != wantedWidth || params.weight != weight) {
                params.height = wanted; params.width = wantedWidth; params.weight = weight; picture.setLayoutParams(params);
            }
        });
        keyboard = new RemoteKeyboard(this, new RemoteKeyboard.Listener() {
            public void text(String value) { sendText(value); }
            public void key(int usage, boolean down) { sendKey(usage, down); }
        });
        sessionPanel.addView(keyboard, new LinearLayout.LayoutParams(1, 1));
    }
    private Button barButton(LinearLayout row, String label, android.view.View.OnClickListener action) {
        Button b = button(row, label, action); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
        p.setMargins(dp(2), dp(3), dp(2), dp(3)); b.setLayoutParams(p); b.setPadding(0, 0, 0, 0);
        return b;
    }
    private void modifier(LinearLayout row, String label, long flag) {
        barButton(row, label, v -> {
            modifiers ^= flag; boolean down = (modifiers & flag) != 0;
            int usage = flag == (1L << 18) ? 0xe0 : flag == (1L << 17) ? 0xe1 : flag == (1L << 19) ? 0xe2 : 0xe3;
            sendKey(usage, down); v.setBackground(shape(down ? 0xff486993 : 0xff272a30, 12));
        });
    }
    private void sendKey(int usage, boolean down) {
        sendKey(usage, down, modifiers);
    }
    private void sendKey(int usage, boolean down, long flags) {
        try { sendAsync(8, new JSONObject().put("key", new JSONObject().put("hidUsage", usage).put("down", down).put("modifiers", flags))); }
        catch (Exception e) { show("按键发送失败"); }
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (connection == null || event.getDeviceId() < 0 || (event.getSource() & android.view.InputDevice.SOURCE_KEYBOARD) == 0)
            return super.dispatchKeyEvent(event);
        int code = event.getKeyCode(), usage;
        if (code >= android.view.KeyEvent.KEYCODE_A && code <= android.view.KeyEvent.KEYCODE_Z) usage = 4 + code - android.view.KeyEvent.KEYCODE_A;
        else if (code >= android.view.KeyEvent.KEYCODE_1 && code <= android.view.KeyEvent.KEYCODE_9) usage = 30 + code - android.view.KeyEvent.KEYCODE_1;
        else usage = switch (code) {
            case android.view.KeyEvent.KEYCODE_0 -> 39; case android.view.KeyEvent.KEYCODE_ENTER -> 40;
            case android.view.KeyEvent.KEYCODE_ESCAPE -> 41; case android.view.KeyEvent.KEYCODE_DEL -> 42;
            case android.view.KeyEvent.KEYCODE_TAB -> 43; case android.view.KeyEvent.KEYCODE_SPACE -> 44;
            case android.view.KeyEvent.KEYCODE_MINUS -> 45; case android.view.KeyEvent.KEYCODE_EQUALS -> 46;
            case android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> 47; case android.view.KeyEvent.KEYCODE_RIGHT_BRACKET -> 48;
            case android.view.KeyEvent.KEYCODE_BACKSLASH -> 49; case android.view.KeyEvent.KEYCODE_SEMICOLON -> 51;
            case android.view.KeyEvent.KEYCODE_APOSTROPHE -> 52; case android.view.KeyEvent.KEYCODE_GRAVE -> 53;
            case android.view.KeyEvent.KEYCODE_COMMA -> 54; case android.view.KeyEvent.KEYCODE_PERIOD -> 55;
            case android.view.KeyEvent.KEYCODE_SLASH -> 56; case android.view.KeyEvent.KEYCODE_FORWARD_DEL -> 76;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 79; case android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 80;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 81; case android.view.KeyEvent.KEYCODE_DPAD_UP -> 82;
            case android.view.KeyEvent.KEYCODE_CTRL_LEFT -> 0xe0; case android.view.KeyEvent.KEYCODE_SHIFT_LEFT -> 0xe1;
            case android.view.KeyEvent.KEYCODE_ALT_LEFT -> 0xe2; case android.view.KeyEvent.KEYCODE_META_LEFT -> 0xe3;
            case android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> 0xe4; case android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xe5;
            case android.view.KeyEvent.KEYCODE_ALT_RIGHT -> 0xe6; case android.view.KeyEvent.KEYCODE_META_RIGHT -> 0xe7;
            default -> 0;
        };
        if (usage == 0) return super.dispatchKeyEvent(event);
        if (event.getRepeatCount() > 0) return true;
        long flags = modifiers | (event.isShiftPressed() ? 1L << 17 : 0) | (event.isCtrlPressed() ? 1L << 18 : 0)
                | (event.isAltPressed() ? 1L << 19 : 0) | (event.isMetaPressed() ? 1L << 20 : 0);
        sendKey(usage, event.getAction() == android.view.KeyEvent.ACTION_DOWN, flags); return true;
    }
    private void tapKey(int usage) { sendKey(usage, true); sendKey(usage, false); }
    private void sendText(String text) {
        if ((modifiers & ((1L << 18) | (1L << 19) | (1L << 20))) != 0 && text.length() == 1) {
            char c = Character.toLowerCase(text.charAt(0));
            int usage = c >= 'a' && c <= 'z' ? 4 + c - 'a' : c >= '1' && c <= '9' ? 30 + c - '1' : c == '0' ? 39 : 0;
            if (usage != 0) { tapKey(usage); return; }
        }
        try { sendAsync(8, new JSONObject().put("text", new JSONObject().put("_0", text))); }
        catch (Exception e) { show("文字发送失败"); }
    }
    private void pointer(String action, float x, float y) {
        try { sendAsync(8, new JSONObject().put("pointer", new JSONObject().put("_0", action).put("x", x).put("y", y))); }
        catch (Exception e) { show("指针发送失败"); }
    }
    private void sendScroll(String phase, float x, float y, float dx, float dy) {
        try {
            JSONObject body = new JSONObject().put("x", x).put("y", y);
            if (phase != null) body.put("_0", phase); else { body.put("dx", dx); body.put("dy", dy); }
            sendAsync(8, new JSONObject().put(phase == null ? "scroll" : "scrollGesture", body));
        } catch (Exception e) { show("滚动发送失败"); }
    }
    private void select(JSONObject source, boolean desktop) {
        try { sendViewport(); sendAsync(6, new JSONObject().put(desktop ? "desktop" : "window", source)); }
        catch (Exception e) { show("选择窗口失败"); }
    }
    private void tapDesktop() {
        if (DesktopNavigation.next(desktopActive, desktopDisplays, desktopDisplay)) sendAsync(47, new JSONObject(), true);
        else select(new JSONObject(), true);
    }
    private void showWindows() {
        if (choices.getChildCount() == 0) return;
        int windows = choices.getChildCount();
        android.view.View[] windowChoices = new android.view.View[windows];
        JSONArray apps = appList;
        String[] names = new String[windows + apps.length()];
        for (int i = 0; i < windows; i++) { windowChoices[i] = choices.getChildAt(i); names[i] = windowChoices[i].getContentDescription().toString(); }
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.optJSONObject(i); names[windows + i] = "打开 " + (app == null ? "" : app.optString("name"));
        }
        new android.app.AlertDialog.Builder(this).setTitle("应用和窗口").setItems(names, (dialog, which) -> {
            if (which < windows) windowChoices[which].performClick();
            else try { sendAsync(7, new JSONObject().put("bundleID", apps.getJSONObject(which - windows).getString("bundleID"))); }
            catch (Exception e) { show("无法打开应用"); }
        }).show();
    }
    private void showMenu(JSONArray items, String title, int version) {
        if (items.length() == 0) { show("当前应用没有可用菜单"); return; }
        String[] names = new String[items.length()];
        for (int i = 0; i < names.length; i++) {
            JSONObject item = items.optJSONObject(i);
            names[i] = item == null || item.optBoolean("separator") ? "────────" : item.optString("title", "") + (item.optString("key").isEmpty() ? "" : "   " + item.optString("key"));
        }
        new android.app.AlertDialog.Builder(this).setTitle(title).setItems(names, (dialog, which) -> {
            JSONObject item = items.optJSONObject(which);
            if (item == null || item.optBoolean("separator") || !item.optBoolean("enabled", true)) return;
            if (version != menuVersion) { show("菜单已更改，请重新打开"); return; }
            try {
                JSONObject message = new JSONObject().put("version", version).put("id", item.getString("id"))
                        .put("title", item.getString("title")).put("token", ++menuToken);
                if (item.optBoolean("submenu")) { pendingMenuToken = menuToken; sendAsync(27, message); }
                else sendAsync(25, message);
            } catch (Exception e) { show("菜单请求失败"); }
        }).show();
    }
    private void chooseScale() {
        String[] labels = {"不调整", "0.5×", "0.75×", "1×", "1.25×", "1.5×"};
        new android.app.AlertDialog.Builder(this).setTitle("文字大小").setItems(labels, (dialog, index) -> {
            textScale = index == 0 ? null : new double[]{0, .5, .75, 1, 1.25, 1.5}[index]; sendViewport();
        }).show();
    }
    private void sendViewport() {
        if (connection == null || picture.getWidth() <= 0 || picture.getHeight() <= 0) return;
        try {
            float density = getResources().getDisplayMetrics().density;
            JSONObject v = new JSONObject().put("width", picture.getWidth() / density).put("height", picture.getHeight() / density).put("fps", 60);
            if (textScale != null) v.put("scale", textScale); sendAsync(9, v);
        } catch (Exception e) { show("画面尺寸发送失败"); }
    }
    private void layoutVideo() {
        if (picture.getWidth() <= 0 || picture.getHeight() <= 0) return;
        FrameGeometry.Rect frame = FrameGeometry.fit(picture.getWidth(), picture.getHeight(), videoWidth, videoHeight);
        int width = Math.max(1, Math.round(frame.width())), height = Math.max(1, Math.round(frame.height()));
        android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) video.getLayoutParams();
        if (params.width != width || params.height != height || params.gravity != android.view.Gravity.CENTER) {
            params.width = width; params.height = height; params.gravity = android.view.Gravity.CENTER; video.setLayoutParams(params);
        }
    }
    private void settings() {
        String[] items = {"Mac 画质和帧率", clipboardEnabled ? "关闭剪贴板共享" : "开启剪贴板共享", "将手机剪贴板粘贴到 Mac", audioEnabled ? "关闭声音（重新连接后生效）" : "开启声音测试（重新连接后生效）", "断开连接"};
        new android.app.AlertDialog.Builder(this).setTitle("设置").setItems(items, (dialog, i) -> {
            if (i == 0) hostSettingsMenu();
            if (i == 1) { clipboardEnabled = !clipboardEnabled; getPreferences(0).edit().putBoolean("shareClipboard", clipboardEnabled).apply(); updateClipboard(); }
            if (i == 2) pasteClipboard();
            if (i == 3) {
                if (!audioEnabled && !AudioPlayer.supported()) { show("此设备没有报告支持 AAC-ELD 解码"); return; }
                audioEnabled = !audioEnabled; getPreferences(0).edit().putBoolean("experimentalAudio", audioEnabled).apply();
                show("声音设置将在下次连接时生效");
            }
            if (i == 4) disconnect();
        }).show();
    }
    private void hostSettingsMenu() {
        JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
        if (settings == null) { show("Mac 尚未提供画质设置"); return; }
        String[] items = {"画质 · " + settings.optInt("bitrate") / 1000000 + " Mbps", "帧率 · " + settings.optInt("maxFPS") + " fps",
                "分辨率 · " + (settings.optDouble("captureScale", 1) >= 2 ? "Retina" : "标准"),
                "虚拟显示器 · " + (settings.optBoolean("virtualDisplay") ? "开" : "关"),
                "Mac 发送声音 · " + (settings.optBoolean("sendAudio") ? "开" : "关")};
        new android.app.AlertDialog.Builder(this).setTitle("Mac 设置（影响连接的设备）").setItems(items, (dialog, index) -> {
            if (index == 0) {
                String[] labels = {"低 4 Mbps", "节省 8 Mbps", "均衡 15 Mbps", "高 25 Mbps", "专业 40 Mbps", "极高 80 Mbps", "极限 150 Mbps"};
                int[] values = {4000000, 8000000, 15000000, 25000000, 40000000, 80000000, 150000000};
                new android.app.AlertDialog.Builder(this).setTitle("画质").setItems(labels, (d, i) -> changeHostSetting("bitrate", values[i])).show();
            } else if (index == 1) {
                new android.app.AlertDialog.Builder(this).setTitle("帧率").setItems(new String[]{"60 fps", "120 fps"}, (d, i) -> changeHostSetting("maxFPS", i == 0 ? 60 : 120)).show();
            } else if (index == 2) {
                new android.app.AlertDialog.Builder(this).setTitle("分辨率").setItems(new String[]{"标准", "Retina"}, (d, i) -> changeHostSetting("captureScale", i == 0 ? 1.0 : 2.0)).show();
            } else if (index == 3) changeHostSetting("virtualDisplay", !settings.optBoolean("virtualDisplay"));
            else if (settings.has("sendAudio")) changeHostSetting("sendAudio", !settings.optBoolean("sendAudio"));
            else show("此 Mac 未提供声音设置");
        }).show();
    }
    private void changeHostSetting(String field, Object value) {
        JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
        if (settings == null || !settings.has(field)) return;
        if (pendingSettingsToken != 0) { show("正在等待 Mac 确认设置"); return; }
        try {
            pendingSettingsToken = ++settingsToken; pendingSettingsField = field; pendingSettingsValue = value;
            sendAsync(17, new JSONObject().put("token", pendingSettingsToken).put(field, value));
            show("正在更新 Mac 设置…");
            int waiting = pendingSettingsToken;
            ui.postDelayed(() -> { if (pendingSettingsToken == waiting) { pendingSettingsToken = 0; show("Mac 未确认设置，请重试"); } }, 10000);
        } catch (Exception e) { pendingSettingsToken = 0; show("设置发送失败"); }
    }
    private void updateClipboard() {
        if (connection == null) return;
        try { sendAsync(32, clipboardEnabled ? new JSONObject() : new JSONObject().put("stop", true)); clipboardSubscribed = clipboardEnabled; }
        catch (Exception e) { show("剪贴板设置失败"); }
    }
    private void pasteClipboard() {
        if (!clipboardEnabled) { show("请先开启剪贴板共享"); return; }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (!clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) return;
        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0 || text.toString().getBytes(StandardCharsets.UTF_8).length > 256 * 1024) return;
        try {
            sendAsync(32, new JSONObject().put("text", text.toString()));
            sendAsync(8, new JSONObject().put("key", new JSONObject().put("hidUsage", 25).put("down", true).put("modifiers", 1L << 20)));
            sendAsync(8, new JSONObject().put("key", new JSONObject().put("hidUsage", 25).put("down", false).put("modifiers", 1L << 20)));
        }
        catch (Exception e) { show("剪贴板发送失败"); }
    }
    private void disconnect() {
        closing = true; SillConnection c = connection;
        if (c != null) try { c.close(); } catch (Exception ignored) { }
    }

    private void show(String text) { ui.post(() -> { status.setText(text); sessionStatus.setText(text); }); }
    private void connect() { start(true); }
    private void start(boolean pairing) {
        String address = host.getText().toString().trim();
        String password = code.getText().toString();
        int number;
        try { number = Integer.parseInt(port.getText().toString()); if (address.isEmpty() || number < 1 || number > 65535) throw new IllegalArgumentException(); }
        catch (Exception e) { show("请填写有效地址和端口。"); return; }
        if (running) { show("已有连接，请退出后重试。"); return; }
        running = true; closing = false; getPreferences(0).edit().putString("host", address).putString("port", String.valueOf(number)).apply();
        network.execute(() -> {
            try {
                String pinKey = "pin:" + address + ":" + number;
                byte[] pin;
                if (pairing) {
                    show("正在配对…");
                    try (SillConnection pair = SillConnection.connect(address, number, "sill-pair/1", null)) {
                        pin = pair.pair(password, android.os.Build.MODEL, android.os.Build.DEVICE);
                    }
                    getPreferences(0).edit().putString(pinKey, Base64.encodeToString(pin, Base64.NO_WRAP)).apply();
                    ui.post(() -> { code.setText(""); refreshSavedMacs(); });
                } else {
                    String saved = getPreferences(0).getString(pinKey, null);
                    if (saved == null) throw new IllegalStateException("请先配对这台 Mac。");
                    pin = Base64.decode(saved, Base64.NO_WRAP);
                }
                show("正在连接…");
                try (SillConnection session = SillConnection.connect(address, number, "sill/1", pin)) {
                    connection = session; session.sessionTimeout();
                    ui.post(() -> { connectionContainer.setVisibility(android.view.View.GONE); sessionPanel.setVisibility(android.view.View.VISIBLE); picture.post(this::sendViewport); });
                    JSONObject hello = new JSONObject().put("protocol", 1).put("appVersion", "0.1.0").put("device", android.os.Build.MODEL);
                    if (audioEnabled && AudioPlayer.supported()) { audio = new AudioPlayer(this::show); hello.put("audio", new JSONArray().put("aac-eld")); }
                    sendJson(23, hello);
                    sendJson(27, new JSONObject());
                    var ping = heartbeat.scheduleAtFixedRate(() -> {
                        try { synchronized (outputLock) { session.send(10, ByteBuffer.allocate(8).putDouble(System.nanoTime() / 1e9).array()); } }
                        catch (Exception e) { try { session.close(); } catch (Exception ignored) { } }
                    }, 0, 1, java.util.concurrent.TimeUnit.SECONDS);
                    show("已连接，选择一个窗口或桌面。");
                    try { while (running) handle(session.read()); } finally { ping.cancel(false); }
                }
            } catch (Exception e) { if (!closing) { android.util.Log.e("SillAndroid", "Connection failed", e); show("连接结束: " + e.getMessage()); } }
            finally {
                connection = null; running = false; clipboardSubscribed = false; releaseDecoder();
                if (audio != null) { audio.close(); audio = null; }
                ui.post(() -> { hostSettings = null; pendingSettingsToken = 0; desktopDisplays = -1; desktopDisplay = 0; desktopActive = false; desktopButton.setText("桌面"); modifiers = 0; for (int i = 2; i < keyRow.getChildCount(); i++) keyRow.getChildAt(i).setBackground(shape(0xff272a30, 12)); sessionPanel.setVisibility(android.view.View.GONE); connectionContainer.setVisibility(android.view.View.VISIBLE); });
            }
        });
    }
    private void sendJson(int kind, JSONObject value) throws Exception {
        synchronized (outputLock) {
            SillConnection c = connection;
            if (c == null) throw new IllegalStateException("尚未连接");
            c.send(kind, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private void sendAsync(int kind, JSONObject value) {
        sendAsync(kind, value, false);
    }
    private void sendAsync(int kind, JSONObject value, boolean empty) {
        SillConnection expected = connection;
        if (expected == null) return;
        input.execute(() -> {
            try {
                synchronized (outputLock) {
                    if (connection == expected) expected.send(kind, empty ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (Exception e) { show("发送失败: " + e.getClass().getSimpleName()); }
        });
    }
    private void handle(StreamWire.Message message) throws Exception {
        switch (message.kind()) {
            case 2 -> {
                JSONObject list = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                JSONArray windows = list.getJSONArray("windows");
                if (clipboardEnabled && !clipboardSubscribed && list.optInt("clipboard", 0) >= 1) updateClipboard();
                ui.post(() -> {
                    int count = list.optInt("desktopDisplays", 0), current = list.optInt("desktopDisplay", 0);
                    JSONObject active = list.optJSONObject("active"); boolean isDesktop = active != null && active.has("desktop");
                    if (count != desktopDisplays || current != desktopDisplay || isDesktop != desktopActive)
                        android.util.Log.i("SillAndroid", "Host " + list.optString("hostVersion", "unknown") + ": desktop " + current + "/" + count + ", active=" + isDesktop);
                    desktopDisplays = count; desktopDisplay = current; desktopActive = isDesktop;
                    desktopButton.setText(desktopDisplays >= 2 && desktopDisplay > 0 ? "桌面 " + desktopDisplay + "/" + desktopDisplays : "桌面");
                    desktopButton.setContentDescription(desktopDisplays >= 2 ? "桌面 " + desktopDisplay + " / " + desktopDisplays + "，再次点击切换显示器" : "桌面");
                    choices.removeAllViews(); thumbnails.clear();
                    addChoice("整个桌面", new JSONObject());
                    for (int i = 0; i < windows.length(); i++) {
                        JSONObject item = windows.optJSONObject(i); if (item == null) continue;
                        try { addChoice(item.optString("appName") + " — " + item.optString("title"), new JSONObject().put("_0", item.getLong("id"))); }
                        catch (Exception ignored) { }
                    }
                });
            }
            case 3 -> {
                byte[] bytes = message.payload();
                if (bytes.length > 4) {
                    long id = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 0, 4).getInt());
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 4, bytes.length - 4);
                    if (bitmap != null) ui.post(() -> { android.widget.ImageView image = thumbnails.get(id); if (image != null) image.setImageBitmap(bitmap); });
                }
            }
            case 5 -> {
                JSONArray apps = new JSONArray(new String(message.payload(), StandardCharsets.UTF_8));
                ui.post(() -> appList = apps);
            }
            case 16 -> {
                JSONObject state = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                ui.post(() -> {
                    hostSettings = state;
                    if (pendingSettingsToken != 0 && state.optInt("answering", -1) == pendingSettingsToken) {
                        JSONObject s = state.optJSONObject("settings"); Object actual = s == null ? null : s.opt(pendingSettingsField);
                        boolean accepted = actual instanceof Number && pendingSettingsValue instanceof Number
                                ? ((Number) actual).doubleValue() == ((Number) pendingSettingsValue).doubleValue()
                                : pendingSettingsValue.equals(actual);
                        pendingSettingsToken = 0; show(accepted ? "Mac 设置已更新" : "Mac 未接受此设置");
                    }
                });
            }
            case 24 -> {
                JSONObject menu = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                ui.post(() -> {
                    if (menu.has("menus")) { menuVersion = menu.optInt("version"); macMenus = menu.optJSONArray("menus"); if (macMenus == null) macMenus = new JSONArray(); }
                    else if (menu.optInt("answering", -1) == pendingMenuToken && menu.has("items")) {
                        pendingMenuToken = 0; JSONArray items = menu.optJSONArray("items");
                        if (items != null) showMenu(items, "菜单", menu.optInt("version"));
                    }
                    if (menu.has("note")) show(menu.optString("note"));
                });
            }
            case 32 -> {
                if (clipboardEnabled) {
                    JSONObject clip = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                    String text = clip.optString("text", "");
                    if (!text.isEmpty() && text.getBytes(StandardCharsets.UTF_8).length <= 256 * 1024) ui.post(() -> {
                        android.content.ClipboardManager manager = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        manager.setPrimaryClip(android.content.ClipData.newPlainText("Mac", text));
                    });
                }
            }
            case 43 -> { if (audio != null) audio.receive(message.payload()); }
            case 0 -> {
                Hevc.Parameters params = Hevc.parameters(message.payload()); nalSize = params.lengthSize();
                releaseDecoder(); Surface target;
                synchronized (surfaceLock) {
                    long until = android.os.SystemClock.uptimeMillis() + 3000;
                    while (surface == null && android.os.SystemClock.uptimeMillis() < until) surfaceLock.wait(100);
                    target = surface;
                }
                if (target == null || !target.isValid()) throw new IllegalStateException("视频画面不可用");
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(params.codecConfig()));
                decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
                decoder.configure(format, target, null, 0); decoder.start();
                decoderSurface = target;
                decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                waitingForKeyframe = true;
            }
            case 1 -> {
                if (decoder == null) return;
                Surface target = surface;
                if (target == null || !target.isValid()) { waitingForKeyframe = true; return; }
                if (target != decoderSurface) {
                    decoder.setOutputSurface(target); decoderSurface = target; waitingForKeyframe = true;
                    synchronized (outputLock) { if (connection != null) connection.send(44, new byte[0]); }
                }
                drainVideo();
                if (waitingForKeyframe && !message.keyframe()) { requestKeyframe(); return; }
                byte[] frame = Hevc.frame(message.payload(), nalSize);
                int index = decoder.dequeueInputBuffer(10_000);
                if (index >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(index); input.clear();
                    if (frame.length > input.remaining()) throw new IllegalStateException("视频帧超过解码缓冲区");
                    input.put(frame); decoder.queueInputBuffer(index, 0, frame.length, (long) (message.timestamp() * 1_000_000), 0);
                    waitingForKeyframe = false;
                } else {
                    waitingForKeyframe = true;
                    requestKeyframe();
                }
                drainVideo();
            }
            case 13 -> { if (waitingForKeyframe && decoder != null && surface != null) requestKeyframe(); }
            case 22 -> throw new IllegalStateException(new String(message.payload(), StandardCharsets.UTF_8));
        }
    }
    private void requestKeyframe() throws java.io.IOException {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastKeyframeRequest < 2000) return;
        lastKeyframeRequest = now;
        synchronized (outputLock) { if (connection != null) connection.send(44, new byte[0]); }
    }
    private void drainVideo() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (decoder != null) {
            int out = decoder.dequeueOutputBuffer(info, 0);
            if (out >= 0) decoder.releaseOutputBuffer(out, true);
            else if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = decoder.getOutputFormat();
                int w = format.getInteger("width"), h = format.getInteger("height");
                if (format.containsKey("crop-left") && format.containsKey("crop-right")) w = format.getInteger("crop-right") - format.getInteger("crop-left") + 1;
                if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) h = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1;
                if (w > 0 && h > 0) { videoWidth = w; videoHeight = h; ui.post(() -> { layoutVideo(); cursor.invalidate(); }); }
            } else break;
        }
    }
    private void addChoice(String title, JSONObject source) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setContentDescription(title);
        tile.setBackground(shape(0xff272a30, 10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(100), dp(66)); p.setMargins(0, dp(3), dp(6), dp(3)); choices.addView(tile, p);
        android.widget.ImageView image = new android.widget.ImageView(this); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        tile.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView label = new TextView(this); label.setText(title); label.setSingleLine(true); label.setTextSize(10); label.setTextColor(-1);
        tile.addView(label); if (source.length() != 0) thumbnails.put(source.optLong("_0"), image);
        tile.setOnClickListener(v -> select(source, source.length() == 0));
        if (source.length() != 0) tile.setOnLongClickListener(v -> {
            String[] labels = {"最小化", "全屏", "关闭窗口"};
            String[] actions = {"minimize", "fullScreen", "close"};
            new android.app.AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, index) -> {
                try { sendAsync(15, new JSONObject().put("id", source.getLong("_0")).put("action", actions[index])); }
                catch (Exception e) { show("窗口操作失败"); }
            }).show(); return true;
        });
    }
    private void releaseDecoder() { if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) { } decoder.release(); decoder = null; decoderSurface = null; } }
    @Override protected void onStart() { super.onStart(); if (discovery != null) discovery.start(); }
    @Override protected void onStop() { if (discovery != null) discovery.stop(); nearby.clear(); nearbyMacs.removeAllViews(); super.onStop(); }
    @Override protected void onDestroy() {
        running = false; SillConnection c = connection; if (c != null) try { c.close(); } catch (Exception ignored) { }
        network.shutdownNow(); input.shutdownNow(); heartbeat.shutdownNow(); super.onDestroy();
    }
}
