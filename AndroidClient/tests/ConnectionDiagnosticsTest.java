package io.github.n2ray.sall;

public final class ConnectionDiagnosticsTest {
    public static void main(String[] args) {
        var rtt = new ConnectionDiagnostics.Rtt();
        assert rtt.median(1000) == null;
        rtt.add(.020, 1000); rtt.add(.100, 1100); rtt.add(.040, 1200);
        assert rtt.median(1200) == 40;
        assert rtt.median(2701) == null;
        rtt.add(Double.NaN, 2701); rtt.add(-1, 2701); rtt.add(61, 2701);
        assert rtt.median(2701) == null;
        for (int i = 0; i < 12; i++) rtt.add(i / 1000.0, 3000 + i);
        assert rtt.median(3011) == 8;
        String output = ConnectionDiagnostics.readout(832, 520, 60, 15, true, 40L);
        assert output.contains("832×520") && output.contains("60 fps") && output.contains("15 Mbps");
        assert output.contains("虚拟显示器") && output.contains("40 ms");
        assert ConnectionDiagnostics.readout(0, 0, 0, 0, true, null).contains("暂未串流");
        assert !ConnectionDiagnostics.readout(0, 0, 0, 0, true, null).contains("虚拟显示器");
        assert ConnectionDiagnostics.warning(false, "stalled").isEmpty();
        assert ConnectionDiagnostics.warning(true, "behind").contains("软件编码");
        assert ConnectionDiagnostics.warning(true, "behind").contains("画质");
        var slow = new ConnectionDiagnostics.Rtt();
        for (int second = 1; second <= 6; second++) slow.add(.300, second * 1000L);
        assert slow.slow(6000);
        assert !slow.slow(8000);
        for (int second = 7; second <= 12; second++) slow.add(.030, second * 1000L);
        assert !slow.slow(12000);
        var advice = ConnectionDiagnostics.advice("behind", false, true, false, true, false, 4000000, 1);
        assert advice.bitrate() == 4000000 && advice.captureScale() == 1;
        assert ConnectionDiagnostics.advice("behind", true, false, false, true, false, 4000000, 1).bitrate() == null;
        assert ConnectionDiagnostics.advice("behind", false, true, false, true, false, 123, 1).bitrate() == null;
        assert ConnectionDiagnostics.advice("behind", false, true, false, true, false, 4000000, Double.NaN).bitrate() == null;
        assert !ConnectionDiagnostics.advice("", false, false, true, true, true, null, null).text().isEmpty();
        assert ConnectionDiagnostics.advice("", false, false, false, true, true, null, null).text().isEmpty();
        assert ConnectionDiagnostics.advice("stalled", false, false, false, false, false, null, null).text().isEmpty();
        assert ConnectionDiagnostics.accepted(4000000L, 4000000, 1, 1.0);
        assert !ConnectionDiagnostics.accepted(4000000, 4000000, 2, 1.0);
        assert !ConnectionDiagnostics.accepted(8000000, 4000000, 1, 1.0);
        assert !ConnectionDiagnostics.accepted(4000000, 4000000, null, 1.0);
        assert ConnectionDiagnostics.accepted(true, true, null, null);
        System.out.println("Connection diagnostics checks passed");
    }
}
