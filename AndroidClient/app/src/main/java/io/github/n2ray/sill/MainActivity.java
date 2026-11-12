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
    // Accessed only under outputLock: serializes an automatic first pick with user picks.
    private InitialSourcePolicy initialSource;
    private volatile HostLockState hostLock;
    private volatile PointerSync pointerSync;
    private volatile boolean running;
    private volatile Surface surface;
    private LinearLayout root, connectionPanel, sessionPanel, choices, savedMacs, nearbyMacs, keyRow;
    private ScrollView connectionContainer;
    private SavedMacStore savedMacStore;
    private TextView status, sessionStatus, pictureStatus;
    private boolean pictureStatusActive;
    private int pictureStatusGeneration;
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
    private TextScaleControl scaleControl;
    private final Runnable viewportUpdate = this::sendViewport;
    private Runnable updateSessionLayout;
    private float portraitImeHeight;
    private boolean largerPicture, largerPictureApplied, settingsOpen, settingsHeldLargerPicture, settingsReanchoring, settingsReanchorPending;
    private android.widget.FrameLayout settingsOverlay;
    private android.window.OnBackInvokedCallback settingsBack;
    private android.app.AlertDialog settingsChild;
    private android.app.AlertDialog settingsDialog;
    private boolean settingsRestoreKeyboard;
    private SillConnection settingsSession;
    private int lastViewportFps = -1;
    private boolean fpsObserversStarted, fpsDisplayRegistered, fpsNetworkRegistered, fpsPowerRegistered;
    private final android.hardware.display.DisplayManager.DisplayListener fpsDisplayListener = new android.hardware.display.DisplayManager.DisplayListener() {
        public void onDisplayAdded(int id) { fpsChanged(); }
        public void onDisplayRemoved(int id) { fpsChanged(); }
        public void onDisplayChanged(int id) { fpsChanged(); }
    };
    private final android.net.ConnectivityManager.NetworkCallback fpsNetworkCallback = new android.net.ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(android.net.Network network) { fpsChanged(); }
        @Override public void onLost(android.net.Network network) { fpsChanged(); }
        @Override public void onCapabilitiesChanged(android.net.Network network, android.net.NetworkCapabilities capabilities) { fpsChanged(); }
    };
    private final android.content.BroadcastReceiver fpsPowerReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context context, android.content.Intent intent) { fpsChanged(); }
    };
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
    private android.widget.PopupWindow windowControls;
    private String windowOrderMac = "";
    private java.util.List<Long> windowOrder = new java.util.ArrayList<>();
    private android.view.View draggedWindow;
    private int dragFrom, dragTo;
    private static final int MOVE_LEFT_ACTION = 0x00ff0001, MOVE_RIGHT_ACTION = 0x00ff0002;
    private static final int CLOSE_WINDOW_ACTION = 0x00ff0003, MINIMIZE_WINDOW_ACTION = 0x00ff0004, FULL_SCREEN_ACTION = 0x00ff0005;
    private final Runnable drawerUpdate = () -> {
        if (appDrawer != null && appDrawer.isShowing()) appDrawer.update(drawerWindows, appList, appIcons, activeWindowId, desktopActive);
    };
    private int menuVersion, menuToken, pendingMenuToken;
    private boolean menuStale;
    private Runnable pendingMenuBack;
    private SillConnection menuOwner, pendingMenuOwner;
    private android.app.AlertDialog menuDialog;
    private JSONObject hostSettings;
    private int settingsToken, pendingSettingsToken;
    private String pendingSettingsField;
    private Object pendingSettingsValue;
    private MacDiscovery discovery;
    private final java.util.Map<String, android.view.View> nearby = new java.util.HashMap<>();
    private volatile MediaCodec decoder;
    private volatile Surface decoderSurface;
    private volatile long surfaceEpoch, decoderEpoch = -1;
    private Hevc.Parameters lastParameters;
    private record FreshFrame(long generation, long presentationUs) { }
    private final java.util.concurrent.atomic.AtomicReference<FreshFrame> revealFrame = new java.util.concurrent.atomic.AtomicReference<>();
    private long recoveryGeneration = -1;
    private int nalSize;
    private volatile int videoWidth = 16, videoHeight = 10;
    private boolean waitingForKeyframe;
    private volatile KeyframeRecovery keyframeRecovery;
    private volatile boolean keyframeForeground = true, keyframeStreaming;
    private volatile long keyframeWorstRtt;
    private long keyframeRttWindow;
    private record RecoveryFrame(long generation, long presentationUs) { }
    private final java.util.concurrent.atomic.AtomicReference<RecoveryFrame> recoveryFrame = new java.util.concurrent.atomic.AtomicReference<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        savedMacStore = new SavedMacStore(getPreferences(0));
        largerPicture = getPreferences(0).getBoolean("largerPicture", false);
        portraitImeHeight = getPreferences(0).getFloat("portraitImeHeight", 0);
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
                if (insets.isVisible(android.view.WindowInsets.Type.ime()) && view.getWidth() < view.getHeight()) {
                    float height = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom / getResources().getDisplayMetrics().density;
                    if (height > 0 && Math.abs(height - portraitImeHeight) > 1) {
                        portraitImeHeight = height;
                        getPreferences(0).edit().putFloat("portraitImeHeight", height).apply();
                        if (!settingsOpen && updateSessionLayout != null) view.post(updateSessionLayout);
                    }
                }
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
        button(connectionPanel, "扫描配对码", v -> startActivityForResult(new android.content.Intent(this, PairScannerActivity.class), 102));
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
                    if (savedMacStore.pin(new SavedMacs.Endpoint(address, number)) != null) start(false);
                    else show("请在 Mac 的 Sill 中打开配对窗口并输入配对码。");
                });
                nearby.put(name, row);
            }
            public void lost(String name) { android.view.View row = nearby.remove(name); if (row != null) nearbyMacs.removeView(row); }
        });
        handlePairIntent(getIntent());
    }

    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        handlePairIntent(intent);
        setIntent(intent);
    }

    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 102 || result != RESULT_OK || data == null) return;
        String raw = data.getStringExtra(PairScannerActivity.RESULT_LINK);
        data.removeExtra(PairScannerActivity.RESULT_LINK);
        if (raw != null) handlePairIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(raw)));
    }

    private void handlePairIntent(android.content.Intent intent) {
        if (intent == null || !android.content.Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        String raw = intent.getData().toString();
        intent.setData(null);
        final PairLink link;
        try { link = PairLink.parse(raw); }
        catch (IllegalArgumentException e) { show("无效的 Sill 配对链接。"); return; }
        StringBuilder message = new StringBuilder("请确认这是你自己的 Mac：\n\n").append(link.name).append("\n");
        for (PairLink.Address a : link.addresses) message.append("\n").append(a.display(link.port));
        if (link.addresses.isEmpty()) message.append("\n将在本地网络查找此 Mac。");
        message.append("\n\n仅信任来自你自己 Mac 的配对链接。");
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("确认配对 Mac").setMessage(message.toString())
                .setNegativeButton("取消", (d, which) -> link.clearSecret())
                .setPositiveButton("配对", (d, which) -> pairLink(link))
                .setOnCancelListener(d -> link.clearSecret()).create();
        dialog.show();
    }

    private void pairLink(PairLink link) {
        if (running) { link.clearSecret(); show("已有连接，请先断开后重新打开配对链接。"); return; }
        running = true; closing = false; show("正在配对…");
        if (link.addresses.isEmpty() && discovery.pairingDoors().isEmpty()) {
            discovery.start();
            ui.postDelayed(() -> beginPairLink(link), 4000);
        } else beginPairLink(link);
    }

    private void beginPairLink(PairLink link) {
        if (!running || closing || isFinishing() || isDestroyed()) { link.clearSecret(); return; }
        java.util.List<PairLink.Address> local = link.localCandidates(discovery.pairingDoors());
        java.util.List<PairLink.Address> candidates = link.candidates(local);
        network.execute(() -> {
            PairLink.Address winner = null; SavedMacs.Identity identity = null;
            byte[] secret = link.secret(); byte[] expected = link.fingerprint();
            try {
                for (int i = 0; i < candidates.size(); i++) {
                    PairLink.Address a = candidates.get(i);
                    if (!running || closing || Thread.currentThread().isInterrupted()) break;
                    int number = a.port() == 0 ? link.port : a.port();
                    try (SillConnection pair = i < local.size()
                            ? SillConnection.connect(a.host(), number, "sill-pair/1", expected, 1200, 1800)
                            : SillConnection.connect(a.host(), number, "sill-pair/1", expected)) {
                        identity = pair.pairQrIdentity(secret, android.os.Build.MODEL, android.os.Build.DEVICE);
                        winner = a; break;
                    } catch (Exception ignored) { }
                }
            } finally { java.util.Arrays.fill(secret, (byte) 0); link.clearSecret(); }
            PairLink.Address selected = winner; SavedMacs.Identity authenticated = identity;
            ui.post(() -> {
                if (!running || closing || isFinishing() || isDestroyed()) return;
                running = false;
                if (selected == null || authenticated == null) { show("配对失败，请确认 Mac 的配对链接仍有效且网络可达。"); return; }
                int number = selected.port() == 0 ? link.port : selected.port();
                savedMacStore.paired(authenticated, new SavedMacs.Endpoint(selected.host(), number));
                getPreferences(0).edit().putString("host", selected.host()).putString("port", String.valueOf(number)).apply();
                host.setText(selected.host()); port.setText(String.valueOf(number)); code.setText(""); refreshSavedMacs();
                start(false);
            });
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
        for (SavedMacs.Mac mac : savedMacStore.all()) {
            String label = mac.displayName() + "  ·  " + mac.preferred().display();
            if (mac.addresses().size() > 1) label += "  (" + mac.addresses().size() + " 个地址)";
            Button row = button(savedMacs, label, v -> {
                if (mac.addresses().size() == 1) { connectSavedMac(mac.identity(), mac.preferred()); return; }
                java.util.List<SavedMacs.Endpoint> endpoints = new java.util.ArrayList<>(mac.addresses());
                endpoints.remove(mac.preferred()); endpoints.add(0, mac.preferred());
                String[] labels = endpoints.stream().map(SavedMacs.Endpoint::display).toArray(String[]::new);
                new android.app.AlertDialog.Builder(this).setTitle(mac.displayName()).setItems(labels,
                        (dialog, which) -> connectSavedMac(mac.identity(), endpoints.get(which))).setNegativeButton("取消", null).show();
            });
            row.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(this).setTitle("移除本机记录？")
                    .setMessage("这只移除手机上的记录；如需撤销 Mac 的信任，请在 Mac 的 Sill 设置中移除设备。")
                    .setNegativeButton("取消", null).setPositiveButton("移除", (d, which) -> {
                        savedMacStore.forget(mac.identity()); refreshSavedMacs();
                    }).show(); return true;
            });
        }
    }
    private void connectSavedMac(SavedMacs.Identity identity, SavedMacs.Endpoint endpoint) {
        host.setText(endpoint.host()); port.setText(String.valueOf(endpoint.port())); start(false, identity);
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
                synchronized (surfaceLock) { surface = holder.getSurface(); surfaceEpoch++; surfaceLock.notifyAll(); }
                // A still source may not produce another frame after its output surface returns.
                // Keep recovery owed until a keyframe renders on the new surface.
                recoveryFrame.set(null);
                interruptPicture();
                SillConnection c = connection;
                if (c != null) input.execute(() -> { try { requestKeyframe(); } catch (Exception ignored) { } });
            }
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { synchronized (surfaceLock) { surface = holder.getSurface(); surfaceLock.notifyAll(); } }
            public void surfaceDestroyed(SurfaceHolder holder) {
                synchronized (surfaceLock) { surface = null; surfaceEpoch++; }
                recoveryFrame.set(null);
                interruptPicture();
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
        pictureStatus = new TextView(this);
        pictureStatus.setTextColor(0xfff2f2f4); pictureStatus.setTextSize(12);
        pictureStatus.setGravity(android.view.Gravity.CENTER); pictureStatus.setMaxLines(2);
        pictureStatus.setPadding(dp(14), dp(7), dp(14), dp(7));
        pictureStatus.setBackground(shape(0xeb303238, 30));
        pictureStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        pictureStatus.setVisibility(android.view.View.GONE);
        android.widget.FrameLayout.LayoutParams statusParams = new android.widget.FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
        statusParams.setMargins(dp(16), dp(8), dp(16), 0);
        picture.addView(pictureStatus, statusParams);
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
        TextScaleControl scaleButton = new TextScaleControl(this, new TextScaleControl.Delegate() {
            private SillConnection owner;
            private long window;
            private boolean offered;
            public boolean available() { return connection != null && activeWindowId >= 0 && !desktopActive && (hostLock == null || (!hostLock.isCovered() && !hostLock.viewOnly())); }
            public boolean offersOff() {
                JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
                JSONObject stream = hostSettings == null ? null : hostSettings.optJSONObject("stream");
                Boolean configured = settings != null && settings.has("virtualDisplay") ? settings.optBoolean("virtualDisplay") : null;
                Boolean running = activeWindowId >= 0 && stream != null && stream.has("onVirtualDisplay") ? stream.optBoolean("onVirtualDisplay") : null;
                return TextScale.offersOff(configured, running);
            }
            public Double scale() { return textScale; }
            public void begin() { dismissSettings(false); dismissMenu(); if (appDrawer != null) appDrawer.dismiss(); owner = connection; window = activeWindowId; offered = offersOff(); }
            public boolean canCommit() { return available() && connection == owner && activeWindowId == window && offered == offersOff(); }
            public void commit(Double scale) { textScale = scale; android.util.Log.i("SillAndroid", "Aa: committed scale=" + scale); scheduleViewport(); }
        });
        scaleControl = scaleButton; scaleButton.setTextColor(-1); scaleButton.setTextSize(12); scaleButton.setBackground(shape(0xff272a30, 12));
        LinearLayout.LayoutParams scaleParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        scaleParams.setMargins(dp(2), dp(3), dp(2), dp(3)); bar.addView(scaleButton, scaleParams); scaleButton.setPadding(0, 0, 0, 0);
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
        updateSessionLayout = () -> {
            int r = root.getWidth(), b = root.getHeight();
            if (r <= 0 || b <= 0) return;
            boolean landscape = r > b;
            int width = r - root.getPaddingLeft() - root.getPaddingRight();
            int height = b - root.getPaddingTop() - root.getPaddingBottom();
            float density = getResources().getDisplayMetrics().density;
            PhoneStreamLayout.Streaming streaming = activeWindowId >= 0 ? PhoneStreamLayout.Streaming.WINDOW
                    : desktopActive ? PhoneStreamLayout.Streaming.DESKTOP : PhoneStreamLayout.Streaming.NOTHING;
            if (settingsOpen) streaming = settingsHeldLargerPicture ? PhoneStreamLayout.Streaming.WINDOW : PhoneStreamLayout.Streaming.NOTHING;
            PhoneStreamLayout geometry = PhoneStreamLayout.forSource(width / density, height / density,
                    portraitImeHeight > 0 ? (double) portraitImeHeight : null, false,
                    settingsOpen ? settingsHeldLargerPicture : largerPicture, streaming);
            boolean large = geometry.largerPicture;
            if (large != largerPictureApplied) { dismissWindowControls(); cancelWindowDrag(); }
            largerPictureApplied = large;
            zoom.setTopAligned(large);
            trackpad.setVerticalRatio(large && videoWidth > 0 && videoHeight > 0 ? (float) videoHeight / videoWidth : 1 / 1.6f);
            int visible = landscape ? android.view.View.GONE : android.view.View.VISIBLE;
            sessionStatus.setVisibility(visible); keys.setVisibility(visible); trackpad.setVisibility(visible);
            pictureStatus.setMaxWidth(Math.max(0, width - dp(32)));
            pictureStatus.setVisibility(landscape && pictureStatusActive ? android.view.View.VISIBLE : android.view.View.GONE);
            stripRow.setVisibility(landscape || large ? android.view.View.GONE : android.view.View.VISIBLE);
            if (landscape && strip.getParent() != bar) {
                stripRow.removeView(strip); if (menuButton.getParent() == stripRow) stripRow.removeView(menuButton); sessionPanel.removeView(bar);
                bar.addView(strip, 1, new LinearLayout.LayoutParams(0, dp(52), 1));
                if (menuButton.getParent() == bar) bar.removeView(menuButton);
                bar.addView(menuButton, 2, new LinearLayout.LayoutParams(dp(48), dp(46)));
                sessionPanel.addView(bar, 1);
            } else if (!landscape && strip.getParent() != stripRow) {
                bar.removeView(strip); if (menuButton.getParent() == bar) bar.removeView(menuButton); sessionPanel.removeView(bar);
                stripRow.addView(strip, new LinearLayout.LayoutParams(0, -1, 1));
                stripRow.addView(menuButton, new LinearLayout.LayoutParams(dp(50), dp(50)));
                sessionPanel.addView(bar, 2);
            }
            if (!landscape && large && menuButton.getParent() != bar) {
                stripRow.removeView(menuButton);
                bar.addView(menuButton, 1, new LinearLayout.LayoutParams(0, dp(50), 1));
            } else if (!landscape && !large && menuButton.getParent() != stripRow) {
                bar.removeView(menuButton);
                stripRow.addView(menuButton, new LinearLayout.LayoutParams(dp(50), dp(50)));
            }
            refreshMenuButton();
            for (Button button : new Button[]{appsButton, scaleButton, keyboardButton, desktopButton, settingsButton}) {
                LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) button.getLayoutParams();
                int bw = landscape ? dp(48) : 0; float weight = landscape ? 0 : 1;
                if (bp.width != bw || bp.weight != weight || bp.height != dp(50)) {
                    bp.width = bw; bp.weight = weight; bp.height = dp(50); button.setLayoutParams(bp);
                }
            }
            int wanted = landscape ? 0 : large ? Math.max(0, Math.min(dp((int) geometry.picture.height()),
                    height - dp((int) portraitImeHeight) - sessionStatus.getMeasuredHeight() - dp(110)))
                    : PhoneStreamLayout.pictureHeight(width, height, dp(200), dp(120));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) picture.getLayoutParams();
            int wantedWidth = -1; float weight = landscape ? 1 : 0;
            if (params.height != wanted || params.width != wantedWidth || params.weight != weight) {
                params.height = wanted; params.width = wantedWidth; params.weight = weight; picture.setLayoutParams(params);
            }
            layoutVideo(); cursor.invalidate();
        };
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
            if (r - l != oright - ol || b - t != ob - ot) { dismissWindowControls(); cancelWindowDrag(); if (scaleControl != null) scaleControl.cancel(); }
            if (oright > ol && ob > ot && (r - l > b - t) != (oright - ol > ob - ot)) {
                android.util.Log.i("SillAndroid", "Settings: rotated open=" + settingsOpen + " child=" + (settingsChild != null));
                if (pictureInput != null) pictureInput.cancel();
                if (appDrawer != null && appDrawer.isShowing()) {
                    AppDrawerDialog drawer = appDrawer;
                    int presentation = drawer.presentationVersion();
                    root.post(() -> {
                        if (appDrawer == drawer && connection != null)
                            drawer.reanchor(presentation, drawerWindows, appList, appIcons, activeWindowId,
                                    desktopActive, picture, root.getWidth() > root.getHeight());
                    });
                }
                if (settingsOpen) {
                    settingsReanchorPending = true;
                    root.post(this::reanchorSettings);
                }
            }
            updateSessionLayout.run();
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
        dismissSettings(false);
        if (appDrawer != null) appDrawer.dismiss();
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
        if (settingsOverlay != null) {
            releaseHardwareKeys();
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP) dismissSettings();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        if (appDrawer != null && appDrawer.isShowing()) {
            releaseHardwareKeys();
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == android.view.KeyEvent.ACTION_UP) appDrawer.handleBack();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
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
        dismissMenu();
        pictureInput.cancel();
        trackpad.cancelGestures();
        try { clipboardReceipt.cancel(); keyboard.finishAndForget(); zoom.reset(); layoutVideo(); cursor.invalidate(); sendViewport(); sendAsync(6, new JSONObject().put(desktop ? "desktop" : "window", source)); }
        catch (Exception e) { show("选择窗口失败"); }
    }
    private void tapDesktop() {
        dismissSettings(false);
        pictureInput.cancel();
        trackpad.cancelGestures();
        if (DesktopNavigation.next(desktopActive, desktopDisplays, desktopDisplay)) { clipboardReceipt.cancel(); keyboard.finishAndForget(); sendAsync(47, new JSONObject(), true); }
        else select(new JSONObject(), true);
    }
    private void showWindows() {
        dismissSettings(false);
        dismissMenu();
        if (connection == null) return;
        if (appDrawer == null) appDrawer = new AppDrawerDialog(this, new AppDrawerDialog.Listener() {
            public void selectWindow(long id) { try { select(new JSONObject().put("_0", id), false); } catch (Exception e) { show("无法选择窗口"); } }
            public void launchApp(String bundleId) {
                try { clipboardReceipt.cancel(); keyboard.finishAndForget(); sendAsync(7, new JSONObject().put("bundleID", bundleId)); }
                catch (Exception e) { show("无法打开应用"); }
            }
        });
        dismissWindowControls();
        pictureInput.cancel();
        releaseHardwareKeys();
        if (!appDrawer.isShowing()) keyboard.finishAndForget();
        if (!appDrawer.isShowing() && root.getWidth() > root.getHeight()) {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyboard.getWindowToken(), 0);
            keyboard.clearFocus();
        }
        appDrawer.show(drawerWindows, appList, appIcons, activeWindowId, desktopActive, picture, root.getWidth() > root.getHeight());
    }
    private void refreshDrawer() { ui.removeCallbacks(drawerUpdate); ui.postDelayed(drawerUpdate, 250); }
    private void dismissMenu() {
        pendingMenuToken = 0; pendingMenuBack = null; pendingMenuOwner = null;
        if (menuDialog != null) { menuDialog.dismiss(); menuDialog = null; }
    }
    private void dismissStaleMenu() {
        if (menuOwner != connection) { dismissMenu(); menuOwner = null; macMenus = new JSONArray(); refreshMenuButton(); }
    }
    private void showMenu(JSONArray items, String title, int version) { dismissMenu(); showMenu(items, title, version, null, menuOwner); }
    private void showMenu(JSONArray items, String title, int version, Runnable back, SillConnection owner) {
        if (owner == null || connection != owner || menuOwner != owner) return;
        dismissSettings(false);
        if (appDrawer != null) appDrawer.dismiss();
        String[] names = new String[Math.max(1, items.length())];
        boolean[] enabled = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            if (i >= items.length()) { names[i] = "无菜单项"; continue; }
            JSONObject item = items.optJSONObject(i);
            if (item == null || item.optBoolean("separator")) { names[i] = "────────"; continue; }
            String itemTitle = item.optString("title", "");
            enabled[i] = MacMenuRow.selectable(false, !menuStale && item.optBoolean("enabled", true), item.isNull("id") ? "" : item.optString("id", ""), itemTitle);
            names[i] = MacMenuRow.label(itemTitle, item.optString("key", ""), item.optString("mark", ""), item.optBoolean("submenu"));
        }
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names) {
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) { return enabled[position]; }
            @Override public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                android.view.View view = super.getView(position, convertView, parent);
                view.setEnabled(enabled[position]);
                view.setAlpha(enabled[position] ? 1f : .45f);
                return view;
            }
        };
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this).setTitle(title).setAdapter(adapter, (dialog, which) -> {
            JSONObject item = items.optJSONObject(which);
            if (!enabled[which] || item == null) return;
            if (connection != owner || menuOwner != owner || version != menuVersion || menuStale) { show("菜单已更改，请重新打开"); return; }
            try {
                JSONObject message = new JSONObject().put("version", version).put("id", item.getString("id"))
                        .put("title", item.getString("title")).put("token", ++menuToken);
                synchronized (outputLock) {
                    if (connection != owner || menuOwner != owner) return;
                    if (item.optBoolean("submenu")) {
                        pendingMenuToken = menuToken; pendingMenuOwner = owner;
                        pendingMenuBack = () -> showMenu(items, title, version, back, owner);
                        sendMenu(owner, 27, message);
                    } else sendMenu(owner, 25, message);
                }
            } catch (Exception e) { show("菜单请求失败"); }
        });
        if (back != null) {
            builder.setNegativeButton("返回", (dialog, which) -> back.run());
            builder.setOnCancelListener(dialog -> back.run());
        }
        menuDialog = builder.create();
        android.app.AlertDialog shown = menuDialog;
        shown.setOnDismissListener(dialog -> { if (menuDialog == shown) menuDialog = null; });
        shown.show();
    }
    private void sendMenu(SillConnection owner, int kind, JSONObject message) {
        input.execute(() -> {
            try {
                synchronized (outputLock) {
                    if (connection == owner && allowedForLock(kind, message)) owner.send(kind, message.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) { showFor(owner, "菜单请求失败"); }
        });
    }
    private void scheduleViewport() {
        ui.removeCallbacks(viewportUpdate); ui.postDelayed(viewportUpdate, 250);
    }
    private void sendViewport() {
        if (connection == null || picture.getWidth() <= 0 || picture.getHeight() <= 0) return;
        try {
            float density = getResources().getDisplayMetrics().density;
            int fps = wantedViewportFps();
            JSONObject v = new JSONObject().put("width", picture.getWidth() / density).put("height", picture.getHeight() / density).put("fps", fps);
            if (textScale != null) v.put("scale", textScale);
            android.util.Log.i("SillAndroid", "Viewport: queued " + Math.round(picture.getWidth() / density) + "x" + Math.round(picture.getHeight() / density) + " scale=" + textScale);
            sendAsync(9, v);
            lastViewportFps = fps;
        } catch (Exception e) { show("画面尺寸发送失败"); }
    }
    private int wantedViewportFps() {
        int ceiling = 60;
        android.view.Display display = root == null ? null : root.getDisplay();
        if (display != null) try {
            float max = 0;
            for (android.view.Display.Mode mode : display.getSupportedModes()) max = Math.max(max, mode.getRefreshRate());
            if (max > 0 && Float.isFinite(max)) ceiling = Math.round(max);
        } catch (RuntimeException ignored) { }
        android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean saving = power != null && power.isPowerSaveMode();
        android.net.ConnectivityManager networks = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkCapabilities caps = null;
        try { if (networks != null) caps = networks.getNetworkCapabilities(networks.getActiveNetwork()); }
        catch (RuntimeException ignored) { }
        boolean tunnelOrMobile = caps == null || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
        java.net.InetAddress destination = null;
        try { destination = android.net.InetAddresses.parseNumericAddress(host.getText().toString().trim()); }
        catch (IllegalArgumentException ignored) { }
        return ViewportFps.wanted(ceiling, saving, ViewportFps.away(destination, tunnelOrMobile));
    }
    private void fpsChanged() {
        ui.post(() -> {
            if (fpsObserversStarted && connection != null && lastViewportFps != wantedViewportFps()) scheduleViewport();
        });
    }
    private void startFpsObservers() {
        if (fpsObserversStarted) return;
        fpsObserversStarted = true;
        android.hardware.display.DisplayManager displays = (android.hardware.display.DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displays != null) try { displays.registerDisplayListener(fpsDisplayListener, ui); fpsDisplayRegistered = true; }
        catch (RuntimeException ignored) { }
        android.net.ConnectivityManager networks = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (networks != null) try { networks.registerDefaultNetworkCallback(fpsNetworkCallback); fpsNetworkRegistered = true; }
        catch (RuntimeException ignored) { }
        android.content.IntentFilter filter = new android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(fpsPowerReceiver, filter, RECEIVER_NOT_EXPORTED);
            else registerReceiver(fpsPowerReceiver, filter);
            fpsPowerRegistered = true;
        } catch (RuntimeException ignored) { }
        fpsChanged();
    }
    private void stopFpsObservers() {
        if (!fpsObserversStarted) return;
        fpsObserversStarted = false;
        android.hardware.display.DisplayManager displays = (android.hardware.display.DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displays != null && fpsDisplayRegistered) { displays.unregisterDisplayListener(fpsDisplayListener); fpsDisplayRegistered = false; }
        android.net.ConnectivityManager networks = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (networks != null && fpsNetworkRegistered) { networks.unregisterNetworkCallback(fpsNetworkCallback); fpsNetworkRegistered = false; }
        if (fpsPowerRegistered) { unregisterReceiver(fpsPowerReceiver); fpsPowerRegistered = false; }
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
    private void settings() { settings(false); }
    private void settings(boolean reanchor) {
        if (!reanchor && settingsOverlay != null) { dismissSettings(); return; }
        if (!reanchor) {
            dismissMenu();
            if (appDrawer != null) appDrawer.dismiss();
            if (connection == null) return;
        }
        boolean reverse = getPreferences(0).getBoolean("reverseScrolling", false);
        boolean gestures = getPreferences(0).getBoolean("macGestures", true);
        float density = getResources().getDisplayMetrics().density;
        boolean offerLarge = portraitImeHeight <= 0 || PhoneStreamLayout.offersLargerPicture(
                (root.getWidth() - root.getPaddingLeft() - root.getPaddingRight()) / density,
                (root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()) / density, portraitImeHeight, false);
        java.util.ArrayList<String> labels = new java.util.ArrayList<>(java.util.Arrays.asList("Mac 画质和帧率", clipboardEnabled ? "关闭剪贴板共享" : "开启剪贴板共享", "将手机剪贴板粘贴到 Mac", audioEnabled ? "关闭声音（重新连接后生效）" : "开启声音测试（重新连接后生效）", reverse ? "滚动方向：反向" : "滚动方向：自然", gestures ? "关闭多指 Mac 手势" : "开启多指 Mac 手势"));
        if (offerLarge) labels.add(android.os.Build.VERSION.SDK_INT < 30 ? "大画面：此 Android 版本暂不支持" : portraitImeHeight <= 0 ? "大画面：请先打开键盘以测量高度" : largerPicture ? "关闭大画面" : "开启大画面");
        int holdIndex = labels.size();
        int holdTenths = Math.max(2, Math.min(20, getPreferences(0).getInt("thumbnailHoldTenths", 5)));
        labels.add("窗口控制长按时间：" + (holdTenths / 10f) + " 秒");
        labels.add("断开连接");
        String[] items = labels.toArray(new String[0]);
        if (!reanchor) {
            android.view.WindowInsets insets = root.getRootWindowInsets();
            boolean restoreKeyboard = android.os.Build.VERSION.SDK_INT >= 30 && insets != null
                    ? insets.isVisible(android.view.WindowInsets.Type.ime()) : keyboard.hasFocus();
            settingsOpen = true;
            settingsHeldLargerPicture = largerPicture && activeWindowId >= 0;
            settingsRestoreKeyboard = restoreKeyboard;
            settingsSession = connection;
            releaseHardwareKeys(); keyboard.finishAndForget();
            if (restoreKeyboard) ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyboard.getWindowToken(), 0);
            keyboard.clearFocus();
        }
        android.util.Log.i("SillAndroid", "Settings: show " + (root.getWidth() > root.getHeight() ? "landscape" : "portrait") + " reanchor=" + reanchor);
        java.util.function.IntConsumer choose = i -> {
            if (i == 0) hostSettingsMenu();
            if (i == 1) { clipboardEnabled = !clipboardEnabled; getPreferences(0).edit().putBoolean("shareClipboard", clipboardEnabled).apply(); updateClipboard(); }
            if (i == 2) pasteClipboard();
            if (i == 3) {
                if (!audioEnabled && !AudioPlayer.supported()) { show("此设备没有报告支持 AAC-ELD 解码"); return; }
                audioEnabled = !audioEnabled; getPreferences(0).edit().putBoolean("experimentalAudio", audioEnabled).apply();
                show("声音设置将在下次连接时生效");
            }
            if (i == 4) { boolean next = !getPreferences(0).getBoolean("reverseScrolling", false); getPreferences(0).edit().putBoolean("reverseScrolling", next).apply(); trackpad.setReverseScrolling(next); show(next ? "已启用反向滚动" : "已启用自然滚动"); }
            if (i == 5) { boolean next = !getPreferences(0).getBoolean("macGestures", true); getPreferences(0).edit().putBoolean("macGestures", next).apply(); trackpad.setMacGesturesEnabled(next && hostGestures >= 1); show(next ? "已开启多指手势" : "已关闭多指手势"); }
            if (offerLarge && i == 6) {
                if (android.os.Build.VERSION.SDK_INT < 30) show("大画面需要 Android 11 或更新版本，以读取键盘高度");
                else if (portraitImeHeight <= 0) show("请先打开并关闭键盘，让 Sill 测量键盘高度");
                else { largerPicture = !largerPicture; getPreferences(0).edit().putBoolean("largerPicture", largerPicture).apply(); }
            }
            if (i == holdIndex) {
                String[] durations = new String[19];
                for (int n = 0; n < durations.length; n++) durations[n] = ((n + 2) / 10f) + " 秒";
                int currentHold = Math.max(2, Math.min(20, getPreferences(0).getInt("thumbnailHoldTenths", 5)));
                showSettingsChild(new android.app.AlertDialog.Builder(this).setTitle("窗口控制长按时间")
                        .setSingleChoiceItems(durations, currentHold - 2, (choice, selected) -> {
                            getPreferences(0).edit().putInt("thumbnailHoldTenths", selected + 2).apply();
                            if (settingsOverlay != null) {
                                TextView holdRow = settingsOverlay.findViewWithTag("thumbnailHold");
                                if (holdRow != null) holdRow.setText("窗口控制长按时间：" + ((selected + 2) / 10f) + " 秒");
                            }
                            choice.dismiss();
                        }).setNegativeButton("取消", null).create());
            }
            if (i == items.length - 1) disconnect();
        };
        if (root.getWidth() > root.getHeight()) {
            pictureInput.cancel();
            android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
            settingsOverlay = overlay;
            overlay.setOnClickListener(v -> dismissSettings());
            LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(14), dp(14), dp(14), dp(14)); panel.setBackground(shape(0xff181a1f, 20));
            panel.setOnClickListener(v -> {});
            TextView title = new TextView(this); title.setText("设置"); title.setTextSize(19); title.setTextColor(-1); panel.addView(title);
            ScrollView scroller = new ScrollView(this); LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); scroller.addView(rows);
            panel.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
            for (int n = 0; n < items.length; n++) {
                final int index = n;
                TextView row = new TextView(this); row.setText(items[n]); row.setTextColor(-1); row.setTextSize(15);
                if (n == holdIndex) row.setTag("thumbnailHold");
                row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(12), 0, dp(12), 0); row.setMinHeight(dp(48));
                row.setOnClickListener(v -> {
                    choose.accept(index);
                    if (settingsOverlay == null) return;
                    if (index == 1) row.setText(clipboardEnabled ? "关闭剪贴板共享" : "开启剪贴板共享");
                    if (index == 3) row.setText(audioEnabled ? "关闭声音（重新连接后生效）" : "开启声音测试（重新连接后生效）");
                    if (index == 4) row.setText(getPreferences(0).getBoolean("reverseScrolling", false) ? "滚动方向：反向" : "滚动方向：自然");
                    if (index == 5) row.setText(getPreferences(0).getBoolean("macGestures", true) ? "关闭多指 Mac 手势" : "开启多指 Mac 手势");
                    if (offerLarge && index == 6 && portraitImeHeight > 0 && android.os.Build.VERSION.SDK_INT >= 30) row.setText(largerPicture ? "关闭大画面" : "开启大画面");
                });
                rows.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            }
            android.widget.FrameLayout.LayoutParams panelParams = new android.widget.FrameLayout.LayoutParams(
                    Math.min(dp(380), Math.max(dp(160), picture.getWidth() - dp(44))), -1,
                    android.view.Gravity.TOP | android.view.Gravity.END);
            panelParams.setMargins(dp(22), dp(8), dp(22), dp(14));
            overlay.addView(panel, panelParams); picture.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                settingsBack = this::dismissSettings;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, settingsBack);
            }
        } else {
            android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("设置").setItems(items, (d, i) -> choose.accept(i)).create();
            settingsDialog = dialog;
            dialog.setOnDismissListener(d -> {
                android.util.Log.i("SillAndroid", "Settings: dialog dismissed current=" + (settingsDialog == dialog) + " child=" + (settingsChild != null));
                if (settingsDialog != dialog) return; settingsDialog = null; if (!settingsReanchoring && settingsChild == null) finishSettings();
            });
            dialog.show();
        }
    }
    private void showSettingsChild(android.app.AlertDialog dialog) {
        settingsChild = dialog;
        dialog.setOnDismissListener(d -> {
            if (settingsChild != dialog) return;
            settingsChild = null;
            if (settingsReanchorPending && settingsOpen) { reanchorSettings(); return; }
            if (settingsOverlay == null && settingsDialog == null) finishSettings();
        });
        dialog.show();
    }
    private void dismissSettings() { dismissSettings(true); }
    private void dismissSettings(boolean restoreKeyboard) {
        if (settingsOpen) {
            StackTraceElement[] trace = new Throwable().getStackTrace();
            String caller = "unknown";
            for (int n = 1; n < trace.length; n++) if (!trace[n].getMethodName().equals("dismissSettings")) { caller = trace[n].getMethodName(); break; }
            android.util.Log.i("SillAndroid", "Settings: dismiss caller=" + caller + " restore=" + restoreKeyboard);
        }
        settingsReanchorPending = false;
        if (!restoreKeyboard) settingsRestoreKeyboard = false;
        if (android.os.Build.VERSION.SDK_INT >= 33 && settingsBack != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(settingsBack); settingsBack = null;
        }
        android.widget.FrameLayout overlay = settingsOverlay; settingsOverlay = null;
        if (overlay != null && overlay.getParent() instanceof android.view.ViewGroup parent) parent.removeView(overlay);
        android.app.AlertDialog child = settingsChild; settingsChild = null;
        if (child != null) child.dismiss();
        android.app.AlertDialog dialog = settingsDialog; settingsDialog = null;
        if (dialog != null) dialog.dismiss();
        finishSettings();
    }
    private void reanchorSettings() {
        if (settingsReanchorPending) android.util.Log.i("SillAndroid", "Settings: reanchor open=" + settingsOpen + " child=" + (settingsChild != null) + " session=" + (connection != null && connection == settingsSession));
        if (!settingsReanchorPending || !settingsOpen || settingsChild != null) return;
        if (connection == null || connection != settingsSession) { dismissSettings(false); return; }
        settingsReanchorPending = false;
        settingsReanchoring = true;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33 && settingsBack != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(settingsBack); settingsBack = null;
            }
            android.widget.FrameLayout overlay = settingsOverlay; settingsOverlay = null;
            if (overlay != null && overlay.getParent() instanceof android.view.ViewGroup parent) parent.removeView(overlay);
            android.app.AlertDialog dialog = settingsDialog; settingsDialog = null;
            if (dialog != null) dialog.dismiss();
            settings(true);
        } finally { settingsReanchoring = false; }
    }
    private void finishSettings() {
        if (!settingsOpen) return;
        android.util.Log.i("SillAndroid", "Settings: finished");
        settingsOpen = false;
        if (updateSessionLayout != null) updateSessionLayout.run();
        SillConnection expected = settingsSession; settingsSession = null;
        boolean restore = settingsRestoreKeyboard; settingsRestoreKeyboard = false;
        if (restore && expected != null && connection == expected) keyboard.post(() -> {
            if (settingsOpen || connection != expected || appDrawer != null && appDrawer.isShowing()) return;
            keyboard.requestFocus();
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(keyboard, 0);
        });
    }
    private void hostSettingsMenu() {
        JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
        if (settings == null) { show("Mac 尚未提供画质设置"); return; }
        String[] items = {"画质 · " + settings.optInt("bitrate") / 1000000 + " Mbps", "帧率 · " + settings.optInt("maxFPS") + " fps",
                "分辨率 · " + (settings.optDouble("captureScale", 1) >= 2 ? "Retina" : "标准"),
                "虚拟显示器 · " + (settings.optBoolean("virtualDisplay") ? "开" : "关"),
                "Mac 发送声音 · " + (settings.optBoolean("sendAudio") ? "开" : "关")};
        showSettingsChild(new android.app.AlertDialog.Builder(this).setTitle("Mac 设置（影响连接的设备）").setItems(items, (dialog, index) -> {
            if (index == 0) {
                String[] labels = {"低 4 Mbps", "节省 8 Mbps", "均衡 15 Mbps", "高 25 Mbps", "专业 40 Mbps", "极高 80 Mbps", "极限 150 Mbps"};
                int[] values = {4000000, 8000000, 15000000, 25000000, 40000000, 80000000, 150000000};
                showSettingsChild(new android.app.AlertDialog.Builder(this).setTitle("画质").setItems(labels, (d, i) -> changeHostSetting("bitrate", values[i])).create());
            } else if (index == 1) {
                showSettingsChild(new android.app.AlertDialog.Builder(this).setTitle("帧率").setItems(new String[]{"60 fps", "120 fps"}, (d, i) -> changeHostSetting("maxFPS", i == 0 ? 60 : 120)).create());
            } else if (index == 2) {
                showSettingsChild(new android.app.AlertDialog.Builder(this).setTitle("分辨率").setItems(new String[]{"标准", "Retina"}, (d, i) -> changeHostSetting("captureScale", i == 0 ? 1.0 : 2.0)).create());
            } else if (index == 3) changeHostSetting("virtualDisplay", !settings.optBoolean("virtualDisplay"));
            else if (settings.has("sendAudio")) changeHostSetting("sendAudio", !settings.optBoolean("sendAudio"));
            else show("此 Mac 未提供声音设置");
        }).create());
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
        dismissSettings(false);
        if (appDrawer != null) appDrawer.dismiss();
        dismissWindowControls(); cancelWindowDrag();
        pictureInput.cancel();
        trackpad.cancelGestures();
        clipboardReceipt.cancel(); keyboard.finishAndForget(); closing = true; SillConnection c = connection;
        synchronized (reconnectLock) { reconnectLock.notifyAll(); }
        if (c != null) try { c.close(); } catch (Exception ignored) { }
    }

    private void show(String text) {
        SillConnection expected = connection;
        ui.post(() -> { if (connection == expected) displayStatus(text); });
    }
    private void showFor(SillConnection expected, String text) {
        ui.post(() -> { if (connection == expected) displayStatus(text); });
    }
    private void displayStatus(String text) {
        status.setText(text); sessionStatus.setText(text); pictureStatus.setText(text);
        int generation = ++pictureStatusGeneration;
        pictureStatusActive = !text.isEmpty();
        pictureStatus.setVisibility(pictureStatusActive && root.getWidth() > root.getHeight()
                ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!text.startsWith("连接中断")) ui.postDelayed(() -> {
            if (pictureStatusGeneration != generation) return;
            pictureStatusActive = false; pictureStatus.setVisibility(android.view.View.GONE);
        }, 5000);
    }
    private void connect() { start(true); }
    private void start(boolean pairing) { start(pairing, null); }
    private void start(boolean pairing, SavedMacs.Identity selectedIdentity) {
        String address = host.getText().toString().trim();
        String password = code.getText().toString();
        int number;
        try { number = Integer.parseInt(port.getText().toString()); if (address.isEmpty() || number < 1 || number > 65535) throw new IllegalArgumentException(); }
        catch (Exception e) { show("请填写有效地址和端口。"); return; }
        if (running) { show("已有连接，请退出后重试。"); return; }
        running = true; closing = false; getPreferences(0).edit().putString("host", address).putString("port", String.valueOf(number)).apply();
        network.execute(() -> {
            try {
                SavedMacs.Endpoint endpoint = new SavedMacs.Endpoint(address, number);
                byte[] pin;
                if (pairing) {
                    show("正在配对…");
                    SavedMacs.Identity identity;
                    try (SillConnection pair = SillConnection.connect(address, number, "sill-pair/1", null)) {
                        identity = pair.pairIdentity(password, android.os.Build.MODEL, android.os.Build.DEVICE);
                    }
                    savedMacStore.paired(identity, endpoint); pin = identity.pin();
                    ui.post(() -> { code.setText(""); refreshSavedMacs(); });
                } else {
                    pin = selectedIdentity == null ? savedMacStore.pin(endpoint) : savedMacStore.pin(selectedIdentity, endpoint);
                    if (pin == null) throw new IllegalStateException("请先配对这台 Mac。");
                }
                boolean established = false;
                int retries = 0;
                while (running && !closing) {
                    long connectedAt = 0;
                    try {
                        if (!established) show("正在连接…");
                        try (SillConnection session = SillConnection.connect(address, number, "sill/1", pin)) {
                            if (!running || closing) break;
                            savedMacStore.connected(pin, endpoint);
                            HostLockState sessionLock = new HostLockState(false); sessionLock.requireFreshFrame();
                            hostLock = sessionLock; keyframeRecovery = new KeyframeRecovery(); keyframeStreaming = false; keyframeWorstRtt = 0; keyframeRttWindow = 0; recoveryFrame.set(null); lastParameters = null; revealFrame.set(null); recoveryGeneration = -1;
                            pointerSync = new PointerSync();
                            synchronized (outputLock) { initialSource = new InitialSourcePolicy(); connection = session; }
                            session.sessionTimeout();
                            ui.post(() -> { if (connection == session) { renderLockOverlay(sessionLock, session); connectionContainer.setVisibility(android.view.View.GONE); sessionPanel.setVisibility(android.view.View.VISIBLE); picture.post(this::sendViewport); } });
                            JSONObject hello = new JSONObject().put("protocol", 1).put("appVersion", "0.1.0").put("device", android.os.Build.MODEL);
                            if (audioEnabled && AudioPlayer.supported()) { audio = new AudioPlayer(text -> showFor(session, text)); hello.put("audio", new JSONArray().put("aac-eld")); }
                            sendJson(23, hello);
                            sendJson(27, new JSONObject());
                            KeyframeRecovery sessionRecovery = keyframeRecovery;
                            var ping = heartbeat.scheduleAtFixedRate(() -> {
                                try { synchronized (outputLock) { if (connection != session) return; session.send(10, ByteBuffer.allocate(8).putDouble(System.nanoTime() / 1e9).array()); } requestKeyframe(session, sessionRecovery); }
                                catch (Exception e) { try { session.close(); } catch (Exception ignored) { } }
                            }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
                            established = true; connectedAt = android.os.SystemClock.uptimeMillis();
                            show("已连接，选择一个窗口或桌面。");
                            try { while (running && !closing) handle(session.read(), session); } finally { ping.cancel(false); }
                        }
                    } catch (Exception error) {
                        if (!running || closing) break;
                        android.util.Log.i("SillAndroid", "Session ended: " + error.getClass().getSimpleName());
                        if (connectedAt != 0 && android.os.SystemClock.uptimeMillis() - connectedAt >= 10000) retries = 0;
                        long delay = ReconnectPolicy.delayMillis(retries);
                        if (!established || !ReconnectPolicy.retryable(error) || delay < 0) throw error;
                        retries++;
                        connection = null; hostLock = null; keyframeRecovery = null; keyframeStreaming = false; recoveryFrame.set(null); lastParameters = null; revealFrame.set(null); recoveryGeneration = -1; pointerSync = null; clipboardSubscribed = false; hostClipboardAvailable = false; clipboardSource = ""; clipboardReceipt.cancel(); releaseDecoder();
                        if (audio != null) { audio.close(); audio = null; }
                        ui.post(() -> {
                            dismissSettings(false); dismissStaleMenu(); dismissWindowControls(); cancelWindowDrag();
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
            } catch (Exception e) { if (!closing) { android.util.Log.e("SillAndroid", "Connection failed", e); ui.post(() -> displayStatus("连接结束: " + e.getMessage())); } }
            finally {
                connection = null; hostLock = null; keyframeRecovery = null; keyframeStreaming = false; recoveryFrame.set(null); lastParameters = null; revealFrame.set(null); recoveryGeneration = -1; pointerSync = null; clipboardSubscribed = false; hostClipboardAvailable = false; clipboardSource = ""; clipboardReceipt.cancel(); releaseDecoder();
                if (audio != null) { audio.close(); audio = null; }
                ui.post(() -> { dismissSettings(false); dismissStaleMenu(); dismissWindowControls(); cancelWindowDrag(); hostSettings = null; pendingSettingsToken = 0; desktopDisplays = -1; desktopDisplay = 0; desktopActive = false; hostGestures = 0; hostKeyRepeat = false; hostKeyRepeatKnown = false; trackpad.setMacGesturesEnabled(false); desktopButton.setText("桌面"); thumbnailImages.clear(); appIcons.clear(); iconBadges.clear(); activeWindowId = -1; if (appDrawer != null) appDrawer.dismiss(); drawerWindows = new JSONArray(); appList = new JSONArray(); zoom.reset(); screenAwake.setStreaming(false); pictureInput.cancel(); trackpad.cancelGestures(); keyboard.discard(); resetCursorShape(); modifiers = heldModifiers = 0; for (android.view.View button : modifierViews.values()) button.setBackground(shape(0xff272a30, 12)); sessionPanel.setVisibility(android.view.View.GONE); connectionContainer.setVisibility(android.view.View.VISIBLE); running = false; });
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
        if (!allowedForLock(kind, value)) {
            if (kind == 6 || kind == 7 || kind == 47) synchronized (outputLock) {
                if (connection != null && initialSource != null) initialSource.chosen();
            }
            return;
        }
        if (kind == 6 || kind == 7 || kind == 47) clipboardReceipt.cancel();
        if (kind == 8) {
            JSONObject pointer = value.optJSONObject("pointer");
            if (value.has("key") || value.has("text") || (pointer != null && pointer.optString("_0").endsWith("Down"))) clipboardReceipt.cancel();
        }
        SillConnection expected;
        synchronized (outputLock) {
            expected = connection;
            if (expected == null) {
                if (kind == 9) android.util.Log.i("SillAndroid", "Viewport: not sent, no session");
                return;
            }
            if ((kind == 6 || kind == 7 || kind == 47) && initialSource != null) initialSource.chosen();
        }
        PointerSync feed = pointerSync;
        if (kind == 8 && feed != null && connection == expected) feed.inputQueued();
        long queuedAt = kind == 9 ? android.os.SystemClock.uptimeMillis() : 0;
        input.execute(() -> {
            long waitMs = kind == 9 ? android.os.SystemClock.uptimeMillis() - queuedAt : 0;
            try {
                synchronized (outputLock) {
                    if (connection == expected && allowedForLock(kind, value)) {
                        expected.send(kind, empty ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8));
                        if (kind == 9) android.util.Log.i("SillAndroid", "Viewport: sent, queue wait=" + waitMs + "ms");
                    } else if (kind == 9) android.util.Log.i("SillAndroid", "Viewport: not sent, session changed, queue wait=" + waitMs + "ms");
                }
            }
            catch (Exception e) {
                if (kind == 9) android.util.Log.i("SillAndroid", "Viewport: send failed, queue wait=" + waitMs + "ms, error=" + e.getClass().getSimpleName());
                showFor(expected, "发送失败: " + e.getClass().getSimpleName());
            }
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
    private void handle(StreamWire.Message message, SillConnection messageSession) throws Exception {
        switch (message.kind()) {
            case 2 -> {
                JSONObject list = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                JSONArray windows = list.getJSONArray("windows");
                JSONObject streamingSource = list.optJSONObject("active");
                boolean streaming = streamingSource != null && (streamingSource.has("window") || streamingSource.has("desktop"));
                if (keyframeStreaming != streaming) android.util.Log.i("SillAndroid", "Recovery: streaming=" + streaming);
                keyframeStreaming = streaming;
                HostLockState lock = hostLock; SillConnection listedSession = connection;
                if (lock != null && listedSession != null) {
                    boolean wasCovered = lock.isCovered();
                    boolean blank = lock.onWindowList(list.optBoolean("locked", false), list.optBoolean("offConsole", false),
                            list.optBoolean("offConsoleView", false), android.os.SystemClock.uptimeMillis());
                    if (blank) { clipboardReceipt.cancel(); releaseDecoder(); waitingForKeyframe = true; }
                    if (wasCovered && !lock.isCovered()) { waitingForKeyframe = true; interruptPicture(); requestKeyframe(); }
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
                synchronized (outputLock) {
                    if (connection == listedSession && listedSession != null && initialSource != null) {
                        InitialSourcePolicy.Source firstSource = streamingSource == null ? InitialSourcePolicy.Source.UNKNOWN
                                : streamingSource.has("desktop") ? InitialSourcePolicy.Source.DESKTOP
                                : streamingSource.has("window") ? InitialSourcePolicy.Source.WINDOW
                                : streamingSource.has("none") ? InitialSourcePolicy.Source.NONE : InitialSourcePolicy.Source.UNKNOWN;
                        boolean firstList = !initialSource.listed();
                        boolean autoDesktop = initialSource.onList(firstSource, allowedForLock(6, new JSONObject()));
                        if (firstList) android.util.Log.i("SillAndroid", "Session: first source=" + firstSource.name().toLowerCase(java.util.Locale.ROOT));
                        if (autoDesktop) {
                            listedSession.send(6, new JSONObject().put("desktop", new JSONObject()).toString().getBytes(StandardCharsets.UTF_8));
                            android.util.Log.i("SillAndroid", "Session: initial desktop requested");
                        }
                    }
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
                    String mac = list.optString("macName", "");
                    if (!mac.equals(windowOrderMac)) { cancelWindowDrag(); windowOrderMac = mac; loadWindowOrder(); }
                    JSONArray sortedWindows = orderedWindows(windows);
                    if (!sameWindowChoices(sortedWindows)) {
                        cancelWindowDrag();
                        dismissWindowControls();
                        choices.removeAllViews(); thumbnails.clear(); iconBadges.clear();
                        for (int i = 0; i < sortedWindows.length(); i++) {
                            JSONObject item = sortedWindows.optJSONObject(i); if (item == null) continue;
                            try { addChoice(item.optString("appName") + " — " + item.optString("title"), new JSONObject().put("_0", item.getLong("id")), item.optString("bundleID")); }
                            catch (Exception ignored) { }
                        }
                        thumbnailImages.keySet().retainAll(thumbnails.keySet());
                    }
                    JSONObject selected = active == null ? null : active.optJSONObject("window");
                    long selectedId = selected == null ? -1 : selected.optLong("_0", -1);
                    if (activeWindowId != selectedId) {
                        activeWindowId = selectedId;
                        if (scaleControl != null) scaleControl.refresh();
                        if (!settingsOpen && updateSessionLayout != null) updateSessionLayout.run();
                        scheduleViewport();
                    }
                    for (int i = 0; i < choices.getChildCount(); i++) {
                        android.view.View tile = choices.getChildAt(i);
                        boolean chosen = tile.getTag() instanceof Long && ((Long) tile.getTag()) == selectedId;
                        android.graphics.drawable.GradientDrawable border = shape(0x00000000, 10);
                        border.setStroke(dp(chosen ? 2 : 1), chosen ? 0xff8eb9fb : 0x33ffffff);
                        tile.setForeground(border);
                    }
                    drawerWindows = windows; refreshDrawer();
                });
            }
            case 11 -> {
                byte[] pong = message.payload(); SillConnection expected = connection;
                if (pong.length == 8) {
                    double elapsed = System.nanoTime() / 1e9 - ByteBuffer.wrap(pong).getDouble();
                    if (Double.isFinite(elapsed) && elapsed >= 0 && elapsed <= 60) {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (now - keyframeRttWindow >= 1000) { keyframeRttWindow = now; keyframeWorstRtt = 0; }
                        keyframeWorstRtt = Math.max(keyframeWorstRtt, (long) (elapsed * 1000));
                    }
                    return;
                }
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
                    if (scaleControl != null) scaleControl.refresh();
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
                    if (connection != messageSession) return;
                    if (menu.has("menus")) { menuOwner = messageSession; menuVersion = menu.optInt("version"); menuStale = menu.optBoolean("stale", false); macMenus = menu.optJSONArray("menus"); if (macMenus == null) macMenus = new JSONArray(); refreshMenuButton(); }
                    else if (pendingMenuOwner == messageSession && menu.optInt("answering", -1) == pendingMenuToken && menu.has("items")) {
                        pendingMenuToken = 0; JSONArray items = menu.optJSONArray("items");
                        Runnable back = pendingMenuBack; pendingMenuBack = null; pendingMenuOwner = null;
                        if (menu.optBoolean("stale", false)) {
                            if (!menu.has("note")) show("菜单已过期，请重新打开");
                        } else if (items != null && menu.optInt("version", -1) == menuVersion) showMenu(items, "菜单", menuVersion, back, messageSession);
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
                if ((lock == null || !lock.isCovered()) && keyframeForeground && surface != null && surface.isValid()) createDecoder(params);
                else interruptPicture();
            }
            case 1 -> {
                HostLockState lock = hostLock;
                if (lock == null || lock.isCovered()) return;
                Surface target; long epoch;
                synchronized (surfaceLock) { target = surface; epoch = surfaceEpoch; }
                if (target == null || !target.isValid() || !keyframeForeground) { waitingForKeyframe = true; interruptPicture(); return; }
                // SurfaceHolder can reuse the same Surface Java object after destroying its native
                // surface. Always rebuild the codec after an epoch change, even if == still holds.
                if (decoder != null && (decoderEpoch != epoch || target != decoderSurface)) {
                    android.util.Log.i("SillAndroid", "Recovery: replacing decoder for new surface");
                    releaseDecoder(); waitingForKeyframe = true; interruptPicture();
                }
                if (decoder != null) drainVideo();
                if (!lock.shouldDecode(message.keyframe(), recoveryGeneration)) { interruptPicture(); requestKeyframe(); return; }
                if (decoder == null) {
                    if (lastParameters == null || !message.keyframe()) { interruptPicture(); requestKeyframe(); return; }
                    createDecoder(lastParameters);
                    if (decoder == null || decoderEpoch != surfaceEpoch) { releaseDecoder(); waitingForKeyframe = true; interruptPicture(); return; }
                }
                drainVideo();
                if (waitingForKeyframe && !message.keyframe()) { interruptPicture(); requestKeyframe(); return; }
                byte[] frame = Hevc.frame(message.payload(), nalSize);
                int index = decoder.dequeueInputBuffer(10_000);
                if (index >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(index); input.clear();
                    if (frame.length > input.remaining()) throw new IllegalStateException("视频帧超过解码缓冲区");
                    long presentationUs = (long) (message.timestamp() * 1_000_000);
                    if (message.keyframe() && lock.needsFreshFrame() && recoveryGeneration != lock.generation()) {
                        recoveryGeneration = lock.generation(); revealFrame.set(new FreshFrame(recoveryGeneration, presentationUs));
                    }
                    KeyframeRecovery recovery = keyframeRecovery;
                    if (message.keyframe() && recovery != null && recovery.isOpen()) {
                        android.util.Log.i("SillAndroid", "Recovery: keyframe queued");
                        long generation = recovery.generation();
                        RecoveryFrame candidate = recoveryFrame.get();
                        if (candidate == null || candidate.generation() != generation)
                            recoveryFrame.set(new RecoveryFrame(generation, presentationUs));
                    }
                    input.put(frame); decoder.queueInputBuffer(index, 0, frame.length, presentationUs, 0);
                    waitingForKeyframe = false;
                } else {
                    waitingForKeyframe = true;
                    interruptPicture(); requestKeyframe();
                }
                drainVideo();
            }
            case 13 -> {
                if (decoder != null && keyframeForeground && surface != null && decoderEpoch == surfaceEpoch && ((hostLock != null && hostLock.needsFreshFrame())
                        || (keyframeRecovery != null && keyframeRecovery.isOpen()))) drainVideo();
                if (waitingForKeyframe && surface != null) interruptPicture();
                requestKeyframe();
            }
            case 22 -> throw new IllegalStateException(new String(message.payload(), StandardCharsets.UTF_8));
        }
    }
    private void createDecoder(Hevc.Parameters params) throws Exception {
        Surface target; long targetEpoch;
        synchronized (surfaceLock) {
            long until = android.os.SystemClock.uptimeMillis() + 3000;
            while (surface == null && android.os.SystemClock.uptimeMillis() < until) surfaceLock.wait(100);
            target = surface; targetEpoch = surfaceEpoch;
        }
        if (target == null || !target.isValid()) {
            waitingForKeyframe = true;
            interruptPicture();
            return;
        }
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(params.codecConfig()));
        SillConnection expected = connection; HostLockState lock = hostLock; KeyframeRecovery recovery = keyframeRecovery;
        MediaCodec made = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
        try {
            made.configure(format, target, null, 0);
            made.setOnFrameRenderedListener((codec, timeUs, nanoTime) -> {
                Surface renderedSurface = surface;
                if (connection != expected || hostLock != lock || keyframeRecovery != recovery || decoder != made || lock == null || lock.isCovered()
                        || !keyframeForeground || renderedSurface == null || !renderedSurface.isValid() || renderedSurface != decoderSurface
                        || surfaceEpoch != targetEpoch || decoderEpoch != targetEpoch) return;
                RecoveryFrame recovered = recoveryFrame.get();
                if (recovery != null && recovered != null && timeUs >= recovered.presentationUs() && recoveryFrame.compareAndSet(recovered, null)) {
                    recovery.pictureBack(android.os.SystemClock.uptimeMillis(), keyframeForeground, recovered.generation());
                    android.util.Log.i("SillAndroid", "Recovery: keyframe presented");
                }
                FreshFrame candidate = revealFrame.get();
                if (candidate == null ||
                        candidate.generation() != lock.generation() || timeUs < candidate.presentationUs()) return;
                if (!revealFrame.compareAndSet(candidate, null)) return;
                lock.onKeyframePresented(candidate.generation());
                renderLockOverlay(lock, expected);
            }, ui);
            made.start(); made.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            decoderSurface = target; decoderEpoch = targetEpoch; decoder = made;
            waitingForKeyframe = true;
        } catch (Exception e) {
            made.release();
            if (surfaceEpoch != targetEpoch || !keyframeForeground) { interruptPicture(); return; }
            throw e;
        }
    }
    private void renderLockOverlay(HostLockState lock, SillConnection expected) {
        if (connection != expected || hostLock != lock) return;
        if (scaleControl != null) scaleControl.refresh();
        if (!lock.isCovered() && !lock.needsFreshFrame()) { lockOverlay.setVisibility(android.view.View.GONE); return; }
        lockOverlay.setText(lock.isCovered() ? "另一位用户正在使用这台 Mac。请切回你的账号以继续。" : "正在等待 Mac 画面…");
        lockOverlay.setVisibility(android.view.View.VISIBLE);
    }
    private void interruptPicture() {
        KeyframeRecovery recovery = keyframeRecovery;
        if (recovery != null) recovery.interrupted(android.os.SystemClock.uptimeMillis());
    }
    private void requestKeyframe() throws java.io.IOException { requestKeyframe(connection, keyframeRecovery); }
    private void requestKeyframe(SillConnection expected, KeyframeRecovery recovery) throws java.io.IOException {
        if (expected == null || recovery == null) return;
        synchronized (outputLock) {
            HostLockState lock = hostLock; Surface target = surface;
            if (connection != expected || keyframeRecovery != recovery || lock == null || lock.isCovered()) return;
            if (recovery.consider(android.os.SystemClock.uptimeMillis(), keyframeForeground && target != null && target.isValid(), keyframeStreaming, keyframeWorstRtt)) {
                expected.send(44, new byte[0]);
                android.util.Log.i("SillAndroid", "Recovery: keyframe requested");
            }
        }
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
                if (w > 0 && h > 0) { videoWidth = w; videoHeight = h; ui.post(() -> { if (largerPictureApplied) trackpad.setVerticalRatio((float) videoHeight / videoWidth); layoutVideo(); cursor.invalidate(); }); }
            } else break;
        }
    }
    private void loadWindowOrder() {
        windowOrder = new java.util.ArrayList<>();
        if (windowOrderMac.isEmpty()) return;
        try {
            JSONArray saved = new JSONArray(getPreferences(0).getString("Sill.windowOrder." + windowOrderMac, "[]"));
            for (int i = 0; i < Math.min(64, saved.length()); i++) windowOrder.add(saved.getLong(i));
        } catch (org.json.JSONException ignored) { windowOrder.clear(); }
    }
    private JSONArray orderedWindows(JSONArray windows) {
        java.util.Map<Long, JSONObject> byId = new java.util.LinkedHashMap<>();
        for (int i = 0; i < windows.length(); i++) {
            JSONObject w = windows.optJSONObject(i);
            if (w != null && w.has("id")) byId.put(w.optLong("id", -1), w);
        }
        JSONArray sorted = new JSONArray();
        for (Long id : WindowOrder.ordered(new java.util.ArrayList<>(byId.keySet()), windowOrder)) sorted.put(byId.get(id));
        return sorted;
    }
    private java.util.List<Long> visibleWindowIds() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < choices.getChildCount(); i++) {
            Object tag = choices.getChildAt(i).getTag(); if (tag instanceof Long) ids.add((Long) tag);
        }
        return ids;
    }
    private void persistWindowOrder() {
        if (windowOrderMac.isEmpty()) return;
        windowOrder = WindowOrder.capped(windowOrder, visibleWindowIds());
        getPreferences(0).edit().putString("Sill.windowOrder." + windowOrderMac, new JSONArray(windowOrder).toString()).apply();
    }
    private void moveWindow(android.view.View tile, int target) {
        int from = choices.indexOfChild(tile);
        if (from < 0 || target < 0 || target >= choices.getChildCount() || from == target) return;
        java.util.List<Long> ids = visibleWindowIds();
        if (!WindowOrder.move(ids, (Long) tile.getTag(), target)) return;
        choices.removeViewAt(from); choices.addView(tile, target); windowOrder = ids;
        tile.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
    }
    private void cancelWindowDrag() {
        if (draggedWindow == null) return;
        for (int i = 0; i < choices.getChildCount(); i++) { choices.getChildAt(i).setTranslationX(0); choices.getChildAt(i).setScaleX(1); choices.getChildAt(i).setScaleY(1); }
        draggedWindow = null; choices.requestDisallowInterceptTouchEvent(false);
    }
    private void dropWindowDrag() {
        android.view.View tile = draggedWindow; int target = dragTo;
        cancelWindowDrag();
        if (tile != null) { moveWindow(tile, target); persistWindowOrder(); }
    }
    private boolean sameWindowChoices(JSONArray windows) {
        if (choices.getChildCount() != windows.length()) return false;
        for (int i = 0; i < windows.length(); i++) {
            JSONObject item = windows.optJSONObject(i);
            if (item == null || !item.has("id")) return false;
            android.view.View tile = choices.getChildAt(i);
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
        tile.setOnClickListener(v -> {
            if (windowControls != null && windowControls.isShowing()) dismissWindowControls();
            else select(source, false);
        });
        tile.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(android.view.View view, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(MOVE_LEFT_ACTION, "向左移动窗口"));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(MOVE_RIGHT_ACTION, "向右移动窗口"));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, "窗口控制"));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(CLOSE_WINDOW_ACTION, "关闭窗口"));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(MINIMIZE_WINDOW_ACTION, "最小化窗口"));
                info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(FULL_SCREEN_ACTION, "全屏窗口"));
            }
            @Override public boolean performAccessibilityAction(android.view.View view, int action, android.os.Bundle args) {
                if (action == MOVE_LEFT_ACTION || action == MOVE_RIGHT_ACTION) {
                    int target = choices.indexOfChild(view) + (action == MOVE_LEFT_ACTION ? -1 : 1);
                    moveWindow(view, target); persistWindowOrder(); return true;
                }
                if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK) { showWindowControls(view, source); return true; }
                if (action == CLOSE_WINDOW_ACTION || action == MINIMIZE_WINDOW_ACTION || action == FULL_SCREEN_ACTION) {
                    sendWindowCommand(connection, source, action == CLOSE_WINDOW_ACTION ? "close" : action == MINIMIZE_WINDOW_ACTION ? "minimize" : "fullScreen"); return true;
                }
                return super.performAccessibilityAction(view, action, args);
            }
        });
        tile.setOnTouchListener(new android.view.View.OnTouchListener() {
            final WindowHold gesture = new WindowHold();
            float lastRawX, lastRawY, heldX, heldY;
            SillConnection gestureSession;
            final Runnable hold = () -> {
                if (!keyframeForeground || !tile.isAttachedToWindow() || gestureSession == null || connection != gestureSession || !thumbnails.containsKey(source.optLong("_0", -1))) return;
                if (!gesture.hold()) return;
                heldX = lastRawX; heldY = lastRawY;
                tile.getParent().requestDisallowInterceptTouchEvent(true);
                tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showWindowControls(tile, source);
            };
            public boolean onTouch(android.view.View view, android.view.MotionEvent event) {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN -> {
                        gesture.begin(event.getX(), event.getY());
                        gestureSession = connection;
                        lastRawX = event.getRawX(); lastRawY = event.getRawY();
                        int tenths = Math.max(2, Math.min(20, getPreferences(0).getInt("thumbnailHoldTenths", 5)));
                        view.postDelayed(hold, tenths * 100L); return true;
                    }
                    case android.view.MotionEvent.ACTION_MOVE -> {
                        if (!keyframeForeground || gestureSession == null || connection != gestureSession) { view.removeCallbacks(hold); gesture.cancel(); if (draggedWindow == tile) cancelWindowDrag(); return true; }
                        lastRawX = event.getRawX(); lastRawY = event.getRawY();
                        if (gesture.isHeld()) {
                            float dx = lastRawX - heldX, dy = lastRawY - heldY;
                            if (draggedWindow == null && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) {
                                dismissWindowControls(); draggedWindow = tile; dragFrom = choices.indexOfChild(tile); dragTo = dragFrom;
                                tile.setScaleX(1.08f); tile.setScaleY(1.08f);
                                tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            }
                            if (draggedWindow == tile) {
                                int slot = tile.getWidth() + dp(6);
                                dragTo = Math.max(0, Math.min(choices.getChildCount() - 1, dragFrom + Math.round(dx / slot)));
                                tile.setTranslationX(dx);
                                for (int i = 0; i < choices.getChildCount(); i++) {
                                    android.view.View other = choices.getChildAt(i); if (other == tile) continue;
                                    other.setTranslationX(i > dragFrom && i <= dragTo ? -slot : i < dragFrom && i >= dragTo ? slot : 0);
                                }
                            }
                        } else if (gesture.move(event.getX(), event.getY(), dp(12))) view.removeCallbacks(hold);
                        return true;
                    }
                    case android.view.MotionEvent.ACTION_UP -> {
                        view.removeCallbacks(hold);
                        if (!keyframeForeground || gestureSession == null || connection != gestureSession) { gesture.cancel(); if (draggedWindow == tile) cancelWindowDrag(); return true; }
                        if (draggedWindow == tile) dropWindowDrag();
                        else if (gesture.end()) view.performClick();
                        if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false); return true;
                    }
                    case android.view.MotionEvent.ACTION_CANCEL -> { view.removeCallbacks(hold); gesture.cancel(); if (draggedWindow == tile) { if (keyframeForeground && gestureSession != null && connection == gestureSession) dropWindowDrag(); else cancelWindowDrag(); } if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false); return true; }
                    default -> { return true; }
                }
            }
        });
    }
    private void dismissWindowControls() {
        if (windowControls != null) { windowControls.dismiss(); windowControls = null; }
    }
    private void sendWindowCommand(SillConnection expected, JSONObject source, String action) {
        if (expected == null || connection != expected || !thumbnails.containsKey(source.optLong("_0", -1))) return;
        try { sendAsync(15, new JSONObject().put("id", source.getLong("_0")).put("action", action)); }
        catch (Exception e) { show("窗口操作失败"); }
    }
    private void showWindowControls(android.view.View anchor, JSONObject source) {
        dismissWindowControls();
        SillConnection expected = connection;
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER);
        row.setBackground(shape(0xff303238, 14));
        String[] labels = {"关闭", "最小化", "全屏"};
        String[] symbols = {"×", "−", "+"};
        String[] actions = {"close", "minimize", "fullScreen"};
        int[] colors = {0xffff5f57, 0xffffbd2e, 0xff28c840};
        for (int i = 0; i < labels.length; i++) {
            final String action = actions[i];
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(android.view.Gravity.CENTER);
            android.widget.TextView light = new android.widget.TextView(this); light.setText(symbols[i]); light.setGravity(android.view.Gravity.CENTER);
            light.setTextColor(0xff333333); light.setTextSize(16); light.setBackground(shape(colors[i], 24));
            item.addView(light, new LinearLayout.LayoutParams(dp(24), dp(24)));
            android.widget.TextView label = new android.widget.TextView(this); label.setText(labels[i]); label.setTextColor(0xffeeeeee); label.setTextSize(10); label.setGravity(android.view.Gravity.CENTER);
            item.addView(label); item.setContentDescription(labels[i] + "窗口");
            item.setOnClickListener(v -> {
                dismissWindowControls();
                sendWindowCommand(expected, source, action);
            });
            row.addView(item, new LinearLayout.LayoutParams(dp(60), -1));
        }
        windowControls = new android.widget.PopupWindow(row, dp(214), dp(62), false);
        windowControls.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        windowControls.setOutsideTouchable(true); windowControls.setElevation(dp(8));
        int[] location = new int[2]; anchor.getLocationInWindow(location);
        int width = root.getRootView().getWidth(), height = root.getRootView().getHeight();
        int x = Math.max(dp(8), Math.min(location[0] + anchor.getWidth() / 2 - dp(107), width - dp(222)));
        boolean below = height > width || location[1] + anchor.getHeight() / 2 < height / 2;
        int y = below ? location[1] + anchor.getHeight() + dp(8) : location[1] - dp(70);
        windowControls.showAtLocation(root, android.view.Gravity.TOP | android.view.Gravity.START, x, Math.max(0, y));
    }
    private void releaseDecoder() { decoderEpoch = -1; recoveryFrame.set(null); revealFrame.set(null); recoveryGeneration = -1; if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) { } decoder.release(); decoder = null; decoderSurface = null; } }
    private void resetCursorShape() { cursorBitmap = null; cursorShape = null; cursor.invalidate(); }
    @Override protected void onStart() { super.onStart(); if (discovery != null) discovery.start(); startFpsObservers(); }
    @Override protected void onResume() { super.onResume(); keyframeForeground = true; android.util.Log.i("SillAndroid", "Recovery: foreground"); if (screenAwake != null) screenAwake.setForeground(true); }
    @Override protected void onPause() { if (scaleControl != null) scaleControl.cancel(); android.util.Log.i("SillAndroid", "Recovery: background"); keyframeForeground = false; synchronized (surfaceLock) { surfaceEpoch++; } recoveryFrame.set(null); interruptPicture(); if (keyframeRecovery != null) keyframeRecovery.leftForeground(); if (screenAwake != null) screenAwake.setForeground(false); super.onPause(); }
    @Override public void onUserInteraction() { super.onUserInteraction(); if (screenAwake != null) screenAwake.personActed(); }
    @Override protected void onStop() { dismissSettings(false); dismissMenu(); if (appDrawer != null) appDrawer.dismiss(); dismissWindowControls(); cancelWindowDrag(); stopFpsObservers(); clipboardReceipt.cancel(); if (pictureInput != null) pictureInput.cancel(); if (trackpad != null) trackpad.cancelGestures(); if (discovery != null) discovery.stop(); nearby.clear(); nearbyMacs.removeAllViews(); super.onStop(); }
    @Override protected void onDestroy() {
        stopFpsObservers();
        if (screenAwake != null) screenAwake.close();
        running = false; SillConnection c = connection; if (c != null) try { c.close(); } catch (Exception ignored) { }
        synchronized (reconnectLock) { reconnectLock.notifyAll(); }
        network.shutdownNow(); input.shutdownNow(); heartbeat.shutdownNow(); super.onDestroy();
    }
}
