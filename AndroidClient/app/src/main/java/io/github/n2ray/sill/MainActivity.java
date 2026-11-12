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
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object outputLock = new Object();
    private volatile SillConnection connection;
    private volatile boolean running;
    private volatile Surface surface;
    private LinearLayout root, choices;
    private TextView status;
    private EditText host, port, code, typing;
    private MediaCodec decoder;
    private int nalSize;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad); setContentView(root);
        status = new TextView(this); status.setText("输入 Mac 地址和配对码，Mac 端需启用 Remote Access。"); root.addView(status);
        host = field("Mac IP 或 Tailscale 地址", getPreferences(0).getString("host", ""));
        port = field("端口", "7455"); port.setInputType(2);
        code = field("Mac 上显示的 12 位配对码", ""); code.setInputType(2);
        Button connect = new Button(this); connect.setText("配对并连接"); root.addView(connect);
        connect.setOnClickListener(v -> connect());
        Button reconnect = new Button(this); reconnect.setText("连接已配对的 Mac"); root.addView(reconnect);
        reconnect.setOnClickListener(v -> start(false));
        ScrollView scroll = new ScrollView(this); choices = new LinearLayout(this); choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        SurfaceView video = new SurfaceView(this); root.addView(video, new LinearLayout.LayoutParams(-1, 0, 2));
        video.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder holder) { surface = holder.getSurface(); }
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { surface = holder.getSurface(); }
            public void surfaceDestroyed(SurfaceHolder holder) { surface = null; }
        });
        video.setOnTouchListener((view, event) -> {
            if (connection == null) return false;
            float x = Math.max(0, Math.min(1, event.getX() / view.getWidth()));
            float y = Math.max(0, Math.min(1, event.getY() / view.getHeight()));
            String action = switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> "leftDown";
                case MotionEvent.ACTION_MOVE -> "move";
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> "leftUp";
                default -> null;
            };
            if (action != null) try { sendJson(8, new JSONObject().put("pointer", new JSONObject().put("_0", action).put("x", x).put("y", y))); }
            catch (Exception e) { show("输入失败: " + e.getMessage()); }
            return true;
        });
        typing = field("输入文字，按发送", "");
        Button send = new Button(this); send.setText("发送文字"); root.addView(send);
        send.setOnClickListener(v -> {
            String text = typing.getText().toString();
            try { sendJson(8, new JSONObject().put("text", new JSONObject().put("_0", text))); typing.setText(""); }
            catch (Exception e) { show("输入失败: " + e.getMessage()); }
        });
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setText(value); root.addView(e); return e;
    }
    private void show(String text) { ui.post(() -> status.setText(text)); }
    private void connect() { start(true); }
    private void start(boolean pairing) {
        String address = host.getText().toString().trim();
        String password = code.getText().toString();
        int number;
        try { number = Integer.parseInt(port.getText().toString()); if (address.isEmpty() || number < 1 || number > 65535) throw new IllegalArgumentException(); }
        catch (Exception e) { show("请填写有效地址和端口。"); return; }
        if (running) { show("已有连接，请退出后重试。"); return; }
        running = true; getPreferences(0).edit().putString("host", address).apply();
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
                    ui.post(() -> code.setText(""));
                } else {
                    String saved = getPreferences(0).getString(pinKey, null);
                    if (saved == null) throw new IllegalStateException("请先配对这台 Mac。");
                    pin = Base64.decode(saved, Base64.NO_WRAP);
                }
                show("正在连接…");
                try (SillConnection session = SillConnection.connect(address, number, "sill/1", pin)) {
                    connection = session; session.sessionTimeout();
                    sendJson(23, new JSONObject().put("protocol", 1).put("appVersion", "0.6.0").put("device", android.os.Build.MODEL));
                    show("已连接，选择一个窗口或桌面。");
                    while (running) handle(session.read());
                }
            } catch (Exception e) { show("连接结束: " + e.getMessage()); }
            finally { connection = null; running = false; releaseDecoder(); }
        });
    }
    private void sendJson(int kind, JSONObject value) throws Exception {
        synchronized (outputLock) {
            SillConnection c = connection;
            if (c == null) throw new IllegalStateException("尚未连接");
            c.send(kind, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    private void handle(StreamWire.Message message) throws Exception {
        switch (message.kind()) {
            case 2 -> {
                JSONObject list = new JSONObject(new String(message.payload(), StandardCharsets.UTF_8));
                JSONArray windows = list.getJSONArray("windows");
                ui.post(() -> {
                    choices.removeAllViews();
                    addChoice("整个桌面", new JSONObject());
                    for (int i = 0; i < windows.length(); i++) {
                        JSONObject item = windows.optJSONObject(i); if (item == null) continue;
                        try { addChoice(item.optString("appName") + " — " + item.optString("title"), new JSONObject().put("_0", item.getLong("id"))); }
                        catch (Exception ignored) { }
                    }
                });
            }
            case 0 -> {
                Hevc.Parameters params = Hevc.parameters(message.payload()); nalSize = params.lengthSize();
                releaseDecoder(); Surface target = surface;
                if (target == null || !target.isValid()) throw new IllegalStateException("视频画面不可用");
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080);
                format.setByteBuffer("csd-0", ByteBuffer.wrap(params.codecConfig()));
                decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
                decoder.configure(format, target, null, 0); decoder.start();
            }
            case 1 -> {
                if (decoder == null) return;
                byte[] frame = Hevc.frame(message.payload(), nalSize);
                int index = decoder.dequeueInputBuffer(10_000);
                if (index >= 0) {
                    ByteBuffer input = decoder.getInputBuffer(index); input.clear();
                    if (frame.length > input.remaining()) throw new IllegalStateException("视频帧超过解码缓冲区");
                    input.put(frame); decoder.queueInputBuffer(index, 0, frame.length, (long) (message.timestamp() * 1_000_000), 0);
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int out; while ((out = decoder.dequeueOutputBuffer(info, 0)) >= 0) decoder.releaseOutputBuffer(out, true);
            }
            case 22 -> throw new IllegalStateException(new String(message.payload(), StandardCharsets.UTF_8));
        }
    }
    private void addChoice(String title, JSONObject source) {
        Button button = new Button(this); button.setText(title); choices.addView(button);
        button.setOnClickListener(v -> {
            try { sendJson(6, new JSONObject().put(source.length() == 0 ? "desktop" : "window", source)); }
            catch (Exception e) { show("选择窗口失败: " + e.getMessage()); }
        });
    }
    private void releaseDecoder() { if (decoder != null) { try { decoder.stop(); } catch (Exception ignored) { } decoder.release(); decoder = null; } }
    @Override protected void onDestroy() {
        running = false; SillConnection c = connection; if (c != null) try { c.close(); } catch (Exception ignored) { }
        network.shutdownNow(); super.onDestroy();
    }
}
