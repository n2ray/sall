package io.github.n2ray.sill;
public final class ClipboardChangeTrackerTest {
    public static void main(String[] args) {
        ClipboardChangeTracker t = new ClipboardChangeTracker();
        assert !t.observe(0, true, 100).text();
        var first = t.observe(100, true, 100); assert first.text() && !first.fromMac();
        assert t.observe(100, true, 100).id() == first.id();
        var second = t.observe(101, true, 101); assert second.id() > first.id();
        t.macWriteStarting(); assert !t.observe(102, true, 102).text();
        var mac = t.macWriteCompleted(102, true, 102); assert mac.fromMac() && mac.id() > second.id();
        assert !t.observe(102, true, 102).fromMac();
        t.macWriteStarting(); assert !t.macWriteCompleted(0, false, 200).text();
        var ambiguous = t.observe(200, true, 201);
        assert !ambiguous.text(); // unknown Mac write quarantines ambiguous timestamps
        ClipboardChipState chip = new ClipboardChipState();
        chip.newSession(1, 0); chip.context(true, false, false, 0);
        chip.observe(1, ambiguous.id(), ambiguous.text(), true, 0);
        assert !chip.visible(); // raw MIME may be text while the tracked observation is quarantined
        var user = t.observe(201, true, 201); assert user.text() && !user.fromMac();
        chip.observe(1, user.id(), user.text(), true, 1); assert chip.visible();
        t.macWriteStarting(); t.macWriteUnidentified(300);
        assert !t.observe(301, true, 299).text(); // wall clock moved backwards: fail closed
        assert !t.observe(305, true, 301).text(); // future timestamp is unverifiable
        assert t.observe(301, true, 301).text(); // later user copy is no longer swallowed
        assert !t.observe(302, false, 302).text(); assert t.observe(302, true, 302).id() > mac.id();
        System.out.println("Clipboard metadata-only change tracking passed");
    }
}
