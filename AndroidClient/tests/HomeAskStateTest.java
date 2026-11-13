package io.github.n2ray.sall;

public final class HomeAskStateTest {
    public static void main(String[] args) throws Exception {
        assert HomeAskState.retryMillis(null) == 1000;
        assert HomeAskState.retryMillis(Double.NaN) == 1000;
        assert HomeAskState.retryMillis(-3) == 200;
        assert HomeAskState.retryMillis(90) == 10000;
        assert HomeAskState.retryMillis(.8) == 800;
        HomeAskState first = new HomeAskState();
        assert !first.shown() && first.isShown();
        assert first.cancel() && !first.cancel() && !first.canComplete();
        HomeAskState late = new HomeAskState();
        assert !late.cancel() && late.shown() && !late.canComplete();
        HomeAskState completed = new HomeAskState();
        assert !completed.shown() && completed.complete() && !completed.cancel();
        for (int i = 0; i < 200; i++) {
            HomeAskState race = new HomeAskState();
            boolean[] result = new boolean[2];
            Thread a = new Thread(() -> result[0] = race.shown());
            Thread b = new Thread(() -> result[1] = race.cancel());
            a.start(); b.start(); a.join(); b.join();
            assert result[0] != result[1] && !race.canComplete();
        }
        System.out.println("Home pairing ask cancellation and late reply passed");
    }
}
