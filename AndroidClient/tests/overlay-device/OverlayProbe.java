package io.github.n2ray.sill;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ThreadPoolExecutor;

/** Runs only in the isolated probe package, with a fake session and camera/network disabled. */
public final class OverlayProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private static Field field(String name) throws Exception { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f; }
    private static Object get(MainActivity a, String name) throws Exception { return field(name).get(a); }
    private static void call(MainActivity a, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = MainActivity.class.getDeclaredMethod(name, types); m.setAccessible(true); m.invoke(a, args);
    }
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    private void onUi(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError("main-thread probe failed", failure.get());
    }
    private static PairLink link() {
        return PairLink.parse("sill://pair?v=1&m=0000000000000000&k=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&s=AQEBAQEBAQEBAQEBAQEBAQ&p=7455&a=192.0.2.1");
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); MainActivity activity = null;
        int resultCode = Activity.RESULT_CANCELED;
        try {
            OverlayPairingAuthorization auth = new OverlayPairingAuthorization();
            Object oldSession = new Object(), newSession = new Object(); byte[] pin = new byte[32];
            auth.session(oldSession, pin);
            var oldAttempt = auth.begin(oldSession, pin, 100, 0);
            final boolean[] closed = {false};
            auth.track(oldAttempt, () -> closed[0] = true, 1);
            auth.session(newSession, pin);
            check(closed[0] && !auth.commit(oldAttempt, pin, 2, () -> { throw new AssertionError("stale callback saved"); }),
                    "old callback or fake socket survived new session");
            Intent intent = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity) startActivitySync(intent);
            MainActivity a = activity;
            AlertDialog[] dialogs = new AlertDialog[2];
            PairLink[] currentLink = new PairLink[1];
            onUi(() -> { try {
                field("connection").set(a, SillConnection.overlayProbeSession());
                currentLink[0] = link();
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, currentLink[0]);
                dialogs[0] = (AlertDialog) get(a, "overlayPairDialog");
                check(dialogs[0] != null && dialogs[0].isShowing(), "overlay did not open");
                Method allowed = MainActivity.class.getDeclaredMethod("allowedForTour", int.class, org.json.JSONObject.class); allowed.setAccessible(true);
                check(!(Boolean) allowed.invoke(a, 8, new org.json.JSONObject().put("text", "synthetic")), "input not blocked");
                check(!(Boolean) allowed.invoke(a, 17, new org.json.JSONObject().put("bitrate", 1)), "setting not blocked");
                call(a, "closeOverlayPair", new Class<?>[0]);
                currentLink[0] = link();
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, currentLink[0]);
                dialogs[1] = (AlertDialog) get(a, "overlayPairDialog");
                dialogs[0].getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            onUi(() -> { try {
                check(dialogs[1] == get(a, "overlayPairDialog") && dialogs[1].isShowing(), "old dialog affected new overlay");
                ((ThreadPoolExecutor) get(a, "qrNetwork")).shutdown();
                dialogs[1].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            onUi(() -> { try {
                check(get(a, "overlayAttempt") == null && get(a, "overlayLink") != null, "rejected worker lost retry link");
                check(currentLink[0].secret()[0] == 1, "retry link secret cleared too early");
                check(dialogs[1].getButton(AlertDialog.BUTTON_POSITIVE).isEnabled(), "retry button disabled");
                ((AlertDialog) get(a, "overlayPairDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            onUi(() -> { try {
                check(get(a, "overlayPairDialog") == null, "cancel button did not close overlay");
                for (byte b : currentLink[0].secret()) check(b == 0, "cancel left pairing secret");
                check(!(Boolean) get(a, "overlayPairBlocked"), "cancel did not unblock");
                Method allowed = MainActivity.class.getDeclaredMethod("allowedForTour", int.class, org.json.JSONObject.class); allowed.setAccessible(true);
                check((Boolean) allowed.invoke(a, 8, new org.json.JSONObject().put("text", "synthetic")), "input not restored");
                check((Boolean) allowed.invoke(a, 17, new org.json.JSONObject().put("bitrate", 1)), "setting not restored");
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, link());
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { callActivityOnPause(a); callActivityOnStop(a); });
            waitForIdleSync();
            check(get(activity, "overlayPairDialog") == null, "background did not close overlay");
            result.putString("result", "PASS: synthetic overlay"); resultCode = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
        } finally { if (activity != null) {
            MainActivity ending = activity;
            try { onUi(ending::finish); } catch (Throwable error) {
                resultCode = Activity.RESULT_CANCELED;
                result.putString("result", "FAIL: activity cleanup " + error.getClass().getSimpleName());
            }
        } }
        finish(resultCode, result);
    }
}
