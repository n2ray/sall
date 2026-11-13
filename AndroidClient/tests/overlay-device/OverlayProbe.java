package io.github.n2ray.sall;

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
        if (failure.get() != null) {
            Throwable cause = failure.get();
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            throw new AssertionError("main-thread probe: " + cause.getClass().getSimpleName() + ": " + String.valueOf(cause.getMessage()), cause);
        }
    }
    private static PairLink link() {
        return PairLink.parse("sill://pair?v=1&m=0000000000000000&k=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&s=AQEBAQEBAQEBAQEBAQEBAQ&p=7455&a=192.0.2.1");
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); MainActivity activity = null;
        int resultCode = Activity.RESULT_CANCELED;
        try {
            java.io.ByteArrayOutputStream wire = new java.io.ByteArrayOutputStream();
            StreamWire.write(wire, 20, "{\"ok\":false,\"reason\":\"shown\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            java.io.ByteArrayOutputStream request = new java.io.ByteArrayOutputStream();
            check("shown".equals(SillConnection.homePairingCommand(new java.io.ByteArrayInputStream(wire.toByteArray()), request, "ask", "Test", "Synthetic").reason()), "ask result");
            StreamWire.Message frame = StreamWire.read(new java.io.ByteArrayInputStream(request.toByteArray()));
            org.json.JSONObject ask = new org.json.JSONObject(new String(frame.payload(), java.nio.charset.StandardCharsets.UTF_8));
            check(frame.kind() == 19 && ask.getInt("v") == 1 && ask.getString("method").equals("ask") && ask.getString("proof").isEmpty(), "ask frame");
            wire.reset(); request.reset(); StreamWire.write(wire, 20, "{\"ok\":false,\"reason\":\"closed\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            check("closed".equals(SillConnection.homePairingCommand(new java.io.ByteArrayInputStream(wire.toByteArray()), request, "cancel", "Test", "Synthetic").reason()), "cancel result");
            wire.reset(); StreamWire.write(wire, 20, "{\"ok\":true,\"method\":\"cable\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try { SillConnection.homePairingCommand(new java.io.ByteArrayInputStream(wire.toByteArray()), request, "ask", "Test", "Synthetic"); throw new AssertionError("unverified cable success accepted"); }
            catch (java.io.IOException expected) { }
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
            onUi(() -> { try {
                call(a, "setAddingMac", new Class<?>[]{boolean.class, boolean.class}, true, true);
                android.widget.LinearLayout typed = (android.widget.LinearLayout) get(a, "typedPairing");
                for (int i = 0; i < typed.getChildCount(); i++) {
                    android.view.View child = typed.getChildAt(i);
                    check(!(child instanceof android.widget.Button b) || !"连接已配对的 Mac".contentEquals(b.getText()),
                            "duplicate legacy connect button remains");
                }
                call(a, "layoutPairingCard", new Class<?>[]{boolean.class}, true);
                check(((android.view.View) get(a, "pairLinkAction")).getParent() == get(a, "pairDetails"),
                        "landscape link escaped the details column");
                call(a, "layoutPairingCard", new Class<?>[]{boolean.class}, false);
                check(((android.view.View) get(a, "pairLinkAction")).getParent() == get(a, "addMacCard"),
                        "portrait link was lost after rotation");
                call(a, "setAddingMac", new Class<?>[]{boolean.class, boolean.class}, false, false);
            } catch (Exception e) { throw new RuntimeException(e); } });
            onUi(() -> { try {
                field("connection").set(a, SillConnection.overlayProbeSession());
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, link());
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            onUi(() -> { try {
                AlertDialog dialog = (AlertDialog) get(a, "overlayPairDialog");
                check(dialog != null && dialog.isShowing(), "synthetic confirmation not shown");
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getCurrentTextColor() == 0xff9bb7ff,
                        "dialog action does not use the accent color");
                check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).getCurrentTextColor() == 0xff101215,
                        "dialog primary action is not styled");
                android.view.View decor = dialog.getWindow().getDecorView();
                check(decor.getWidth() > 0 && decor.getHeight() > 0, "dialog has no layout");
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                decor.draw(new android.graphics.Canvas(bitmap));
                try (java.io.FileOutputStream out = a.openFileOutput("synthetic-dialog.png", 0)) {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out), "dialog image failed");
                } finally { bitmap.recycle(); }
                call(a, "closeOverlayPair", new Class<?>[0]);
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            AlertDialog[] dialogs = new AlertDialog[2];
            PairLink[] currentLink = new PairLink[1], differentLink = new PairLink[1];
            Object[] originalSession = new Object[1];
            onUi(() -> { try {
                field("connection").set(a, SillConnection.overlayProbeSession()); originalSession[0] = get(a, "connection");
                @SuppressWarnings("unchecked") SessionMacInfoCache<SignedMacInfo.Update> cache = (SessionMacInfoCache<SignedMacInfo.Update>) get(a, "verifiedSessionInfo");
                byte[] sessionPin = new byte[32]; cache.bind(originalSession[0], sessionPin);
                SignedMacInfo.Update info = new SignedMacInfo.Update(PairLink.macId(sessionPin), "Synthetic", 1000, true,
                        java.util.List.of(new SavedMacs.Endpoint("100.64.0.2", 7455)), java.util.List.of("vpn"));
                cache.offer(originalSession[0], sessionPin, info.issuedAt(), info);
                String syntheticQr = "sill://pair?v=1&m=0000000000000000&k=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&s=AQEBAQEBAQEBAQEBAQEBAQ&p=7455&a=192.0.2.1";
                field("sessionScanOwner").set(a, SillConnection.overlayProbeSession());
                Intent staleScan = new Intent().putExtra(PairScannerActivity.RESULT_LINK, syntheticQr);
                call(a, "onActivityResult", new Class<?>[]{int.class, int.class, Intent.class}, 102, Activity.RESULT_OK, staleScan);
                check(get(a, "overlayPairDialog") == null && !staleScan.hasExtra(PairScannerActivity.RESULT_LINK), "stale scanner result was used");
                field("sessionScanOwner").set(a, originalSession[0]);
                Intent validScan = new Intent().putExtra(PairScannerActivity.RESULT_LINK, syntheticQr);
                call(a, "onActivityResult", new Class<?>[]{int.class, int.class, Intent.class}, 102, Activity.RESULT_OK, validScan);
                check(get(a, "overlayPairDialog") != null && !validScan.hasExtra(PairScannerActivity.RESULT_LINK), "active-session scan did not open confirmation");
                call(a, "closeOverlayPair", new Class<?>[0]);
                call(a, "openPairLinkInput", new Class<?>[0]);
                android.widget.EditText pasteInput = (android.widget.EditText) get(a, "pairLinkInput");
                check(pasteInput != null, "production paste input missing");
                pasteInput.setText("sill://pair?v=1&m=0000000000000000&k=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&s=AQEBAQEBAQEBAQEBAQEBAQ&p=7455&a=192.0.2.1");
                ((AlertDialog) get(a, "pairLinkInputDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                check(get(a, "pairLinkInput") == null && pasteInput.length() == 0, "paste input not cleared");
                check(get(a, "overlayPairDialog") != null, "paste did not open confirmation");
                call(a, "closeOverlayPair", new Class<?>[0]);
                byte[] other = new byte[32]; other[0] = 1;
                String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(other);
                PairLink different = PairLink.parse("sill://pair?v=1&m=" + PairLink.macId(other) + "&k=" + encoded
                        + "&s=AQEBAQEBAQEBAQEBAQEBAQ&p=7455&a=192.0.2.1");
                differentLink[0] = different;
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, different);
                AlertDialog otherDialog = (AlertDialog) get(a, "overlayPairDialog");
                check(otherDialog != null && otherDialog.isShowing() && get(a, "overlayLink") == different, "different Mac confirmation missing");
                check(get(a, "connection") != null, "different Mac changed the current stream");
                otherDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitForIdleSync();
            onUi(() -> { try {
                check(get(a, "overlayPairDialog") == null, "different Mac cancellation failed");
                for (byte value : differentLink[0].secret()) check(value == 0, "different Mac secret retained");
                byte[] other = new byte[32]; other[0] = 1;
                String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(other);
                differentLink[0] = PairLink.parse("sill://pair?v=1&m=" + PairLink.macId(other) + "&k=" + encoded
                        + "&s=AQEBAQEBAQEBAQEBAQEBAQ&p=7455&a=192.0.2.1");
                ((android.view.View) get(a, "unpairedIndicator")).setVisibility(android.view.View.VISIBLE);
                SillConnection.overlayProbeFakePair = true;
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, differentLink[0]);
                ((AlertDialog) get(a, "overlayPairDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            long until = android.os.SystemClock.elapsedRealtime() + 5000;
            while (((SavedMacStore) get(a, "savedMacStore")).all().isEmpty() && android.os.SystemClock.elapsedRealtime() < until)
                android.os.SystemClock.sleep(50);
            waitForIdleSync();
            onUi(() -> { try {
                check(((SavedMacStore) get(a, "savedMacStore")).all().size() == 1, "different Mac was not saved");
                check(get(a, "connection") == originalSession[0], "successful pairing changed current session");
                check(((android.view.View) get(a, "unpairedIndicator")).getVisibility() == android.view.View.VISIBLE,
                        "pairing another Mac removed current session trust warning");
                check(get(a, "overlayPairDialog") == null, "success did not close overlay");
                check(((SavedMacStore) get(a, "savedMacStore")).all().get(0).advertised().isEmpty(), "current Mac addresses leaked to another Mac");
                currentLink[0] = link();
                call(a, "openOverlayPair", new Class<?>[]{PairLink.class}, currentLink[0]);
                ((AlertDialog) get(a, "overlayPairDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            long secondUntil = android.os.SystemClock.elapsedRealtime() + 5000;
            while (((SavedMacStore) get(a, "savedMacStore")).all().size() < 2 && android.os.SystemClock.elapsedRealtime() < secondUntil)
                android.os.SystemClock.sleep(50);
            waitForIdleSync();
            onUi(() -> { try {
                SavedMacs.Mac current = ((SavedMacStore) get(a, "savedMacStore")).all().stream()
                        .filter(m -> java.security.MessageDigest.isEqual(m.identity().pin(), new byte[32])).findFirst().orElseThrow();
                check(current.advertised().stream().anyMatch(x -> x.endpoint().host().equals("100.64.0.2")), "verified current-session addresses not saved");
                check(get(a, "connection") == originalSession[0], "same-Mac pairing changed session");
                check(((android.view.View) get(a, "unpairedIndicator")).getVisibility() == android.view.View.GONE, "same-Mac pairing kept unpaired warning");
                SillConnection.overlayProbeFakePair = false;
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
