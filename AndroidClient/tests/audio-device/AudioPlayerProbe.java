package io.github.n2ray.sall;

import android.util.Base64;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Real AudioPlayer on the device, with locally encoded silence and a muted track. */
final class AudioPlayerProbe {
    static String run(Properties fixture) throws Exception {
        for (int session = 0; session < 2; session++) runSession(fixture, session);
        return "PASS: two fresh sessions, clock recovery, mute, resume and malformed packets";
    }

    private static void runSession(Properties f, int session) throws Exception {
        AtomicReference<String> error = new AtomicReference<>();
        AudioPlayer player = new AudioPlayer(error::set);
        try {
            player.setRemote(true);
            JSONObject format = new JSONObject().put("codec", "aac-eld").put("sampleRate", 48000)
                    .put("channels", 2).put("framesPerPacket", 480)
                    .put("primingFrames", Integer.parseInt(f.getProperty("priming480")))
                    .put("epoch", 1).put("cookie", f.getProperty("cookie480"));
            byte[] json = format.toString().getBytes(StandardCharsets.UTF_8);
            byte[] header = new byte[json.length + 1]; header[0] = 1; System.arraycopy(json, 0, header, 1, json.length);
            long start = System.nanoTime(); double wall = System.currentTimeMillis() / 1000.0;
            player.receive(header, wall, start);
            for (int i = 0; i < 160; i++) {
                while (System.nanoTime() - start < i * 10_000_000L) Thread.sleep(1);
                long now = System.nanoTime();
                if (i % 2 == 0) player.videoFrame(wall + i * .01, now);
                if (session == 1 && i == 40) player.setPlaybackEnabled(false);
                if (session == 1 && i == 65) player.setPlaybackEnabled(true);
                if (session == 0 && i == 80) {
                    var workerField = AudioPlayer.class.getDeclaredField("worker"); workerField.setAccessible(true);
                    var runtimeField = AudioPlayer.class.getDeclaredField("runtime"); runtimeField.setAccessible(true);
                    ScheduledExecutorService worker = (ScheduledExecutorService) workerField.get(player);
                    AudioRuntime runtime = (AudioRuntime) runtimeField.get(player);
                    worker.submit(() -> {
                        try {
                            var clockField = AudioRuntime.class.getDeclaredField("clock"); clockField.setAccessible(true);
                            AudioPresentationClock clock = (AudioPresentationClock) clockField.get(runtime);
                            if (clock == null) throw new IllegalStateException("clock not opened");
                            clock.reset(System.nanoTime() - 2_000_000_000L);
                        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
                    }).get(5, TimeUnit.SECONDS);
                }
                if (i == 90) {
                    player.receive(new byte[]{2, 0, 1}, wall + i * .01, now);
                    player.receive(new byte[16385], wall + i * .01, now);
                }
                byte[] packet = Base64.decode(f.getProperty("packet480." + i), Base64.DEFAULT);
                byte[] payload = ByteBuffer.allocate(11 + packet.length).put((byte) 2).putShort((short) 1)
                        .putInt(i).put((byte) (i == 0 ? 1 : 0)).put((byte) 1)
                        .putShort((short) packet.length).put(packet).array();
                player.receive(payload, wall + i * .01, now);
            }
            Thread.sleep(1200);
            var workerField = AudioPlayer.class.getDeclaredField("worker"); workerField.setAccessible(true);
            var runtimeField = AudioPlayer.class.getDeclaredField("runtime"); runtimeField.setAccessible(true);
            ScheduledExecutorService worker = (ScheduledExecutorService) workerField.get(player);
            AudioRuntime runtime = (AudioRuntime) runtimeField.get(player);
            String diagnostics = worker.submit(runtime::diagnostics).get(5, TimeUnit.SECONDS);
            if (error.get() != null || diagnostics == null || !diagnostics.contains("clock=READY"))
                throw new IllegalStateException("session " + session + ": " + error.get() + "; " + diagnostics);
            if (session == 0) {
                var recoveryField = AudioPlayer.class.getDeclaredField("clockRecovery"); recoveryField.setAccessible(true);
                var attemptsField = AudioRecoveryPolicy.class.getDeclaredField("attempts"); attemptsField.setAccessible(true);
                if (((java.util.ArrayDeque<?>) attemptsField.get(recoveryField.get(player))).size() != 1)
                    throw new IllegalStateException("clock recovery was not exercised");
            }
        } finally { player.close(); }
    }
}
