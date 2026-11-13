package io.github.n2ray.sill;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/** Synthetic data only, in a run-unique namespace; never reads app previews or preferences. */
public final class PreviewProbe extends Instrumentation {
    private String phase, token, ownerNonce;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); phase = args.getString("phase", ""); token = args.getString("token", "");
        ownerNonce = args.getString("owner", ""); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!token.matches("[0-9a-f]{32}") || !ownerNonce.matches("[0-9a-f]{32}"))
                throw new AssertionError("invalid synthetic namespace");
            Context context = getTargetContext();
            AndroidConnectionPreviewBackend backend = AndroidConnectionPreviewBackend.probe(context, token);
            byte[] pin = MessageDigest.getInstance("SHA-256").digest(("preview-probe:" + token).getBytes(StandardCharsets.US_ASCII));
            String identity = ConnectionPreviewPolicy.fingerprintName(pin);
            String namespace = "sill-preview-probe-" + token;
            File ownerDir = new File(context.getNoBackupFilesDir(), "sill-preview-probe-owners");
            File owner = new File(ownerDir, token);
            if (phase.equals("claim")) {
                if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                if (backend.key(identity, false) != null || backend.tombstone(identity) || backend.read(identity, 2_000_032) != null)
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
                if (phase.equals("write")) {
                    if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
                    SecretKey key = backend.key(identity, true);
                    try {
                        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
                        byte[] sealed = cipher.doFinal(new byte[]{0}); Arrays.fill(sealed, (byte) 0);
                    }
                    catch (UserNotAuthenticatedException needAuth) {
                        finishResult(result, "SKIP: recent strong authentication required", Activity.RESULT_OK); return;
                    }
                    ConnectionPreviewStore store = new ConnectionPreviewStore(backend);
                    store.bind(1, pin); store.context(true, true);
                    ConnectionPreviewPolicy.Ticket ticket = store.begin(1, pin, true);
                    byte[] sample = sample();
                    if (ticket == null || !store.save(ticket, 1, pin, true, true, sample)) throw new AssertionError("encrypted write failed");
                    byte[] ciphertext = backend.read(identity, 2_000_032);
                    if (ciphertext == null || contains(ciphertext, sample)) throw new AssertionError("plaintext present in cache");
                    Arrays.fill(sample, (byte) 0);
                } else if (phase.equals("read")) {
                    if (!backend.available()) { finishResult(result, "SKIP: device not securely unlocked", Activity.RESULT_OK); return; }
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
                    if (keys.containsAlias(namespace + "-" + identity) || backend.tombstone(identity)
                            || backend.read(identity, 2_000_032) != null) throw new AssertionError("synthetic data remains");
                    File cache = new File(context.getCacheDir(), namespace);
                    File markers = new File(context.getNoBackupFilesDir(), namespace);
                    if ((cache.exists() && !cache.delete()) || (markers.exists() && !markers.delete()) || !owner.delete())
                        throw new AssertionError("synthetic namespace cleanup failed");
                    ownerDir.delete();
                } else throw new AssertionError("invalid phase");
            }
            finishResult(result, "PASS: synthetic preview " + phase, Activity.RESULT_OK);
        } catch (Throwable failure) {
            finishResult(result, "FAIL: " + failure.getClass().getSimpleName(), Activity.RESULT_CANCELED);
        }
    }
    private void finishResult(Bundle result, String value, int code) { result.putString("result", value); finish(code, result); }
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
