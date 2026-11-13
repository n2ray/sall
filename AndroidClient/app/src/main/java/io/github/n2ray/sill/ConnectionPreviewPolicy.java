package io.github.n2ray.sill;

import java.security.MessageDigest;
import java.util.Locale;

/** Pure preview policy. It never retains image pixels or writes a file. */
final class ConnectionPreviewPolicy {
    static final int BLACK_LEVEL = 24, MAX_WIDTH = 640;
    // Refuse extreme decoder sizes rather than allocating an unbounded bitmap.
    static final int MAX_SOURCE_DIMENSION = 16_384, MAX_PREVIEW_HEIGHT = 4_096;
    record Size(int width, int height) {}
    enum PixelFormat { RGBA, BGRA, ARGB, ABGR }

    static Size size(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 1 || height < 1
                || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION) return null;
        int resultWidth = (int) Math.min(MAX_WIDTH, Math.floor(width));
        double resultHeight = Math.max(1, Math.floor(height * resultWidth / width));
        return resultHeight > MAX_PREVIEW_HEIGHT ? null : new Size(resultWidth, (int) resultHeight);
    }
    // Only alpha-last decoded bytes are accepted; unknown/alpha-first input is rejected as unusable.
    static boolean looksBlack(byte[] pixels, int width, int height, int bytesPerRow, PixelFormat format) {
        if ((format != PixelFormat.RGBA && format != PixelFormat.BGRA)
                || pixels == null || width <= 0 || height <= 0 || width > MAX_WIDTH || height > MAX_PREVIEW_HEIGHT
                || bytesPerRow < (long) width * 4
                || (long) bytesPerRow * (height - 1) + (long) width * 4 > pixels.length) return true;
        for (int y = 0; y < height; y++) {
            int row = y * bytesPerRow;
            for (int x = 0; x < width; x++) {
                int at = row + x * 4;
                if ((pixels[at] & 255) > BLACK_LEVEL || (pixels[at + 1] & 255) > BLACK_LEVEL
                        || (pixels[at + 2] & 255) > BLACK_LEVEL) return false;
            }
        }
        return true;
    }
    static String fingerprintName(byte[] fingerprint) {
        if (fingerprint == null || fingerprint.length != 32) return null;
        byte[] stable = fingerprint.clone();
        StringBuilder name = new StringBuilder(64);
        for (byte value : stable) name.append(String.format(Locale.ROOT, "%02x", value & 255));
        return name.toString();
    }

    /** Guard owns one Android session. All delete/publish decisions must use this same guard. */
    static final class Guard {
        private long session, revision;
        private byte[] fingerprint;
        private boolean tiles, foreground, unlocked;
        private Ticket pending;

        synchronized void bind(long session, byte[] fingerprint) {
            invalidate(); this.session = session;
            tiles = foreground = unlocked = false;
            this.fingerprint = fingerprint == null || fingerprint.length != 32 ? null : fingerprint.clone();
        }
        synchronized void context(boolean tiles, boolean foreground, boolean unlocked) {
            if (!tiles || !foreground || !unlocked) invalidate();
            this.tiles = tiles; this.foreground = foreground; this.unlocked = unlocked;
        }
        synchronized Ticket begin(long session, byte[] pin, boolean saved) {
            byte[] stable = pin == null ? null : pin.clone();
            if (!allowed(session, stable, saved)) return null;
            invalidate(); pending = new Ticket(this, session, revision, stable); return pending;
        }
        synchronized void cancel() { invalidate(); }
        synchronized void forget(byte[] pin, Runnable erase) {
            byte[] stable = pin == null ? null : pin.clone();
            if (stable == null || stable.length != 32) throw new IllegalArgumentException("Invalid fingerprint");
            if (same(fingerprint, stable)) { invalidate(); fingerprint = null; }
            erase.run();
        }
        // Final publication must run synchronously here; a later async rename would reintroduce a delete race.
        synchronized boolean publish(Ticket ticket, long currentSession, byte[] currentPin, boolean saved, boolean nonBlack, Runnable commit) {
            if (ticket == null || ticket != pending || ticket.owner != this) return false;
            byte[] stable = currentPin == null ? null : currentPin.clone();
            pending = null;
            if (ticket.revision != revision || ticket.session != currentSession
                    || !nonBlack || !same(ticket.fingerprint, stable) || !allowed(currentSession, stable, saved)) return false;
            commit.run(); return true;
        }
        private boolean allowed(long id, byte[] pin, boolean saved) {
            return tiles && foreground && unlocked && saved && fingerprint != null && session == id && same(fingerprint, pin);
        }
        private void invalidate() { revision++; pending = null; }
    }
    static final class Ticket {
        private final Guard owner;
        private final long session, revision;
        private final byte[] fingerprint;
        private Ticket(Guard owner, long session, long revision, byte[] fingerprint) {
            this.owner = owner; this.session = session; this.revision = revision; this.fingerprint = fingerprint;
        }
    }
    private static boolean same(byte[] a, byte[] b) {
        return a != null && b != null && a.length == 32 && b.length == 32 && MessageDigest.isEqual(a, b);
    }
    private ConnectionPreviewPolicy() {}
}
