package io.github.n2ray.sill;

public final class ClipboardDefaultPolicyTest {
    static final class FakeStore implements ClipboardDefaultPolicy.Store {
        Boolean memory, disk;
        boolean existing, failCommits;
        FakeStore(Boolean disk, boolean existing, boolean failCommits) {
            this.memory = this.disk = disk; this.existing = existing; this.failCommits = failCommits;
        }
        public boolean contains() { return memory != null; }
        public boolean value() { return memory != null && memory; }
        public boolean hasExistingData() { return existing; }
        public boolean commit(boolean value) {
            memory = value;
            if (failCommits) return false;
            disk = value; return true;
        }
        FakeStore restart(boolean fail) { return new FakeStore(disk, existing, fail); }
    }
    public static void main(String[] args) {
        assert ClipboardDefaultPolicy.initial(false, false, false, 10, 10, 20);
        assert !ClipboardDefaultPolicy.initial(false, false, true, 10, 10, 20); // restored preferences
        assert !ClipboardDefaultPolicy.initial(false, false, false, 10, 15, 20);
        assert !ClipboardDefaultPolicy.initial(false, false, false, 0, 0, 20);
        assert !ClipboardDefaultPolicy.initial(false, false, false, -1, -1, 20);
        assert !ClipboardDefaultPolicy.initial(false, false, false, 10, 10, 0);
        assert !ClipboardDefaultPolicy.initial(false, false, false, 21, 21, 20);
        assert !ClipboardDefaultPolicy.initial(false, false, false, 15, 10, 20);
        assert ClipboardDefaultPolicy.initial(true, true, true, 0, 0, 0);
        assert !ClipboardDefaultPolicy.initial(true, false, false, 10, 10, 20);
        FakeStore fresh = new FakeStore(null, false, false);
        assert ClipboardDefaultPolicy.initialize(fresh, 10, 10, 20);
        assert ClipboardDefaultPolicy.initialize(fresh.restart(false), 10, 15, 20);
        FakeStore restored = new FakeStore(null, true, false);
        assert !ClipboardDefaultPolicy.initialize(restored, 10, 10, 20);
        assert !ClipboardDefaultPolicy.initialize(restored.restart(false), 10, 10, 20);
        FakeStore failed = new FakeStore(null, false, true);
        assert !ClipboardDefaultPolicy.initialize(failed, 10, 10, 20);
        assert Boolean.FALSE.equals(failed.memory) && failed.disk == null;
        assert !ClipboardDefaultPolicy.initialize(failed.restart(true), 10, 10, 20);
        FakeStore explicit = new FakeStore(false, true, true);
        assert !ClipboardDefaultPolicy.initialize(explicit, 10, 10, 20);
        assert Boolean.FALSE.equals(explicit.disk);
        System.out.println("Clipboard default migration checks passed");
    }
}
