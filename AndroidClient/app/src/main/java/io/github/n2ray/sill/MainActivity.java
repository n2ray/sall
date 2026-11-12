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
    private final Object reconnectLock = new Object();
    private volatile SillConnection connection;
    private volatile HostLockState hostLock;
    private volatile PointerSync pointerSync;
    private volatile boolean running;
    private volatile Surface surface;
    private LinearLayout root, connectionPanel, sessionPanel, choices, savedMacs, nearbyMacs, keyRow;
    private ScrollView connectionContainer;
    private TextView status, sessionStatus;
    private EditText host, port, code;
    private RemoteKeyboard keyboard;
    private TrackpadView trackpad;
    private android.widget.FrameLayout picture;
    private SessionScreenAwake screenAwake;
    private SurfaceView video;
    private TextView lockOverlay;
    private android.view.View cursor;
    private PictureInput pictureInput;
    private android.graphics.Bitmap cursorBitmap;
    private CursorShapeBlob.Shape cursorShape;
    private final java.util.Map<Long, android.widget.ImageView> thumbnails = new java.util.HashMap<>();
    private final java.util.Map<Long, android.graphics.Bitmap> thumbnailImages = new java.util.HashMap<>();
    private final java.util.Map<String, android.graphics.Bitmap> appIcons = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, java.util.List<android.widget.ImageView>> iconBadges = new java.util.HashMap<>();
    private long activeWindowId = -1;
    private final PictureZoom zoom = new PictureZoom();
    private final Object surfaceLock = new Object();
    private volatile boolean clipboardEnabled;
    private volatile boolean hostClipboardAvailable;
    private final ClipboardReceipt clipboardReceipt = new ClipboardReceipt();
    private long clipboardToken;
    private volatile String clipboardSource = "";
    private boolean audioEnabled;
    private AudioPlayer audio;
    private volatile boolean clipboardSubscribed;
    private Double textScale;
    private final Runnable viewportUpdate = this::sendViewport;
    private int desktopDisplays = -1, desktopDisplay, hostGestures;
    private boolean desktopActive;
    private Button desktopButton, menuButton;
    private volatile boolean closing;
    private long modifiers;
    private long heldModifiers;
    private final java.util.Map<Long, android.view.View> modifierViews = new java.util.LinkedHashMap<>();
    private final HardwareKeyFlow hardwareKeys = new HardwareKeyFlow();
    private SillConnection hardwareKeySession;
    private volatile boolean hostKeyRepeat;
    private volatile boolean hostKeyRepeatKnown;
    private JSONArray appList = new JSONArray(), macMenus = new JSONArray();
    private JSONArray drawerWindows = new JSONArray();
    private AppDrawerDialog appDrawer;
    private final Runnable drawerUpdate = () -> {
        if (appDrawer != null && appDrawer.isShowing()) appDrawer.update(drawerWindows, appList, appIcons, activeWindowId);
    };
    private int menuVersion, menuToken, pendingMenuToken;
    private JSONObject hostSettings;
    private int settingsToken, pendingSettingsToken;
    private String pendingSettingsField;
    private Object pendingSettingsValue;
    private MacDiscovery discovery;
    private final java.util.Map<String, android.view.View> nearby = new java.util.HashMap<>();
    private volatile MediaCodec decoder;
    private Surface decoderSurface;
    private Hevc.Parameters lastParameters;
    private record FreshFrame(long generation, long presentationUs) { }
    private final java.util.concurrent.atomic.AtomicReference<FreshFrame> revealFrame = new java.util.concurrent.atomic.AtomicReference<>();
    private long recoveryGeneration = -1;
    private int nalSize;
    private volatile int videoWidth = 16, videoHeight = 10;
    private boolean waitingForKeyframe;
    private long lastKeyframeRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad); setContentView(root); screenAwake = new SessionScreenAwake(root);
        if (android.os.Build.VERSION.SDK_INT >= 30) root.post(() -> {
            android.view.WindowInsetsController controller = root.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        });
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
            if ((r - l) != (oright - ol) || (b - t) != (ob - ot)) picture.post(() -> {
                layoutVideo(); scheduleViewport();
            });
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
                FrameGeometry.Rect frame = zoom.frame(getWidth(), getHeight(), videoWidth, videoHeight);
                float x = frame.left() + trackpad.pointerX() * frame.width(), y = frame.top() + trackpad.pointerY() * frame.height();
                if (cursorBitmap != null && cursorShape != null) {
                    float left = x - dp(cursorShape.hotX()), top = y - dp(cursorShape.hotY());
                    paint.setStyle(android.graphics.Paint.Style.FILL); paint.setColor(android.graphics.Color.WHITE);
                    canvas.drawBitmap(cursorBitmap, null, new android.graphics.RectF(left, top,
                            left + dp(cursorShape.width()), top + dp(cursorShape.height())), paint);
                    return;
                }
                android.graphics.Path arrow = new android.graphics.Path(); arrow.moveTo(x, y); arrow.lineTo(x + dp(5), y + dp(16));
                arrow.lineTo(x + dp(8), y + dp(10)); arrow.lineTo(x + dp(13), y + dp(9)); arrow.close();
                paint.setColor(android.graphics.Color.BLACK); paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); canvas.drawPath(arrow, paint);
                paint.setColor(android.graphics.Color.WHITE); paint.setStyle(android.graphics.Paint.Style.FILL); canvas.drawPath(arrow, paint);
            }
        };
        picture.addView(cursor, new android.widget.FrameLayout.LayoutParams(-1, -1));
        lockOverlay = new TextView(this); lockOverlay.setGravity(android.view.Gravity.CENTER);
        lockOverlay.setTextColor(0xffd6d9de); lockOverlay.setTextSize(16); lockOverlay.setPadding(dp(20), dp(20), dp(20), dp(20));
        lockOverlay.setBackgroundColor(0xff111216); lockOverlay.setText("正在等待 Mac 画面…");
        picture.addView(lockOverlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        pictureInput = new PictureInput(cursor, zoom, new PictureInput.Listener() {
            public boolean beginPicture() {
                if (trackpad == null) return false;
                trackpad.setBeforeTwoFinger(pictureInput::stopMomentum); return trackpad.beginPicture();
            }
            public void endPicture() { if (trackpad != null) trackpad.endPicture(); }
            public boolean connected() { return connection != null; }
            public boolean reverseScrolling() { return getPreferences(0).getBoolean("reverseScrolling", false); }
            public FrameGeometry.Rect frame() { return zoom.frame(cursor.getWidth(), cursor.getHeight(), videoWidth, videoHeight); }
            public void pointer(String action, float x, float y) {
                trackpad.position(x, y); cursor.invalidate(); MainActivity.this.pointer(action, x, y);
            }
            public void scroll(String phase, float x, float y, float dx, float dy) { sendScroll(phase, x, y, dx, dy); }
            public void gesture(String name, int fingers) {
                SillConnection expected = connection;
                ui.post(() -> { if (expected != null && connection == expected) sendMacGesture(name, fingers); });
            }
            public void zoomChanged() { layoutVideo(); cursor.invalidate(); }
        });
        cursor.setOnTouchListener(pictureInput);
        cursor.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(android.view.View view) { }
            public void onViewDetachedFromWindow(android.view.View view) { pictureInput.cancel(); }
        });
        LinearLayout bar = new LinearLayout(this); sessionPanel.addView(bar);
        Button appsButton = barButton(bar, "应用", v -> showWindows());
        menuButton = barButton(bar, "菜单", v -> showMenu(macMenus, "Mac 菜单", menuVersion));
        Button scaleButton = barButton(bar, "Aa", v -> chooseScale());
        Button keyboardButton = barButton(bar, "键盘", v -> toggleKeyboard());
        desktopButton = barButton(bar, "桌面", v -> tapDesktop());
        Button settingsButton = barButton(bar, "设置", v -> settings());
        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false); choices = new LinearLayout(this); choices.setOrientation(LinearLayout.HORIZONTAL);
        strip.addView(choices);
        LinearLayout stripRow = new LinearLayout(this); stripRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.removeView(menuButton); stripRow.addView(strip, new LinearLayout.LayoutParams(0, -1, 1));
        stripRow.addView(menuButton, new LinearLayout.LayoutParams(dp(50), dp(50)));
        sessionPanel.addView(stripRow, new LinearLayout.LayoutParams(-1, dp(62)));
        LinearLayout keys = new LinearLayout(this); keyRow = keys; sessionPanel.addView(keys);
        barButton(keys, "esc", v -> { keyboard.finishAndForget(); tapKey(41); });
        barButton(keys, "tab", v -> { keyboard.finishAndForget(); tapKey(43); });
        modifier(keys, "ctrl", 1L << 18); modifier(keys, "opt", 1L << 19);
        modifier(keys, "cmd", 1L << 20); modifier(keys, "shift", 1L << 17);
        trackpad = new TrackpadView(this, new TrackpadView.Listener() {
            public void pointer(String action, float x, float y) { MainActivity.this.pointer(action, x, y); cursor.invalidate(); }
            public void scroll(String phase, float x, float y, float dx, float dy) { sendScroll(phase, x, y, dx, dy); }
            public void gesture(String name, int fingers) {
                SillConnection expected = connection;
                ui.post(() -> { if (expected != null && connection == expected) sendMacGesture(name, fingers); });
            }
        });
        trackpad.setReverseScrolling(getPreferences(0).getBoolean("reverseScrolling", false));
        sessionPanel.addView(trackpad, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
            boolean landscape = r - l > b - t;
            if (oright > ol && ob > ot && landscape != (oright - ol > ob - ot) && pictureInput != null) pictureInput.cancel();
            int visible = landscape ? android.view.View.GONE : android.view.View.VISIBLE;
            sessionStatus.setVisibility(visible); keys.setVisibility(visible); trackpad.setVisibility(visible);
            stripRow.setVisibility(visible);
            if (landscape && strip.getParent() != bar) {
                stripRow.removeView(strip); stripRow.removeView(menuButton); sessionPanel.removeView(bar);
                bar.addView(strip, 1, new LinearLayout.LayoutParams(0, dp(52), 1));
                bar.addView(menuButton, 2, new LinearLayout.LayoutParams(dp(48), dp(46)));
                sessionPanel.addView(bar, 1);
            } else if (!landscape && strip.getParent() != stripRow) {
                bar.removeView(strip); bar.removeView(menuButton); sessionPanel.removeView(bar);
                stripRow.addView(strip, new LinearLayout.LayoutParams(0, -1, 1));
                stripRow.addView(menuButton, new LinearLayout.LayoutParams(dp(50), dp(50)));
                sessionPanel.addView(bar, 2);
            }
            int width = r - l - root.getPaddingLeft() - root.getPaddingRight();
            int height = b - t - root.getPaddingTop() - root.getPaddingBottom();
            refreshMenuButton();
            for (Button button : new Button[]{appsButton, scaleButton, keyboardButton, desktopButton, settingsButton}) {
                LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) button.getLayoutParams();
                int bw = landscape ? dp(48) : 0; float weight = landscape ? 0 : 1;
                if (bp.width != bw || bp.weight != weight || bp.height != dp(50)) {
                    bp.width = bw; bp.weight = weight; bp.height = dp(50); button.setLayoutParams(bp);
                }
            }
            int wanted = landscape ? 0 : PhoneStreamLayout.pictureHeight(width, height, dp(200), dp(120));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) picture.getLayoutParams();
            int wantedWidth = -1; float weight = landscape ? 1 : 0;
            if (params.height != wanted || params.width != wantedWidth || params.weight != weight) {
                params.height = wanted; params.width = wantedWidth; params.weight = weight; picture.setLayoutParams(params);
            }
        });
        keyboard = new RemoteKeyboard(this, new RemoteKeyboard.Listener() {
            public void text(String value) { screenAwake.personActed(); sendText(value); }
            public void key(int usage, boolean down) { if (down) { screenAwake.personActed(); pressModifiers(); } sendKey(usage, down); if (!down) releaseModifiers(); }
        });
        sessionPanel.addView(keyboard, new LinearLayout.LayoutParams(1, 1));
    }
    private Button barButton(LinearLayout row, String label, android.view.View.OnClickListener action) {
        Button b = button(row, label, action); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
        p.setMargins(dp(2), dp(3), dp(2), dp(3)); b.setLayoutParams(p); b.setPadding(0, 0, 0, 0);
        return b;
    }
    private void toggleKeyboard() {
        android.view.inputmethod.InputMethodManager manager = (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        android.view.WindowInsets insets = root.getRootWindowInsets();
        boolean visible = android.os.Build.VERSION.SDK_INT >= 30 && insets != null
                ? insets.isVisible(android.view.WindowInsets.Type.ime()) : keyboard.hasFocus();
        if (visible) { manager.hideSoftInputFromWindow(keyboard.getWindowToken(), 0); keyboard.clearFocus(); }
        else { keyboard.requestFocus(); manager.showSoftInput(keyboard, 0); }
    }
    private void refreshMenuButton() {
        if (menuButton == null || root == null) return;
        int width = root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();
        boolean landscape = root.getWidth() > root.getHeight();
        boolean fits = width >= dp(landscape ? 6 * 48 + 100 : 320);
        menuButton.setVisibility(fits && macMenus.length() > 0 ? android.view.View.VISIBLE : android.view.View.GONE);
    }
    private void modifier(LinearLayout row, String label, long flag) {
        Button button = barButton(row, label, v -> {
            modifiers ^= flag; boolean down = (modifiers & flag) != 0;
            v.setBackground(shape(down ? 0xff486993 : 0xff272a30, 12));
        });
        modifierViews.put(flag, button);
    }
    private int modifierUsage(long flag) {
        return flag == (1L << 18) ? 0xe0 : flag == (1L << 17) ? 0xe1 : flag == (1L << 19) ? 0xe2 : 0xe3;
    }
    private void pressModifiers() {
        for (long flag : modifierViews.keySet()) {
            if ((modifiers & flag) != 0 && (heldModifiers & flag) == 0) {
                heldModifiers |= flag; sendKey(modifierUsage(flag), true, heldModifiers);
            }
        }
    }
    private void releaseModifiers() {
        for (var entry : modifierViews.entrySet()) {
            long flag = entry.getKey();
            if ((heldModifiers & flag) != 0) { heldModifiers &= ~flag; sendKey(modifierUsage(flag), false, heldModifiers); }
            if ((modifiers & flag) == 0) continue;
            modifiers &= ~flag;
            entry.getValue().setBackground(shape(0xff272a30, 12));
        }
    }
    private void sendKey(int usage, boolean down) {
        sendKey(usage, down, modifiers);
    }
    private void sendKey(int usage, boolean down, long flags) {
        try { sendAsync(8, new JSONObject().put("key", new JSONObject().put("hidUsage", usage).put("down", down).put("modifiers", flags))); }
        catch (Exception e) { show("按键发送失败"); }
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        android.view.View focused = getCurrentFocus();
        if (!HardwareKeyGate.sendRemote(connection != null, hasWindowFocus(), sessionPanel.getVisibility() == android.view.View.VISIBLE,
                focused != null && focused != keyboard && focused.onCheckIsTextEditor())) {
            releaseHardwareKeys();
            return super.dispatchKeyEvent(event);
        }
        if (event.getDeviceId() < 0 || (event.getSource() & android.view.InputDevice.SOURCE_KEYBOARD) != android.view.InputDevice.SOURCE_KEYBOARD)
            return super.dispatchKeyEvent(event);
        if (event.getAction() != android.view.KeyEvent.ACTION_DOWN && event.getAction() != android.view.KeyEvent.ACTION_UP)
            return super.dispatchKeyEvent(event);
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) screenAwake.personActed();
        if (hardwareKeySession != connection) { hardwareKeys.release(); hardwareKeySession = connection; }
        int code = event.getKeyCode(), usage;
        if (code >= android.view.KeyEvent.KEYCODE_A && code <= android.view.KeyEvent.KEYCODE_Z) usage = 4 + code - android.view.KeyEvent.KEYCODE_A;
        else if (code >= android.view.KeyEvent.KEYCODE_1 && code <= android.view.KeyEvent.KEYCODE_9) usage = 30 + code - android.view.KeyEvent.KEYCODE_1;
        else if (code >= android.view.KeyEvent.KEYCODE_F1 && code <= android.view.KeyEvent.KEYCODE_F12) usage = 58 + code - android.view.KeyEvent.KEYCODE_F1;
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
            case android.view.KeyEvent.KEYCODE_MOVE_HOME -> 74; case android.view.KeyEvent.KEYCODE_PAGE_UP -> 75;
            case android.view.KeyEvent.KEYCODE_MOVE_END -> 77; case android.view.KeyEvent.KEYCODE_PAGE_DOWN -> 78;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 79; case android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 80;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 81; case android.view.KeyEvent.KEYCODE_DPAD_UP -> 82;
            case android.view.KeyEvent.KEYCODE_CTRL_LEFT -> 0xe0; case android.view.KeyEvent.KEYCODE_SHIFT_LEFT -> 0xe1;
            case android.view.KeyEvent.KEYCODE_ALT_LEFT -> 0xe2; case android.view.KeyEvent.KEYCODE_META_LEFT -> 0xe3;
            case android.view.KeyEvent.KEYCODE_CTRL_RIGHT -> 0xe4; case android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xe5;
            case android.view.KeyEvent.KEYCODE_ALT_RIGHT -> 0xe6; case android.view.KeyEvent.KEYCODE_META_RIGHT -> 0xe7;
            default -> 0;
        };
        if (usage == 0) return super.dispatchKeyEvent(event);
        long identity = ((long) event.getDeviceId() << 32) | (code & 0xffffffffL);
        boolean down = event.getAction() == android.view.KeyEvent.ACTION_DOWN;
        HardwareKeyFlow.Action action = down
                ? hardwareKeys.down(identity, usage, keyboard.hasFocus() && keyboard.isComposing(), !hostKeyRepeatKnown || hostKeyRepeat, event.getRepeatCount())
                : hardwareKeys.up(identity);
        if (action == HardwareKeyFlow.Action.INPUT_METHOD) return super.dispatchKeyEvent(event);
        if (action == HardwareKeyFlow.Action.IGNORE) return true;
        long flags = modifiers | (event.isShiftPressed() ? 1L << 17 : 0) | (event.isCtrlPressed() ? 1L << 18 : 0)
                | (event.isAltPressed() ? 1L << 19 : 0) | (event.isMetaPressed() ? 1L << 20 : 0);
        if (action == HardwareKeyFlow.Action.REMOTE_REPEAT) { sendKey(usage, false, flags); sendKey(usage, true, flags); return true; }
        if (action == HardwareKeyFlow.Action.REMOTE_DOWN && usage < 0xe0) { keyboard.finishAndForget(); pressModifiers(); }
        sendKey(usage, down, flags); if (!down && usage < 0xe0) releaseModifiers(); return true;
    }
    private void releaseHardwareKeys() {
        java.util.List<Integer> released = hardwareKeys.release();
        if (connection != null && connection == hardwareKeySession)
            for (int usage : released) sendKey(usage, false, 0);
        hardwareKeySession = null;
        if (!released.isEmpty()) releaseModifiers();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) clipboardReceipt.cancel();
        if (!hasFocus) releaseHardwareKeys();
    }
    private void tapKey(int usage) { pressModifiers(); sendKey(usage, true); sendKey(usage, false); releaseModifiers(); }
    private void sendText(String text) {
        if ((modifiers & ((1L << 18) | (1L << 19) | (1L << 20))) != 0 && text.length() == 1) {
            char c = Character.toLowerCase(text.charAt(0));
            int usage = c >= 'a' && c <= 'z' ? 4 + c - 'a' : c >= '1' && c <= '9' ? 30 + c - '1' : c == '0' ? 39 : 0;
            if (usage != 0) { tapKey(usage); return; }
        }
        try { pressModifiers(); sendAsync(8, new JSONObject().put("text", new JSONObject().put("_0", text))); releaseModifiers(); }
        catch (Exception e) { show("文字发送失败"); }
    }
    private void pointer(String action, float x, float y) {
        try { if (action.endsWith("Down")) { keyboard.finishAndForget(); pressModifiers(); } sendAsync(8, new JSONObject().put("pointer", new JSONObject().put("_0", action).put("x", x).put("y", y))); if (action.endsWith("Up")) releaseModifiers(); }
        catch (Exception e) { show("指针发送失败"); }
    }
    private void sendScroll(String phase, float x, float y, float dx, float dy) {
        try {
            JSONObject body = new JSONObject().put("x", x).put("y", y);
            if (phase != null) body.put("_0", phase); else { body.put("dx", dx); body.put("dy", dy); }
            sendAsync(8, new JSONObject().put(phase == null ? "scroll" : "scrollGesture", body));
        } catch (Exception e) { show("滚动发送失败"); }
    }
    private void sendMacGesture(String name, int fingers) {
        if (connection == null || hostGestures < 1 || !getPreferences(0).getBoolean("macGestures", true)) return;
        try {
            if (activeWindowId >= 0) select(new JSONObject(), true);
            sendAsync(28, new JSONObject().put("gesture", name).put("fingers", fingers));
        } catch (Exception e) { show("手势发送失败"); }
    }
    private void select(JSONObject source, boolean desktop) {
        pictureInput.cancel();
        trackpad.cancelGestures();
        try { clipboardReceipt.cancel(); keyboard.finishAndForget(); zoom.reset(); layoutVideo(); cursor.invalidate(); sendViewport(); sendAsync(6, new JSONObject().put(desktop ? "desktop" : "window", source)); }
        catch (Exception e) { show("选择窗口失败"); }
    }
    private void tapDesktop() {
        pictureInput.cancel();
        trackpad.cancelGestures();
        if (DesktopNavigation.next(desktopActive, desktopDisplays, desktopDisplay)) { clipboardReceipt.cancel(); keyboard.finishAndForget(); sendAsync(47, new JSONObject(), true); }
        else select(new JSONObject(), true);
    }
    private void showWindows() {
        if (connection == null) return;
        if (appDrawer == null) appDrawer = new AppDrawerDialog(this, new AppDrawerDialog.Listener() {
            public void selectWindow(long id) { try { select(new JSONObject().put("_0", id), false); } catch (Exception e) { show("无法选择窗口"); } }
            public void launchApp(String bundleId) {
                try { clipboardReceipt.cancel(); keyboard.finishAndForget(); sendAsync(7, new JSONObject().put("bundleID", bundleId)); }
                catch (Exception e) { show("无法打开应用"); }
            }
        });
        appDrawer.show(drawerWindows, appList, appIcons, activeWindowId);
    }
    private void refreshDrawer() { ui.removeCallbacks(drawerUpdate); ui.postDelayed(drawerUpdate, 250); }
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
        if (desktopActive) { show("文字大小只用于调整单个窗口；桌面保持原来的尺寸。"); return; }
        String[] labels = {"不调整", "0.5×", "0.6×", "0.75×", "1×", "1.25×", "1.5×"};
        new android.app.AlertDialog.Builder(this).setTitle("文字大小").setItems(labels, (dialog, index) -> {
            textScale = index == 0 ? null : new double[]{0, .5, .6, .75, 1, 1.25, 1.5}[index]; scheduleViewport();
        }).show();
    }
    private void scheduleViewport() {
        ui.removeCallbacks(viewportUpdate); ui.postDelayed(viewportUpdate, 250);
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
        FrameGeometry.Rect enlarged = zoom.frame(picture.getWidth(), picture.getHeight(), videoWidth, videoHeight);
        float factor = enlarged.width() / frame.width();
        video.setScaleX(factor); video.setScaleY(factor);
        video.setTranslationX(enlarged.left() + enlarged.width() / 2 - picture.getWidth() / 2f);
        video.setTranslationY(enlarged.top() + enlarged.height() / 2 - picture.getHeight() / 2f);
    }
    private void settings() {
        boolean reverse = getPreferences(0).getBoolean("reverseScrolling", false);
        boolean gestures = getPreferences(0).getBoolean("macGestures", true);
        String[] items = {"Mac 画质和帧率", clipboardEnabled ? "关闭剪贴板共享" : "开启剪贴板共享", "将手机剪贴板粘贴到 Mac", audioEnabled ? "关闭声音（重新连接后生效）" : "开启声音测试（重新连接后生效）", reverse ? "滚动方向：反向" : "滚动方向：自然", gestures ? "关闭多指 Mac 手势" : "开启多指 Mac 手势", "断开连接"};
        new android.app.AlertDialog.Builder(this).setTitle("设置").setItems(items, (dialog, i) -> {
            if (i == 0) hostSettingsMenu();
            if (i == 1) { clipboardEnabled = !clipboardEnabled; getPreferences(0).edit().putBoolean("shareClipboard", clipboardEnabled).apply(); updateClipboard(); }
            if (i == 2) pasteClipboard();
            if (i == 3) {
                if (!audioEnabled && !AudioPlayer.supported()) { show("此设备没有报告支持 AAC-ELD 解码"); return; }
                audioEnabled = !audioEnabled; getPreferences(0).edit().putBoolean("experimentalAudio", audioEnabled).apply();
                show("声音设置将在下次连接时生效");
            }
            if (i == 4) { boolean next = !reverse; getPreferences(0).edit().putBoolean("reverseScrolling", next).apply(); trackpad.setReverseScrolling(next); show(next ? "已启用反向滚动" : "已启用自然滚动"); }
            if (i == 5) { boolean next = !gestures; getPreferences(0).edit().putBoolean("macGestures", next).apply(); trackpad.setMacGesturesEnabled(next && hostGestures >= 1); show(next ? "已开启多指手势" : "已关闭多指手势"); }
            if (i == 6) disconnect();
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
        if (!clipboardEnabled) clipboardReceipt.cancel();
        if (connection == null) { clipboardSubscribed = false; return; }
        if (clipboardEnabled && !hostClipboardAvailable) { clipboardSubscribed = false; return; }
        try { sendAsync(32, clipboardEnabled ? new JSONObject() : new JSONObject().put("stop", true)); clipboardSubscribed = clipboardEnabled; }
        catch (Exception e) { show("剪贴板设置失败"); }
    }
    private void pasteClipboard() {
        if (!clipboardEnabled) { show("请先开启剪贴板共享"); return; }
        SillConnection expected = connection;
        if (expected == null || !hostClipboardAvailable || clipboardSource.isEmpty() || clipboardSource.contains("\"none\"")) { show("Mac 未开启剪贴板共享或没有正在显示的窗口"); return; }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (!clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) return;
        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0 || text.toString().getBytes(StandardCharsets.UTF_8).length > 256 * 1024) return;
        try {
            keyboard.finishAndForget();
            byte[] payload = new JSONObject().put("text", text.toString()).toString().getBytes(StandardCharsets.UTF_8);
            if (payload.length > StreamWire.MAX_OUTGOING) { show("剪贴板文字过长"); return; }
            byte[] nonce = new byte[16]; new java.security.SecureRandom().nextBytes(nonce);
            long token = clipboardReceipt.begin(nonce, android.os.SystemClock.uptimeMillis()); clipboardToken = token;
            input.execute(() -> {
                try {
                    synchronized (outputLock) {
                        if (connection != expected || !clipboardEnabled || !hostClipboardAvailable || !clipboardReceipt.pending(token)) return;
                        expected.send(32, payload); expected.send(10, nonce);
                    }
                } catch (Exception e) {
                    if (clipboardReceipt.pending(token)) { clipboardReceipt.cancel(token); showFor(expected, "剪贴板发送失败"); }
                }
            });
            ui.postDelayed(() -> { if (clipboardReceipt.expire(token, android.os.SystemClock.uptimeMillis())) showFor(expected, "Mac 未确认剪贴板，请重试"); }, 4000);
        }
        catch (Exception e) { show("剪贴板发送失败"); }
    }
    private void disconnect() {
        pictureInput.cancel();
        trackpad.cancelGestures();
        clipboardReceipt.cancel(); keyboard.finishAndForget(); closing = true; SillConnection c = connection;
        synchronized (reconnectLock) { reconnectLock.notifyAll(); }
        if (c != null) try { c.close(); } catch (Exception ignored) { }
    }

    private void show(String text) { ui.post(() -> { status.setText(text); sessionStatus.setText(text); }); }
    private void showFor(SillConnection expected, String text) {
        ui.post(() -> { if (connection == expected) { status.setText(text); sessionStatus.setText(text); } });
    }
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
                boolean established = false;
                int retries = 0;
                while (running && !closing) {
                    long connectedAt = 0;
                    try {
                        if (!established) show("正在连接…");
                        try (SillConnection session = SillConnection.connect(address, number, "sill/1", pin)) {
                            if (!running || closing) break;
                            HostLockState sessionLock = new HostLockState(false); sessionLock.requireFreshFrame();
                            hostLock = sessionLock; lastParameters = null; revealFrame.set(null); recoveryGeneration = -1;
                            pointerSync = new PointerSync(); connection = session; session.sessionTimeout();
                            ui.post(() -> { if (connection == session) { renderLockOverlay(sessionLock, session); connectionContainer.setVisibility(android.view.View.GONE); sessionPanel.setVisibility(android.view.View.VISIBLE); picture.post(this::sendViewport); } });
                            JSONObject hello = new JSONObject().put("protocol", 1).put("appVersion", "0.1.0").put("device", android.os.Build.MODEL);
                            if (audioEnabled && AudioPlayer.supported()) { audio = new AudioPlayer(text -> showFor(session, text)); hello.put("audio", new JSONArray().put("aac-eld")); }
                            sendJson(23, hello);
                            sendJson(27, new JSONObject());
                            var ping = heartbeat.scheduleAtFixedRate(() -> {
                                try { synchronized (outputLock) { session.send(10, ByteBuffer.allocate(8).putDouble(System.nanoTime() / 1e9).array()); } }
                                catch (Exception e) { try { session.close(); } catch (Exception ignored) { } }
                            }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
                            established = true; connectedAt = android.os.SystemClock.uptimeMillis();
                            show("已连接，选择一个窗口或桌面。");
                            try { while (running && !closing) handle(session.read()); } finally { ping.cancel(false); }
                        }
                    } catch (Exception error) {
                        if (!running || closing) break;
                        if (connectedAt != 0 && android.os.SystemClock.uptimeMillis() - connectedAt >= 10000) retries = 0;
                        long delay = ReconnectPolicy.delayMillis(retries);
                        if (!established || !ReconnectPolicy.retryable(error) || delay < 0) throw error;
                        retries++;
                        connection = null; hostLock = null; lastParameters = null; revealFrame.set(null); recoveryGeneration = -1; pointerSync = null; clipboardSubscribed = false; hostClipboardAvailable = false; clipboardSource = ""; clipboardReceipt.cancel(); releaseDecoder();
                        if (audio != null) { audio.close(); audio = null; }
                        ui.post(() -> {
                            hostSettings = null; pendingSettingsToken = 0; desktopDisplays = -1; desktopDisplay = 0; desktopActive = false; hostGestures = 0; hostKeyRepeat = false; hostKeyRepeatKnown = false; trackpad.setMacGesturesEnabled(false);
                            desktopButton.setText("桌面"); choices.removeAllViews(); thumbnails.clear(); thumbnailImages.clear(); appIcons.clear(); iconBadges.clear(); activeWindowId = -1;
                            if (appDrawer != null) appDrawer.dismiss(); drawerWindows = new JSONArray(); appList = new JSONArray();
                            screenAwake.setStreaming(false); pictureInput.cancel(); trackpad.cancelGestures(); keyboard.discard(); resetCursorShape();
                            modifiers = heldModifiers = 0; for (android.view.View button : modifierViews.values()) button.setBackground(shape(0xff272a30, 12));
                        });
                        show("连接中断，正在重新连接（" + retries + "/4）…");
                        synchronized (reconnectLock) { if (running && !closing) reconnectLock.wait(delay); }
                    }
                }
            } catch (Exception e) { if (!closing) { android.util.Log.e("SillAndroid", "Connection failed", e); show("连接结束: " + e.getMessage()); } }
            finally {
                connection = null; hostLock = null; lastParameters = null; revealFrame.set(null); recoveryGeneration = -1; pointerSync = null; clipboardSubscribed = false; hostClipboardAvailable = false; clipboardSource = ""; clipboardReceipt.cancel(); releaseDecoder();
                if (audio != null) { audio.close(); audio = null; }
                ui.post(() -> { hostSettings = null; pendingSettingsToken = 0; desktopDisplays = -1; desktopDisplay = 0; desktopActive = false; hostGestures = 0; hostKeyRepeat = false; hostKeyRepeatKnown = false; trackpad.setMacGesturesEnabled(false); desktopButton.setText("桌面"); thumbnailImages.clear(); appIcons.clear(); iconBadges.clear(); activeWindowId = -1; if (appDrawer != null) appDrawer.dismiss(); drawerWindows = new JSONArray(); appList = new JSONArray(); zoom.reset(); screenAwake.setStreaming(false); pictureInput.cancel(); trackpad.cancelGestures(); keyboard.discard(); resetCursorShape(); modifiers = heldModifiers = 0; for (android.view.View button : modifierViews.values()) button.setBackground(shape(0xff272a30, 12)); sessionPanel.setVisibility(android.view.View.GONE); connectionContainer.setVisibility(android.view.View.VISIBLE); running = false; });
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
        if (!allowedForLock(kind, value)) return;
        if (kind == 6 || kind == 7 || kind == 47) clipboardReceipt.cancel();
        if (kind == 8) {
            JSONObject pointer = value.optJSONObject("pointer");
            if (value.has("key") || value.has("text") || (pointer != null && pointer.optString("_0").endsWith("Down"))) clipboardReceipt.cancel();
        }
        SillConnection expected = connection;
        if (expected == null) return;
        PointerSync feed = pointerSync;
        if (kind == 8 && feed != null && connection == expected) feed.inputQueued();
        input.execute(() -> {
            try {
                synchronized (outputLock) {
                    if (connection == expected && allowedForLock(kind, value)) expected.send(kind, empty ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (Exception e) { showFor(expected, "发送失败: " + e.getClass().getSimpleName()); }
        });
    }
    private boolean allowedForLock(int kind, JSONObject value) {
        HostLockState lock = hostLock;
        if (lock == null || lock.maySendControl()) return true;
        boolean control = kind == 6 || kind == 7 || kind == 8 || kind == 15 || kind == 25 || kind == 28 || kind == 32 || kind == 47;
        return !control || isRelease(kind, value);
    }
    private static boolean isRelease(int kind, JSONObject value) {
        if (kind == 32) return value.optBoolean("stop", false);
        if (kind != 8) return false;
        JSONObject key = value.optJSONObject("key"), pointer = value.optJSONObject("pointer"), scroll = value.optJSONObject("scrollGesture");
        return key != null && key.has("down") && !key.optBoolean("down", true)
                || pointer != null && pointer.optString("_0").endsWith("Up")
                || scroll != null && ("ended".equals(scroll.optString("_0")) || "momentumEnded".equals(scroll.optString("_0")));
    }
    private void handle(StreamWire.Message message) throws Exception {
        switch (message.kind()) {
            case 2 -> {
                JSONObject list = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                JSONArray windows = list.getJSONArray("windows");
                HostLockState lock = hostLock; SillConnection listedSession = connection;
                if (lock != null && listedSession != null) {
                    boolean wasCovered = lock.isCovered();
                    boolean blank = lock.onWindowList(list.optBoolean("locked", false), list.optBoolean("offConsole", false),
                            list.optBoolean("offConsoleView", false), android.os.SystemClock.uptimeMillis());
                    if (blank) { clipboardReceipt.cancel(); releaseDecoder(); waitingForKeyframe = true; }
                    if (wasCovered && !lock.isCovered()) { waitingForKeyframe = true; requestKeyframe(); }
                    ui.post(() -> {
                        if (connection != listedSession || hostLock != lock) return;
                        if (blank) { trackpad.cancelGestures(); keyboard.discard(); }
                        renderLockOverlay(lock, listedSession);
                    });
                    if (wasCovered && !lock.isCovered()) ui.postDelayed(() -> {
                        if (connection != listedSession || hostLock != lock) return;
                        lock.onTime(android.os.SystemClock.uptimeMillis()); renderLockOverlay(lock, listedSession);
                    }, HostLockState.UNLOCK_TIMEOUT_MS);
                }
                hostClipboardAvailable = list.optInt("clipboard", 0) >= 1;
                if (!hostClipboardAvailable) { clipboardSubscribed = false; clipboardReceipt.cancel(); }
                if (clipboardEnabled && !clipboardSubscribed && hostClipboardAvailable) updateClipboard();
                SillConnection listConnection = connection;
                ui.post(() -> {
                    if (connection != listConnection || listConnection == null) return;
                    int count = list.optInt("desktopDisplays", 0), current = list.optInt("desktopDisplay", 0);
                    hostGestures = list.optInt("gestures", 0);
                    hostKeyRepeat = list.optInt("keyRepeat", 0) >= 1;
                    hostKeyRepeatKnown = true;
                    trackpad.setMacGesturesEnabled(hostGestures >= 1 && getPreferences(0).getBoolean("macGestures", true));
                    JSONObject active = list.optJSONObject("active"); boolean isDesktop = active != null && active.has("desktop");
                    screenAwake.setStreaming(active != null && (isDesktop || active.has("window")));
                    String source = active == null ? "" : active.toString();
                    if (!source.equals(clipboardSource)) { clipboardReceipt.cancel(); clipboardSource = source; }
                    if (count != desktopDisplays || current != desktopDisplay || isDesktop != desktopActive)
                        android.util.Log.i("SillAndroid", "Host " + list.optString("hostVersion", "unknown") + ": desktop " + current + "/" + count + ", active=" + isDesktop);
                    desktopDisplays = count; desktopDisplay = current; desktopActive = isDesktop;
                    desktopButton.setText(desktopDisplays >= 2 && desktopDisplay > 0 ? "桌面 " + desktopDisplay + "/" + desktopDisplays : "桌面");
                    desktopButton.setContentDescription(desktopDisplays >= 2 ? "桌面 " + desktopDisplay + " / " + desktopDisplays + "，再次点击切换显示器" : "桌面");
                    if (!sameWindowChoices(windows)) {
                        choices.removeAllViews(); thumbnails.clear(); iconBadges.clear();
                        addChoice("整个桌面", new JSONObject(), "");
                        for (int i = 0; i < windows.length(); i++) {
                            JSONObject item = windows.optJSONObject(i); if (item == null) continue;
                            try { addChoice(item.optString("appName") + " — " + item.optString("title"), new JSONObject().put("_0", item.getLong("id")), item.optString("bundleID")); }
                            catch (Exception ignored) { }
                        }
                        thumbnailImages.keySet().retainAll(thumbnails.keySet());
                    }
                    JSONObject selected = active == null ? null : active.optJSONObject("window");
                    long selectedId = selected == null ? -1 : selected.optLong("_0", -1);
                    if (activeWindowId != selectedId) {
                        activeWindowId = selectedId;
                        scheduleViewport();
                    }
                    for (int i = 0; i < choices.getChildCount(); i++) {
                        android.view.View tile = choices.getChildAt(i);
                        boolean chosen = i == 0 ? isDesktop : tile.getTag() instanceof Long && ((Long) tile.getTag()) == selectedId;
                        android.graphics.drawable.GradientDrawable border = shape(0x00000000, 10);
                        border.setStroke(dp(chosen ? 2 : 1), chosen ? 0xff8eb9fb : 0x33ffffff);
                        tile.setForeground(border);
                    }
                    drawerWindows = windows; refreshDrawer();
                });
            }
            case 11 -> {
                byte[] pong = message.payload(); SillConnection expected = connection;
                if (expected == null || pong.length != 16) return;
                ui.post(() -> {
                    long token = clipboardToken;
                    if (connection != expected || !clipboardEnabled || !hostClipboardAvailable ||
                            !clipboardReceipt.receive(pong, android.os.SystemClock.uptimeMillis(), token)) return;
                    String source = clipboardSource;
                    ui.postDelayed(() -> {
                        if (connection != expected || !clipboardEnabled || !hostClipboardAvailable ||
                                !source.equals(clipboardSource)) { clipboardReceipt.cancel(token); return; }
                        input.execute(() -> {
                            try {
                                synchronized (outputLock) {
                                    if (connection != expected || hostLock == null || !hostLock.maySendControl() || !clipboardEnabled || !hostClipboardAvailable ||
                                            !source.equals(clipboardSource) || !clipboardReceipt.take(token)) return;
                                    JSONObject key = new JSONObject().put("hidUsage", 25).put("modifiers", 1L << 20);
                                    byte[] down = new JSONObject().put("key", new JSONObject(key.toString()).put("down", true)).toString().getBytes(StandardCharsets.UTF_8);
                                    byte[] up = new JSONObject().put("key", key.put("down", false)).toString().getBytes(StandardCharsets.UTF_8);
                                    PointerSync feed = pointerSync; if (feed != null) { feed.inputQueued(); feed.inputQueued(); }
                                    expected.send(8, down); expected.send(8, up);
                                }
                                showFor(expected, "已向 Mac 发送粘贴");
                            } catch (Exception e) { showFor(expected, "粘贴失败"); }
                        });
                    }, 50);
                });
            }
            case 3 -> {
                byte[] bytes = message.payload();
                if (bytes.length > 4 && bytes.length <= 2 * 1024 * 1024) {
                    long id = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 0, 4).getInt());
                    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 4, bytes.length - 4, bounds);
                    if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 4096 || bounds.outHeight > 4096) return;
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 256);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 4, bytes.length - 4, options);
                    if (bitmap != null) ui.post(() -> { android.widget.ImageView image = thumbnails.get(id); if (image != null) { thumbnailImages.put(id, bitmap); image.setImageBitmap(bitmap); } });
                }
            }
            case 4 -> {
                byte[] bytes = message.payload(); AppIconBlob.Icon icon = AppIconBlob.parse(bytes);
                if (icon == null) return;
                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeByteArray(bytes, icon.imageOffset(), bytes.length - icon.imageOffset(), bounds);
                if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 2048 || bounds.outHeight > 2048) return;
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                options.inSampleSize = Math.max(1, Math.max(bounds.outWidth, bounds.outHeight) / 96);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, icon.imageOffset(), bytes.length - icon.imageOffset(), options);
                SillConnection expected = connection;
                if (bitmap != null && expected != null) ui.post(() -> {
                    if (connection != expected) return;
                    if (appIcons.size() >= 256 && !appIcons.containsKey(icon.bundleId())) appIcons.remove(appIcons.keySet().iterator().next());
                    appIcons.put(icon.bundleId(), bitmap);
                    java.util.List<android.widget.ImageView> views = iconBadges.get(icon.bundleId());
                    if (views != null) for (android.widget.ImageView badge : views) badge.setImageBitmap(bitmap);
                    refreshDrawer();
                });
            }
            case 5 -> {
                JSONArray apps = new JSONArray(new String(message.payload(), StandardCharsets.UTF_8));
                ui.post(() -> { appList = apps; refreshDrawer(); });
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
                    if (menu.has("menus")) { menuVersion = menu.optInt("version"); macMenus = menu.optJSONArray("menus"); if (macMenus == null) macMenus = new JSONArray(); refreshMenuButton(); }
                    else if (menu.optInt("answering", -1) == pendingMenuToken && menu.has("items")) {
                        pendingMenuToken = 0; JSONArray items = menu.optJSONArray("items");
                        if (items != null) showMenu(items, "菜单", menu.optInt("version"));
                    }
                    if (menu.has("note")) show(menu.optString("note"));
                });
            }
            case 14 -> {
                byte[] data = message.payload();
                CursorShapeBlob.Shape shape = CursorShapeBlob.parse(data);
                if (shape == null) return;
                android.graphics.Bitmap bitmap;
                try { bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 16, data.length - 16); }
                catch (RuntimeException ignored) { return; }
                SillConnection expected = connection;
                if (bitmap != null && expected != null) ui.post(() -> {
                    if (connection != expected) return;
                    cursorBitmap = bitmap; cursorShape = shape; cursor.invalidate();
                });
            }
            case 26 -> {
                JSONObject report = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                long seen = report.optLong("seen", 0);
                boolean inside = report.optBoolean("inside", false);
                double x = report.optDouble("x", Double.NaN), y = report.optDouble("y", Double.NaN);
                PointerSync feed = pointerSync; SillConnection expected = connection;
                if (feed != null && expected != null) ui.post(() -> {
                    if (connection == expected && pointerSync == feed && feed.accepts(seen, inside, x, y)) {
                        trackpad.position((float) x, (float) y); cursor.invalidate();
                    }
                });
            }
            case 32 -> {
                if (clipboardEnabled) {
                    SillConnection expected = connection;
                    JSONObject clip = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                    String text = clip.optString("text", "");
                    if (!text.isEmpty() && text.getBytes(StandardCharsets.UTF_8).length <= 256 * 1024) ui.post(() -> {
                        if (connection != expected || expected == null || !clipboardEnabled || !hasWindowFocus() || sessionPanel.getVisibility() != android.view.View.VISIBLE) return;
                        android.content.ClipboardManager manager = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        manager.setPrimaryClip(android.content.ClipData.newPlainText("Mac", text));
                    });
                }
            }
            case 43 -> { if (audio != null) audio.receive(message.payload()); }
            case 0 -> {
                Hevc.Parameters params = Hevc.parameters(message.payload()); nalSize = params.lengthSize();
                lastParameters = params; releaseDecoder(); waitingForKeyframe = true;
                HostLockState lock = hostLock;
                if (lock == null || !lock.isCovered()) createDecoder(params);
            }
            case 1 -> {
                HostLockState lock = hostLock;
                if (lock == null || lock.isCovered()) return;
                if (decoder != null) drainVideo();
                if (!lock.shouldDecode(message.keyframe(), recoveryGeneration)) { requestKeyframe(); return; }
                if (decoder == null) {
                    if (lastParameters == null) { requestKeyframe(); return; }
                    createDecoder(lastParameters);
                }
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
                    long presentationUs = (long) (message.timestamp() * 1_000_000);
                    if (message.keyframe() && lock.needsFreshFrame() && recoveryGeneration != lock.generation()) {
                        recoveryGeneration = lock.generation(); revealFrame.set(new FreshFrame(recoveryGeneration, presentationUs));
                    }
                    input.put(frame); decoder.queueInputBuffer(index, 0, frame.length, presentationUs, 0);
                    waitingForKeyframe = false;
                } else {
                    waitingForKeyframe = true;
                    requestKeyframe();
                }
                drainVideo();
            }
            case 13 -> {
                if (decoder != null && hostLock != null && hostLock.needsFreshFrame()) drainVideo();
                if (waitingForKeyframe && surface != null) requestKeyframe();
            }
            case 22 -> throw new IllegalStateException(new String(message.payload(), StandardCharsets.UTF_8));
        }
    }
    private void createDecoder(Hevc.Parameters params) throws Exception {
        Surface target;
        synchronized (surfaceLock) {
            long until = android.os.SystemClock.uptimeMillis() + 3000;
            while (surface == null && android.os.SystemClock.uptimeMillis() < until) surfaceLock.wait(100);
            target = surface;
        }
        if (target == null || !target.isValid()) throw new IllegalStateException("视频画面不可用");
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(params.codecConfig()));
        SillConnection expected = connection; HostLockState lock = hostLock;
        MediaCodec made = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        try {
            made.configure(format, target, null, 0);
            made.setOnFrameRenderedListener((codec, timeUs, nanoTime) -> {
                FreshFrame candidate = revealFrame.get();
                if (connection != expected || hostLock != lock || decoder != made || lock == null || candidate == null ||
                        candidate.generation() != lock.generation() || timeUs < candidate.presentationUs()) return;
                if (!revealFrame.compareAndSet(candidate, null)) return;
                lock.onKeyframePresented(candidate.generation());
                renderLockOverlay(lock, expected);
            }, ui);
            made.start(); made.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            decoder = made; decoderSurface = target;
            waitingForKeyframe = true;
        } catch (Exception e) { made.release(); throw e; }
    }
    private void renderLockOverlay(HostLockState lock, SillConnection expected) {
        if (connection != expected || hostLock != lock) return;
        if (!lock.isCovered() && !lock.needsFreshFrame()) { lockOverlay.setVisibility(android.view.View.GONE); return; }
        lockOverlay.setText(lock.isCovered() ? "另一位用户正在使用这台 Mac。请切回你的账号以继续。" : "正在等待 Mac 画面…");
        lockOverlay.setVisibility(android.view.View.VISIBLE);
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
    private boolean sameWindowChoices(JSONArray windows) {
        if (choices.getChildCount() != windows.length() + 1) return false;
        for (int i = 0; i < windows.length(); i++) {
            JSONObject item = windows.optJSONObject(i);
            if (item == null || !item.has("id")) return false;
            android.view.View tile = choices.getChildAt(i + 1);
            String title = item.optString("appName") + " — " + item.optString("title");
            if (!(tile.getTag() instanceof Long) || ((Long) tile.getTag()).longValue() != item.optLong("id", -1)
                    || !title.contentEquals(tile.getContentDescription())) return false;
        }
        return true;
    }
    private void addChoice(String title, JSONObject source, String bundleId) {
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this); tile.setContentDescription(title);
        tile.setBackground(shape(0xff272a30, 10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(100), dp(66)); p.setMargins(0, dp(3), dp(6), dp(3)); choices.addView(tile, p);
        android.widget.ImageView image = new android.widget.ImageView(this); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        tile.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (source.length() != 0) {
            long id = source.optLong("_0"); tile.setTag(id); thumbnails.put(id, image); image.setImageBitmap(thumbnailImages.get(id));
            android.widget.ImageView badge = new android.widget.ImageView(this); badge.setBackground(shape(0xff353942, 6));
            badge.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER); badge.setPadding(dp(2), dp(2), dp(2), dp(2));
            android.widget.FrameLayout.LayoutParams badgeLayout = new android.widget.FrameLayout.LayoutParams(dp(24), dp(24), android.view.Gravity.BOTTOM | android.view.Gravity.START);
            tile.addView(badge, badgeLayout); badge.setImageBitmap(appIcons.get(bundleId));
            iconBadges.computeIfAbsent(bundleId, ignored -> new java.util.ArrayList<>()).add(badge);
        } else { image.setImageResource(android.R.drawable.ic_menu_view); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE); image.setColorFilter(0xffaeb5c0); tile.setContentDescription(title + "（无实时预览）"); }
        tile.setClipToOutline(true);
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
    private void releaseDecoder() { revealFrame.set(null); recoveryGeneration = -1; if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) { } decoder.release(); decoder = null; decoderSurface = null; } }
    private void resetCursorShape() { cursorBitmap = null; cursorShape = null; cursor.invalidate(); }
    @Override protected void onStart() { super.onStart(); if (discovery != null) discovery.start(); }
    @Override protected void onResume() { super.onResume(); if (screenAwake != null) screenAwake.setForeground(true); }
    @Override protected void onPause() { if (screenAwake != null) screenAwake.setForeground(false); super.onPause(); }
    @Override public void onUserInteraction() { super.onUserInteraction(); if (screenAwake != null) screenAwake.personActed(); }
    @Override protected void onStop() { clipboardReceipt.cancel(); if (pictureInput != null) pictureInput.cancel(); if (trackpad != null) trackpad.cancelGestures(); if (discovery != null) discovery.stop(); nearby.clear(); nearbyMacs.removeAllViews(); super.onStop(); }
    @Override protected void onDestroy() {
        if (screenAwake != null) screenAwake.close();
        running = false; SillConnection c = connection; if (c != null) try { c.close(); } catch (Exception ignored) { }
        synchronized (reconnectLock) { reconnectLock.notifyAll(); }
        network.shutdownNow(); input.shutdownNow(); heartbeat.shutdownNow(); super.onDestroy();
    }
}
