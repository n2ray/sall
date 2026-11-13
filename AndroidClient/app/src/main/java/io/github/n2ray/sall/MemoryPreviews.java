package io.github.n2ray.sall;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Images live only as long as this activity; replacing or removing one erases its bytes. */
final class MemoryPreviews {
    private final Map<String, byte[]> images = new HashMap<>();
    synchronized void put(byte[] pin, byte[] image) {
        String key = ConnectionPreviewPolicy.fingerprintName(pin);
        if (key == null || image == null || image.length == 0 || image.length > 2_000_000) return;
        byte[] old = images.put(key, image.clone());
        if (old != null) Arrays.fill(old, (byte) 0);
    }
    synchronized byte[] get(byte[] pin) {
        String key = ConnectionPreviewPolicy.fingerprintName(pin);
        byte[] image = key == null ? null : images.get(key);
        return image == null ? null : image.clone();
    }
    synchronized void remove(byte[] pin) {
        String key = ConnectionPreviewPolicy.fingerprintName(pin);
        byte[] old = key == null ? null : images.remove(key);
        if (old != null) Arrays.fill(old, (byte) 0);
    }
    synchronized void clear() {
        for (byte[] image : images.values()) Arrays.fill(image, (byte) 0);
        images.clear();
    }
}
