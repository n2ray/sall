package io.github.n2ray.sall;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Runs only in a random, isolated test package with its own identity and no saved Macs. */
public final class OpenProbe extends Instrumentation {
    private boolean inspect;
    @Override public void onCreate(Bundle args) { super.onCreate(args); inspect = "inspect".equals(args.getString("mode")); start(); }
    private static Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
    }
    private void main(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("UI action failed", failure.get());
    }
    private static void check(boolean condition, String text) { if (!condition) throw new AssertionError(text); }
    @Override public void onStart() {
        MainActivity activity = null; Bundle result = new Bundle(); int code = Activity.RESULT_CANCELED;
        try {
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity app = activity;
            if (inspect) {
                long end = SystemClock.elapsedRealtime() + 30000; boolean[] done = {false}; String[] summary = {"no discovery"};
                while (!done[0] && SystemClock.elapsedRealtime() < end) {
                    main(() -> { try {
                        @SuppressWarnings("unchecked") Map<String, View> rows = (Map<String, View>) field(app, "nearby");
                        if (rows.size() != 1) return;
                        String name = rows.keySet().iterator().next(); MacDiscovery discovery = (MacDiscovery) field(app, "discovery");
                        Field networks = MacDiscovery.class.getDeclaredField("networks"); networks.setAccessible(true);
                        android.net.Network raw = ((Map<String, android.net.Network>) networks.get(discovery)).get(name);
                        android.net.Network safe = discovery.localNetwork(name);
                        String binding = "not-attempted";
                        if (safe != null) try (java.net.Socket socket = new java.net.Socket()) { safe.bindSocket(socket); binding = "ok"; }
                        catch (Exception e) { String m = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT); binding = e.getClass().getSimpleName() + "-" + (m.contains("eacces") || m.contains("permission denied") ? "permission" : m.contains("eperm") || m.contains("operation not permitted") ? "not-permitted" : m.contains("enonet") || m.contains("not on network") ? "not-on-network" : m.contains("enonet") || m.contains("no such network") ? "no-network" : m.contains("enodev") ? "no-device" : m.contains("einval") || m.contains("invalid") ? "invalid" : m.contains("ebadf") || m.contains("bad file") ? "bad-fd" : m.contains("closed") ? "closed" : "other"); }
                        String initialized = "not-attempted";
                        if (safe != null) try (java.net.Socket socket = new java.net.Socket()) {
                            socket.bind(new java.net.InetSocketAddress(0)); safe.bindSocket(socket); initialized = "ok";
                        } catch (Exception e) {
                            String m = String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
                            initialized = e.getClass().getSimpleName() + "-" + (m.contains("eacces") || m.contains("permission denied") ? "permission" : m.contains("eperm") || m.contains("operation not permitted") ? "not-permitted" : m.contains("enonet") || m.contains("not on network") ? "not-on-network" : m.contains("no such network") ? "no-network" : m.contains("enodev") ? "no-device" : m.contains("einval") || m.contains("invalid") ? "invalid" : m.contains("ebadf") || m.contains("bad file") ? "bad-fd" : m.contains("closed") ? "closed" : "other");
                        }
                        summary[0] = "raw-network=" + (raw != null) + " safe-network=" + (safe != null) + " bind=" + binding + " initialized-bind=" + initialized;
                        done[0] = true;
                    } catch (Exception e) { throw new RuntimeException(e); } });
                    if (!done[0]) SystemClock.sleep(300);
                }
                check(done[0], "one local Mac was not discovered");
                result.putString("result", "PASS: inspect " + summary[0]); code = Activity.RESULT_OK;
                finish(code, result); return;
            }
            long until = SystemClock.elapsedRealtime() + 30000; boolean[] selected = {false};
            while (!selected[0] && SystemClock.elapsedRealtime() < until) {
                main(() -> { try {
                    @SuppressWarnings("unchecked") Map<String, View> rows = (Map<String, View>) field(app, "nearby");
                    MacDiscovery discovery = (MacDiscovery) field(app, "discovery");
                    if (rows.size() != 1) return;
                    String name = rows.keySet().iterator().next();
                    if (discovery.door(name) != HomeDiscoveryMetadata.Door.TLS_OPEN) return;
                    check(((SavedMacStore) field(app, "savedMacStore")).all().isEmpty(), "unexpected saved identity");
                    selected[0] = rows.get(name).performClick();
                } catch (Exception e) { throw new RuntimeException(e); } });
                if (!selected[0]) SystemClock.sleep(300);
            }
            check(selected[0], "one open local TLS Mac was not discovered");
            until = SystemClock.elapsedRealtime() + 45000; boolean[] connected = {false};
            while (!connected[0] && SystemClock.elapsedRealtime() < until) {
                main(() -> { try {
                    connected[0] = field(app, "connection") != null
                            && ((View) field(app, "unpairedIndicator")).getVisibility() == View.VISIBLE
                            && (boolean) field(app, "picturePresented");
                } catch (Exception e) { throw new RuntimeException(e); } });
                if (!connected[0]) SystemClock.sleep(300);
            }
            check(connected[0], "open TLS session or presented picture missing");
            check(((SavedMacStore) field(app, "savedMacStore")).all().isEmpty(), "unpaired session was saved");
            main(() -> { try { Method stop = MainActivity.class.getDeclaredMethod("disconnect"); stop.setAccessible(true); stop.invoke(app); }
                catch (Exception e) { throw new RuntimeException(e); } });
            result.putString("result", "PASS: real local open TLS, presented frame, no saved identity"); code = Activity.RESULT_OK;
        } catch (Throwable failure) { result.putString("result", "FAIL: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()); }
        finally { if (activity != null) { MainActivity closing = activity; try { main(closing::finish); } catch (Throwable ignored) { } } }
        finish(code, result);
    }
}
