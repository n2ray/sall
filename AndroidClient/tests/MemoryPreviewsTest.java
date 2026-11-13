package io.github.n2ray.sall;

import java.util.Arrays;

public final class MemoryPreviewsTest {
    public static void main(String[] args) {
        byte[] pin = new byte[32], other = new byte[32], image = {1, 2, 3}; other[0] = 1;
        MemoryPreviews previews = new MemoryPreviews();
        previews.put(pin, image); image[0] = 9;
        byte[] loaded = previews.get(pin);
        assert Arrays.equals(loaded, new byte[]{1, 2, 3});
        loaded[0] = 8; assert previews.get(pin)[0] == 1;
        assert previews.get(other) == null;
        previews.remove(other); assert previews.get(pin) != null;
        previews.remove(pin); assert previews.get(pin) == null;
        previews.put(pin, image); previews.clear(); assert previews.get(pin) == null;
        assert new MemoryPreviews().get(pin) == null;
        System.out.println("Memory-only preview isolation and cleanup passed");
    }
}
