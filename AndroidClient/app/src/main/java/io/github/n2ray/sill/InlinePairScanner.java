package io.github.n2ray.sill;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;
import java.util.function.Consumer;

/** Camera is owned by the visible pairing card and is stopped before a result is delivered. */
final class InlinePairScanner extends LinearLayout {
    static final int CAMERA_REQUEST = 103;
    private final Activity activity;
    private final BarcodeView camera;
    private final FrameLayout frame;
    private final TextView status;
    private final Button action;
    private final Consumer<String> onResult;
    private boolean visible, foreground, started, blocked, requested, failed, invalidShown;
    private final Runnable clearInvalid = this::clearInvalidStatus;
    private void clearInvalidStatus() { invalidShown = false; update(); }
    private int dp(float x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    InlinePairScanner(Activity activity, Consumer<String> onResult) {
        super(activity); this.activity = activity; this.onResult = onResult;
        setOrientation(VERTICAL);
        frame = new FrameLayout(activity); frame.setBackgroundColor(0xff23262c);
        camera = new BarcodeView(activity);
        camera.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        frame.addView(camera, new FrameLayout.LayoutParams(-1, -1));
        View corners = new View(activity) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                float side = Math.min(getWidth(), getHeight()) * .62f, arm = side * .18f;
                float l = (getWidth() - side) / 2, t = (getHeight() - side) / 2;
                paint.setColor(0xd9ffffff); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
                for (int x : new int[]{0, 1}) for (int y : new int[]{0, 1}) {
                    float px = l + x * side, py = t + y * side, dx = x == 0 ? arm : -arm, dy = y == 0 ? arm : -arm;
                    canvas.drawLine(px, py, px + dx, py, paint); canvas.drawLine(px, py, px, py + dy, paint);
                }
            }
        };
        corners.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        frame.addView(corners, new FrameLayout.LayoutParams(-1, -1));
        frame.setContentDescription("相机：对准 Mac 上的 Sill 配对码");
        addView(frame, new LayoutParams(-1, dp(230)));
        status = new TextView(activity); status.setTextSize(13); status.setTextColor(0xffa4abb5);
        status.setPadding(0, dp(6), 0, 0); addView(status);
        action = new Button(activity); action.setAllCaps(false); action.setTextColor(0xff9bb7ff);
        action.setBackgroundColor(android.graphics.Color.TRANSPARENT); action.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        action.setOnClickListener(v -> {
            if (activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && requested) {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + activity.getPackageName())));
            } else { blocked = false; failed = false; update(); }
        });
        addView(action, new LayoutParams(-1, dp(44)));
        camera.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() {} public void previewStarted() {} public void previewStopped() {} public void cameraClosed() {}
            public void cameraError(Exception error) {
                started = false; failed = true; invalidShown = false; removeCallbacks(clearInvalid); update();
            }
        });
        camera.decodeContinuous(result -> {
            if (!visible || !foreground || blocked) return;
            String raw = result.getText();
            try { PairLink parsed = PairLink.parse(raw); parsed.clearSecret(); }
            catch (IllegalArgumentException error) {
                if (!invalidShown) {
                    invalidShown = true; status.setText("这不是有效的 Sill 配对码。"); status.setTextColor(0xffffa64d);
                    postDelayed(clearInvalid, 2000);
                }
                return;
            }
            blocked = true; update(); onResult.accept(raw);
        });
    }
    void setVisible(boolean value) { visible = value; if (!value) { removeCallbacks(clearInvalid); invalidShown = false; } update(); }
    void setCompact(boolean compact) {
        LayoutParams p = (LayoutParams) frame.getLayoutParams();
        int height = dp(compact ? 200 : 230);
        if (p.height != height) { p.height = height; frame.setLayoutParams(p); }
    }
    void reset() { blocked = false; failed = false; invalidShown = false; removeCallbacks(clearInvalid); }
    void hold() { blocked = true; invalidShown = false; removeCallbacks(clearInvalid); update(); }
    void setForeground(boolean value) { foreground = value; if (!value) { removeCallbacks(clearInvalid); invalidShown = false; } update(); }
    void permissionResult() { requested = true; update(); }
    private void update() {
        boolean hardware = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
        boolean granted = activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean shouldRun = visible && foreground && hardware && granted && !blocked && !failed;
        if (!shouldRun && started) { started = false; camera.pause(); }
        if (shouldRun && !started) { started = true; camera.resume(); }
        if (visible && foreground && hardware && !granted && !requested) {
            requested = true; activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
        status.setTextColor(0xffa4abb5);
        status.setText(!hardware ? "此设备没有可用的相机，请手动输入配对码。" : !granted ? "请允许使用相机，或手动输入配对码。" : failed ? "无法打开相机，请重试或手动输入。" : blocked ? "点按重试以再次扫描。" : "将 Mac 上的配对码放入画面。" );
        action.setVisibility(visible && (blocked || failed || (hardware && !granted && requested)) ? View.VISIBLE : View.GONE);
        action.setText(!granted ? "打开设置" : "重新扫描");
    }
}
