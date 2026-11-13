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
    private final java.util.concurrent.ThreadPoolExecutor qrNetwork = new java.util.concurrent.ThreadPoolExecutor(
            0, 2, 30, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(),
            task -> { Thread thread = new Thread(task, "sill-qr-pair"); thread.setDaemon(true); return thread; });
    private final ExecutorService input = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object outputLock = new Object();
    private final Object reconnectLock = new Object();
    private volatile SillConnection connection;
    private SessionAuthorization activeAuthorization;
    private PairingAuthorization pendingPairing;
    private QrPairingAttempt qrAttempt;
    private PairLink qrLink;
    private Runnable qrBegin;
    private volatile RemoteDial.Attempt pendingDial;
    // Accessed only under outputLock: serializes an automatic first pick with user picks.
    private InitialSourcePolicy initialSource;
    private volatile HostLockState hostLock;
    private volatile PointerSync pointerSync;
    private volatile boolean running;
    private volatile Surface surface;
    private LinearLayout root, connectionPanel, sessionPanel, choices, savedMacs, nearbyMacs, keyRow;
    private android.widget.FrameLayout sessionLayer;
    private LinearLayout sessionBar;
    private Button tourAppsButton, tourKeyboardButton, tourSettingsButton;
    private android.widget.HorizontalScrollView tourStrip;
    private TourOverlay tourOverlay;
    private TourPolicy.Memory tourMemory;
    private TourPolicy.Session tourSession = TourPolicy.Session.fresh();
    private TourPolicy.Run tourRun;
    private TourPolicy.Layout tourLayout;
    private SillConnection tourOwner;
    private Double tourPictureAt, tourLastActivity;
    private double tourLayoutAt;
    private int tourTouches;
    private boolean tourRestoreKeyboard;
    private volatile boolean tourInputBlocked;
    private volatile long tourInputGeneration;
    private int tourAccessibilityBefore, tourFocusabilityBefore;
    private android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener tourAccessibilityListener;
    private android.view.View tourFocusBefore;
    private android.window.OnBackInvokedCallback tourBack;
    private final Runnable tourWait = this::considerTour;
    private ScrollView connectionContainer;
    private LinearLayout connectionHome, addMacCard, typedPairing;
    private LinearLayout pairDetails, pairActions;
    private TextView pairWords;
    private boolean pairSideBySide;
    private InlinePairScanner inlineScanner;
    private android.app.AlertDialog pairConfirmation;
    private PairLink confirmationLink;
    private TextView connectionTitle;
    private Button connectionStyle, pairingPath, cancelConnection;
    private android.window.OnBackInvokedCallback addMacBack;
    private boolean addingMac, typingPairing, connectionTiles;
    private int savedMacColumns;
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
    private volatile boolean clipboardEnabled, pasteRightAway;
    private volatile boolean hostClipboardAvailable;
    private final ClipboardReceipt clipboardReceipt = new ClipboardReceipt();
    private long clipboardToken;
    private volatile String clipboardSource = "";
    private boolean audioEnabled;
    private volatile AudioPlayer audio;
    private volatile String audioEndpointHost;
    private LocalAudioControl localAudio;
    private PipController pip;
    private boolean pipEnabled, pipUi;
    private int pipReturnWidthDp, pipReturnHeightDp;
    private volatile boolean picturePresented;
    private SillConnection pipOwner;
    private final java.util.Map<android.view.View, Integer> pipVisibility = new java.util.IdentityHashMap<>();
    private volatile boolean clipboardSubscribed;
    private Double textScale;
    private TextScaleControl scaleControl;
    private final Runnable viewportUpdate = this::sendViewport;
    private Runnable updateSessionLayout;
    private float portraitImeHeight;
    private int insetLeft, insetTop, insetRight, insetBottom;
    private boolean largerPicture, largerPictureApplied, settingsOpen, settingsHeldLargerPicture, settingsReanchoring, settingsReanchorPending;
    private android.widget.FrameLayout settingsOverlay;
    private ScrollView settingsScroll;
    private int settingsScrollY;
    private android.window.OnBackInvokedCallback settingsBack;
    private android.app.AlertDialog settingsChild;
    private android.app.AlertDialog settingsDialog;
    private Runnable refreshSettingsMute;
    private Runnable refreshInlineHostSettings;
    private android.widget.PopupMenu qualityMenu;
    private static final String[] QUALITY_NAMES = {"低", "节省", "均衡", "高", "专业", "极高", "极限"};
    private static final int[] QUALITY_RATES = {4000000, 8000000, 15000000, 25000000, 40000000, 80000000, 150000000};
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
    private Boolean toolbarLandscape;
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
    private WindowHold activeWindowHold;
    private android.view.View activeWindowHoldView;
    private Runnable activeWindowHoldRunnable;
    private float draggedWindowOriginalZ;
    private int dragFrom, dragTo;
    private java.util.List<Long> dragPreview;
    private static final int MOVE_LEFT_ACTION = 0x00ff0001, MOVE_RIGHT_ACTION = 0x00ff0002;
    private static final int CLOSE_WINDOW_ACTION = 0x00ff0003, MINIMIZE_WINDOW_ACTION = 0x00ff0004, FULL_SCREEN_ACTION = 0x00ff0005;
    private final Runnable drawerUpdate = () -> {
        HostLockState lock = hostLock;
        if (appDrawer != null && appDrawer.isShowing() && lock != null)
            lock.withPresentedList(() -> appDrawer.update(drawerWindows, appList, appIcons, activeWindowId, desktopActive));
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
        loadTourMemory();
        android.view.accessibility.AccessibilityManager accessibility = (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibility != null) {
            tourAccessibilityListener = enabled -> ui.post(this::tourAccessibilityChanged);
            accessibility.addTouchExplorationStateChangeListener(tourAccessibilityListener);
        }
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
                insetLeft = bars.left; insetTop = bars.top; insetRight = bars.right; insetBottom = bars.bottom;
                applyContentInsets();
                if (insets.isVisible(android.view.WindowInsets.Type.ime()) && view.getWidth() < view.getHeight()) {
                    float height = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom / getResources().getDisplayMetrics().density;
                    if (height > 0 && Math.abs(height - portraitImeHeight) > 1) {
                        portraitImeHeight = height;
                        getPreferences(0).edit().putFloat("portraitImeHeight", height).apply();
                        if (!settingsOpen && updateSessionLayout != null) view.post(updateSessionLayout);
                    }
                }
            } else {
                insetLeft = insets.getSystemWindowInsetLeft(); insetTop = insets.getSystemWindowInsetTop();
                insetRight = insets.getSystemWindowInsetRight(); insetBottom = insets.getSystemWindowInsetBottom();
                applyContentInsets();
            }
            return insets;
        });
        root.setBackgroundColor(0xff08090b);
        buildConnectionScreen();
        clipboardEnabled = getPreferences(0).getBoolean("shareClipboard", false);
        pasteRightAway = getPreferences(0).getBoolean("pasteRightAway", true);
        audioEnabled = getPreferences(0).getBoolean("experimentalAudio", false);
        localAudio = new LocalAudioControl(this, getPreferences(0).getBoolean("soundMuted", false), muted -> {
            getPreferences(0).edit().putBoolean("soundMuted", muted).apply();
            runOnUiThread(() -> { if (refreshSettingsMute != null) refreshSettingsMute.run(); });
        }, () -> {
            show("耳机已断开，本机声音已静音");
            if (root != null) root.announceForAccessibility("耳机已断开，本机声音已静音");
        });
        refreshSavedMacs();
        buildSession();
        initializePip();
        discovery = new MacDiscovery(this, new MacDiscovery.Listener() {
            public void found(String name, String address, int number) {
                android.view.View previous = nearby.remove(name); if (previous != null) nearbyMacs.removeView(previous);
                String tag = discovery.recognitionTag(name);
                SavedMacs.Identity recognized = savedMacStore.recognized(tag);
                String label = recognized != null && !recognized.name().isEmpty() ? recognized.name() : name;
                Button row = button(nearbyMacs, "附近 · " + label, v -> {
                    if (running) { show("请先取消当前连接。"); return; }
                    host.setText(address); port.setText(String.valueOf(number));
                    if (recognized != null) start(false, recognized, tag);
                    else if (savedMacStore.pin(new SavedMacs.Endpoint(address, number)) != null) start(false, null, null, false);
                    else { setAddingMac(true, true); show("请在 Mac 的 Sill 中打开配对窗口并输入配对码。"); }
                });
                styleConnectionRow(row);
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

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == InlinePairScanner.CAMERA_REQUEST && inlineScanner != null) inlineScanner.permissionResult();
    }

    private void handlePairIntent(android.content.Intent intent) {
        if (intent == null || !android.content.Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        if (inlineScanner != null) inlineScanner.hold();
        clearPairConfirmation();
        String raw = intent.getData().toString();
        intent.setData(null);
        final PairLink link;
        try { link = PairLink.parse(raw); }
        catch (IllegalArgumentException e) { show("无效的 Sill 配对链接。"); return; }
        StringBuilder message = new StringBuilder("请确认这是你自己的 Mac：\n\n").append(link.name).append("\n");
        for (PairLink.Address a : link.addresses) message.append("\n").append(a.display(link.port));
        if (link.addresses.isEmpty()) message.append("\n将在本地网络查找此 Mac。");
        message.append("\n\n仅信任来自你自己 Mac 的配对链接。");
        confirmationLink = link;
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("确认配对 Mac").setMessage(message.toString())
                .setNegativeButton("取消", (d, which) -> clearPairConfirmation())
                .setPositiveButton("配对", (d, which) -> {
                    if (confirmationLink != link) return;
                    confirmationLink = null; pairConfirmation = null; pairLink(link);
                })
                .setOnCancelListener(d -> clearPairConfirmation()).create();
        pairConfirmation = dialog;
        dialog.show();
    }

    private void clearPairConfirmation() {
        PairLink link = confirmationLink; confirmationLink = null;
        android.app.AlertDialog dialog = pairConfirmation; pairConfirmation = null;
        if (link != null) link.clearSecret();
        if (dialog != null) dialog.dismiss();
    }

    private void pairLink(PairLink link) {
        if (running) { link.clearSecret(); show("已有连接，请先断开后重新打开配对链接。"); return; }
        QrPairingAttempt attempt = new QrPairingAttempt(); qrAttempt = attempt; qrLink = link;
        running = true; closing = false; updateConnectionCancel(); show("正在配对…");
        qrBegin = () -> beginPairLink(link, attempt);
        if (link.addresses.isEmpty() && discovery.pairingDoors().isEmpty()) {
            discovery.start();
            ui.postDelayed(qrBegin, 4000);
        } else qrBegin.run();
    }

    private void beginPairLink(PairLink link, QrPairingAttempt run) {
        if (qrAttempt != run || !run.active() || isFinishing() || isDestroyed()) { link.clearSecret(); return; }
        qrBegin = null;
        java.util.List<PairLink.Address> local = link.localCandidates(discovery.pairingDoors());
        java.util.List<PairLink.Address> candidates = link.candidates(local);
        try { qrNetwork.execute(() -> {
            PairLink.Address winner = null; SavedMacs.Identity identity = null;
            byte[] secret = link.secret(); byte[] expected = link.fingerprint();
            try {
                for (int i = 0; i < candidates.size(); i++) {
                    PairLink.Address a = candidates.get(i);
                    if (!run.active() || Thread.currentThread().isInterrupted()) break;
                    int number = a.port() == 0 ? link.port : a.port();
                    RemoteDial.Attempt transport = new RemoteDial.Attempt();
                    if (!run.track(transport)) break;
                    try (SillConnection pair = SillConnection.connect(a.host(), number, "sill-pair/1", expected,
                            i < local.size() ? 1200 : 10000, i < local.size() ? 1800 : 15000, transport)) {
                        transport.check();
                        SavedMacs.Identity result = pair.pairQrIdentity(secret, android.os.Build.MODEL, android.os.Build.DEVICE);
                        transport.check();
                        if (!java.security.MessageDigest.isEqual(expected, result.pin())) throw new SecurityException("Mac identity changed");
                        identity = result; winner = a; break;
                    } catch (Exception ignored) { }
                    finally { transport.close(); run.untrack(transport); }
                }
            } finally { java.util.Arrays.fill(secret, (byte) 0); link.clearSecret(); }
            PairLink.Address selected = winner; SavedMacs.Identity authenticated = identity;
            ui.post(() -> {
                if (qrAttempt != run || isFinishing() || isDestroyed()) return;
                boolean completed = run.complete(() -> {
                    if (selected != null && authenticated != null) {
                        int number = selected.port() == 0 ? link.port : selected.port();
                        synchronized (savedMacStore) { savedMacStore.paired(authenticated, new SavedMacs.Endpoint(selected.host(), number)); }
                    }
                });
                if (!completed) return;
                qrAttempt = null; qrLink = null;
                running = false; updateConnectionCancel();
                if (selected == null || authenticated == null) { show("配对失败，请确认 Mac 的配对链接仍有效且网络可达。"); return; }
                int number = selected.port() == 0 ? link.port : selected.port();
                getPreferences(0).edit().putString("host", selected.host()).putString("port", String.valueOf(number)).apply();
                host.setText(selected.host()); port.setText(String.valueOf(number)); code.setText(""); refreshSavedMacs();
                start(false, null, null, false);
            });
        }); } catch (java.util.concurrent.RejectedExecutionException busy) {
            cancelQrPairing(); show("配对线程仍在结束，请稍后重试。");
        }
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
    private void buildConnectionScreen() {
        connectionTiles = getPreferences(0).getBoolean("connectionTiles", true);
        connectionContainer = new ScrollView(this); connectionContainer.setFillViewport(true);
        connectionContainer.setBackgroundColor(android.graphics.Color.BLACK);
        connectionContainer.setVerticalScrollBarEnabled(false);
        android.widget.FrameLayout space = new android.widget.FrameLayout(this);
        connectionContainer.addView(space, new ScrollView.LayoutParams(-1, -1));
        connectionPanel = new LinearLayout(this); connectionPanel.setOrientation(LinearLayout.VERTICAL);
        android.widget.FrameLayout.LayoutParams column = new android.widget.FrameLayout.LayoutParams(dp(380), -2, android.view.Gravity.CENTER);
        space.addView(connectionPanel, column); root.addView(connectionContainer, new LinearLayout.LayoutParams(-1, -1));
        connectionContainer.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, oright, ob) -> {
            boolean side = addingMac && !typingPairing && bottom - top < dp(520) && right - left >= dp(540);
            int width = Math.max(1, Math.min(dp(side ? 620 : 380), right - left - dp(8)));
            android.widget.FrameLayout.LayoutParams p = (android.widget.FrameLayout.LayoutParams) connectionPanel.getLayoutParams();
            if (p.width != width) { p.width = width; connectionPanel.setLayoutParams(p); }
            int columns = width >= dp(340) && getResources().getConfiguration().fontScale < 1.3f ? 2 : 1;
            if (columns != savedMacColumns) { savedMacColumns = columns; refreshSavedMacs(); }
            if (pairSideBySide != side) connectionContainer.post(this::updatePairingGeometry);
        });
        connectionContainer.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                boolean imeVisible = insets.isVisible(android.view.WindowInsets.Type.ime());
                int imeBottom = imeVisible ? Math.max(0, insets.getInsets(android.view.WindowInsets.Type.ime()).bottom - insetBottom) : 0;
                if (connectionContainer.getPaddingBottom() != imeBottom) {
                    connectionContainer.setPadding(0, 0, 0, imeBottom);
                    if (imeVisible) connectionContainer.post(() -> {
                        android.view.View focused = getCurrentFocus();
                        if (focused != host && focused != port && focused != code) return;
                        android.graphics.Rect bounds = new android.graphics.Rect();
                        focused.getDrawingRect(bounds); connectionContainer.offsetDescendantRectToMyCoords(focused, bounds);
                        connectionContainer.smoothScrollTo(0, Math.max(0, bounds.bottom - connectionContainer.getHeight() + connectionContainer.getPaddingBottom() + dp(16)));
                    });
                }
                android.widget.FrameLayout.LayoutParams p = (android.widget.FrameLayout.LayoutParams) connectionPanel.getLayoutParams();
                int gravity = imeVisible
                        ? android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL : android.view.Gravity.CENTER;
                if (p.gravity != gravity) { p.gravity = gravity; connectionPanel.setLayoutParams(p); }
            }
            return insets;
        });
        LinearLayout heading = new LinearLayout(this); heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(10), 0, 0, 0); connectionPanel.addView(heading);
        connectionTitle = new TextView(this); connectionTitle.setTextSize(17); connectionTitle.setTextColor(0xffeef0f3);
        connectionTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        heading.addView(connectionTitle, new LinearLayout.LayoutParams(0, -2, 1));
        connectionStyle = button(heading, "", v -> {
            connectionTiles = !connectionTiles; getPreferences(0).edit().putBoolean("connectionTiles", connectionTiles).apply();
            updateConnectionStyle(); refreshSavedMacs();
        });
        connectionStyle.setTextSize(20); connectionStyle.setTextColor(0xff9bb7ff);
        connectionStyle.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        connectionStyle.setPadding(0, 0, 0, 0); connectionStyle.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        status = new TextView(this); status.setTextSize(13); status.setTextColor(0xffa4abb5);
        status.setPadding(dp(10), 0, dp(10), dp(10));
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setText("正在查找 Mac。跨网络连接需在 Mac 上启用 Remote Access。"); connectionPanel.addView(status);
        connectionHome = new LinearLayout(this); connectionHome.setOrientation(LinearLayout.VERTICAL); connectionPanel.addView(connectionHome);
        savedMacs = new LinearLayout(this); savedMacs.setOrientation(LinearLayout.VERTICAL);
        savedMacs.setPadding(dp(10), 0, dp(10), 0); connectionHome.addView(savedMacs);
        nearbyMacs = new LinearLayout(this); nearbyMacs.setOrientation(LinearLayout.VERTICAL);
        nearbyMacs.setPadding(dp(10), 0, dp(10), 0); connectionHome.addView(nearbyMacs);
        Button add = connectionLink(connectionHome, "添加 Mac…", v -> { if (!running) setAddingMac(true, false); });
        add.setContentDescription("添加 Mac，用配对码连接");
        cancelConnection = connectionLink(connectionHome, "取消连接", v -> cancelConnectionAttempt());
        updateConnectionCancel();
        addMacCard = new LinearLayout(this); addMacCard.setOrientation(LinearLayout.VERTICAL);
        addMacCard.setPadding(dp(10), 0, dp(10), 0); connectionPanel.addView(addMacCard);
        pairWords = new TextView(this); pairWords.setTextSize(13); pairWords.setTextColor(0xffa4abb5);
        pairWords.setText("配对一次后，即可通过 VPN 或网络连接这台 Mac。\n在 Mac 的 Sill 菜单中打开配对窗口。");
        addMacCard.addView(pairWords);
        inlineScanner = new InlinePairScanner(this, raw -> {
            if (!running && addingMac && !typingPairing)
                handlePairIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(raw)));
        });
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(-1, -2); scanParams.topMargin = dp(10);
        addMacCard.addView(inlineScanner, scanParams);
        typedPairing = new LinearLayout(this); typedPairing.setOrientation(LinearLayout.VERTICAL); addMacCard.addView(typedPairing);
        host = field("Mac IP 或 Tailscale 地址", getPreferences(0).getString("host", ""));
        port = field("端口", getPreferences(0).getString("port", "7455")); port.setInputType(2);
        code = field("Mac 上显示的 12 位配对码", ""); code.setInputType(2);
        Button pair = button(typedPairing, "配对", v -> connect());
        pair.setTextSize(15); pair.setTextColor(android.graphics.Color.BLACK); pair.setBackground(shape(0xff9bb7ff, 12));
        LinearLayout.LayoutParams pairParams = new LinearLayout.LayoutParams(dp(88), dp(44)); pairParams.topMargin = dp(8); pair.setLayoutParams(pairParams);
        connectionLink(typedPairing, "连接已配对的 Mac", v -> start(false));
        pairActions = new LinearLayout(this); addMacCard.addView(pairActions);
        pairingPath = connectionLink(pairActions, "", v -> setAddingMac(true, !typingPairing));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(-2, dp(44)); pathParams.rightMargin = dp(18); pairingPath.setLayoutParams(pathParams);
        connectionLink(pairActions, "取消", v -> closeAddMac());
        pairDetails = new LinearLayout(this); pairDetails.setOrientation(LinearLayout.VERTICAL);
        inlineScanner.setTag("scanner"); setAddingMac(false, false); updateConnectionStyle();
    }
    private Button connectionLink(LinearLayout parent, String label, android.view.View.OnClickListener action) {
        Button b = button(parent, label, action); b.setTextSize(15); b.setTextColor(0xff9bb7ff);
        b.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        b.setBackgroundColor(android.graphics.Color.TRANSPARENT); b.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        b.setPadding(dp(10), 0, dp(10), 0); b.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44))); return b;
    }
    private void updateConnectionStyle() {
        connectionStyle.setText(connectionTiles ? "☰" : "▦");
        connectionStyle.setContentDescription(connectionTiles ? "显示为列表" : "显示为卡片");
    }
    private void setAddingMac(boolean adding, boolean typed) {
        if (!adding && addingMac) clearPairConfirmation();
        if (inlineScanner != null && (addingMac != adding || typingPairing != typed)) inlineScanner.reset();
        if (addingMac && (!adding || typingPairing != typed)) {
            android.view.View focused = getCurrentFocus();
            if (focused == host || focused == port || focused == code) {
                ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(), 0);
                focused.clearFocus();
            }
        }
        if (addingMac && !adding) code.setText("");
        addingMac = adding; typingPairing = typed;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (adding && addMacBack == null) {
                addMacBack = this::closeAddMac;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, addMacBack);
            } else if (!adding && addMacBack != null) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(addMacBack); addMacBack = null;
            }
        }
        connectionTitle.setText(adding ? "添加 Mac" : "连接 Mac");
        connectionStyle.setVisibility(adding ? android.view.View.GONE : android.view.View.VISIBLE);
        connectionHome.setVisibility(adding ? android.view.View.GONE : android.view.View.VISIBLE);
        addMacCard.setVisibility(adding ? android.view.View.VISIBLE : android.view.View.GONE);
        typedPairing.setVisibility(typed ? android.view.View.VISIBLE : android.view.View.GONE);
        android.view.View scanner = addMacCard.findViewWithTag("scanner");
        if (scanner != null) scanner.setVisibility(typed ? android.view.View.GONE : android.view.View.VISIBLE);
        if (inlineScanner != null) inlineScanner.setVisible(adding && !typed && !running);
        pairingPath.setText(typed ? "改为扫码" : "手动输入配对码");
        connectionContainer.post(this::updatePairingGeometry);
    }
    private void updatePairingGeometry() {
        int width = connectionContainer.getWidth(), height = connectionContainer.getHeight();
        if (width <= 0 || height <= 0) return;
        boolean side = addingMac && !typingPairing && height < dp(520) && width >= dp(540);
        int columnWidth = Math.max(1, Math.min(dp(side ? 620 : 380), width - dp(8)));
        android.widget.FrameLayout.LayoutParams p = (android.widget.FrameLayout.LayoutParams) connectionPanel.getLayoutParams();
        if (p.width != columnWidth) { p.width = columnWidth; connectionPanel.setLayoutParams(p); }
        layoutPairingCard(side);
    }
    private void layoutPairingCard(boolean side) {
        if (pairSideBySide == side || inlineScanner == null) return;
        pairSideBySide = side;
        inlineScanner.setVisible(false);
        if (side) {
            addMacCard.removeView(pairWords); addMacCard.removeView(inlineScanner);
            addMacCard.removeView(typedPairing); addMacCard.removeView(pairActions);
            addMacCard.setOrientation(LinearLayout.HORIZONTAL);
            inlineScanner.setCompact(true);
            addMacCard.addView(inlineScanner, new LinearLayout.LayoutParams(dp(260), -2));
            pairDetails.addView(pairWords); pairDetails.addView(typedPairing); pairDetails.addView(pairActions);
            LinearLayout.LayoutParams details = new LinearLayout.LayoutParams(0, -2, 1); details.leftMargin = dp(16);
            addMacCard.addView(pairDetails, details);
        } else {
            pairDetails.removeView(pairWords); pairDetails.removeView(typedPairing); pairDetails.removeView(pairActions);
            addMacCard.removeView(pairDetails); addMacCard.removeView(inlineScanner);
            addMacCard.setOrientation(LinearLayout.VERTICAL);
            inlineScanner.setCompact(false);
            addMacCard.addView(pairWords);
            LinearLayout.LayoutParams scan = new LinearLayout.LayoutParams(-1, -2); scan.topMargin = dp(10);
            addMacCard.addView(inlineScanner, scan); addMacCard.addView(typedPairing); addMacCard.addView(pairActions);
        }
        inlineScanner.setVisible(addingMac && !typingPairing && !running);
    }
    private void updateConnectionCancel() {
        if (cancelConnection != null) cancelConnection.setVisibility(running ? android.view.View.VISIBLE : android.view.View.GONE);
        if (inlineScanner != null) inlineScanner.setVisible(addingMac && !typingPairing && !running);
    }
    private void cancelConnectionAttempt() {
        if (running) {
            if (qrAttempt != null) cancelQrPairing(); else disconnect();
            displayStatus("已取消连接。");
        }
    }
    private void cancelQrPairing() {
        QrPairingAttempt attempt = qrAttempt; if (attempt == null) return;
        qrAttempt = null;
        if (qrBegin != null) { ui.removeCallbacks(qrBegin); qrBegin = null; }
        if (qrLink != null) { qrLink.clearSecret(); qrLink = null; }
        running = false; updateConnectionCancel(); attempt.cancel();
    }
    private void closeAddMac() { cancelConnectionAttempt(); setAddingMac(false, false); }
    @Override public void onBackPressed() {
        if (addingMac && connectionContainer.getVisibility() == android.view.View.VISIBLE) closeAddMac();
        else super.onBackPressed();
    }
    private void styleConnectionRow(Button row) {
        row.setTextSize(15); row.setTextColor(0xffeef0f3); row.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        row.setPadding(dp(10), 0, dp(10), 0); row.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        row.setMinHeight(dp(50)); row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
    }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setText(value);
        e.setTextColor(0xffeef0f3); e.setHintTextColor(0xffa4abb5); e.setTextSize(15);
        e.setPadding(dp(14), 0, dp(14), 0); e.setBackground(shape(0xff23262c, 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48)); p.topMargin = dp(8);
        typedPairing.addView(e, p); return e;
    }
    private void refreshSavedMacs() {
        savedMacs.removeAllViews();
        LinearLayout tileRow = null; int inRow = 0;
        for (SavedMacs.Mac mac : savedMacStore.all()) {
            android.view.View row;
            if (connectionTiles) {
                if (tileRow == null || inRow == Math.max(1, savedMacColumns)) {
                    tileRow = new LinearLayout(this); tileRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                    if (savedMacs.getChildCount() > 0) rowParams.topMargin = dp(10);
                    savedMacs.addView(tileRow, rowParams); inRow = 0;
                }
                LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
                tile.setBackground(shape(0xff23262c, 12)); tile.setClipToOutline(true);
                android.widget.FrameLayout image = new android.widget.FrameLayout(this); image.setBackgroundColor(0xff2b2e33);
                android.widget.ImageView icon = new android.widget.ImageView(this);
                icon.setImageDrawable(new ToolbarSymbol("desktop", 0xd9ffffff));
                image.addView(icon, new android.widget.FrameLayout.LayoutParams(dp(24), dp(24), android.view.Gravity.CENTER));
                tile.addView(image, new LinearLayout.LayoutParams(-1, dp(92)));
                LinearLayout description = new LinearLayout(this); description.setOrientation(LinearLayout.VERTICAL);
                description.setPadding(dp(10), dp(8), dp(10), dp(8)); description.setMinimumHeight(dp(46));
                TextView name = new TextView(this); name.setText(mac.displayName()); name.setTextSize(14); name.setTextColor(0xffeef0f3);
                name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
                name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END); description.addView(name);
                TextView kind = new TextView(this); kind.setText("已配对"); kind.setTextSize(12); kind.setTextColor(0xffa4abb5); description.addView(kind);
                tile.addView(description); tile.setContentDescription(mac.displayName() + "，已配对");
                tile.setOnClickListener(v -> connectSavedMac(mac.identity(), mac.preferred()));
                LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, -2, 1);
                if (inRow > 0) tileParams.leftMargin = dp(10); tileRow.addView(tile, tileParams); inRow++;
                if (Math.max(1, savedMacColumns) == 2 && inRow == 1) {
                    // The final tile keeps one column's width even when the row has no second Mac.
                    android.view.View spacer = new android.view.View(this);
                    LinearLayout.LayoutParams spare = new LinearLayout.LayoutParams(0, 1, 1); spare.leftMargin = dp(10);
                    tileRow.addView(spacer, spare);
                } else if (inRow == 2) tileRow.removeViewAt(1);
                row = tile;
            } else {
                Button list = button(savedMacs, mac.displayName() + "  ·  已配对", v -> connectSavedMac(mac.identity(), mac.preferred()));
                styleConnectionRow(list); row = list;
            }
            row.setOnLongClickListener(v -> {
                if (running) { show("请先取消当前连接。"); return true; }
                new android.app.AlertDialog.Builder(this).setTitle("移除本机记录？")
                    .setMessage("这只移除手机上的记录；如需撤销 Mac 的信任，请在 Mac 的 Sill 设置中移除设备。")
                    .setNegativeButton("取消", null).setPositiveButton("移除", (d, which) -> {
                        if (running) { show("请先取消当前连接。"); return; }
                        java.io.Closeable cancelled = null;
                        synchronized (savedMacStore) {
                            savedMacStore.forget(mac.identity());
                            if (activeAuthorization != null && activeAuthorization.matches(mac.identity().pin())) {
                                closing = true;
                                cancelled = activeAuthorization.revoke();
                            }
                            if (pendingPairing != null) {
                                java.io.Closeable pairingResource = pendingPairing.forget(mac.identity().pin());
                                if (pairingResource != null) { closing = true; cancelled = pairingResource; }
                            }
                        }
                        if (cancelled != null) try { cancelled.close(); } catch (java.io.IOException ignored) { }
                        synchronized (reconnectLock) { reconnectLock.notifyAll(); }
                        refreshSavedMacs();
                    }).show(); return true;
            });
        }
    }
    private void connectSavedMac(SavedMacs.Identity identity, SavedMacs.Endpoint endpoint) {
        if (running) { show("请先取消当前连接。"); return; }
        host.setText(endpoint.host()); port.setText(String.valueOf(endpoint.port())); start(false, identity);
    }
    private void buildSession() {
        sessionPanel = new LinearLayout(this); sessionPanel.setOrientation(LinearLayout.VERTICAL);
        sessionLayer = new android.widget.FrameLayout(this);
        sessionPanel.setVisibility(android.view.View.GONE);
        sessionLayer.addView(sessionPanel, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(sessionLayer, new LinearLayout.LayoutParams(-1, -1));
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
            public boolean trackpadBusy() { return trackpad != null && trackpad.busyForPicture(); }
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
        cursor.setOnGenericMotionListener(pictureInput);
        cursor.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(android.view.View view) { }
            public void onViewDetachedFromWindow(android.view.View view) { pictureInput.cancel(); }
        });
        LinearLayout bar = new LinearLayout(this); sessionBar = bar; bar.setGravity(android.view.Gravity.CENTER_VERTICAL); sessionPanel.addView(bar);
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
            if ((t != ot || b != ob) && root.getWidth() < root.getHeight() && appDrawer != null && appDrawer.isShowing()) {
                AppDrawerDialog drawer = appDrawer;
                int presentation = drawer.presentationVersion();
                bar.post(() -> {
                    if (appDrawer == drawer && connection != null)
                        drawer.reanchor(presentation, drawerWindows, appList, appIcons, activeWindowId,
                                desktopActive, picture, sessionLayer, sessionPanel, sessionBar,
                                root.getWidth() > root.getHeight(), largerPictureApplied);
                });
            }
        });
        Button appsButton = barButton(bar, "应用", v -> showWindows()); tourAppsButton = appsButton;
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
        Button keyboardButton = barButton(bar, "键盘", v -> toggleKeyboard()); tourKeyboardButton = keyboardButton;
        desktopButton = barButton(bar, "桌面", v -> tapDesktop());
        Button settingsButton = barButton(bar, "设置", v -> settings()); tourSettingsButton = settingsButton;
        configureToolbarButton(appsButton, "apps"); configureToolbarButton(menuButton, "menus");
        configureToolbarButton(keyboardButton, "keyboard"); configureToolbarButton(desktopButton, "desktop");
        configureToolbarButton(settingsButton, "settings");
        scaleButton.setTextColor(0xffeef0f3); scaleButton.setTextSize(11); scaleButton.setIncludeFontPadding(false);
        scaleButton.setBackground(shape(0xff23262c, 14));
        scaleButton.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            public void onTextChanged(CharSequence text, int start, int before, int count) { }
            public void afterTextChanged(android.text.Editable text) {
                if (text.length() >= 2) {
                    text.setSpan(new android.text.style.AbsoluteSizeSpan(17, true), 0, 2, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, 2, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        });
        scaleButton.refresh();
        root.getViewTreeObserver().addOnGlobalLayoutListener(this::refreshToolbarAppearance);
        android.widget.HorizontalScrollView strip = new android.widget.HorizontalScrollView(this); tourStrip = strip;
        strip.setHorizontalScrollBarEnabled(false); choices = new LinearLayout(this); choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(android.view.Gravity.CENTER_VERTICAL); choices.setPadding(dp(2), 0, dp(2), 0);
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
            if (pip != null && pip.active()) { layoutPip(); return; }
            applyContentInsets();
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
                    portraitImeHeight > 0 ? Math.max(0, (double) portraitImeHeight - root.getPaddingBottom() / density) : null, false,
                    settingsOpen ? settingsHeldLargerPicture : largerPicture, streaming);
            boolean large = geometry.largerPicture;
            if (large != largerPictureApplied) { dismissWindowControls(); cancelWindowDrag(); }
            largerPictureApplied = large;
            zoom.setTopAligned(large);
            trackpad.setVerticalRatio(large && videoWidth > 0 && videoHeight > 0 ? (float) videoHeight / videoWidth : 1 / 1.6f);
            int visible = landscape ? android.view.View.GONE : android.view.View.VISIBLE;
            sessionStatus.setVisibility(android.view.View.GONE); keys.setVisibility(visible); trackpad.setVisibility(visible);
            int statusWidth = Math.max(0, width - dp(32));
            if (pictureStatus.getMaxWidth() != statusWidth) pictureStatus.setMaxWidth(statusWidth);
            pictureStatus.setVisibility(pictureStatusActive ? android.view.View.VISIBLE : android.view.View.GONE);
            stripRow.setVisibility(landscape || large ? android.view.View.GONE : android.view.View.VISIBLE);
            if (landscape && strip.getParent() != bar) {
                stripRow.removeView(strip); if (menuButton.getParent() == stripRow) stripRow.removeView(menuButton); sessionPanel.removeView(bar);
                bar.addView(strip, 1, new LinearLayout.LayoutParams(0, dp(78), 1));
                if (menuButton.getParent() == bar) bar.removeView(menuButton);
                bar.addView(menuButton, 2, new LinearLayout.LayoutParams(dp(64), dp(58)));
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
            if (!landscape) {
                if (bar.getPaddingLeft() != 0) bar.setPadding(0, 0, 0, 0);
                bar.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                // Keep the picture's full room as the host viewport. The aspect-fit video and
                // cursor share its top-aligned pane in Larger Picture, without cropping.
                placePortraitRow(picture, geometry.picture, 0);
                placePortraitRow(bar, geometry.row1, geometry.picture.bottom());
                boolean menus = menuButton.getVisibility() == android.view.View.VISIBLE;
                portraitButtons(bar, 50);
                if (!large) {
                    placePortraitRow(stripRow, geometry.strip, geometry.row1.bottom());
                    PhoneStreamLayout.Rect stripFrame = menus ? geometry.stripBesideMenus : geometry.strip;
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp((float) stripFrame.width()), dp(62));
                    applyRowParams(strip, sp);
                    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp((float) geometry.menus.width()), dp(50));
                    mp.leftMargin = dp(8); applyRowParams(menuButton, mp);
                }
                placePortraitRow(keys, geometry.keys, large ? geometry.row1.bottom() : geometry.strip.bottom());
                portraitButtons(keys, 44);
                placePortraitRow(trackpad, geometry.trackpad, geometry.keys.bottom());
            } else {
                if (bar.getPaddingLeft() != dp(14) || bar.getPaddingTop() != 0) bar.setPadding(dp(14), 0, dp(14), 0);
                bar.setBackgroundColor(0xff16181c);
                applyRowParams(bar, new LinearLayout.LayoutParams(-1, dp(78)));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1);
                applyRowParams(picture, params);
                for (Button button : new Button[]{appsButton, scaleButton, keyboardButton, desktopButton, settingsButton}) {
                    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(64), dp(58));
                    bp.setMargins(dp(6), 0, dp(6), 0); applyRowParams(button, bp);
                }
                if (menuButton.getParent() == bar) {
                    LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(64), dp(58));
                    mp.setMargins(dp(6), 0, dp(6), 0); applyRowParams(menuButton, mp);
                }
            }
            updateWindowChoiceGeometry(landscape);
            refreshToolbarAppearance();
            layoutVideo(); cursor.invalidate();
            tourLayoutChanged();
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
                                    desktopActive, picture, sessionLayer, sessionPanel, sessionBar,
                                    root.getWidth() > root.getHeight(), largerPictureApplied);
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
    private void applyContentInsets() {
        if (pip != null && pip.active()) { applyRootPadding(0, 0, 0, 0); return; }
        int padding = sessionPanel != null && sessionPanel.getVisibility() == android.view.View.VISIBLE ? 0 : dp(12);
        applyRootPadding(padding + insetLeft, padding + insetTop, padding + insetRight, padding + insetBottom);
    }
    private void applyRootPadding(int left, int top, int right, int bottom) {
        if (root.getPaddingLeft() != left || root.getPaddingTop() != top
                || root.getPaddingRight() != right || root.getPaddingBottom() != bottom)
            root.setPadding(left, top, right, bottom);
    }
    private static PhoneStreamLayout.RowParams rowParams(LinearLayout.LayoutParams p) {
        return new PhoneStreamLayout.RowParams(p.width, p.height, p.weight, p.gravity,
                p.leftMargin, p.topMargin, p.rightMargin, p.bottomMargin);
    }
    private void applyRowParams(android.view.View row, LinearLayout.LayoutParams desired) {
        if (!(row.getLayoutParams() instanceof LinearLayout.LayoutParams current)
                || !rowParams(current).equals(rowParams(desired))) row.setLayoutParams(desired);
    }
    private void placePortraitRow(android.view.View row, PhoneStreamLayout.Rect frame, double previousBottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp((float) frame.width()),
                dp((float) frame.bottom()) - dp((float) frame.y()));
        params.leftMargin = dp((float) frame.x());
        params.topMargin = dp((float) frame.y()) - dp((float) previousBottom);
        applyRowParams(row, params);
    }
    private void portraitButtons(LinearLayout row, int height) {
        boolean first = true;
        for (int i = 0; i < row.getChildCount(); i++) {
            android.view.View child = row.getChildAt(i);
            if (child.getVisibility() == android.view.View.GONE) continue;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(height), 1);
            params.leftMargin = first ? 0 : dp(8);
            applyRowParams(child, params); first = false;
        }
    }
    private Button barButton(LinearLayout row, String label, android.view.View.OnClickListener action) {
        Button b = button(row, label, action); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1);
        p.setMargins(dp(2), dp(3), dp(2), dp(3)); b.setLayoutParams(p); b.setPadding(0, 0, 0, 0);
        return b;
    }
    private void configureToolbarButton(Button button, String symbol) {
        button.setTag(symbol); button.setTextSize(11); button.setGravity(android.view.Gravity.CENTER);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        button.setIncludeFontPadding(false); button.setSingleLine(true); button.setCompoundDrawablePadding(dp(3));
        styleToolbarButton(button, false);
    }
    private void styleToolbarButton(Button button, boolean open) {
        if (button == null) return;
        button.setSelected(open); button.setTextColor(open ? 0xff9bb7ff : 0xffc4c9d1);
        button.setBackground(shape(open ? 0xff2c3550 : 0xff23262c, root != null && root.getWidth() > root.getHeight() ? 16 : 14));
        ToolbarSymbol icon = new ToolbarSymbol((String) button.getTag(), open ? 0xff9bb7ff : 0xffeef0f3);
        icon.setBounds(0, 0, dp(22), dp(24)); button.setCompoundDrawables(null, icon, null, null);
    }
    private void refreshToolbarAppearance() {
        if (tourAppsButton == null || root == null) return;
        boolean landscape = root.getWidth() > root.getHeight();
        if (toolbarLandscape == null || toolbarLandscape != landscape) {
            toolbarLandscape = landscape;
            for (Button button : new Button[]{tourAppsButton, menuButton, tourKeyboardButton, desktopButton, tourSettingsButton})
                if (button != null) styleToolbarButton(button, button.isSelected());
            if (scaleControl != null) scaleControl.setBackground(shape(0xff23262c, landscape ? 16 : 14));
        }
        android.view.WindowInsets insets = root.getRootWindowInsets();
        boolean ime = android.os.Build.VERSION.SDK_INT >= 30 && insets != null
                ? insets.isVisible(android.view.WindowInsets.Type.ime()) : keyboard != null && keyboard.hasFocus();
        updateToolbarButton(tourAppsButton, appDrawer != null && appDrawer.isShowing());
        updateToolbarButton(menuButton, menuDialog != null && menuDialog.isShowing());
        updateToolbarButton(tourKeyboardButton, ime && keyboard != null && keyboard.hasFocus());
        updateToolbarButton(desktopButton, desktopActive);
        updateToolbarButton(tourSettingsButton, settingsOpen);
    }
    private void updateToolbarButton(Button button, boolean open) {
        if (button != null && button.isSelected() != open) styleToolbarButton(button, open);
    }
    private static final class ToolbarSymbol extends android.graphics.drawable.Drawable {
        private final String symbol;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ToolbarSymbol(String symbol, int color) {
            this.symbol = symbol; paint.setColor(color); paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.7f); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds(); int save = canvas.save();
            canvas.translate(b.left, b.top); canvas.scale(b.width() / 24f, b.height() / 24f);
            switch (symbol) {
                case "apps" -> { canvas.drawCircle(10, 10, 6, paint); canvas.drawLine(14.5f, 14.5f, 20, 20, paint); }
                case "desktop" -> { canvas.drawRoundRect(3, 4, 21, 16, 1.5f, 1.5f, paint); canvas.drawLine(12, 16, 12, 20, paint); canvas.drawLine(8, 20, 16, 20, paint); }
                case "keyboard" -> {
                    canvas.drawRoundRect(2, 6, 22, 18, 2, 2, paint);
                    for (int x = 6; x <= 18; x += 4) { canvas.drawPoint(x, 10, paint); canvas.drawPoint(x, 13, paint); }
                    canvas.drawLine(8, 16, 16, 16, paint);
                }
                case "menus" -> { for (int y = 7; y <= 17; y += 5) { canvas.drawPoint(4, y, paint); canvas.drawLine(8, y, 20, y, paint); } }
                case "settings" -> {
                    canvas.drawCircle(12, 12, 6.5f, paint); canvas.drawCircle(12, 12, 2.5f, paint);
                    for (int i = 0; i < 8; i++) { double angle = i * Math.PI / 4; float x = (float) Math.cos(angle), y = (float) Math.sin(angle); canvas.drawLine(12 + 7*x, 12 + 7*y, 12 + 9.5f*x, 12 + 9.5f*y, paint); }
                }
            }
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
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
        boolean fits = width >= dp(landscape ? 6 * 64 + 5 * 12 + 92 + 28 : 320);
        int visibility = fits && macMenus.length() > 0 ? android.view.View.VISIBLE : android.view.View.GONE;
        boolean changed = visibility != menuButton.getVisibility();
        menuButton.setVisibility(visibility);
        if (changed && updateSessionLayout != null) root.post(updateSessionLayout);
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
        if (pip != null && pip.active()) return super.dispatchKeyEvent(event);
        if (tourInputBlocked && tourOverlay != null) {
            if (tourOverlay.handleBack(event.getKeyCode(), event)) return true;
            return super.dispatchKeyEvent(event);
        }
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
        if (!hasFocus && pictureInput != null) pictureInput.cancel();
        if (!hasFocus && trackpad != null) trackpad.cancelGestures();
        if (!hasFocus) { dismissWindowControls(); cancelWindowDrag(); }
        else if (picture != null) picture.post(this::considerTourPicture);
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
        if (!appDrawer.isShowing()) {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyboard.getWindowToken(), 0);
            keyboard.clearFocus();
        }
        HostLockState drawerLock = hostLock;
        appDrawer.setLock(drawerLock == null ? HostLockState.Reason.NONE : drawerLock.reason(),
                drawerLock != null && drawerLock.viewOnly(), windowOrderMac);
        if (drawerLock == null || !drawerLock.withPresentedList(() -> {
            appDrawer.acceptLockList();
            appDrawer.show(drawerWindows, appList, appIcons, activeWindowId, desktopActive, picture,
                    sessionLayer, sessionPanel, sessionBar, root.getWidth() > root.getHeight(), largerPictureApplied);
        })) {
            appDrawer.awaitLockList();
            appDrawer.show(new JSONArray(), new JSONArray(), java.util.Collections.emptyMap(), -1, false, picture,
                    sessionLayer, sessionPanel, sessionBar, root.getWidth() > root.getHeight(), largerPictureApplied);
        }
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
        long generation = tourInputGeneration;
        input.execute(() -> {
            try {
                synchronized (outputLock) {
                    if (connection == owner && !tourInputBlocked && generation == tourInputGeneration && allowedForLock(kind, message)) owner.send(kind, message.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) { showFor(owner, "菜单请求失败"); }
        });
    }
    private void scheduleViewport() {
        ui.removeCallbacks(viewportUpdate); ui.postDelayed(viewportUpdate, 250);
    }
    private void sendViewport() {
        if (pip != null && pip.active()) return;
        if (pipReturnWidthDp > 0) {
            if (!keyframeForeground || root.getWidth() < dp(pipReturnWidthDp) - dp(2)
                    || root.getHeight() < dp(pipReturnHeightDp) - dp(48)) return;
            pipReturnWidthDp = pipReturnHeightDp = 0;
        }
        if (connection == null || picture.getWidth() <= 0 || picture.getHeight() <= 0) return;
        try {
            float density = getResources().getDisplayMetrics().density;
            int fps = wantedViewportFps();
            JSONObject v = new JSONObject().put("width", picture.getWidth() / density).put("height", picture.getHeight() / density).put("fps", fps);
            if (textScale != null) v.put("scale", textScale);
            android.util.Log.i("SillAndroid", "Viewport: queued " + Math.round(picture.getWidth() / density) + "x" + Math.round(picture.getHeight() / density) + " scale=" + textScale);
            sendAsync(9, v);
            lastViewportFps = fps;
            updateAudioTiming();
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
            updateAudioTiming();
            if (fpsObserversStarted && connection != null && lastViewportFps != wantedViewportFps()) scheduleViewport();
        });
    }
    private void updateAudioTiming() {
        AudioPlayer player = audio;
        if (player == null) return;
        android.view.Display display = root == null ? null : root.getDisplay();
        float hz = display == null ? 60 : display.getRefreshRate();
        if (!Float.isFinite(hz) || hz <= 0) hz = 60;
        JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
        int fps = Math.min(lastViewportFps > 0 ? lastViewportFps : 60, settings == null ? 60 : settings.optInt("maxFPS", 60));
        JSONObject stream = hostSettings == null ? null : hostSettings.optJSONObject("stream");
        double reportedFPS = stream == null ? Double.NaN : stream.optDouble("fps", Double.NaN);
        player.timing(1.0 / hz, AudioRuntime.streamFPS(reportedFPS, fps));
        android.net.NetworkCapabilities caps = null;
        try {
            android.net.ConnectivityManager networks = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (networks != null) caps = networks.getNetworkCapabilities(networks.getActiveNetwork());
        } catch (RuntimeException ignored) { }
        boolean tunnel = caps == null || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
        java.net.InetAddress address = null;
        try { if (audioEndpointHost != null) address = android.net.InetAddresses.parseNumericAddress(audioEndpointHost); }
        catch (IllegalArgumentException ignored) { }
        player.setRemote(ViewportFps.away(address, tunnel));
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
        if (pip != null && pip.active()) {
            if (picture.getWidth() <= 0 || picture.getHeight() <= 0) return;
            FrameGeometry.Rect fit = FrameGeometry.fit(picture.getWidth(), picture.getHeight(), videoWidth, videoHeight);
            android.widget.FrameLayout.LayoutParams pp = (android.widget.FrameLayout.LayoutParams) video.getLayoutParams();
            int width = Math.max(1, Math.round(fit.width())), height = Math.max(1, Math.round(fit.height()));
            if (pp.width != width || pp.height != height || pp.gravity != android.view.Gravity.CENTER) {
                pp.width = width; pp.height = height; pp.gravity = android.view.Gravity.CENTER; video.setLayoutParams(pp);
            }
            video.setScaleX(1); video.setScaleY(1); video.setTranslationX(0); video.setTranslationY(0);
            return;
        }
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
                (root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()) / density, Math.max(0, portraitImeHeight - root.getPaddingBottom() / density), false);
        java.util.ArrayList<String> labels = new java.util.ArrayList<>(java.util.Arrays.asList("Mac 画质和帧率", clipboardEnabled ? "关闭剪贴板共享" : "开启剪贴板共享", (pasteRightAway ? "将手机剪贴板粘贴到 Mac" : "发送手机剪贴板到 Mac"), audioEnabled ? "关闭声音（重新连接后生效）" : "开启声音测试（重新连接后生效）", reverse ? "滚动方向：反向" : "滚动方向：自然", gestures ? "关闭多指 Mac 手势" : "开启多指 Mac 手势"));
        if (offerLarge) labels.add(android.os.Build.VERSION.SDK_INT < 30 ? "大画面：此 Android 版本暂不支持" : portraitImeHeight <= 0 ? "大画面：请先打开键盘以测量高度" : largerPicture ? "关闭大画面" : "开启大画面");
        int pipIndex = labels.size();
        labels.add(pip == null || !pip.supported() ? "画中画：此设备不支持" : pipEnabled ? "关闭画中画" : "开启画中画");
        int holdIndex = labels.size();
        int holdTenths = Math.max(2, Math.min(20, getPreferences(0).getInt("thumbnailHoldTenths", 5)));
        labels.add("窗口控制长按时间：" + (holdTenths / 10f) + " 秒");
        int muteIndex = labels.size();
        labels.add(localAudio.muted() ? "取消本机静音" : "本机静音");
        boolean[] muteShown = {localAudio.muted()};
        final int pasteModeIndex;
        if (clipboardEnabled && hostClipboardAvailable) {
            pasteModeIndex = labels.size();
            labels.add(pasteRightAway ? "关闭确认后立即粘贴" : "开启确认后立即粘贴");
        } else pasteModeIndex = -1;
        int tourIndex = labels.size(); labels.add("使用引导");
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
            if (i == 1) { clipboardEnabled = !clipboardEnabled; getPreferences(0).edit().putBoolean("shareClipboard", clipboardEnabled).apply(); updateClipboard(); if (settingsOverlay != null) { settingsReanchorPending = true; reanchorSettings(); } }
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
            if (i == pipIndex && pip != null && pip.supported()) { pipEnabled = !pipEnabled; getPreferences(0).edit().putBoolean("pictureInPicture", pipEnabled).apply(); }
            if (i == muteIndex) localAudio.setMuted(!muteShown[0]);
            if (i == pasteModeIndex) { pasteRightAway = !pasteRightAway; getPreferences(0).edit().putBoolean("pasteRightAway", pasteRightAway).apply(); }
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
            if (i == tourIndex) {
                SillConnection expected = connection;
                boolean restore = settingsRestoreKeyboard;
                dismissSettings(false);
                if (connection == expected && expected != null) startTour(TourPolicy.replay(currentTourLayout(), tourAccessibility(), null), restore);
            }
            if (i == items.length - 1) disconnect();
        };
        pictureInput.cancel(); trackpad.cancelGestures();
        boolean landscape = root.getWidth() > root.getHeight();
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(this);
        settingsOverlay = overlay;
        if (landscape) overlay.setOnClickListener(v -> dismissSettings());
        else {
            AppDrawerModel.PortraitPlacement placement = AppDrawerModel.portraitPlacement(
                    sessionLayer.getHeight(), sessionBar.getTop(), sessionBar.getBottom(), dp(8), false);
            android.view.View above = new android.view.View(this), below = new android.view.View(this);
            above.setOnClickListener(v -> dismissSettings()); below.setOnClickListener(v -> dismissSettings());
            overlay.addView(above, new android.widget.FrameLayout.LayoutParams(-1, placement.topDim(), android.view.Gravity.TOP));
            android.widget.FrameLayout.LayoutParams bottom = new android.widget.FrameLayout.LayoutParams(-1, placement.bottomDim(), android.view.Gravity.TOP);
            bottom.topMargin = placement.bottomStart(); overlay.addView(below, bottom);
        }
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable panelShape = shape(0xff16181c, 20);
        panelShape.setStroke(dp(1), 0x1affffff); panel.setBackground(panelShape); panel.setElevation(dp(24));
        panel.setOnClickListener(v -> { });
        LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(4), dp(12)); panel.addView(header);
        TextView title = new TextView(this); title.setText(windowOrderMac.isEmpty() ? "Mac" : windowOrderMac);
        title.setTextSize(17); title.setTextColor(0xffeef0f3); title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setAccessibilityHeading(true); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = button(header, "完成", v -> dismissSettings());
        done.setAllCaps(false); done.setTextColor(0xff9bb7ff); done.setTextSize(15);
        done.setPadding(dp(4), 0, dp(4), 0); done.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        done.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(44)));
        ScrollView scroller = new ScrollView(this); settingsScroll = scroller;
        scroller.setVerticalScrollBarEnabled(false); scroller.setFillViewport(false);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); scroller.addView(rows);
        panel.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        addSettingsHeading(rows, "Mac");
        LinearLayout hostRows = new LinearLayout(this); hostRows.setOrientation(LinearLayout.VERTICAL); rows.addView(hostRows);
        String[] hostSnapshot = {null};
        refreshInlineHostSettings = () -> {
            if (settingsOverlay != overlay) return;
            JSONObject state = hostSettings, values = state == null ? null : state.optJSONObject("settings");
            String snapshot = values == null ? "none" : values.opt("bitrate") + "|" + values.opt("captureScale") + "|"
                    + values.opt("maxFPS") + "|" + values.opt("prioritizeSpeed") + "|" + values.opt("virtualDisplay")
                    + "|" + values.opt("sendAudio") + "|" + state.opt("virtualDisplayAvailable") + "|" + state.opt("virtualDisplayNote");
            snapshot += "|" + pendingSettingsToken + "|" + pendingSettingsField;
            if (snapshot.equals(hostSnapshot[0])) return;
            if (qualityMenu != null) { qualityMenu.dismiss(); qualityMenu = null; }
            hostSnapshot[0] = snapshot; hostRows.removeAllViews();
            if (values == null) { addSettingsNote(hostRows, "正在等待 Mac 提供设置。"); return; }
            LinearLayout streamGroup = new LinearLayout(this); streamGroup.setOrientation(LinearLayout.VERTICAL);
            streamGroup.setBackground(shape(0xff23262c, 12));
            LinearLayout.LayoutParams group = new LinearLayout.LayoutParams(-1, -2); group.bottomMargin = dp(6);
            hostRows.addView(streamGroup, group);
            addHostChoice(streamGroup, "画质", "bitrate", values.opt("bitrate"));
            addHostChoice(streamGroup, "分辨率", "captureScale", values.opt("captureScale"));
            addHostChoice(streamGroup, "帧率", "maxFPS", values.opt("maxFPS"));
            if (streamGroup.getChildCount() == 0) hostRows.removeView(streamGroup);
            if (values.opt("prioritizeSpeed") instanceof Boolean value)
                addHostToggle(hostRows, "优先编码速度", "prioritizeSpeed", value, true, null);
            if (values.opt("virtualDisplay") instanceof Boolean value) {
                Boolean available = state.opt("virtualDisplayAvailable") instanceof Boolean b ? b : null;
                boolean enabled = HostSettingsOptions.canChangeVirtualDisplay(value, available);
                String note = state.opt("virtualDisplayNote") instanceof String text ? text.trim() : "";
                if (note.isEmpty() && !enabled) note = "此 Mac 暂不支持虚拟显示器";
                addHostToggle(hostRows, "虚拟显示器", "virtualDisplay", value, enabled, note);
            }
            Boolean sound = values.opt("sendAudio") instanceof Boolean b ? b : null;
            addHostToggle(hostRows, "Mac 发送声音（测试版）", "sendAudio", Boolean.TRUE.equals(sound), sound != null,
                    sound == null ? "更新 Mac 上的 Sill 以使用声音。" : null);
        };
        refreshInlineHostSettings.run();
        addSettingsHeading(rows, "此设备");
        java.util.ArrayList<Integer> order = new java.util.ArrayList<>();
        order.add(pipIndex); if (offerLarge) order.add(6);
        order.add(1); if (pasteModeIndex >= 0) order.add(pasteModeIndex); order.add(2);
        order.add(4); order.add(holdIndex); order.add(5); order.add(3); order.add(muteIndex); order.add(tourIndex);
        java.util.function.IntFunction<Boolean> checked = index -> {
            if (index == 1) return clipboardEnabled;
            if (index == 3) return audioEnabled;
            if (index == 4) return getPreferences(0).getBoolean("reverseScrolling", false);
            if (index == 5) return getPreferences(0).getBoolean("macGestures", true);
            if (offerLarge && index == 6) return largerPicture;
            if (index == pipIndex) return pipEnabled;
            if (index == muteIndex) return localAudio.muted();
            if (index == pasteModeIndex) return pasteRightAway;
            return null;
        };
        LinearLayout clipboardGroup = null;
        for (int index : order) {
            String label = index == pipIndex ? (pip == null || !pip.supported() ? items[index] : "画中画")
                    : offerLarge && index == 6 ? (android.os.Build.VERSION.SDK_INT < 30 || portraitImeHeight <= 0 ? items[index] : "大画面")
                    : index == 1 ? "共享剪贴板" : index == pasteModeIndex ? "确认后立即粘贴"
                    : index == 4 ? "反向滚动" : index == 5 ? "多指 Mac 手势"
                    : index == 3 ? "声音测试（重新连接后生效）" : index == muteIndex ? "本机静音"
                    : items[index];
            Boolean value = checked.apply(index);
            boolean clipboardRow = index == 1 || index == 2 || index == pasteModeIndex;
            if (clipboardRow && clipboardGroup == null) {
                clipboardGroup = new LinearLayout(this); clipboardGroup.setOrientation(LinearLayout.VERTICAL);
                clipboardGroup.setBackground(shape(0xff23262c, 12));
                LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2); groupParams.bottomMargin = dp(6);
                rows.addView(clipboardGroup, groupParams);
            }
            LinearLayout target = clipboardRow ? clipboardGroup : rows;
            if (clipboardRow && target.getChildCount() > 0) {
                android.view.View divider = new android.view.View(this); divider.setBackgroundColor(0xff3a3d44);
                LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(1)); line.leftMargin = dp(14); target.addView(divider, line);
            }
            LinearLayout row = addSettingsRow(target, label, value, clipboardRow, () -> {
                choose.accept(index);
                if (settingsOverlay == overlay && settingsChild == null && settingsOpen && checked.apply(index) != null) {
                    settingsReanchorPending = true; reanchorSettings();
                }
            });
            if (index == holdIndex) row.getChildAt(0).setTag("thumbnailHold");
            if (index == 2) row.getChildAt(0).setTag("clipboardAction");
            if (index == muteIndex) refreshSettingsMute = () -> {
                if (settingsOverlay == overlay && localAudio.muted() != muteShown[0]) {
                    muteShown[0] = localAudio.muted(); settingsReanchorPending = true; reanchorSettings();
                }
            };
            if ((index == pipIndex && (pip == null || !pip.supported()))
                    || (offerLarge && index == 6 && (android.os.Build.VERSION.SDK_INT < 30 || portraitImeHeight <= 0))) {
                row.setAlpha(.5f);
            }
        }
        Button disconnectButton = button(panel, "⊗  断开连接", v -> choose.accept(items.length - 1));
        disconnectButton.setTextColor(0xff9bb7ff); disconnectButton.setTextSize(15);
        disconnectButton.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        disconnectButton.setPadding(dp(14), 0, dp(14), 0); disconnectButton.setBackground(shape(0xff23262c, 12));
        LinearLayout.LayoutParams foot = new LinearLayout.LayoutParams(-1, dp(44)); foot.topMargin = dp(10); disconnectButton.setLayoutParams(foot);
        android.widget.FrameLayout.LayoutParams panelParams;
        if (landscape) {
            panelParams = new android.widget.FrameLayout.LayoutParams(
                    Math.min(dp(340), Math.max(0, picture.getWidth() - dp(28))), -1,
                    android.view.Gravity.TOP | android.view.Gravity.END);
            panelParams.setMargins(dp(14), dp(8), dp(14), dp(14));
        } else {
            AppDrawerModel.PortraitPlacement placement = AppDrawerModel.portraitPlacement(
                    sessionLayer.getHeight(), sessionBar.getTop(), sessionBar.getBottom(), dp(8), false);
            panelParams = new android.widget.FrameLayout.LayoutParams(Math.max(0, sessionLayer.getWidth() - dp(28)), placement.panelHeight(),
                    android.view.Gravity.TOP | android.view.Gravity.END);
            panelParams.rightMargin = dp(14); panelParams.topMargin = placement.panelTop();
        }
        overlay.addView(panel, panelParams);
        (landscape ? picture : sessionLayer).addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        int scrollY = settingsScrollY; scroller.post(() -> { if (settingsScroll == scroller) scroller.scrollTo(0, scrollY); });
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            settingsBack = this::dismissSettings;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, settingsBack);
        }
    }
    private void addSettingsHeading(LinearLayout rows, String text) {
        TextView heading = new TextView(this); heading.setText(text); heading.setTextSize(13); heading.setTextColor(0xffa4abb5);
        heading.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        heading.setPadding(dp(14), dp(6), dp(14), dp(6)); heading.setAccessibilityHeading(true); rows.addView(heading);
    }
    private void addSettingsNote(LinearLayout rows, String text) {
        TextView note = new TextView(this); note.setText(text); note.setTextSize(12); note.setTextColor(0xffa4abb5);
        note.setPadding(dp(14), dp(2), dp(14), dp(8)); rows.addView(note);
    }
    private void addHostChoice(LinearLayout group, String title, String field, Object value) {
        if (!(value instanceof Number number)) return;
        if (group.getChildCount() > 0) {
            android.view.View line = new android.view.View(this); line.setBackgroundColor(0xff3a3d44);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1)); lp.leftMargin = dp(14); group.addView(line, lp);
        }
        if (!field.equals("bitrate")) {
            addHostSegments(group, title, field, number);
            return;
        }
        String display = qualityTitle(number);
        if (pendingSettingsToken != 0 && field.equals(pendingSettingsField)) display = "等待 Mac…";
        LinearLayout row = addSettingsRow(group, title, null, true, () -> {});
        TextView detail = new TextView(this); detail.setText(display + "  ▾"); detail.setTextSize(14); detail.setTextColor(0xff9bb7ff);
        detail.setSingleLine(true); detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(detail); row.setContentDescription(title + "，" + display);
        TextView label = (TextView) row.getChildAt(0);
        row.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, oright, ob) -> {
            float required = label.getPaint().measureText(label.getText().toString())
                    + detail.getPaint().measureText(detail.getText().toString()) + dp(12);
            boolean stack = required > r - l - row.getPaddingLeft() - row.getPaddingRight();
            int direction = stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
            if (row.getOrientation() == direction) return;
            row.setOrientation(direction);
            row.setGravity(stack ? android.view.Gravity.START : android.view.Gravity.CENTER_VERTICAL);
            label.setLayoutParams(new LinearLayout.LayoutParams(stack ? -1 : 0, -2, stack ? 0 : 1));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(stack ? -1 : -2, -2);
            if (stack) p.topMargin = dp(4);
            detail.setLayoutParams(p);
        });
        row.setOnClickListener(v -> showQualityMenu(row));
        if (pendingSettingsToken != 0) { row.setEnabled(false); row.setAlpha(.5f); }
    }
    private String qualityTitle(Number value) {
        double rate = value.doubleValue(), mbps = rate / 1000000d;
        String quality = "自定义";
        for (int i = 0; i < QUALITY_RATES.length; i++) if (rate == QUALITY_RATES[i]) quality = QUALITY_NAMES[i];
        return quality + " — " + (mbps == Math.rint(mbps) ? String.valueOf((long) mbps)
                : String.format(java.util.Locale.ROOT, "%.1f", mbps)) + " Mbps";
    }
    private void addHostSegments(LinearLayout group, String title, String field, Number current) {
        boolean fps = field.equals("maxFPS");
        String[] labels = fps ? new String[]{"60 fps", "120 fps"} : new String[]{"Retina", "标准"};
        double[] values = fps ? new double[]{60, 120} : new double[]{2, 1};
        LinearLayout row = new LinearLayout(this);
        boolean stacked = getResources().getConfiguration().fontScale > 1.2f
                || root.getWidth() - root.getPaddingLeft() - root.getPaddingRight() < dp(320);
        row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(stacked ? android.view.Gravity.START : android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(6), dp(14), dp(6)); row.setMinimumHeight(dp(44));
        TextView label = new TextView(this); label.setText(title); label.setTextColor(0xffeef0f3); label.setTextSize(15);
        if (pendingSettingsToken != 0 && field.equals(pendingSettingsField)) label.setText(title + " · 等待 Mac…");
        row.addView(label, new LinearLayout.LayoutParams(stacked ? -1 : 0, -2, stacked ? 0 : 1));
        LinearLayout segments = new LinearLayout(this); segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setPadding(dp(2), dp(2), dp(2), dp(2)); segments.setBackground(shape(0xff34373d, 8));
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-2, -2);
        if (stacked) segmentParams.topMargin = dp(6);
        row.addView(segments, segmentParams);
        for (int i = 0; i < labels.length; i++) {
            final int selected = i;
            boolean active = Double.compare(current.doubleValue(), values[i]) == 0;
            Button segment = new Button(this); segment.setAllCaps(false); segment.setText(labels[i]); segment.setTextSize(13);
            segment.setTextColor(0xffeef0f3); segment.setIncludeFontPadding(false);
            segment.setPadding(dp(8), dp(4), dp(8), dp(4)); segment.setMinWidth(0); segment.setMinimumWidth(dp(62));
            segment.setMinHeight(dp(30)); segment.setMinimumHeight(dp(30));
            segment.setBackground(shape(active ? 0xff64676e : 0x00000000, 6));
            segment.setSelected(active);
            segment.setContentDescription(title + "，" + labels[i] + (active ? "，已选中" : ""));
            segment.setEnabled(pendingSettingsToken == 0);
            segment.setOnClickListener(v -> {
                JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
                if (settings == null || !(settings.opt(field) instanceof Number latest)) { show("Mac 已不提供此设置"); return; }
                if (Double.compare(latest.doubleValue(), values[selected]) != 0)
                    changeHostSetting(field, fps ? (Object) (int) values[selected] : values[selected]);
            });
            segments.addView(segment, new LinearLayout.LayoutParams(-2, -2));
        }
        if (pendingSettingsToken != 0) row.setAlpha(.5f);
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    private void addHostToggle(LinearLayout rows, String title, String field, boolean value, boolean available, String note) {
        String label = pendingSettingsToken != 0 && field.equals(pendingSettingsField) ? title + " · 等待 Mac…" : title;
        LinearLayout row = addSettingsRow(rows, label, value, false, () -> {
            JSONObject state = hostSettings, latest = state == null ? null : state.optJSONObject("settings");
            if (latest == null || !(latest.opt(field) instanceof Boolean current)) { show("Mac 已不提供此设置"); return; }
            Boolean virtualAvailable = state.opt("virtualDisplayAvailable") instanceof Boolean b ? b : null;
            if (field.equals("virtualDisplay") && !HostSettingsOptions.canChangeVirtualDisplay(current, virtualAvailable)) {
                String reason = state.opt("virtualDisplayNote") instanceof String text ? text.trim() : "";
                show(reason.isEmpty() ? "此 Mac 暂不支持虚拟显示器" : reason); return;
            }
            changeHostSetting(field, !current);
        });
        if (!available || pendingSettingsToken != 0) {
            row.setEnabled(false); row.setAlpha(.5f);
            for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setEnabled(false);
        }
        if (note != null && !note.isEmpty()) addSettingsNote(rows, note);
    }
    private LinearLayout addSettingsRow(LinearLayout rows, String label, Boolean checked, boolean grouped, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(6), dp(14), dp(6)); row.setMinimumHeight(dp(44));
        if (!grouped) row.setBackground(shape(0xff23262c, 12));
        TextView text = new TextView(this); text.setText(label); text.setTextColor(0xffeef0f3); text.setTextSize(15);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (checked != null) {
            android.widget.Switch toggle = new android.widget.Switch(this); toggle.setChecked(checked); toggle.setContentDescription(label);
            toggle.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{0xff9bb7ff, 0xffc4c9d1}));
            toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{0xff536b9f, 0xff50545c}));
            toggle.setOnCheckedChangeListener((button, value) -> action.run());
            row.addView(toggle); row.setOnClickListener(v -> toggle.toggle());
            row.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            text.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        } else row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); if (!grouped) params.bottomMargin = dp(6); rows.addView(row, params);
        return row;
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
        if (qualityMenu != null) { qualityMenu.dismiss(); qualityMenu = null; }
        if (!restoreKeyboard) settingsRestoreKeyboard = false;
        if (android.os.Build.VERSION.SDK_INT >= 33 && settingsBack != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(settingsBack); settingsBack = null;
        }
        android.widget.FrameLayout overlay = settingsOverlay; settingsOverlay = null;
        settingsScroll = null; refreshInlineHostSettings = null;
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
            if (qualityMenu != null) { qualityMenu.dismiss(); qualityMenu = null; }
            if (settingsScroll != null) settingsScrollY = settingsScroll.getScrollY();
            settingsScroll = null; refreshInlineHostSettings = null;
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
        refreshSettingsMute = null;
        settingsScroll = null; settingsScrollY = 0; refreshInlineHostSettings = null;
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
    private void showQualityMenu(android.view.View anchor) {
        if (pendingSettingsToken != 0) { show("正在等待 Mac 确认设置"); return; }
        JSONObject state = hostSettings, values = state == null ? null : state.optJSONObject("settings");
        if (values == null || !(values.opt("bitrate") instanceof Number current)) { show("Mac 已不提供此设置"); return; }
        SillConnection owner = connection;
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        qualityMenu = popup;
        boolean preset = false;
        for (int i = 0; i < QUALITY_RATES.length; i++) {
            android.view.MenuItem item = popup.getMenu().add(0, i, i, qualityTitle(QUALITY_RATES[i]));
            boolean checked = current.doubleValue() == QUALITY_RATES[i]; preset |= checked;
            item.setCheckable(true).setChecked(checked);
        }
        if (!preset) popup.getMenu().add(0, QUALITY_RATES.length, QUALITY_RATES.length, qualityTitle(current))
                .setCheckable(true).setChecked(true).setEnabled(false);
        popup.setOnMenuItemClickListener(item -> {
            if (connection != owner || !settingsOpen || pendingSettingsToken != 0) return true;
            JSONObject latest = hostSettings == null ? null : hostSettings.optJSONObject("settings");
            if (latest == null || !(latest.opt("bitrate") instanceof Number rate)) { show("Mac 已不提供此设置"); return true; }
            if (item.getItemId() < 0 || item.getItemId() >= QUALITY_RATES.length) return true;
            int next = QUALITY_RATES[item.getItemId()];
            if (rate.longValue() != next) changeHostSetting("bitrate", next);
            return true;
        });
        popup.setOnDismissListener(menu -> { if (qualityMenu == popup) qualityMenu = null; });
        popup.show();
    }
    private void changeHostSetting(String field, Object value) {
        JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
        if (settings == null || !settings.has(field)) return;
        if (pendingSettingsToken != 0) { show("正在等待 Mac 确认设置"); return; }
        try {
            pendingSettingsToken = ++settingsToken; pendingSettingsField = field; pendingSettingsValue = value;
            sendAsync(17, new JSONObject().put("token", pendingSettingsToken).put(field, value));
            if (refreshInlineHostSettings != null) refreshInlineHostSettings.run();
            show("正在更新 Mac 设置…");
            int waiting = pendingSettingsToken;
            ui.postDelayed(() -> { if (pendingSettingsToken == waiting) { pendingSettingsToken = 0; if (refreshInlineHostSettings != null) refreshInlineHostSettings.run(); show("Mac 未确认设置，请重试"); } }, 10000);
        } catch (Exception e) { pendingSettingsToken = 0; if (refreshInlineHostSettings != null) refreshInlineHostSettings.run(); show("设置发送失败"); }
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
        if (!keyframeForeground || (pip != null && pip.active())) { show("请返回 Sill 后发送剪贴板"); return; }
        String source = clipboardSource;
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = clipboard.getPrimaryClip();
            CharSequence text = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(this);
            String value = text == null ? null : text.toString();
            ClipboardPastePolicy.Text preflight = ClipboardPastePolicy.text(value, 0);
            if (preflight == ClipboardPastePolicy.Text.EMPTY) { show("剪贴板没有文本"); return; }
            if (preflight == ClipboardPastePolicy.Text.TOO_LONG) { show("剪贴板文字过长"); return; }
            keyboard.finishAndForget();
            byte[] payload = new JSONObject().put("text", value).toString().getBytes(StandardCharsets.UTF_8);
            if (ClipboardPastePolicy.text(value, payload.length) != ClipboardPastePolicy.Text.READY) { show("剪贴板文字过长"); return; }
            byte[] nonce = new byte[16]; new java.security.SecureRandom().nextBytes(nonce);
            long token = clipboardReceipt.begin(nonce, android.os.SystemClock.uptimeMillis()); clipboardToken = token;
            input.execute(() -> {
                try {
                    synchronized (outputLock) {
                        if (connection != expected || tourInputBlocked || !keyframeForeground || !clipboardEnabled || !hostClipboardAvailable ||
                                !source.equals(clipboardSource) || !clipboardReceipt.pending(token)) return;
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
    private boolean clipboardMayPaste(SillConnection expected, String source) {
        HostLockState lock = hostLock;
        return !tourInputBlocked && ClipboardPastePolicy.paste(pasteRightAway, true, connection == expected,
                clipboardEnabled, hostClipboardAvailable, keyframeStreaming && source.equals(clipboardSource),
                keyframeForeground && (pip == null || !pip.active()),
                lock != null && lock.reason() == HostLockState.Reason.NONE);
    }
    // The tour belongs to a logical connection session, never to a particular Mac window.
    private void loadTourMemory() {
        android.content.SharedPreferences preferences = getPreferences(0);
        java.util.Set<String> names = preferences.getStringSet("tour.seen", java.util.Set.of());
        TourPolicy.Memory saved = TourPolicy.Memory.fromNames(new java.util.ArrayList<>(names), preferences.getBoolean("tour.skipped", false));
        Integer version = preferences.contains("tour.version") ? preferences.getInt("tour.version", 0) : null;
        TourPolicy.Upgrade upgrade = TourPolicy.upgraded(saved, version);
        tourMemory = upgrade.memory();
        if (upgrade.write()) saveTourMemory(true);
    }
    private void saveTourMemory(boolean version) {
        android.content.SharedPreferences.Editor edit = getPreferences(0).edit()
                .putStringSet("tour.seen", new java.util.HashSet<>(tourMemory.names()))
                .putBoolean("tour.skipped", tourMemory.skipped());
        if (version) edit.putInt("tour.version", TourPolicy.VERSION);
        edit.apply();
    }
    private static double tourNow() { return android.os.SystemClock.uptimeMillis() / 1000.0; }
    private boolean tourAccessibility() {
        android.view.accessibility.AccessibilityManager manager = (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        return manager != null && manager.isTouchExplorationEnabled();
    }
    private TourPolicy.Layout currentTourLayout() {
        return root.getWidth() > root.getHeight() ? TourPolicy.Layout.LANDSCAPE : TourPolicy.Layout.PHONE;
    }
    private boolean tourImeVisible() {
        android.view.WindowInsets insets = root.getRootWindowInsets();
        return android.os.Build.VERSION.SDK_INT >= 30 && insets != null
                ? insets.isVisible(android.view.WindowInsets.Type.ime()) : keyboard != null && keyboard.hasFocus();
    }
    private boolean tourBusy() {
        return !keyframeForeground || !hasWindowFocus() || (pip != null && pip.active()) || tourImeVisible()
                || (keyboard != null && keyboard.isComposing())
                || settingsOpen || settingsChild != null || (appDrawer != null && appDrawer.isShowing())
                || (menuDialog != null && menuDialog.isShowing()) || pendingMenuToken != 0
                || (windowControls != null && windowControls.isShowing()) || draggedWindow != null;
    }
    private void resetTourSession() {
        endTour(false); ui.removeCallbacks(tourWait);
        tourOwner = null; tourSession = TourPolicy.Session.fresh(); tourPictureAt = null;
        tourLastActivity = null; tourLayout = null;
    }
    private void tourNewSession(SillConnection owner) {
        if (connection != owner) return;
        boolean reconnect = tourOwner != null;
        hideTour(); tourRun = null; tourRestoreKeyboard = false; tourFocusBefore = null;
        tourSession = TourPolicy.nextSession(tourSession, reconnect);
        tourOwner = owner; tourPictureAt = null; tourLastActivity = null;
        tourLayout = null; ui.removeCallbacks(tourWait);
    }
    private void suspendTourForReconnect() { hideTour(); ui.removeCallbacks(tourWait); }
    private void tourLayoutChanged() {
        if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
        TourPolicy.Layout layout = currentTourLayout();
        if (layout != tourLayout) {
            tourLayout = layout; tourLayoutAt = tourNow();
            if (tourRun != null) {
                tourRun = TourPolicy.carry(tourRun, layout, tourAccessibility(), tourMemory);
                if (tourRun == null) { endTour(true); return; }
                java.util.Set<TourPolicy.Layout> offered = new java.util.HashSet<>(tourSession.offered());
                offered.add(layout); tourSession = new TourPolicy.Session(true, offered, true);
            }
        }
        if (tourRun != null && tourOverlay != null) sessionLayer.post(this::updateTourStep);
        else considerTour();
    }
    private void tourAccessibilityChanged() {
        if (tourRun != null && tourLayout != null) {
            tourRun = TourPolicy.carry(tourRun, tourLayout, tourAccessibility(), tourMemory);
            if (tourRun == null) { endTour(true); return; }
            if (tourOverlay != null) updateTourStep();
        } else considerTour();
    }
    private void considerTourPicture() {
        HostLockState lock = hostLock;
        if (connection == null || connection != tourOwner || !picturePresented || !keyframeStreaming
                || lock == null || lock.isCovered() || lock.needsFreshFrame() || !keyframeForeground
                || (pip != null && pip.active())) return;
        if (tourPictureAt == null) tourPictureAt = tourNow();
        if (tourRun != null) { if (!tourBusy()) showTour(); return; }
        considerTour();
    }
    private void considerTour() {
        ui.removeCallbacks(tourWait);
        if (tourRun != null || tourLayout == null || connection == null || connection != tourOwner) return;
        TourPolicy.Moment moment = new TourPolicy.Moment(tourNow(), tourPictureAt, tourLayoutAt, tourSession.decided(),
                tourLastActivity, tourTouches, tourBusy(), tourSession.offered().contains(tourLayout), tourAccessibility(), true);
        TourPolicy.Decision decision = TourPolicy.automatic(moment, tourLayout, tourMemory);
        if (decision.kind() == TourPolicy.Kind.WAIT) {
            if (decision.until() != null) ui.postDelayed(tourWait, Math.max(1, (long) Math.ceil((decision.until() - tourNow()) * 1000)));
            return;
        }
        java.util.Set<TourPolicy.Layout> offered = new java.util.HashSet<>(tourSession.offered());
        offered.add(tourLayout);
        tourSession = new TourPolicy.Session(true, offered, false);
        if (decision.kind() == TourPolicy.Kind.SHOW) startTour(TourPolicy.run(decision.steps()), false);
    }
    private void startTour(TourPolicy.Run run, boolean restoreKeyboard) {
        if (run == null || connection == null || connection != tourOwner) return;
        ui.removeCallbacks(tourWait);
        tourRun = run; tourRestoreKeyboard = restoreKeyboard;
        tourFocusBefore = getCurrentFocus();
        java.util.Set<TourPolicy.Layout> offered = new java.util.HashSet<>(tourSession.offered());
        offered.add(currentTourLayout()); tourSession = new TourPolicy.Session(true, offered, true);
        showTour();
    }
    private void showTour() {
        if (tourRun == null || tourOverlay != null || connection == null || connection != tourOwner
                || !keyframeForeground || (pip != null && pip.active())) return;
        HostLockState lock = hostLock;
        if (lock == null || lock.isCovered() || lock.needsFreshFrame()) return;
        // Release held inputs before blocking new ones. The output lock closes the clipboard receipt race.
        pictureInput.cancel(); trackpad.cancelGestures(); releaseHardwareKeys();
        synchronized (outputLock) { clipboardReceipt.cancel(); tourInputGeneration++; tourInputBlocked = true; }
        tourAccessibilityBefore = sessionPanel.getImportantForAccessibility();
        sessionPanel.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        tourFocusabilityBefore = sessionPanel.getDescendantFocusability();
        sessionPanel.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tourOverlay = new TourOverlay(this, new TourOverlay.Actions() {
            public void next() { tourNext(); }
            public void skip() { tourSkip(); }
        });
        sessionLayer.addView(tourOverlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
        tourOverlay.requestFocus();
        // Retire every old IME connection after focus has left it, so restartInput cannot create
        // another connection and late commits cannot be mistaken for input after this tour.
        keyboard.discard();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            tourBack = this::tourBackPressed;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, tourBack);
        }
        sessionLayer.post(this::updateTourStep);
    }
    private void hideTour() {
        ui.removeCallbacks(tourWait);
        if (tourOverlay == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33 && tourBack != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(tourBack); tourBack = null;
        }
        sessionLayer.removeView(tourOverlay); tourOverlay = null;
        sessionPanel.setImportantForAccessibility(tourAccessibilityBefore);
        sessionPanel.setDescendantFocusability(tourFocusabilityBefore);
        synchronized (outputLock) { tourInputBlocked = false; }
    }
    private void endTour(boolean restoreKeyboard) {
        SillConnection owner = tourOwner;
        boolean restore = restoreKeyboard && tourRestoreKeyboard;
        android.view.View priorFocus = tourFocusBefore; tourFocusBefore = null;
        hideTour(); tourRun = null; tourRestoreKeyboard = false;
        if (!restore && restoreKeyboard && owner != null && connection == owner && keyframeForeground
                && priorFocus != null && priorFocus.isAttachedToWindow()) priorFocus.requestFocus();
        tourSession = new TourPolicy.Session(tourSession.decided(), tourSession.offered(), false);
        if (restore && owner != null && connection == owner) keyboard.post(() -> {
            if (connection != owner || !keyframeForeground || settingsOpen || tourRun != null
                    || (pip != null && pip.active()) || (appDrawer != null && appDrawer.isShowing())) return;
            keyboard.requestFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(keyboard, 0);
        });
    }
    private void tourNext() {
        if (tourRun == null || connection != tourOwner) return;
        tourMemory = TourPolicy.passed(tourRun.at(), tourMemory); saveTourMemory(false);
        tourRun = TourPolicy.next(tourRun);
        if (tourRun == null) { endTour(true); return; }
        updateTourStep();
    }
    private void tourBackPressed() {
        if (tourRun != null && tourRun.isLast()) tourNext(); else tourSkip();
    }
    private void tourSkip() {
        if (tourRun == null || connection != tourOwner) return;
        TourPolicy.Memory memory = TourPolicy.skip(tourRun, tourMemory);
        if (!memory.equals(tourMemory)) { tourMemory = memory; saveTourMemory(false); }
        endTour(true);
    }
    private boolean tourControl(int kind) {
        return kind == 6 || kind == 7 || kind == 8 || kind == 15 || kind == 25 || kind == 28 || kind == 47;
    }
    private boolean allowedForTour(int kind, JSONObject value) {
        return !tourInputBlocked || !tourControl(kind) || isRelease(kind, value);
    }
    private boolean allowedTourGeneration(int kind, JSONObject value, long generation) {
        return generation == tourInputGeneration || !tourControl(kind) || isRelease(kind, value);
    }
    private void updateTourStep() {
        if (tourOverlay == null || tourRun == null || connection != tourOwner) return;
        java.util.List<android.graphics.RectF> targets = new java.util.ArrayList<>();
        for (TourPolicy.Target target : TourPolicy.targets(tourRun.at(), currentTourLayout(), !largerPictureApplied)) {
            android.view.View view = switch (target) {
                case STREAM -> picture; case APPS -> tourAppsButton; case STRIP -> tourStrip;
                case MENUS -> menuButton; case TEXT_SIZE -> scaleControl; case KEYBOARD -> tourKeyboardButton;
                case SETTINGS -> tourSettingsButton; case KEYS -> keyRow; case TRACKPAD -> trackpad;
            };
            if (view == null || !view.isShown() || view.getWidth() <= 0 || view.getHeight() <= 0) continue;
            int[] origin = new int[2], position = new int[2]; tourOverlay.getLocationInWindow(origin); view.getLocationInWindow(position);
            float x = position[0] - origin[0], y = position[1] - origin[1];
            targets.add(new android.graphics.RectF(x, y, x + view.getWidth(), y + view.getHeight()));
        }
        JSONObject settings = hostSettings == null ? null : hostSettings.optJSONObject("settings");
        Boolean virtualAvailable = hostSettings != null && hostSettings.opt("virtualDisplayAvailable") instanceof Boolean b ? b : null;
        boolean virtual = settings != null && settings.opt("virtualDisplay") instanceof Boolean
                && HostSettingsOptions.canChangeVirtualDisplay(settings.optBoolean("virtualDisplay"), virtualAvailable);
        boolean sound = settings != null && settings.opt("sendAudio") instanceof Boolean && AudioPlayer.supported();
        float density = getResources().getDisplayMetrics().density;
        boolean largerOffered = android.os.Build.VERSION.SDK_INT >= 30 && (portraitImeHeight <= 0 || PhoneStreamLayout.offersLargerPicture(
                (root.getWidth() - root.getPaddingLeft() - root.getPaddingRight()) / density,
                (root.getHeight() - root.getPaddingTop() - root.getPaddingBottom()) / density,
                Math.max(0, portraitImeHeight - root.getPaddingBottom() / density), false));
        TourContent.Features features = new TourContent.Features(menuButton.isShown(),
                hostGestures >= 1 && getPreferences(0).getBoolean("macGestures", true), virtual,
                virtual && settings.optBoolean("virtualDisplay"), hostClipboardAvailable, pasteRightAway,
                sound, largerOffered, !largerPictureApplied);
        TourContent.Copy copy = TourContent.forStep(tourRun, currentTourLayout(), tourAccessibility(), windowOrderMac, features);
        int[] origin = new int[2], picturePosition = new int[2];
        tourOverlay.getLocationInWindow(origin); picture.getLocationInWindow(picturePosition);
        float pictureX = picturePosition[0] - origin[0], pictureY = picturePosition[1] - origin[1];
        android.graphics.RectF room = new android.graphics.RectF(pictureX, pictureY,
                pictureX + picture.getWidth(), pictureY + picture.getHeight());
        tourOverlay.showStep(tourRun, copy, targets, room);
    }
    private void disconnect() {
        status.setText("已断开连接。");
        dismissSettings(false);
        if (appDrawer != null) appDrawer.dismiss();
        dismissWindowControls(); cancelWindowDrag();
        pictureInput.cancel();
        trackpad.cancelGestures();
        clipboardReceipt.cancel(); keyboard.finishAndForget(); closing = true;
        RemoteDial.Attempt dial = pendingDial; if (dial != null) dial.close();
        SillConnection c = connection;
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
        pictureStatus.setVisibility(pictureStatusActive && (pip == null || !pip.active())
                ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!text.startsWith("连接中断")) ui.postDelayed(() -> {
            if (pictureStatusGeneration != generation) return;
            pictureStatusActive = false; pictureStatus.setVisibility(android.view.View.GONE);
        }, 5000);
    }
    private void connect() { start(true); }
    private void start(boolean pairing) { start(pairing, null, null, true); }
    private void start(boolean pairing, SavedMacs.Identity selectedIdentity) {
        start(pairing, selectedIdentity, null, false);
    }
    private void start(boolean pairing, SavedMacs.Identity selectedIdentity, String recognitionTag) {
        start(pairing, selectedIdentity, recognitionTag, false);
    }
    private void start(boolean pairing, SavedMacs.Identity selectedIdentity, String recognitionTag, boolean typedByUser) {
        String address = host.getText().toString().trim();
        String password = code.getText().toString();
        int number;
        try { number = Integer.parseInt(port.getText().toString()); if (address.isEmpty() || number < 1 || number > 65535) throw new IllegalArgumentException(); }
        catch (Exception e) { show("请填写有效地址和端口。"); return; }
        if (running) { show("已有连接，请退出后重试。"); return; }
        resetTourSession();
        PairingAuthorization pairingGuard;
        synchronized (savedMacStore) {
            activeAuthorization = null;
            pairingGuard = pairing ? new PairingAuthorization(savedMacStore.pin(new SavedMacs.Endpoint(address, number))) : null;
            pendingPairing = pairingGuard;
        }
        running = true; closing = false; updateConnectionCancel(); getPreferences(0).edit().putString("host", address).putString("port", String.valueOf(number)).apply();
        network.execute(() -> {
            try {
                SavedMacs.Endpoint endpoint = new SavedMacs.Endpoint(address, number);
                byte[] pin;
                SessionAuthorization authorization;
                if (pairing) {
                    show("正在配对…");
                    SavedMacs.Identity identity;
                    RemoteDial.Attempt pairingAttempt = new RemoteDial.Attempt();
                    synchronized (savedMacStore) {
                        pairingGuard.track(pairingAttempt);
                        pendingDial = pairingAttempt;
                    }
                    try {
                        if (!running || closing) pairingAttempt.close();
                        try (SillConnection pair = SillConnection.connect(address, number, "sill-pair/1", null, 10000, 15000, pairingAttempt)) {
                            synchronized (savedMacStore) { pairingGuard.identify(pair.peerFingerprint()); }
                            identity = pair.pairIdentity(password, android.os.Build.MODEL, android.os.Build.DEVICE);
                            pairingAttempt.check();
                        }
                    } finally { pairingAttempt.close(); pendingDial = null; }
                    synchronized (savedMacStore) {
                        pairingGuard.identify(identity.pin());
                        if (closing || !running) throw new java.util.concurrent.CancellationException();
                        savedMacStore.paired(identity, endpoint); pin = identity.pin();
                        pendingPairing = null;
                        authorization = new SessionAuthorization(pin); activeAuthorization = authorization;
                    }
                    ui.post(() -> { code.setText(""); refreshSavedMacs(); });
                } else {
                    synchronized (savedMacStore) {
                    pin = selectedIdentity == null ? savedMacStore.pin(endpoint)
                            : recognitionTag == null ? savedMacStore.pin(selectedIdentity, endpoint)
                            : savedMacStore.recognizedPin(selectedIdentity, recognitionTag);
                    if (pin == null) throw new IllegalStateException("请先配对这台 Mac。");
                        authorization = new SessionAuthorization(pin); activeAuthorization = authorization;
                    }
                }
                boolean established = false;
                SavedMacs.Endpoint verifiedThisRun = null;
                int retries = 0;
                while (running && !closing) {
                    long connectedAt = 0;
                    try {
                        if (!established) show("正在连接…");
                        RemoteDial.Attempt dial = new RemoteDial.Attempt();
                        synchronized (savedMacStore) {
                            authorization.track(dial);
                            pendingDial = dial;
                        }
                        boolean wantsAudio = audioEnabled && AudioPlayer.supported();
                        JSONObject greeting = new JSONObject().put("protocol", 1).put("appVersion", "0.1.0").put("device", android.os.Build.MODEL);
                        if (wantsAudio) greeting.put("audio", new JSONArray().put("aac-eld"));
                        RemoteDial.Connected<ReadyStream> route;
                        try {
                            if (!running || closing) dial.close();
                            route = RemoteDial.connect(RemoteDial.reconnectOrder(savedMacStore.all(), pin, endpoint,
                                    !established && (selectedIdentity == null || recognitionTag != null), verifiedThisRun), pin, dial,
                                    (candidate, identityPin, attempt) -> {
                                        SillConnection candidateSession = SillConnection.connect(candidate.host(), candidate.port(),
                                                "sill/1", identityPin, RemoteDial.CONNECT_MS, RemoteDial.HANDSHAKE_MS, attempt);
                                        try {
                                            candidateSession.send(23, greeting.toString().getBytes(StandardCharsets.UTF_8));
                                            candidateSession.send(27, new JSONObject().toString().getBytes(StandardCharsets.UTF_8));
                                            var initial = FirstWindowList.await(candidateSession::readUntil, message -> {
                                                if (message.kind() == 22) {
                                                    synchronized (attempt) {
                                                        attempt.check();
                                                        if (pendingDial != attempt || !running || closing) throw new java.util.concurrent.CancellationException();
                                                        handle(message, candidateSession, attempt);
                                                    }
                                                    return;
                                                }
                                                String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                                        .decode(ByteBuffer.wrap(message.payload())).toString();
                                                org.json.JSONTokener tokener = new org.json.JSONTokener(json);
                                                Object value = tokener.nextValue();
                                                if (!(value instanceof JSONObject object) || tokener.nextClean() != 0
                                                        || !(object.opt("windows") instanceof JSONArray)) throw new java.io.IOException("Invalid Mac window list");
                                            });
                                            return new ReadyStream(candidateSession, initial);
                                        } catch (Exception error) { RemoteDial.closeAfterFailure(candidateSession, error); throw error; }
                                    });
                        } finally { pendingDial = null; }
                        try (SillConnection session = route.value().session()) {
                            if (!running || closing) break;
                            synchronized (savedMacStore) {
                                authorization.track(session);
                                verifiedThisRun = route.endpoint();
                                savedMacStore.connected(pin, route.endpoint(), SavedMacs.manuallyEnteredRoute(typedByUser, endpoint, route.endpoint()));
                            }
                            HostLockState sessionLock = new HostLockState(false); sessionLock.requireFreshFrame();
                            picturePresented = false; hostLock = sessionLock; keyframeRecovery = new KeyframeRecovery(); keyframeStreaming = false; keyframeWorstRtt = 0; keyframeRttWindow = 0; recoveryFrame.set(null); lastParameters = null; revealFrame.set(null); recoveryGeneration = -1;
                            pointerSync = new PointerSync();
                            synchronized (savedMacStore) {
                                authorization.check();
                                synchronized (outputLock) { initialSource = new InitialSourcePolicy(); connection = session; }
                            }
                            session.sessionTimeout();
                            ui.post(() -> { if (connection == session) { refreshSavedMacs(); tourNewSession(session); renderLockOverlay(sessionLock, session); setAddingMac(false, false); connectionContainer.setVisibility(android.view.View.GONE); sessionPanel.setVisibility(android.view.View.VISIBLE); updateSessionLayout.run(); picture.post(this::sendViewport); } });
                            if (wantsAudio) { audioEndpointHost = route.endpoint().host(); audio = new AudioPlayer(text -> showFor(session, text)); localAudio.setWatched(pictureWatched()); localAudio.setPlayer(audio); ui.post(this::updateAudioTiming); }
                            for (StreamWire.Message initial : route.value().initial()) handle(initial, session);
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
                        if (pip != null && pip.active()) { ui.post(pip::stop); break; }
                        if (connectedAt != 0 && android.os.SystemClock.uptimeMillis() - connectedAt >= 10000) retries = 0;
                        long delay = ReconnectPolicy.delayMillis(retries);
                        GoodbyePolicy.Outcome goodbye = error instanceof GoodbyePolicy.Ended ended ? ended.outcome : null;
                        // Local-only redial needs authenticated discovery route metadata; stop until it exists.
                        if (goodbye != null && (!goodbye.reconnect() || !goodbye.remoteAllowed())) throw error;
                        if (!established || (goodbye == null && !ReconnectPolicy.retryable(error)) || delay < 0) throw error;
                        retries++;
                        if (pip != null && pip.active()) ui.post(pip::stop);
                connection = null; hostLock = null; keyframeRecovery = null; keyframeStreaming = false; recoveryFrame.set(null); lastParameters = null; revealFrame.set(null); recoveryGeneration = -1; pointerSync = null; clipboardSubscribed = false; hostClipboardAvailable = false; clipboardSource = ""; clipboardReceipt.cancel(); releaseDecoder();
                        if (audio != null) { localAudio.setPlayer(null); audio.close(); audio = null; }
                        ui.post(() -> {
                            suspendTourForReconnect(); dismissSettings(false); dismissStaleMenu(); dismissWindowControls(); cancelWindowDrag();
                            hostSettings = null; pendingSettingsToken = 0; desktopDisplays = -1; desktopDisplay = 0; desktopActive = false; hostGestures = 0; hostKeyRepeat = false; hostKeyRepeatKnown = false; trackpad.setMacGesturesEnabled(false);
                            desktopButton.setText("桌面"); choices.removeAllViews(); thumbnails.clear(); thumbnailImages.clear(); appIcons.clear(); iconBadges.clear(); activeWindowId = -1;
                            if (appDrawer != null) appDrawer.dismiss(); drawerWindows = new JSONArray(); appList = new JSONArray();
                            screenAwake.setStreaming(false); pictureInput.cancel(); trackpad.cancelGestures(); keyboard.discard(); resetCursorShape();
                            modifiers = heldModifiers = 0; for (android.view.View button : modifierViews.values()) button.setBackground(shape(0xff272a30, 12));
                        });
                        show(goodbye == null ? "连接中断，正在重新连接（" + retries + "/4）…" : goodbye.text());
                        synchronized (reconnectLock) { if (running && !closing) reconnectLock.wait(delay); }
                    }
                }
            } catch (Exception e) { if (!closing) {
                if (e instanceof GoodbyePolicy.Ended ended) ui.post(() -> displayStatus(ended.outcome.text()));
                else { android.util.Log.e("SillAndroid", "Connection failed", e); ui.post(() -> displayStatus("连接结束: " + e.getMessage())); }
            } }
            finally {
                synchronized (savedMacStore) { if (pendingPairing == pairingGuard) pendingPairing = null; }
                if (pip != null && pip.active()) ui.post(pip::stop);
                connection = null; hostLock = null; keyframeRecovery = null; keyframeStreaming = false; recoveryFrame.set(null); lastParameters = null; revealFrame.set(null); recoveryGeneration = -1; pointerSync = null; clipboardSubscribed = false; hostClipboardAvailable = false; clipboardSource = ""; clipboardReceipt.cancel(); releaseDecoder();
                if (audio != null) { localAudio.setPlayer(null); audio.close(); audio = null; }
                ui.post(() -> { endTour(false); dismissSettings(false); dismissStaleMenu(); dismissWindowControls(); cancelWindowDrag(); hostSettings = null; pendingSettingsToken = 0; desktopDisplays = -1; desktopDisplay = 0; desktopActive = false; hostGestures = 0; hostKeyRepeat = false; hostKeyRepeatKnown = false; trackpad.setMacGesturesEnabled(false); desktopButton.setText("桌面"); thumbnailImages.clear(); appIcons.clear(); iconBadges.clear(); activeWindowId = -1; if (appDrawer != null) appDrawer.dismiss(); drawerWindows = new JSONArray(); appList = new JSONArray(); zoom.reset(); screenAwake.setStreaming(false); pictureInput.cancel(); trackpad.cancelGestures(); keyboard.discard(); resetCursorShape(); modifiers = heldModifiers = 0; for (android.view.View button : modifierViews.values()) button.setBackground(shape(0xff272a30, 12)); sessionPanel.setVisibility(android.view.View.GONE); connectionContainer.setVisibility(android.view.View.VISIBLE); applyContentInsets(); running = false; updateConnectionCancel(); });
            }
        });
    }
    private record ReadyStream(SillConnection session, java.util.List<StreamWire.Message> initial) implements java.io.Closeable {
        @Override public void close() throws java.io.IOException { session.close(); }
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
        if (!allowedForTour(kind, value)) return;
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
        long inputGeneration;
        synchronized (outputLock) {
            expected = connection;
            inputGeneration = tourInputGeneration;
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
                    if (connection == expected && allowedForLock(kind, value) && allowedForTour(kind, value) && allowedTourGeneration(kind, value, inputGeneration)) {
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
        if (!PipPolicy.maySend(pip != null && pip.active(), kind, isRelease(kind, value))) return false;
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
        handle(message, messageSession, null);
    }
    private void handle(StreamWire.Message message, SillConnection messageSession, RemoteDial.Attempt candidateAttempt) throws Exception {
        long audioArrivalNanos = System.nanoTime();
        switch (message.kind()) {
            case 18 -> {
                if (messageSession != connection || message.payload().length > 98304) return;
                try {
                    JSONObject envelope = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                    byte[] pin = messageSession.peerFingerprint();
                    byte[] bytes = SignedMacInfo.verify(envelope.getString("info"), envelope.getString("key"), envelope.getString("sig"), pin);
                    String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
                    JSONObject info = new JSONObject(json);
                    String macId = info.getString("macID");
                    if (!macId.equals(PairLink.macId(pin))) return;
                    int defaultPort = info.getInt("remotePort");
                    if (defaultPort < 1 || defaultPort > 65535 || info.getDouble("remotePort") != defaultPort) return;
                    JSONArray advertised = info.getJSONArray("addresses");
                    java.util.List<SavedMacs.Endpoint> addresses = new java.util.ArrayList<>();
                    java.util.List<String> kinds = new java.util.ArrayList<>();
                    java.util.List<Boolean> implicitPorts = new java.util.ArrayList<>();
                    for (int i = 0; i < advertised.length(); i++) {
                        JSONObject entry = advertised.getJSONObject(i);
                        int port = entry.isNull("port") ? defaultPort : entry.getInt("port");
                        if (!entry.isNull("port") && entry.getDouble("port") != port) continue;
                        SavedMacs.Endpoint address = SignedMacInfo.address(entry.getString("host"), port);
                        if (address != null && !SignedMacInfo.containsAddress(addresses, implicitPorts, address, entry.isNull("port"))) {
                            addresses.add(address); kinds.add(entry.getString("kind")); implicitPorts.add(entry.isNull("port"));
                        }
                    }
                    SignedMacInfo.Update update = new SignedMacInfo.Update(macId, info.getString("name"),
                            info.getDouble("issuedAt"), info.getBoolean("remoteAccess"), addresses, kinds, implicitPorts, defaultPort);
                    ui.post(() -> {
                        if (connection == messageSession && savedMacStore.refresh(pin, update)) refreshSavedMacs();
                    });
                } catch (java.security.GeneralSecurityException | IllegalArgumentException | org.json.JSONException | java.nio.charset.CharacterCodingException ignored) {
                    android.util.Log.w("SillAndroid", "Rejected invalid signed Mac info");
                }
            }
            case 2 -> {
                if (messageSession == null || connection != messageSession) return;
                JSONObject list = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                JSONArray windows = list.getJSONArray("windows");
                JSONObject streamingSource = list.optJSONObject("active");
                boolean streaming = streamingSource != null && (streamingSource.has("window") || streamingSource.has("desktop"));
                if (keyframeStreaming != streaming) android.util.Log.i("SillAndroid", "Recovery: streaming=" + streaming);
                keyframeStreaming = streaming;
                HostLockState lock = hostLock; SillConnection listedSession = connection;
                long listVersion = -1;
                if (lock != null && listedSession != null) {
                    boolean wasCovered = lock.isCovered();
                    HostLockState.ListUpdate lockUpdate = lock.onWindowListUpdate(list.optBoolean("locked", false), list.optBoolean("offConsole", false),
                            list.optBoolean("offConsoleView", false), android.os.SystemClock.uptimeMillis());
                    boolean blank = lockUpdate.blank(); listVersion = lockUpdate.version();
                    if (blank) { clipboardReceipt.cancel(); releaseDecoder(); waitingForKeyframe = true; }
                    if (wasCovered && !lock.isCovered()) { waitingForKeyframe = true; interruptPicture(); requestKeyframe(); }
                    ui.post(() -> {
                        if (connection != listedSession || hostLock != lock) return;
                        if (blank) { trackpad.cancelGestures(); keyboard.discard(); }
                        if (appDrawer != null) appDrawer.setLock(lock.reason(), lock.viewOnly(), windowOrderMac);
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
                final long receivedListVersion = listVersion;
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
                        updateWindowChoiceGeometry(root.getWidth() > root.getHeight());
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
                        if (tile instanceof android.widget.FrameLayout frame && frame.getChildCount() > 0)
                            frame.getChildAt(0).setVisibility(chosen ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                    }
                    if (lock != null && hostLock == lock && connection == listedSession) lock.presentList(receivedListVersion, () -> {
                        drawerWindows = windows;
                        if (appDrawer != null) {
                            appDrawer.setLock(lock.reason(), lock.viewOnly(), windowOrderMac);
                            appDrawer.acceptLockList();
                        }
                        refreshDrawer();
                    });
                    considerTourPicture();
                    if (tourOverlay != null) updateTourStep();
                });
            }
            case 11 -> {
                byte[] pong = message.payload(); SillConnection expected = messageSession;
                if (pong.length == 8) {
                    double elapsed = System.nanoTime() / 1e9 - ByteBuffer.wrap(pong).getDouble();
                    if (Double.isFinite(elapsed) && elapsed >= 0 && elapsed <= 60) {
                        AudioPlayer player = audio;
                        if (player != null && messageSession == connection) player.rttSample(elapsed, audioArrivalNanos);
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
                    if (!clipboardMayPaste(expected, source)) {
                        if (clipboardReceipt.take(token)) showFor(expected, "已发送到 Mac 剪贴板");
                        return;
                    }
                    ui.postDelayed(() -> {
                        input.execute(() -> {
                            try {
                                synchronized (outputLock) {
                                    if (!clipboardReceipt.take(token)) return;
                                    if (!clipboardMayPaste(expected, source)) {
                                        if (connection == expected) showFor(expected, "已发送到 Mac 剪贴板");
                                        return;
                                    }
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
                if (connection != messageSession) return;
                JSONObject state = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                ui.post(() -> {
                    if (connection != messageSession) return;
                    hostSettings = state;
                    if (tourOverlay != null) updateTourStep();
                    updateAudioTiming();
                    if (scaleControl != null) scaleControl.refresh();
                    if (pendingSettingsToken != 0 && state.optInt("answering", -1) == pendingSettingsToken) {
                        JSONObject s = state.optJSONObject("settings"); Object actual = s == null ? null : s.opt(pendingSettingsField);
                        boolean accepted = actual instanceof Number && pendingSettingsValue instanceof Number
                                ? ((Number) actual).doubleValue() == ((Number) pendingSettingsValue).doubleValue()
                                : pendingSettingsValue.equals(actual);
                        pendingSettingsToken = 0; show(accepted ? "Mac 设置已更新" : "Mac 未接受此设置");
                    }
                    if (refreshInlineHostSettings != null) refreshInlineHostSettings.run();
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
                        if (connection != expected || expected == null || !clipboardEnabled || !keyframeForeground || (pip != null && pip.active()) || !hasWindowFocus() || sessionPanel.getVisibility() != android.view.View.VISIBLE) return;
                        android.content.ClipboardManager manager = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                        manager.setPrimaryClip(android.content.ClipData.newPlainText("Mac", text));
                    });
                }
            }
            case 43 -> { AudioPlayer player = audio; if (player != null && messageSession == connection) { byte[] data = message.payload(); localAudio.received(data); player.receive(data, message.timestamp(), audioArrivalNanos); } }
            case 0 -> {
                Hevc.Parameters params = Hevc.parameters(message.payload()); nalSize = params.lengthSize();
                lastParameters = params;
                if (pip != null && pip.paused()) { waitingForKeyframe = true; interruptPicture(); return; }
                releaseDecoder(); waitingForKeyframe = true;
                HostLockState lock = hostLock;
                if ((lock == null || !lock.isCovered()) && pictureWatched() && surface != null && surface.isValid()) createDecoder(params);
                else interruptPicture();
            }
            case 1 -> {
                AudioPlayer player = audio;
                if (player != null && messageSession == connection) player.videoFrame(message.timestamp(), audioArrivalNanos);
                HostLockState lock = hostLock;
                if (lock == null || lock.isCovered()) return;
                Surface target; long epoch;
                synchronized (surfaceLock) { target = surface; epoch = surfaceEpoch; }
                if (target == null || !target.isValid() || !pictureWatched()) { waitingForKeyframe = true; interruptPicture(); return; }
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
                if (decoder != null && pictureWatched() && surface != null && decoderEpoch == surfaceEpoch && ((hostLock != null && hostLock.needsFreshFrame())
                        || (keyframeRecovery != null && keyframeRecovery.isOpen()))) drainVideo();
                if (waitingForKeyframe && surface != null) interruptPicture();
                requestKeyframe();
            }
            case 22 -> {
                java.util.Map<String, Object> fields = new java.util.HashMap<>();
                try {
                    String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(message.payload())).toString();
                    org.json.JSONTokener tokener = new org.json.JSONTokener(json);
                    Object parsed = tokener.nextValue();
                    if (!(parsed instanceof JSONObject object) || tokener.nextClean() != 0)
                        throw new org.json.JSONException("Invalid goodbye");
                    for (String key : new String[]{"reason", "message", "minimumVersion", "reconnect"})
                        if (object.has(key) && !object.isNull(key)) fields.put(key, object.get(key));
                } catch (org.json.JSONException | java.nio.charset.CharacterCodingException ignored) { fields.clear(); }
                GoodbyePolicy.Goodbye goodbye = GoodbyePolicy.decode(fields);
                byte[] pin = messageSession.peerFingerprint();
                SavedMacs.Identity identity = null;
                for (SavedMacs.Mac mac : savedMacStore.all())
                    if (java.security.MessageDigest.isEqual(pin, mac.identity().pin())) { identity = mac.identity(); break; }
                GoodbyePolicy.Outcome result = GoodbyePolicy.outcome(goodbye, identity == null ? "Mac" : identity.name(), identity != null);
                if ("removed".equals(goodbye.reason()) && (connection == messageSession
                        || candidateAttempt != null && pendingDial == candidateAttempt && running && !closing) && savedMacStore.forgetPin(pin))
                    ui.post(this::refreshSavedMacs);
                throw new GoodbyePolicy.Ended(result);
            }
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
                        || !pictureWatched() || renderedSurface == null || !renderedSurface.isValid() || renderedSurface != decoderSurface
                        || surfaceEpoch != targetEpoch || decoderEpoch != targetEpoch) return;
                picturePresented = true;
                considerTourPicture();
                if (pip != null) pip.dimensions(videoWidth, videoHeight);
                RecoveryFrame recovered = recoveryFrame.get();
                if (recovery != null && recovered != null && timeUs >= recovered.presentationUs() && recoveryFrame.compareAndSet(recovered, null)) {
                    recovery.pictureBack(android.os.SystemClock.uptimeMillis(), pictureWatched(), recovered.generation());
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
            if (surfaceEpoch != targetEpoch || !pictureWatched()) { interruptPicture(); return; }
            throw e;
        }
    }
    private void renderLockOverlay(HostLockState lock, SillConnection expected) {
        if (connection != expected || hostLock != lock) return;
        if (scaleControl != null) scaleControl.refresh();
        if (pip != null) pip.validate();
        if (!lock.isCovered() && !lock.needsFreshFrame()) { lockOverlay.setVisibility(android.view.View.GONE); considerTourPicture(); return; }
        hideTour();
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
            if (recovery.consider(android.os.SystemClock.uptimeMillis(), pictureWatched() && target != null && target.isValid(), keyframeStreaming, keyframeWorstRtt)) {
                expected.send(44, new byte[0]);
                android.util.Log.i("SillAndroid", "Recovery: keyframe requested");
            }
        }
    }
    private void drainVideo() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (decoder != null) {
            int out = decoder.dequeueOutputBuffer(info, 0);
            if (out >= 0) decoder.releaseOutputBuffer(out, pictureWatched());
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
        choices.removeViewAt(from); choices.addView(tile, target); windowOrder = WindowOrder.retainMissing(ids, windowOrder);
        tile.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
    }
    private void cancelWindowDrag() {
        if (activeWindowHoldView != null && activeWindowHoldRunnable != null)
            activeWindowHoldView.removeCallbacks(activeWindowHoldRunnable);
        if (activeWindowHold != null) activeWindowHold.cancel();
        activeWindowHold = null; activeWindowHoldView = null; activeWindowHoldRunnable = null;
        if (draggedWindow == null) return;
        draggedWindow.setTranslationZ(draggedWindowOriginalZ);
        for (int i = 0; i < choices.getChildCount(); i++) { choices.getChildAt(i).setTranslationX(0); choices.getChildAt(i).setScaleX(1); choices.getChildAt(i).setScaleY(1); }
        draggedWindow = null; dragPreview = null; choices.requestDisallowInterceptTouchEvent(false);
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
    private void updateWindowChoiceGeometry(boolean landscape) {
        if (choices == null) return;
        for (int i = 0; i < choices.getChildCount(); i++) {
            if (!(choices.getChildAt(i) instanceof android.widget.FrameLayout tile) || tile.getChildCount() < 2) continue;
            int width = dp(landscape ? 104 : 92), height = dp(landscape ? 70 : 62);
            boolean resize = tile.getLayoutParams().width != width;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            params.topMargin = params.bottomMargin = landscape ? dp(4) : 0; applyRowParams(tile, params);
            if (!resize) continue;
            android.graphics.drawable.GradientDrawable halo = shape(android.graphics.Color.TRANSPARENT, landscape ? 14 : 13);
            halo.setStroke(dp(2), 0xff9bb7ff); tile.getChildAt(0).setBackground(halo);
            android.view.View preview = tile.getChildAt(1);
            preview.setBackground(shape(0xff26282d, landscape ? 9 : 8));
            android.graphics.drawable.GradientDrawable edge = shape(android.graphics.Color.TRANSPARENT, landscape ? 9 : 8);
            edge.setStroke(dp(1), 0x1fffffff); preview.setForeground(edge);
            if (tile.getChildCount() > 2) {
                android.view.View badge = tile.getChildAt(2);
                android.widget.FrameLayout.LayoutParams bp = (android.widget.FrameLayout.LayoutParams) badge.getLayoutParams();
                bp.width = bp.height = dp(landscape ? 28 : 26); badge.setLayoutParams(bp);
            }
        }
    }
    private void addChoice(String title, JSONObject source, String bundleId) {
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this); tile.setContentDescription(title);
        choices.addView(tile, new LinearLayout.LayoutParams(dp(92), dp(62)));
        android.view.View halo = new android.view.View(this);
        android.graphics.drawable.GradientDrawable haloShape = shape(android.graphics.Color.TRANSPARENT, 13);
        haloShape.setStroke(dp(2), 0xff9bb7ff); halo.setBackground(haloShape); halo.setVisibility(android.view.View.INVISIBLE);
        tile.addView(halo, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.widget.FrameLayout preview = new android.widget.FrameLayout(this);
        preview.setBackground(shape(0xff26282d, 8)); preview.setClipToOutline(true);
        android.widget.FrameLayout.LayoutParams previewParams = new android.widget.FrameLayout.LayoutParams(-1, -1);
        previewParams.setMargins(dp(6), dp(6), dp(6), dp(6)); tile.addView(preview, previewParams);
        android.view.View titleBar = new android.view.View(this); titleBar.setBackgroundColor(0xff34373d);
        preview.addView(titleBar, new android.widget.FrameLayout.LayoutParams(-1, dp(9), android.view.Gravity.TOP));
        android.widget.ImageView image = new android.widget.ImageView(this); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        preview.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -1));
        android.graphics.drawable.GradientDrawable border = shape(android.graphics.Color.TRANSPARENT, 8);
        border.setStroke(dp(1), 0x1fffffff); preview.setForeground(border);
        if (source.length() != 0) {
            long id = source.optLong("_0"); tile.setTag(id); thumbnails.put(id, image); image.setImageBitmap(thumbnailImages.get(id));
            android.widget.FrameLayout ring = new android.widget.FrameLayout(this); ring.setPadding(dp(2), dp(2), dp(2), dp(2));
            ring.setBackground(shape(0xff16181c, 8));
            android.widget.FrameLayout badgeBody = new android.widget.FrameLayout(this); badgeBody.setBackground(shape(0xff3a3d44, 6)); badgeBody.setClipToOutline(true);
            ring.addView(badgeBody, new android.widget.FrameLayout.LayoutParams(-1, -1));
            String name = title.contains(" — ") ? title.substring(0, title.indexOf(" — ")) : title;
            int end = name.offsetByCodePoints(0, Math.min(2, name.codePointCount(0, name.length())));
            TextView initials = new TextView(this); initials.setText(name.substring(0, end)); initials.setTextSize(10);
            initials.setTextColor(android.graphics.Color.WHITE); initials.setGravity(android.view.Gravity.CENTER);
            initials.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            badgeBody.addView(initials, new android.widget.FrameLayout.LayoutParams(-1, -1));
            android.widget.ImageView badge = new android.widget.ImageView(this);
            badge.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP); badge.setImageBitmap(appIcons.get(bundleId));
            badgeBody.addView(badge, new android.widget.FrameLayout.LayoutParams(-1, -1));
            android.widget.FrameLayout.LayoutParams badgeLayout = new android.widget.FrameLayout.LayoutParams(dp(26), dp(26), android.view.Gravity.BOTTOM | android.view.Gravity.START);
            tile.addView(ring, badgeLayout);
            iconBadges.computeIfAbsent(bundleId, ignored -> new java.util.ArrayList<>()).add(badge);
        } else { image.setImageResource(android.R.drawable.ic_menu_view); image.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE); image.setColorFilter(0xffaeb5c0); tile.setContentDescription(title + "（无实时预览）"); }
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
                        cancelWindowDrag();
                        gesture.begin(event.getX(), event.getY());
                        activeWindowHold = gesture; activeWindowHoldView = view; activeWindowHoldRunnable = hold;
                        gestureSession = connection;
                        lastRawX = event.getRawX(); lastRawY = event.getRawY();
                        int tenths = Math.max(2, Math.min(20, getPreferences(0).getInt("thumbnailHoldTenths", 5)));
                        view.postDelayed(hold, tenths * 100L); return true;
                    }
                    case android.view.MotionEvent.ACTION_MOVE -> {
                        if (!keyframeForeground || gestureSession == null || connection != gestureSession) { view.removeCallbacks(hold); gesture.cancel(); if (draggedWindow == tile || activeWindowHold == gesture) cancelWindowDrag(); return true; }
                        lastRawX = event.getRawX(); lastRawY = event.getRawY();
                        if (gesture.isHeld()) {
                            float dx = lastRawX - heldX, dy = lastRawY - heldY;
                            if (draggedWindow == null && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) {
                                dismissWindowControls(); draggedWindow = tile; dragFrom = choices.indexOfChild(tile); dragTo = dragFrom;
                                dragPreview = visibleWindowIds();
                                draggedWindowOriginalZ = tile.getTranslationZ(); tile.setTranslationZ(dp(16));
                                tile.setScaleX(1.08f); tile.setScaleY(1.08f);
                                tile.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                            }
                            if (draggedWindow == tile) {
                                int slot = tile.getWidth();
                                int target = WindowOrder.dragTarget(dragFrom, dx, slot, choices.getChildCount());
                                if (target != dragTo && dragPreview != null && WindowOrder.move(dragPreview, (Long) tile.getTag(), target)) {
                                    dragTo = target;
                                    tile.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                                }
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
                        if (!keyframeForeground || gestureSession == null || connection != gestureSession) { gesture.cancel(); if (draggedWindow == tile || activeWindowHold == gesture) cancelWindowDrag(); return true; }
                        if (draggedWindow == tile) dropWindowDrag();
                        else { boolean tap = gesture.end(); if (activeWindowHold == gesture) cancelWindowDrag(); if (tap) view.performClick(); }
                        if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false); return true;
                    }
                    case android.view.MotionEvent.ACTION_CANCEL -> { view.removeCallbacks(hold); gesture.cancel(); if (draggedWindow == tile || activeWindowHold == gesture) cancelWindowDrag(); if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false); return true; }
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
        android.graphics.drawable.GradientDrawable menu = shape(0xff23262c, 18);
        menu.setStroke(dp(1), 0x1affffff); row.setBackground(menu); row.setPadding(dp(10), 0, dp(10), 0);
        String[] labels = {"关闭", "最小化", "全屏"};
        String[] symbols = {"×", "−", "⤢"};
        String[] actions = {"close", "minimize", "fullScreen"};
        int[] colors = {0xffff5f57, 0xffffbd2e, 0xff28c840};
        for (int i = 0; i < labels.length; i++) {
            final String action = actions[i];
            LinearLayout item = new LinearLayout(this); item.setOrientation(LinearLayout.VERTICAL); item.setGravity(android.view.Gravity.CENTER);
            android.widget.TextView light = new android.widget.TextView(this); light.setText(symbols[i]); light.setGravity(android.view.Gravity.CENTER);
            light.setTextColor(0xa6000000); light.setTextSize(12); light.setTypeface(null, android.graphics.Typeface.BOLD); light.setBackground(shape(colors[i], 24));
            item.addView(light, new LinearLayout.LayoutParams(dp(24), dp(24)));
            android.widget.TextView label = new android.widget.TextView(this); label.setText(labels[i]); label.setTextColor(0xffc4c9d1); label.setTextSize(10); label.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2); labelParams.topMargin = dp(5);
            item.addView(label, labelParams); item.setContentDescription(labels[i] + "窗口");
            item.setOnClickListener(v -> {
                dismissWindowControls();
                sendWindowCommand(expected, source, action);
            });
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(60), -1);
            if (i > 0) itemParams.leftMargin = dp(6); row.addView(item, itemParams);
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
    private boolean pictureWatched() { return pip == null ? keyframeForeground : pip.watched(keyframeForeground); }
    private void initializePip() {
        pipEnabled = getPreferences(0).getBoolean("pictureInPicture", false);
        pip = new PipController(this, picture, new PipController.Listener() {
            public boolean eligible() {
                HostLockState lock = hostLock;
                boolean covered = lock == null || lock.reason() != HostLockState.Reason.NONE || lock.isCovered() || lock.needsFreshFrame();
                return PipPolicy.qualifies(pipEnabled, pip.supported(), connection != null && !closing
                        && (pipOwner == null || connection == pipOwner), keyframeStreaming, picturePresented, covered);
            }
            public void changed() { pipChanged(); }
            public void ending() { disconnect(); }
        });
    }
    private void pipChanged() {
        boolean active = pip.active();
        synchronized (surfaceLock) { surfaceEpoch++; }
        recoveryFrame.set(null);
        if (active && !pipUi) {
            hideTour();
            pipOwner = connection;
            dismissSettings(false); dismissMenu(); dismissWindowControls(); cancelWindowDrag();
            if (appDrawer != null) appDrawer.dismiss();
            if (scaleControl != null) scaleControl.cancel();
            releaseHardwareKeys(); keyboard.finishAndForget(); keyboard.clearFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(keyboard.getWindowToken(), 0);
            pictureInput.cancel(); trackpad.cancelGestures(); clipboardReceipt.cancel();
            pipVisibility.clear();
            for (int i = 0; i < sessionPanel.getChildCount(); i++) {
                android.view.View child = sessionPanel.getChildAt(i);
                if (child != picture) { pipVisibility.put(child, child.getVisibility()); child.setVisibility(android.view.View.GONE); }
            }
            for (int i = 0; i < picture.getChildCount(); i++) {
                android.view.View child = picture.getChildAt(i);
                if (child != video) { pipVisibility.put(child, child.getVisibility()); child.setVisibility(android.view.View.GONE); }
            }
            pipUi = true; root.setPadding(0, 0, 0, 0); layoutPip();
        } else if (!active && pipUi) {
            pipUi = false; pipOwner = null;
            for (var entry : pipVisibility.entrySet()) entry.getKey().setVisibility(entry.getValue());
            pipVisibility.clear(); applyContentInsets();
            if (updateSessionLayout != null) updateSessionLayout.run();
            picture.post(this::sendViewport);
            picture.post(this::considerTourPicture);
        }
        if (localAudio != null) localAudio.setWatched(pictureWatched());
        if (pictureWatched()) interruptPicture();
    }
    private void layoutPip() {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) picture.getLayoutParams();
        if (params.height != 0 || params.weight != 1 || params.leftMargin != 0 || params.topMargin != 0) { params.width = -1; params.height = 0; params.weight = 1; params.setMargins(0, 0, 0, 0); picture.setLayoutParams(params); }
        layoutVideo();
    }
    @Override protected void onStart() { super.onStart(); if (discovery != null) discovery.start(); startFpsObservers(); }
    @Override protected void onResume() {
        super.onResume(); keyframeForeground = true;
        if (inlineScanner != null) inlineScanner.setForeground(true);
        android.util.Log.i("SillAndroid", "Recovery: foreground");
        if (localAudio != null) localAudio.setWatched(pictureWatched());
        if (screenAwake != null) screenAwake.setForeground(true);
        if (picture != null) { picture.post(this::sendViewport); picture.post(this::considerTourPicture); }
    }
    @Override protected void onPause() {
        if (inlineScanner != null) inlineScanner.setForeground(false);
        if (scaleControl != null) scaleControl.cancel();
        hideTour();
        android.util.Log.i("SillAndroid", "Recovery: background"); keyframeForeground = false;
        if (!pictureWatched()) {
            synchronized (surfaceLock) { surfaceEpoch++; }
            recoveryFrame.set(null); interruptPicture();
            if (keyframeRecovery != null) keyframeRecovery.leftForeground();
        }
        if (localAudio != null) localAudio.setWatched(pictureWatched());
        if (screenAwake != null) screenAwake.setForeground(false);
        super.onPause();
    }
    @Override protected void onUserLeaveHint() { super.onUserLeaveHint(); if (pip != null) pip.leave(); }
    @Override public void onPictureInPictureModeChanged(boolean active, android.content.res.Configuration configuration) {
        super.onPictureInPictureModeChanged(active, configuration);
        if (active) keyframeForeground = false;
        else if (pip != null && pip.active()) {
            // The mode callback can precede the full-size view layout during the return animation.
            pipReturnWidthDp = configuration.screenWidthDp; pipReturnHeightDp = configuration.screenHeightDp;
        }
        if (pip != null) pip.modeChanged(active);
    }
    @Override public void onUserInteraction() {
        super.onUserInteraction(); if (screenAwake != null) screenAwake.personActed();
        if (tourRun == null && tourPictureAt != null) { tourLastActivity = tourNow(); considerTour(); }
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (tourRun == null && tourPictureAt != null) tourLastActivity = tourNow();
                tourTouches = action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP ? 0 : event.getPointerCount() - (action == MotionEvent.ACTION_POINTER_UP ? 1 : 0);
                if (tourRun == null) considerTour();
            }
        }
        return super.dispatchTouchEvent(event);
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (tourInputBlocked) return true;
        return super.dispatchGenericMotionEvent(event);
    }
    @Override protected void onStop() { if (pip != null && pip.active()) pip.stop(); dismissSettings(false); dismissMenu(); if (appDrawer != null) appDrawer.dismiss(); dismissWindowControls(); cancelWindowDrag(); stopFpsObservers(); clipboardReceipt.cancel(); if (pictureInput != null) pictureInput.cancel(); if (trackpad != null) trackpad.cancelGestures(); if (discovery != null) discovery.stop(); nearby.clear(); nearbyMacs.removeAllViews(); super.onStop(); }
    @Override protected void onDestroy() {
        clearPairConfirmation();
        android.view.accessibility.AccessibilityManager accessibility = (android.view.accessibility.AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (accessibility != null && tourAccessibilityListener != null) accessibility.removeTouchExplorationStateChangeListener(tourAccessibilityListener);
        if (pip != null) pip.close();
        if (localAudio != null) localAudio.close();
        stopFpsObservers();
        if (screenAwake != null) screenAwake.close();
        cancelQrPairing(); running = false; RemoteDial.Attempt dial = pendingDial; if (dial != null) dial.close(); SillConnection c = connection; if (c != null) try { c.close(); } catch (Exception ignored) { }
        synchronized (reconnectLock) { reconnectLock.notifyAll(); }
        qrNetwork.shutdownNow(); network.shutdownNow(); input.shutdownNow(); heartbeat.shutdownNow(); super.onDestroy();
    }
}
