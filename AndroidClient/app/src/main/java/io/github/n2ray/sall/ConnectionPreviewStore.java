package io.github.n2ray.sall;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted preview storage. Single app process only; callers must erase returned plaintext. */
final class ConnectionPreviewStore {
    private static final byte[] MAGIC = {0x53, 0x49, 0x4c, 0x01};
    private static final int IV_BYTES = 12, TAG_BYTES = 16, MAX_IMAGE_BYTES = 2_000_000;
    private static final Object STORAGE_LOCK = new Object();
    private static final java.util.Map<String, Object> REVISIONS = new java.util.HashMap<>();
    private static final java.util.Map<String, Object> BINDINGS = new java.util.HashMap<>();
    private static final java.util.Set<String> BLOCKED = new java.util.HashSet<>();
    interface Backend {
        boolean available();
        SecretKey key(String identity, boolean create) throws IOException, GeneralSecurityException;
        void deleteKey(String identity) throws IOException, GeneralSecurityException;
        boolean tombstone(String identity) throws IOException;
        void mark(String identity) throws IOException;
        void unmark(String identity) throws IOException;
        byte[] read(String identity, int maximum) throws IOException;
        boolean write(String identity, byte[] encrypted, BooleanSupplier mayCommit) throws IOException;
        void delete(String identity) throws IOException;
        boolean permanentlyInvalid(Throwable failure);
    }
    private final Backend backend;
    private final ConnectionPreviewPolicy.Guard guard = new ConnectionPreviewPolicy.Guard();
    private boolean tiles, foreground;
    private volatile boolean lifecycleAllowed, authenticationRequired;
    private ConnectionPreviewPolicy.Ticket pending;
    private Object pendingRevision;
    private String boundIdentity;
    private Object bindingRevision;

