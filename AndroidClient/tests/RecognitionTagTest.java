package io.github.n2ray.sill;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class RecognitionTagTest {
    public static void main(String[] args) {
        byte[] key = new byte[32]; Arrays.fill(key, (byte) 0x11);
        String vector = "oaKjpKWmL4V47Ctm";
        assert RecognitionTag.matches(vector, key);
        assert !RecognitionTag.matches("oaKjpKWmL4V47Ctn", key);
        assert !RecognitionTag.matches(vector + "=", key);
        assert !RecognitionTag.matches(vector, new byte[31]);
        assert !RecognitionTag.matches(vector, new byte[32]);
        assert vector.equals(RecognitionTag.fromTxt(vector.getBytes(StandardCharsets.US_ASCII)));
        assert RecognitionTag.fromTxt("oaKjpKWmL4V47Ct=".getBytes(StandardCharsets.US_ASCII)) == null;
        assert RecognitionTag.fromTxt(new byte[16]) == null;
        assert RecognitionTag.fromTxt(null) == null;
        System.out.println("Bonjour recognition tag checks passed");
    }
}
