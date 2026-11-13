package io.github.n2ray.sall;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import java.util.Collections;

/** Reads only Sill pairing codes; the receiving activity verifies the host before trusting it. */
public final class PairScannerActivity extends Activity {
    static final String RESULT_LINK = "pairLink";
    private static final int CAMERA_REQUEST = 1;
    private BarcodeView camera;
    private TextView status;
    private boolean active, completed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN, android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xff101215);
        TextView title = new TextView(this);
        title.setText("扫描 Mac 上的 Sill 配对码");
        title.setTextColor(0xffeef0f3);
        title.setTextSize(17);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        root.addView(title);
        camera = new BarcodeView(this);
        camera.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        root.addView(camera, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this);
        status.setTextColor(0xffa4abb5);
        status.setTextSize(13);
        status.setPadding(0, dp(12), 0, dp(8));
        status.setText("将配对码放入画面");
        root.addView(status);
        camera.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() {}
            public void previewStarted() {}
            public void previewStopped() {}
            public void cameraClosed() {}
            public void cameraError(Exception error) { status.setText("无法打开相机，请返回使用配对码。"); }
        });
        Button close = new Button(this);
        close.setText("取消");
        close.setAllCaps(false); close.setTextSize(15); close.setTextColor(0xff9bb7ff);
        close.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        close.setMinHeight(dp(44));
        close.setOnClickListener(v -> finish());
        root.addView(close);
        setContentView(root);
        camera.decodeContinuous(result -> {
            if (completed) return;
            String raw = result.getText();
            try {
                PairLink parsed = PairLink.parse(raw);
                parsed.clearSecret();
            } catch (IllegalArgumentException e) {
                status.setText("这不是有效的 Sill 配对码。");
                return;
            }
            completed = true;
            setResult(RESULT_OK, new Intent().putExtra(RESULT_LINK, raw));
            finish();
        });
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
            status.setText("此设备没有可用的相机，请使用配对码。");
        else if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        startCamera();
    }
    @Override protected void onPause() {
        active = false;
        camera.pause();
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != CAMERA_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else status.setText("请允许使用相机，或返回使用配对码。");
    }
    private void startCamera() {
        if (active && !completed && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) camera.resume();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
