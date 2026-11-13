package io.github.n2ray.sall;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.security.keystore.UserNotAuthenticatedException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Synthetic data only, in a run-unique namespace; never reads app previews or preferences. */
public final class PreviewProbe extends Instrumentation {
    private String phase, token, ownerNonce, authMode;
    private long startedAt;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); phase = args.getString("phase", ""); token = args.getString("token", "");
        ownerNonce = args.getString("owner", ""); authMode = args.getString("auth_mode", "mixed"); start();
    }
    @Override public void onStart() {
        startedAt = SystemClock.elapsedRealtime();
        Bundle result = new Bundle();
        try {
            if (!token.matches("[0-9a-f]{32}") || !ownerNonce.matches("[0-9a-f]{32}"))
                throw new AssertionError("invalid synthetic namespace");
            if (!authMode.equals("mixed") && !authMode.equals("credential")) throw new AssertionError("invalid auth mode");
            Context context = getTargetContext();
            AndroidConnectionPreviewBackend backend = AndroidConnectionPreviewBackend.probe(context, token, phase.equals("prepare"));
            byte[] pin = MessageDigest.getInstance("SHA-256").digest(("preview-probe:" + token).getBytes(StandardCharsets.US_ASCII));
            String identity = ConnectionPreviewPolicy.fingerprintName(pin);
            String namespace = "sill-preview-probe-" + token;
            File ownerDir = new File(context.getNoBackupFilesDir(), "sill-preview-probe-owners");
            File owner = new File(ownerDir, token);
            File created = new File(ownerDir, token + ".created");
            String alias = namespace + "-" + identity;
            if (phase.equals("claim")) {
                if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                if (created.exists() || backend.key(identity, false) != null || backend.tombstone(identity) || backend.read(identity, 2_000_032) != null)
                    throw new AssertionError("synthetic namespace occupied");
                if ((!ownerDir.isDirectory() && !ownerDir.mkdirs()) || !owner.createNewFile()) throw new AssertionError("namespace claim failed");
                try (java.io.FileOutputStream marker = new java.io.FileOutputStream(owner)) {
                    marker.write(ownerNonce.getBytes(StandardCharsets.US_ASCII)); marker.getFD().sync();
                }
            } else {
                if (!owner.exists() && phase.equals("cleanup")) {
                    finishResult(result, "PASS: synthetic preview cleanup", Activity.RESULT_OK); return;
                }
                if (!owned(owner, ownerNonce)) throw new AssertionError("namespace ownership not verified");
                if (phase.equals("prepare")) {
                    if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                    if (created.exists() || backend.key(identity, false) != null || backend.key(identity, true) == null)
                        throw new AssertionError("synthetic key creation failed");
                    long creationTime = creationTime(alias);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(created);
                         java.io.DataOutputStream data = new java.io.DataOutputStream(out)) {
                        data.writeLong(creationTime); data.flush(); out.getFD().sync();
                    }
                } else if (phase.equals("auth")) {
                    if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                    verifyKey(alias, created);
                    PreviewAuthActivity.prepare();
                    Intent intent = new Intent(context, PreviewAuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra("credential_only", authMode.equals("credential"));
                    Activity prompt = startActivitySync(intent);
                    if (!PreviewAuthActivity.await()) {
                        runOnMainSync(prompt::finish);
                        finishResult(result, "SKIP: authentication timed out", Activity.RESULT_OK); return;
                    }
                    result.putString("auth_type", PreviewAuthActivity.authenticationType());
                    if (!PreviewAuthActivity.exceptionType().isEmpty()) result.putString("exception", PreviewAuthActivity.exceptionType());
                    if (!PreviewAuthActivity.accepted()) {
                        finishResult(result, "SKIP: authentication cancelled or unavailable", Activity.RESULT_OK); return;
                    }
                    verifyKey(alias, created);
                    long cryptoStart = SystemClock.elapsedRealtime();
                    try {
                        SecretKey key = backend.key(identity, false);
                        if (key == null) throw new IllegalStateException();
                        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding");
                        encrypt.init(Cipher.ENCRYPT_MODE, key);
                        byte[] synthetic = sample();
                        byte[] ciphertext = encrypt.doFinal(synthetic);
                        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding");
                        decrypt.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, encrypt.getIV()));
                        byte[] actual = decrypt.doFinal(ciphertext);
                        boolean matches = Arrays.equals(synthetic, actual);
                        Arrays.fill(synthetic, (byte) 0); Arrays.fill(actual, (byte) 0);
                        if (!matches) throw new IllegalStateException();
                    } catch (Exception failure) {
                        result.putLong("crypto_ms", SystemClock.elapsedRealtime() - cryptoStart);
                        result.putString("exception", failure.getClass().getSimpleName());
                        finishResult(result, "SAME_PROCESS_FAILED", Activity.RESULT_OK); return;
                    }
                    result.putLong("crypto_ms", SystemClock.elapsedRealtime() - cryptoStart);
                } else if (phase.equals("write")) {
                    if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                    verifyKey(alias, created);
                    try {
                        SecretKey key = backend.key(identity, false);
                        if (key == null) throw new AssertionError("synthetic key unavailable");
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
                        byte[] sealed = cipher.doFinal(new byte[]{0}); Arrays.fill(sealed, (byte) 0);
                    }
                    catch (UserNotAuthenticatedException needAuth) {
                        result.putString("exception", needAuth.getClass().getSimpleName());
                        finishResult(result, "AUTH_REQUIRED: recent strong authentication required", Activity.RESULT_OK); return;
                    }
                    ConnectionPreviewStore store = new ConnectionPreviewStore(backend);
                    store.bind(1, pin); store.context(true, true);
                    ConnectionPreviewPolicy.Ticket ticket = store.begin(1, pin, true);
                    byte[] sample = sample();
                    if (ticket == null || !store.save(ticket, 1, pin, true, true, sample)) throw new AssertionError("encrypted write failed");
                    byte[] ciphertext = backend.read(identity, 2_000_032);
                    if (ciphertext == null || contains(ciphertext, sample)) throw new AssertionError("plaintext present in cache");
                    Arrays.fill(sample, (byte) 0);
                } else if (phase.equals("crash_before") || phase.equals("crash_after")) {
                    if (!backend.available()) throw new AssertionError("device locked");
                    verifyKey(alias, created);
                    ConnectionPreviewStore store = new ConnectionPreviewStore(backend);
                    store.bind(3, pin); store.context(true, true);
                    ConnectionPreviewPolicy.Ticket ticket = store.begin(3, pin, true);
                    AndroidConnectionPreviewBackend.probeCrashPoint = phase.substring("crash_".length());
                    AndroidConnectionPreviewBackend.probeCrashMarker = new File(ownerDir, token + "." + AndroidConnectionPreviewBackend.probeCrashPoint);
                    store.save(ticket, 3, pin, true, true, "new synthetic bytes".getBytes(StandardCharsets.US_ASCII));
                    throw new AssertionError("crash hook did not terminate process");
                } else if (phase.equals("verify_before") || phase.equals("verify_after")) {
                    String point = phase.substring("verify_".length());
                    if (!new File(ownerDir, token + "." + point).isFile()) throw new AssertionError("crash marker missing");
                    ConnectionPreviewStore store = new ConnectionPreviewStore(backend);
                    store.bind(4, pin); store.context(true, true);
                    byte[] actual = store.load(pin, true);
                    byte[] expected = point.equals("before") ? sample() : "new synthetic bytes".getBytes(StandardCharsets.US_ASCII);
                    if (!Arrays.equals(actual, expected)) throw new AssertionError("atomic recovery failed");
                    Arrays.fill(actual, (byte) 0); Arrays.fill(expected, (byte) 0);
                    if (point.equals("after")) {
                        ConnectionPreviewPolicy.Ticket ticket = store.begin(4, pin, true);
                        byte[] reset = sample();
                        if (!store.save(ticket, 4, pin, true, true, reset)) throw new AssertionError("reset failed");
                        Arrays.fill(reset, (byte) 0);
                    }
                } else if (phase.equals("read")) {
                    if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                    verifyKey(alias, created);
                    ConnectionPreviewStore store = new ConnectionPreviewStore(backend);
                    store.bind(2, pin); store.context(true, true);
                    byte[] actual = store.load(pin, true), expected = sample();
                    if (!Arrays.equals(actual, expected)) throw new AssertionError("cross-process read failed");
                    Arrays.fill(actual, (byte) 0); Arrays.fill(expected, (byte) 0);
                    store.context(true, false);
                    if (store.load(pin, true) != null) throw new AssertionError("background read allowed");
                    store.context(true, true);
                    if (!store.forget(pin) || store.load(pin, true) != null || !backend.tombstone(identity))
                        throw new AssertionError("forget failed");
                } else if (phase.equals("cleanup")) {
                    backend.deleteKey(identity); backend.delete(identity); backend.unmark(identity);
                    KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
                    if (keys.containsAlias(alias) || backend.tombstone(identity)
                            || backend.read(identity, 2_000_032) != null) throw new AssertionError("synthetic data remains");
                    File cache = new File(context.getCacheDir(), namespace);
                    File markers = new File(context.getNoBackupFilesDir(), namespace);
                    if ((cache.exists() && !cache.delete()) || (markers.exists() && !markers.delete())
                            || (created.exists() && !created.delete()) || !owner.delete())
                        throw new AssertionError("synthetic namespace cleanup failed");
                    for (String point : new String[]{"before", "after"}) {
                        File marker = new File(ownerDir, token + "." + point);
                        if (marker.exists() && !marker.delete()) throw new AssertionError("crash marker cleanup failed");
                    }
                    ownerDir.delete();
                } else throw new AssertionError("invalid phase");
            }
            finishResult(result, "PASS: synthetic preview " + phase, Activity.RESULT_OK);
        } catch (Throwable failure) {
            result.putString("exception", failure.getClass().getSimpleName());
            finishResult(result, "FAIL: " + failure.getClass().getSimpleName(), Activity.RESULT_CANCELED);
        }
    }
    private void finishResult(Bundle result, String value, int code) { result.putLong("phase_ms", SystemClock.elapsedRealtime() - startedAt); result.putString("result", value); finish(code, result); }
    private static long creationTime(String alias) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        java.util.Date created = keys.getCreationDate(alias);
        if (created == null) throw new AssertionError("synthetic key missing");
        return created.getTime();
    }
    private static void verifyKey(String alias, File created) throws Exception {
        if (!created.isFile() || created.length() != Long.BYTES) throw new AssertionError("synthetic key marker missing");
        long expected;
        try (java.io.DataInputStream input = new java.io.DataInputStream(new java.io.FileInputStream(created))) { expected = input.readLong(); }
        if (creationTime(alias) != expected) throw new AssertionError("synthetic key changed");
    }
    private static byte[] sample() { byte[] value = new byte[512]; for (int i = 0; i < value.length; i++) value[i] = (byte) (i * 37); return value; }
    private static boolean owned(File owner, String expected) throws java.io.IOException {
        if (!owner.isFile() || owner.length() != 32) return false;
        byte[] actual = new byte[32];
        try (java.io.FileInputStream input = new java.io.FileInputStream(owner)) {
            int total = 0, n;
            while (total < actual.length && (n = input.read(actual, total, actual.length - total)) != -1) total += n;
            return total == actual.length && input.read() == -1
                    && MessageDigest.isEqual(actual, expected.getBytes(StandardCharsets.US_ASCII));
        }
    }
    private static boolean contains(byte[] data, byte[] needle) {
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (data[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }
}
