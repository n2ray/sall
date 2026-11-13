package io.github.n2ray.sall;

import java.util.Arrays;
import java.util.Locale;

/** Host-reported stream status and recent ping round trips, without inferring a network route. */
final class ConnectionDiagnostics {
    static final class Rtt {
        private final long[] times = new long[8];
        private final double[] values = new double[8];
        private int next, count;
        private final long[] seconds = new long[5];
        private int secondsNext, secondsCount;
        private long bucket = -1;

        synchronized void add(double seconds, long now) {
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > 60 || now < 0) return;
            long nextBucket = now / 1000;
            if (bucket >= 0 && nextBucket < bucket) return;
            if (bucket >= 0 && nextBucket > bucket) {
                Long previous = bucketMedian(bucket);
                if (previous != null) { this.seconds[secondsNext] = previous; secondsNext = (secondsNext + 1) % 5; secondsCount = Math.min(5, secondsCount + 1); }
            }
            bucket = nextBucket;
            times[next] = now; values[next] = seconds * 1000;
            next = (next + 1) % times.length; count = Math.min(count + 1, times.length);
        }

        synchronized Long median(long now) {
            double[] recent = new double[count]; int n = 0;
            for (int i = 0; i < count; i++) {
                long age = now - times[i];
                if (age >= 0 && age <= 1500) recent[n++] = values[i];
            }
            if (n == 0) return null;
            Arrays.sort(recent, 0, n);
            return Math.round(n % 2 == 1 ? recent[n / 2] : (recent[n / 2 - 1] + recent[n / 2]) / 2);
        }

        private Long bucketMedian(long second) {
            double[] recent = new double[count]; int n = 0;
            for (int i = 0; i < count; i++) if (times[i] / 1000 == second) recent[n++] = values[i];
            if (n == 0) return null;
            Arrays.sort(recent, 0, n);
            return Math.round(n % 2 == 1 ? recent[n / 2] : (recent[n / 2 - 1] + recent[n / 2]) / 2);
        }

        synchronized boolean slow(long now) {
            if (secondsCount < 5 || median(now) == null) return false;
            long[] recent = this.seconds.clone(); Arrays.sort(recent);
            return recent[2] > 250;
        }
    }

    record Advice(String text, Integer bitrate, Double captureScale) {}

    static boolean accepted(Object actual, Object expected, Object actualScale, Double expectedScale) {
        boolean primary = actual instanceof Number && expected instanceof Number
                ? ((Number) actual).doubleValue() == ((Number) expected).doubleValue()
                : expected != null && expected.equals(actual);
        return primary && (expectedScale == null || actualScale instanceof Number
                && ((Number) actualScale).doubleValue() == expectedScale);
    }

    static Advice advice(String state, boolean away, boolean awayRunning, boolean olderHost, boolean remote, boolean slow,
                         Number suggestedBitrate, Number suggestedScale) {
        if ("behind".equals(state)) {
            if (away && !awayRunning) return new Advice("当前连接无法稳定承载画质；由于有本地设备连接，Mac 正在使用本地画质。", null, null);
            int rate = suggestedBitrate == null ? 0 : suggestedBitrate.intValue();
            boolean known = suggestedBitrate != null && suggestedBitrate.doubleValue() == rate
                    && (rate == 4000000 || rate == 8000000 || rate == 15000000 || rate == 25000000
                    || rate == 40000000 || rate == 80000000 || rate == 150000000);
            Double scale = suggestedScale == null ? null : suggestedScale.doubleValue();
            if (known && (scale == null || scale == 1 || scale == 2))
                return new Advice("Mac 报告当前连接无法稳定承载画质，建议降低画质。", rate, scale);
            return new Advice("Mac 报告连接无法稳定承载当前画质，可尝试降低画质或分辨率。", null, null);
        }
        if (olderHost && remote && slow) return new Advice("网络往返时间持续较高，画面可能较慢；可尝试降低画质或分辨率。", null, null);
        return new Advice("", null, null);
    }

    static String readout(int width, int height, int fps, int mbps, boolean virtual, Long rtt) {
        String stream = width > 0 && height > 0 && fps > 0 && mbps > 0
                ? String.format(Locale.ROOT, "%d×%d · %d fps · %d Mbps", width, height, fps, mbps)
                : "暂未串流";
        if (virtual && width > 0 && height > 0 && fps > 0 && mbps > 0) stream += "\n正在使用虚拟显示器";
        return stream + "\n网络往返 " + (rtt == null ? "–" : rtt + " ms");
    }

    static String warning(boolean software, String linkState) {
        String result = software ? "Mac 硬件编码器忙碌或无响应，当前使用软件编码，画质或帧率可能受限。" : "";
        if ("behind".equals(linkState)) {
            if (!result.isEmpty()) result += "\n";
            result += "Mac 报告连接无法稳定承载当前画质，可尝试降低画质或分辨率。";
        }
        return result;
    }
    private ConnectionDiagnostics() {}
}
