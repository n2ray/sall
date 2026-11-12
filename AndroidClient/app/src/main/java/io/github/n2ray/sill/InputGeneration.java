package io.github.n2ray.sill;

/** Reject IME callbacks created before a remote caret or session change. */
final class InputGeneration {
    private long current;
    long next() { return ++current; }
    boolean current(long value) { return current == value; }
}
