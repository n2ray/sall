package io.github.n2ray.sall;

public final class WindowHoldTest {
    public static void main(String[] args) {
        WindowHold hold = new WindowHold();
        hold.begin(0, 0); assert hold.end();
        hold.begin(0, 0); assert !hold.move(12, 0, 12); assert hold.hold(); assert !hold.end();
        hold.begin(0, 0); assert hold.move(13, 0, 12); assert !hold.hold(); assert !hold.end();
        hold.begin(0, 0); hold.cancel(); assert !hold.hold(); assert !hold.end();
        hold.begin(0, 0); assert hold.hold(); assert hold.isHeld();
        hold.cancel(); assert !hold.isHeld(); hold.move(50, 0, 12);
        assert !hold.isHeld() && !hold.hold() && !hold.end();
        hold.begin(0, 0); assert hold.hold(); assert !hold.end();
        System.out.println("Window hold checks passed");
    }
}
