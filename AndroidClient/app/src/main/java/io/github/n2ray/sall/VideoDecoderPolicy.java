package io.github.n2ray.sall;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A connection run tries the platform default, then each distinct HEVC software decoder once. */
final class VideoDecoderPolicy {
    private final Set<String> failed = new HashSet<>();
    private boolean softwareOnly;

    // Empty string means platform default; null means no decoder remains.
    String next(List<String> software) {
        if (!softwareOnly) return "";
        for (String name : software) if (name != null && !name.isEmpty() && !failed.contains(name)) return name;
        return null;
    }

    void failed(String name) {
        softwareOnly = true;
        if (name != null && !name.isEmpty()) failed.add(name);
    }

    boolean softwareOnly() { return softwareOnly; }
}
