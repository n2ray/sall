package io.github.n2ray.sill;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.function.BooleanSupplier;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Single-process backend; a failure disables previews rather than weakening protection. */
final class AndroidConnectionPreviewBackend implements ConnectionPreviewStore.Backend {
    private static final String KEY_PREFIX = "sill-preview-v1-";
    private final Context context;
    private final String keyPrefix, cacheDirectory, tombstoneDirectory;
    private final boolean keyCreationAllowed;
    AndroidConnectionPreviewBackend(Context context) {
        this(context, KEY_PREFIX, "sill-previews", "sill-preview-tombstones", true);
    }
    private AndroidConnectionPreviewBackend(Context context, String keyPrefix, String cacheDirectory, String tombstoneDirectory, boolean keyCreationAllowed) {
        this.context = context.getApplicationContext(); this.keyPrefix = keyPrefix;
        this.cacheDirectory = cacheDirectory; this.tombstoneDirectory = tombstoneDirectory; this.keyCreationAllowed = keyCreationAllowed;
    }
    static AndroidConnectionPreviewBackend probe(Context context, String token, boolean keyCreationAllowed) {
        if (token == null || !token.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid probe token");
        String namespace = "sill-preview-probe-" + token;
        return new AndroidConnectionPreviewBackend(context, namespace + "-", namespace, namespace, keyCreationAllowed);
    }

    @Override public boolean available() {
        if (Build.VERSION.SDK_INT < 35 || context == null) return false;
        KeyguardManager guard = context.getSystemService(KeyguardManager.class);
        PowerManager power = context.getSystemService(PowerManager.class);
        return guard != null && power != null && guard.isDeviceSecure() && !guard.isDeviceLocked()
                && !guard.isKeyguardLocked() && power.isInteractive();
    }
    @Override public SecretKey key(String identity, boolean create) throws IOException, GeneralSecurityException {
        if (!available()) return null;
        String alias = keyPrefix + valid(identity);
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        Key existing = store.getKey(alias, null);
        if (existing != null) {
            if (!(existing instanceof SecretKey key)) throw new GeneralSecurityException("Invalid key type");
            return key;
        }
        if (!create || !keyCreationAllowed || !available()) return null;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setUnlockedDeviceRequired(true)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(300, KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG)
                .build());
        return generator.generateKey();
    }
    @Override public void deleteKey(String identity) throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        String alias = keyPrefix + valid(identity); store.deleteEntry(alias);
        if (store.containsAlias(alias)) throw new GeneralSecurityException("Preview key deletion failed");
    }
    @Override public boolean tombstone(String identity) throws IOException { return exists(marker(identity)); }
    @Override public void mark(String identity) throws IOException { writeAtomically(marker(identity), new byte[]{1}, () -> true); }
    @Override public void unmark(String identity) throws IOException { deleteAtomically(marker(identity)); }
    @Override public byte[] read(String identity, int maximum) throws IOException {
        AtomicFile file = new AtomicFile(ciphertext(identity));
        try (FileInputStream input = file.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n, count = 0;
            while ((n = input.read(buffer)) != -1) {
                count += n; if (count > maximum) throw new IOException("Preview is too large");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (FileNotFoundException missing) {
            if (exists(ciphertext(identity))) throw missing;
            return null;
        }
    }
    @Override public boolean write(String identity, byte[] encrypted, BooleanSupplier mayCommit) throws IOException {
        return writeAtomically(ciphertext(identity), encrypted, mayCommit);
    }
    @Override public void delete(String identity) throws IOException { deleteAtomically(ciphertext(identity)); }
    @Override public boolean permanentlyInvalid(Throwable failure) {
        for (int i = 0; failure != null && i < 8; i++, failure = failure.getCause())
            if (failure instanceof KeyPermanentlyInvalidatedException) return true;
        return false;
    }
    private File ciphertext(String identity) throws IOException {
        return new File(directory(context.getCacheDir(), cacheDirectory), valid(identity) + ".enc");
    }
    private File marker(String identity) throws IOException {
        return new File(directory(context.getNoBackupFilesDir(), tombstoneDirectory), valid(identity) + ".deleted");
    }
    private static File directory(File parent, String name) throws IOException {
        if (parent == null) throw new IOException("Private storage unavailable");
        File dir = new File(parent, name);
        if ((!dir.isDirectory() && !dir.mkdirs()) || !dir.canRead() || !dir.canWrite()) throw new IOException("Private storage unavailable");
        return dir;
    }
    private static String valid(String identity) {
        if (identity == null || !identity.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid identity");
        return identity;
    }
    private static boolean exists(File base) {
        return base.exists() || new File(base.getPath() + ".bak").exists() || new File(base.getPath() + ".new").exists();
    }
    private static boolean writeAtomically(File base, byte[] bytes, BooleanSupplier mayCommit) throws IOException {
        AtomicFile file = new AtomicFile(base); FileOutputStream out = file.startWrite();
        boolean finished = false;
        try {
            out.write(bytes);
            if (!mayCommit.getAsBoolean()) { file.failWrite(out); return false; }
            file.finishWrite(out); finished = true;
            try (FileInputStream input = file.openRead()) {
                byte[] buffer = new byte[4096]; int offset = 0, n;
                while ((n = input.read(buffer)) != -1) {
                    if (n > bytes.length - offset) throw new IOException("Atomic write differs");
                    for (int i = 0; i < n; i++) if (buffer[i] != bytes[offset + i]) throw new IOException("Atomic write differs");
                    offset += n;
                }
                if (offset != bytes.length) throw new IOException("Atomic write incomplete");
            }
            return true;
        } catch (IOException | RuntimeException failure) { if (!finished) file.failWrite(out); throw failure; }
    }
    private static void deleteAtomically(File base) throws IOException {
        new AtomicFile(base).delete(); if (exists(base)) throw new IOException("Preview deletion failed");
    }
}
