package io.github.n2ray.sall;
public final class SessionMacInfoCacheTest {
    public static void main(String[] args) {
        SessionMacInfoCache<String> cache = new SessionMacInfoCache<>();
        Object first = new Object(), next = new Object(); byte[] pin = new byte[32], other = new byte[32]; other[0] = 1;
        cache.bind(first, pin); pin[0] = 9; byte[] original = new byte[32];
        cache.offer(first, original, 2, "new"); cache.offer(first, original, 1, "old");
        assert "new".equals(cache.get(first, original));
        cache.offer(first, other, 3, "wrong Mac"); cache.offer(next, original, 3, "wrong session");
        assert "new".equals(cache.get(first, original)) && cache.get(first, other) == null;
        cache.bind(next, original); cache.clear(first);
        assert cache.get(next, original) == null;
        cache.offer(first, original, 4, "late"); assert cache.get(next, original) == null;
        cache.offer(next, original, 5, "current"); cache.clear(next); assert cache.get(next, original) == null;
        System.out.println("Verified Mac metadata is limited to its current session and identity");
    }
}
