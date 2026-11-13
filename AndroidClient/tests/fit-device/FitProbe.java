package io.github.n2ray.sall;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic, isolated Activity checks: no host, no user app data or network. */
public final class FitProbe extends Instrumentation {
    private boolean skipPreview;
    @Override public void onCreate(Bundle args) { super.onCreate(args); skipPreview = "true".equals(args.getString("skipPreview")); start(); }
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Object get(MainActivity a, String name) throws Exception { return field(MainActivity.class, name).get(a); }
    private static void set(MainActivity a, String name, Object value) throws Exception { field(MainActivity.class, name).set(a, value); }
    private static void call(MainActivity a, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name, types); m.setAccessible(true); m.invoke(a, args);
    }
    private static Object fitRecord(String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = Class.forName(MainActivity.class.getName() + "$" + name);
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
    private static void observed(MainActivity a, long window, int width, int height) throws Exception {
        set(a, "observedWindow", fitRecord("ObservedWindow",
                new Class<?>[]{SillConnection.class, long.class, int.class, int.class},
                get(a, "connection"), window, width, height));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static JSONObject firstSelection() throws Exception {
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(SillConnection.fitProbeOutput.toByteArray());
        while (input.available() > 0) {
            StreamWire.Message message = StreamWire.read(input);
            if (message.kind() == 6) return new JSONObject(new String(message.payload(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return null;
    }
    private static boolean hasScale(double scale) throws Exception {
        byte[] bytes;
        synchronized (SillConnection.fitProbeOutput) { bytes = SillConnection.fitProbeOutput.toByteArray(); }
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(bytes);
        while (input.available() > 0) {
            StreamWire.Message message = StreamWire.read(input);
            if (message.kind() != 9) continue;
            JSONObject payload = new JSONObject(new String(message.payload(), java.nio.charset.StandardCharsets.UTF_8));
            if (Math.abs(payload.optDouble("scale", -1) - scale) < .02) return true;
        }
        return false;
    }
    private static boolean hasSuggestedSettings(int token) throws Exception {
        byte[] bytes;
        synchronized (SillConnection.fitProbeOutput) { bytes = SillConnection.fitProbeOutput.toByteArray(); }
        java.io.ByteArrayInputStream input = new java.io.ByteArrayInputStream(bytes);
        while (input.available() > 0) {
            StreamWire.Message message = StreamWire.read(input);
            if (message.kind() != 17) continue;
            JSONObject payload = new JSONObject(new String(message.payload(), java.nio.charset.StandardCharsets.UTF_8));
            if (payload.optInt("token") == token && payload.optInt("bitrate") == 4000000
                    && payload.optDouble("captureScale") == 1) return true;
        }
        return false;
    }
    private void onUi(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { failure.set(e); } });
        if (failure.get() != null) throw new AssertionError("UI probe", failure.get());
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); MainActivity activity = null; int code = Activity.RESULT_CANCELED;
        try {
            getTargetContext().getSharedPreferences("MainActivity", 0).edit().putBoolean("shareClipboard", false).commit();
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity a = activity;
            if (!skipPreview) {
            byte[] fakePin = java.security.MessageDigest.getInstance("SHA-256").digest("isolated-preview-ui".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SavedMacs.Identity fakeIdentity = SavedMacs.Identity.migrated(fakePin);
            SavedMacStore fakeMacs = (SavedMacStore) get(a, "savedMacStore");
            fakeMacs.paired(fakeIdentity, new SavedMacs.Endpoint("127.0.0.1", 7455));
            ConnectionPreviewStore previewStore = (ConnectionPreviewStore) get(a, "previews");
            previewStore.bind(999, fakePin); previewStore.context(true, true);
            ConnectionPreviewPolicy.Ticket ticket = previewStore.begin(999, fakePin, true);
            byte[] fakeImage = "isolated synthetic image".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            check(ticket != null && previewStore.save(ticket, 999, fakePin, true, true, fakeImage), "synthetic encrypted preview unavailable");
            check(java.util.Arrays.equals(fakeImage, previewStore.load(fakePin, true)), "synthetic encrypted preview missing");
            SavedMacs.Mac fakeMac = fakeMacs.all().get(0);
            onUi(() -> { try { call(a, "confirmForgetMac", new Class<?>[]{SavedMacs.Mac.class}, fakeMac); }
                catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo window = getUiAutomation().getRootInActiveWindow();
            check(window != null, "forget dialog not visible");
            boolean clicked = false;
            for (android.view.accessibility.AccessibilityNodeInfo node : window.findAccessibilityNodeInfosByText("移除")) {
                if ("移除".contentEquals(node.getText()) && node.isClickable()) {
                    clicked = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); break;
                }
            }
            check(clicked, "forget confirmation not clicked");
            waitForIdleSync();
            check(fakeMacs.all().isEmpty() && previewStore.load(fakePin, true) == null, "UI removal left saved Mac or preview");
            AndroidConnectionPreviewBackend fakeBackend = new AndroidConnectionPreviewBackend(a);
            String fakeName = ConnectionPreviewPolicy.fingerprintName(fakePin);
            check(fakeBackend.key(fakeName, false) == null && fakeBackend.read(fakeName, 2000032) == null,
                    "UI removal left synthetic key or ciphertext");
            java.util.Arrays.fill(fakeImage, (byte) 0);
            }
            int[] recommendationToken = {0};
            onUi(() -> { try {
                ((View) get(a, "connectionContainer")).setVisibility(View.GONE);
                ((View) get(a, "sessionPanel")).setVisibility(View.VISIBLE);
                ((Runnable) get(a, "updateSessionLayout")).run();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            java.util.concurrent.CountDownLatch copied = new java.util.concurrent.CountDownLatch(1);
            int[] copyResult = {-1};
            android.graphics.Bitmap synthetic = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888);
            onUi(() -> { try {
                android.view.SurfaceView video = (android.view.SurfaceView) get(a, "video");
                android.graphics.Canvas canvas = video.getHolder().lockCanvas();
                check(canvas != null, "synthetic surface unavailable");
                canvas.drawColor(android.graphics.Color.GREEN);
                video.getHolder().unlockCanvasAndPost(canvas);
                android.view.PixelCopy.request(video, synthetic, status -> { copyResult[0] = status; copied.countDown(); },
                        new android.os.Handler(android.os.Looper.getMainLooper()));
            } catch (Exception e) { throw new RuntimeException(e); } });
            check(copied.await(5, java.util.concurrent.TimeUnit.SECONDS) && copyResult[0] == android.view.PixelCopy.SUCCESS,
                    "synthetic surface copy failed");
            check((synthetic.getPixel(4, 4) & 0x00ffffff) == 0x0000ff00, "synthetic surface pixels changed");
            synthetic.eraseColor(android.graphics.Color.TRANSPARENT); synthetic.recycle();
            onUi(() -> { try {
                View picture = (View) get(a, "picture");
                check(a.hasWindowFocus() && picture.getWidth() > 0 && picture.getHeight() > 0, "picture not ready");
                float density = a.getResources().getDisplayMetrics().density;
                int width = Math.round(picture.getWidth() / density);
                int height = Math.round(picture.getHeight() / density);
                set(a, "connection", SillConnection.fitProbeSession());
                set(a, "activeAuthorization", new SessionAuthorization(new byte[32]));
                set(a, "hostLock", new HostLockState(false));
                set(a, "keyframeForeground", true);
                set(a, "activeWindowId", 42L); set(a, "autoFitWindow", 42L);
                set(a, "hostSettings", new JSONObject().put("settings", new JSONObject().put("bitrate", 8000000).put("captureScale", 2))
                        .put("softwareEncoder", true).put("link", new JSONObject().put("state", "behind")
                                .put("suggestedBitrate", 4000000).put("suggestedCaptureScale", 1))
                        .put("stream", new JSONObject().put("onVirtualDisplay", true).put("width", 832)
                                .put("height", 520).put("fps", 60).put("mbps", 15)));
                ((ConnectionDiagnostics.Rtt) get(a, "diagnosticsRtt")).add(.040, android.os.SystemClock.uptimeMillis());
                call(a, "settings", new Class<?>[0]);
                android.widget.TextView readout = (android.widget.TextView) get(a, "diagnosticsReadout");
                android.widget.TextView warning = (android.widget.TextView) get(a, "diagnosticsWarning");
                check(readout != null && readout.getText().toString().contains("832×520")
                        && readout.getText().toString().contains("40 ms"), "diagnostics readout missing");
                check(warning != null && warning.getVisibility() == View.VISIBLE
                        && warning.getText().toString().contains("软件编码")
                        && warning.getText().toString().contains("画质"), "diagnostics warning missing");
                android.widget.Button apply = (android.widget.Button) get(a, "diagnosticsApply");
                check(apply != null && apply.getVisibility() == View.VISIBLE && apply.performClick(), "recommendation not available");
                recommendationToken[0] = (Integer) get(a, "pendingSettingsToken");
                check(recommendationToken[0] != 0, "recommendation token missing");
                call(a, "dismissSettings", new Class<?>[]{boolean.class}, false);
                check(get(a, "diagnosticsReadout") == null, "diagnostics view retained after dismiss");
                set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", height);
                observed(a, 42L, width, height);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "matching sample not scheduled");
            } catch (Exception e) { throw new RuntimeException(e); } });
            ((java.util.concurrent.ExecutorService) get(a, "input")).submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            check(hasSuggestedSettings(recommendationToken[0]), "recommendation did not send both settings together");
            onUi(() -> { try {
                JSONObject reply = new JSONObject().put("answering", recommendationToken[0])
                        .put("settings", new JSONObject().put("bitrate", 4000000).put("captureScale", 1));
                call(a, "handle", new Class<?>[]{StreamWire.Message.class, SillConnection.class},
                        new StreamWire.Message(16, 0, false, reply.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        get(a, "connection"));
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            onUi(() -> { try { check((Integer) get(a, "pendingSettingsToken") == 0, "recommendation reply not acknowledged"); }
                catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                check(get(a, "automaticScale") == null && Boolean.FALSE.equals(get(a, "autoFitAttempted")),
                        "matching sample consumed the fit attempt");
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") == null, "unchanged sample scheduled again");
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                int width = Math.round(picture.getWidth() / density * 496f / 368f);
                int height = Math.round(picture.getHeight() / density * 490f / 230f);
                set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", height);
                observed(a, 42L, width, height);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "changed two-axis minimum sample not scheduled");
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                Double value = (Double) get(a, "automaticScale");
                check(value != null && Math.abs(value - 230.0 / 490) < .02,
                        "changed two-axis minimum sample did not fit");
                check(hasScale(230.0 / 490), "no two-axis automatic viewport sent");
                call(a, "resetAutoFit", new Class<?>[]{boolean.class}, true);
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                int width = Math.round(picture.getWidth() / density * 2.5f);
                int height = Math.round(picture.getHeight() / density);
                set(a, "autoFitWindow", 42L); set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", height);
                observed(a, 42L, width, height);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "automatic fit not scheduled");
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                Double value = (Double) get(a, "automaticScale");
                check(value != null && Math.abs(value - .4) < .02, "automatic fit not applied");
                check(hasScale(.4), "no synthetic automatic viewport sent");
                TextScaleControl control = (TextScaleControl) get(a, "scaleControl");
                TextScaleControl.Delegate delegate = (TextScaleControl.Delegate) field(TextScaleControl.class, "delegate").get(control);
                delegate.commit(.75);
                check(Boolean.TRUE.equals(get(a, "manualScale")) && get(a, "automaticScale") == null
                        && Double.valueOf(.75).equals(get(a, "textScale")), "manual Aa did not override automatic fit");
                set(a, "manualScale", false); set(a, "textScale", null);
                set(a, "autoFitQueued", true); set(a, "autoFitAttempted", true);
                call(a, "cancelAutoFitPending", new Class<?>[0]);
                check(Boolean.FALSE.equals(get(a, "autoFitQueued")) && Boolean.FALSE.equals(get(a, "autoFitAttempted")), "cancelled Aa blocks retry");
                set(a, "automaticScale", .4);
                View picture = (View) get(a, "picture");
                set(a, "automaticPanelWidth", picture.getWidth()); set(a, "automaticPanelHeight", picture.getHeight());
                callActivityOnPause(a);
                check(Double.valueOf(.4).equals(get(a, "automaticScale")), "pause lost applied auto scale");
                callActivityOnResume(a);
                set(a, "keyframeForeground", true);
                set(a, "automaticPanelHeight", picture.getHeight() + 1);
                PipController pip = (PipController) get(a, "pip");
                PipPolicy pipState = (PipPolicy) field(PipController.class, "state").get(pip);
                pipState.started();
                try {
                    call(a, "sendViewport", new Class<?>[0]);
                    check(Double.valueOf(.4).equals(get(a, "automaticScale")), "PiP cleared fit on temporary picture size");
                } finally { pipState.ended(); }
                call(a, "sendViewport", new Class<?>[0]);
                check(get(a, "automaticScale") == null, "foreground panel change retained the old fit");
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                int width = Math.round(picture.getWidth() / density * 2.5f);
                int height = Math.round(picture.getHeight() / density);
                set(a, "automaticScale", null); set(a, "autoFitAttempted", false);
                set(a, "activeWindowId", 43L); set(a, "autoFitWindow", 43L);
                set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", height);
                observed(a, 43L, width, height);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "stale-session scenario not scheduled");
                set(a, "connection", SillConnection.fitProbeSession());
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3200); waitForIdleSync();
            onUi(() -> { try {
                check(get(a, "automaticScale") == null, "old connection changed the new session");
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                SillConnection owner = SillConnection.fitProbeSession();
                set(a, "connection", owner);
                set(a, "activeAuthorization", new SessionAuthorization(new byte[32]));
                set(a, "hostLock", new HostLockState(false));
                set(a, "keyframeForeground", true); set(a, "desktopActive", false);
                set(a, "manualScale", false); set(a, "textScale", null);
                set(a, "autoFitAwaitingSelection", false); set(a, "activeWindowId", 101L);
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                double panelWidth = picture.getWidth() / density, panelHeight = picture.getHeight() / density;
                int width = (int) Math.round(panelWidth * 2), height = (int) Math.round(panelHeight / .3);
                Object observed = fitRecord("ObservedWindow", new Class<?>[]{SillConnection.class, long.class, int.class, int.class},
                        owner, 101L, width, height);
                set(a, "observedWindow", observed);
                call(a, "resetAutoFit", new Class<?>[]{boolean.class}, true);
                check(get(a, "observedWindow") == observed, "rotation discarded confirmed window snapshot");
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                Object base = fitRecord("AutoFitBase", new Class<?>[]{SillConnection.class, double.class, double.class},
                        owner, panelWidth, panelHeight);
                set(a, "autoFitBase", base);
                long epoch = ((java.util.concurrent.atomic.AtomicLong) get(a, "autoFitEpoch")).get();
                Class<?> baseClass = base.getClass();
                set(a, "connection", SillConnection.fitProbeSession());
                call(a, "reuseObservedWindow", new Class<?>[]{baseClass, long.class}, base, epoch);
                check(((Long) get(a, "autoFitWindow")) == -1 && get(a, "autoFitPending") == null,
                        "different connection reused a window snapshot");
                set(a, "connection", owner); set(a, "activeWindowId", 102L);
                call(a, "reuseObservedWindow", new Class<?>[]{baseClass, long.class}, base, epoch);
                check(((Long) get(a, "autoFitWindow")) == -1 && get(a, "autoFitPending") == null,
                        "different window reused a window snapshot");
                set(a, "activeWindowId", 101L);
                call(a, "reuseObservedWindow", new Class<?>[]{baseClass, long.class}, base, epoch);
                check(((Long) get(a, "autoFitWindow")) == 101 && get(a, "autoFitPending") != null,
                        "same-window rotation did not reuse the snapshot");
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                Double value = (Double) get(a, "automaticScale");
                check(value != null && Math.abs(value - .3) < .02 && hasScale(.3),
                        "reused snapshot did not send the new panel's fit");
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                call(a, "resetAutoFit", new Class<?>[]{boolean.class}, true);
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                double panelWidth = picture.getWidth() / density, panelHeight = picture.getHeight() / density;
                int width = (int) Math.round(panelWidth * 2), oldHeight = (int) Math.round(panelHeight / .36);
                set(a, "activeWindowId", 103L); set(a, "autoFitWindow", 103L);
                set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", oldHeight);
                observed(a, 103L, width, oldHeight);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", oldHeight)));
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "old sample was not scheduled");
                observed(a, 103L, width, (int) Math.round(panelHeight / .26));
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                check(get(a, "automaticScale") == null && Boolean.FALSE.equals(get(a, "autoFitAttempted")) && !hasScale(.36),
                        "changed window dimensions allowed an obsolete sample to send");
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                int width = Math.round(picture.getWidth() / density * 2), height = Math.round(picture.getHeight() / density / .26f);
                set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", height);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "new sample was not scheduled");
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                Double value = (Double) get(a, "automaticScale");
                check(value != null && Math.abs(value - .26) < .02 && hasScale(.26),
                        "updated window sample did not send its fit");
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                call(a, "resetAutoFit", new Class<?>[]{boolean.class}, true);
                View picture = (View) get(a, "picture");
                float density = a.getResources().getDisplayMetrics().density;
                int width = Math.round(picture.getWidth() / density * 2.5f);
                int height = Math.round(picture.getHeight() / density);
                set(a, "autoFitWindow", 103L); set(a, "autoFitWindowWidth", width); set(a, "autoFitWindowHeight", height);
                observed(a, 103L, width, height);
                set(a, "hostSettings", new JSONObject().put("stream", new JSONObject()
                        .put("onVirtualDisplay", true).put("width", width).put("height", height)));
                SillConnection.fitProbeFailViewport = true;
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "failure case not scheduled");
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                check(Boolean.FALSE.equals(get(a, "autoFitAttempted")) && get(a, "autoFitSample") == null,
                        "failed viewport permanently blocked retry");
                call(a, "considerAutoFit", new Class<?>[0]);
                check(get(a, "autoFitPending") != null, "failed viewport did not reschedule");
            } catch (Exception e) { throw new RuntimeException(e); } });
            Thread.sleep(3400); waitForIdleSync();
            onUi(() -> { try {
                Double value = (Double) get(a, "automaticScale");
                check(value != null && Math.abs(value - .4) < .02, "viewport retry did not apply fit");
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                SillConnection session = SillConnection.fitProbeSession();
                set(a, "connection", session); set(a, "hostLock", new HostLockState(false));
                set(a, "initialSource", new InitialSourcePolicy());
                ReconnectSourcePolicy resume = new ReconnectSourcePolicy();
                resume.onList(InitialSourcePolicy.Source.WINDOW, 42, "test-launch", false, true);
                resume.disconnected(); set(a, "reconnectSource", resume);
                SillConnection.fitProbeOutput.reset();
                JSONObject syntheticWindow = new JSONObject().put("id", 42).put("appName", "Synthetic").put("title", "Test")
                        .put("bundleID", "test.synthetic").put("width", 600).put("height", 400);
                JSONObject list = new JSONObject().put("windows", new org.json.JSONArray().put(syntheticWindow))
                        .put("active", new JSONObject().put("none", new JSONObject())).put("launchID", "test-launch");
                StreamWire.Message message = new StreamWire.Message(2, 0, false,
                        list.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                call(a, "handle", new Class<?>[]{StreamWire.Message.class, SillConnection.class}, message, session);
                JSONObject selection = firstSelection();
                check(selection != null && selection.getJSONObject("window").getLong("_0") == 42,
                        "reconnect did not request the original window");
                SillConnection.fitProbeOutput.reset();
                call(a, "handle", new Class<?>[]{StreamWire.Message.class, SillConnection.class}, message, session);
                check(firstSelection() == null, "reconnect sent the source twice");
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                android.widget.LinearLayout rows = new android.widget.LinearLayout(a);
                rows.setOrientation(android.widget.LinearLayout.VERTICAL);
                int[] changes = {0};
                Method add = MainActivity.class.getDeclaredMethod("addSettingsRow", android.widget.LinearLayout.class,
                        String.class, Boolean.class, boolean.class, Runnable.class);
                add.setAccessible(true);
                android.widget.LinearLayout row = (android.widget.LinearLayout) add.invoke(a, rows, "声音", false, false,
                        (Runnable) () -> changes[0]++);
                android.widget.Switch toggle = (android.widget.Switch) row.getChildAt(1);
                check(!toggle.isChecked() && toggle.getTrackDrawable().getIntrinsicWidth() > 0, "switch style missing");
                toggle.performClick();
                check(toggle.isChecked() && changes[0] == 1, "switch action or checked state changed");
                int width = Math.round(320 * a.getResources().getDisplayMetrics().density);
                rows.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                rows.layout(0, 0, width, rows.getMeasuredHeight());
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(width, rows.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                rows.draw(new android.graphics.Canvas(bitmap));
                try (java.io.FileOutputStream out = a.openFileOutput("synthetic-switch.png", 0)) {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out), "switch image failed");
                } finally { bitmap.recycle(); }
            } catch (Exception e) { throw new RuntimeException(e); } });
            result.putString("result", "PASS: synthetic fit and reconnect source" + (skipPreview ? "; preview checks skipped" : "")); code = Activity.RESULT_OK;
        } catch (Throwable e) {
            StringBuilder detail = new StringBuilder("FAIL");
            for (Throwable cause = e; cause != null && detail.length() < 600; cause = cause.getCause())
                detail.append(": ").append(cause.getClass().getSimpleName()).append(" ").append(cause.getMessage());
            result.putString("result", detail.toString());
        } finally {
            if (activity != null) {
                MainActivity a = activity;
                try { onUi(() -> { try { set(a, "connection", null); a.finish(); } catch (Exception e) { throw new RuntimeException(e); } }); }
                catch (Throwable e) { code = Activity.RESULT_CANCELED; result.putString("result", "FAIL: cleanup " + e.getClass().getSimpleName()); }
            }
        }
        finish(code, result);
    }
}
