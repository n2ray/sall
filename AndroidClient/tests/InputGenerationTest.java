package io.github.n2ray.sill;

public final class InputGenerationTest {
    public static void main(String[] args) {
        InputGeneration generation = new InputGeneration();
        long first = generation.next();
        assert generation.current(first);
        long afterCaretMove = generation.next();
        assert !generation.current(first) && generation.current(afterCaretMove);
        long afterSession = generation.next();
        assert !generation.current(afterCaretMove) && generation.current(afterSession);
        System.out.println("Input generation checks passed");
    }
}