    ConnectionPreviewStore(Backend backend) { this.backend = java.util.Objects.requireNonNull(backend); }
    void bind(long session, byte[] fingerprint) {
        synchronized (STORAGE_LOCK) {
            byte[] stablePin = fingerprint == null ? null : fingerprint.clone();
            guard.bind(session, stablePin); pending = null; tiles = foreground = lifecycleAllowed = false;
            boundIdentity = ConnectionPreviewPolicy.fingerprintName(stablePin);
            bindingRevision = boundIdentity == null ? null : BINDINGS.computeIfAbsent(boundIdentity, ignored -> new Object());
        }
    }
    void context(boolean tiles, boolean foreground) {
        if (!tiles || !foreground) lifecycleAllowed = false;
        synchronized (STORAGE_LOCK) {
            this.tiles = tiles; this.foreground = foreground;
            lifecycleAllowed = tiles && foreground;
            boolean allowed = available(); guard.context(tiles, foreground, allowed);
            if (!allowed) pending = null;
        }
    }
    ConnectionPreviewPolicy.Ticket begin(long session, byte[] fingerprint, boolean saved) {
        synchronized (STORAGE_LOCK) {
            byte[] stablePin = fingerprint == null ? null : fingerprint.clone();
            String identity = ConnectionPreviewPolicy.fingerprintName(stablePin);
            if (identity == null || !identity.equals(boundIdentity) || bindingRevision != BINDINGS.get(identity)) {
                guard.cancel(); pending = null; pendingRevision = null; return null;
            }
            guard.context(tiles, foreground, available());
            pending = guard.begin(session, stablePin, saved);
            pendingRevision = pending == null ? null : new Object();
            if (pendingRevision != null) REVISIONS.put(identity, pendingRevision);
            return pending;
        }
    }
    boolean save(ConnectionPreviewPolicy.Ticket ticket, long session, byte[] fingerprint,
                 boolean saved, boolean nonBlack, byte[] image) {
        synchronized (STORAGE_LOCK) { return saveLocked(ticket, session, fingerprint, saved, nonBlack, image); }
    }
    private boolean saveLocked(ConnectionPreviewPolicy.Ticket ticket, long session, byte[] fingerprint,
                               boolean saved, boolean nonBlack, byte[] image) {
        if (!available()) { guard.context(tiles, foreground, false); pending = null; pendingRevision = null; return false; }
        if (image == null || image.length == 0 || image.length > MAX_IMAGE_BYTES) return false;
        byte[] stablePin = fingerprint == null ? null : fingerprint.clone();
        String identity = ConnectionPreviewPolicy.fingerprintName(stablePin);
        if (identity == null || ticket == null || ticket != pending || pendingRevision != REVISIONS.get(identity)) return false;
        pending = null; pendingRevision = null;
        boolean[] written = {false};
        boolean accepted = guard.publish(ticket, session, stablePin, saved, nonBlack,
                () -> written[0] = encryptAndWrite(identity, stablePin, image));
        return accepted && written[0];
    }
    byte[] load(byte[] fingerprint, boolean saved) {
        synchronized (STORAGE_LOCK) { return loadLocked(fingerprint, saved); }
    }
    private byte[] loadLocked(byte[] fingerprint, boolean saved) {
        byte[] stablePin = fingerprint == null ? null : fingerprint.clone();
        String identity = ConnectionPreviewPolicy.fingerprintName(stablePin);
        if (!saved || !tiles || !foreground || identity == null || BLOCKED.contains(identity) || !available()) return null;
        byte[] result = null;
        try {
            if (backend.tombstone(identity)) return null;
            byte[] envelope = backend.read(identity, MAGIC.length + IV_BYTES + TAG_BYTES + MAX_IMAGE_BYTES);
            if (envelope == null || envelope.length < MAGIC.length + IV_BYTES + TAG_BYTES
                    || !Arrays.equals(MAGIC, Arrays.copyOf(envelope, MAGIC.length))) return null;
            SecretKey key = backend.key(identity, false); if (key == null || !available()) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, envelope, MAGIC.length, IV_BYTES));
            cipher.updateAAD(MAGIC); cipher.updateAAD(stablePin);
            result = cipher.doFinal(envelope, MAGIC.length + IV_BYTES, envelope.length - MAGIC.length - IV_BYTES);
            if (result.length == 0 || result.length > MAX_IMAGE_BYTES || !available()) {
                Arrays.fill(result, (byte) 0); return null;
            }
            authenticationRequired = false;
            return result;
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            recordAuthenticationFailure(failure);
            if (result != null) Arrays.fill(result, (byte) 0);
            if (permanentlyInvalid(failure)) invalidate(identity);
            return null;
        }
    }
    boolean forget(byte[] fingerprint) {
        synchronized (STORAGE_LOCK) { return forgetLocked(fingerprint); }
    }
    private boolean forgetLocked(byte[] fingerprint) {
        byte[] stablePin = fingerprint == null ? null : fingerprint.clone();
        String identity = ConnectionPreviewPolicy.fingerprintName(stablePin); if (identity == null) return false;
        REVISIONS.put(identity, new Object());
        boolean[] erased = {false}; guard.forget(stablePin, () -> erased[0] = invalidate(identity)); return erased[0];
    }
    private boolean encryptAndWrite(String identity, byte[] pin, byte[] image) {
        try {
            if (!available()) return false;
            boolean recovering = backend.tombstone(identity);
            if (BLOCKED.contains(identity) && !recovering) return false;
            if (recovering) { backend.deleteKey(identity); backend.delete(identity); }
            SecretKey key = backend.key(identity, true); if (key == null || !available()) return false;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV(); if (iv == null || iv.length != IV_BYTES) return false;
            cipher.updateAAD(MAGIC); cipher.updateAAD(pin);
            byte[] encrypted = cipher.doFinal(image);
            byte[] envelope = new byte[MAGIC.length + iv.length + encrypted.length];
            System.arraycopy(MAGIC, 0, envelope, 0, MAGIC.length);
            System.arraycopy(iv, 0, envelope, MAGIC.length, iv.length);
            System.arraycopy(encrypted, 0, envelope, MAGIC.length + iv.length, encrypted.length);
            if (!available() || !backend.write(identity, envelope, this::available)) return false;
            if (recovering) { if (!available()) return false; backend.unmark(identity); BLOCKED.remove(identity); }
            authenticationRequired = false;
            return available();
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            recordAuthenticationFailure(failure);
            if (permanentlyInvalid(failure)) invalidate(identity);
            return false;
        }
    }
    // A durable marker precedes key deletion, so an interrupted forget cannot revive old ciphertext.
    private boolean invalidate(String identity) {
        REVISIONS.put(identity, new Object());
        BINDINGS.put(identity, new Object());
        BLOCKED.add(identity);
        boolean marked = false, keyDeleted = false, fileDeleted = false;
        try { backend.mark(identity); marked = backend.tombstone(identity); }
        catch (IOException | RuntimeException ignored) { }
        try { backend.deleteKey(identity); keyDeleted = true; }
        catch (IOException | GeneralSecurityException | RuntimeException ignored) { }
        try { backend.delete(identity); fileDeleted = true; }
        catch (IOException | RuntimeException ignored) { }
        if (keyDeleted && fileDeleted && !marked) BLOCKED.remove(identity);
        return keyDeleted && fileDeleted;
    }
    private boolean available() {
        try { return lifecycleAllowed && tiles && foreground && backend.available(); }
        catch (RuntimeException failure) { return false; }
    }
    boolean needsAuthentication() { return authenticationRequired; }
    private void recordAuthenticationFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause.getClass().getSimpleName().equals("UserNotAuthenticatedException")) { authenticationRequired = true; return; }
    }
    private boolean permanentlyInvalid(Throwable failure) {
        try { return backend.permanentlyInvalid(failure); }
        catch (RuntimeException ignored) { return false; }
    }
}
