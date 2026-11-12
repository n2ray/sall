package io.github.n2ray.sill;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

public final class SignedMacInfoTest {
    public static void main(String[] args) throws Exception {
        Properties fixture = new Properties();
        try (var input = Files.newInputStream(Path.of(args[0]))) { fixture.load(input); }
        String info = fixture.getProperty("info"), key = fixture.getProperty("key"), sig = fixture.getProperty("sig");
        byte[] pin = Base64.getDecoder().decode(fixture.getProperty("pin"));
        byte[] bytes = SignedMacInfo.verify(info, key, sig, pin);
        check(Arrays.equals(bytes, Base64.getDecoder().decode(info)), "Apple CryptoKit signature verifies over original bytes");
        check(PairLink.macId(pin).equals(fixture.getProperty("macID")), "Apple fixture Mac ID agrees with Android");
        byte[] other = pin.clone(); other[31] ^= 1;
        reject(() -> SignedMacInfo.verify(info, key, sig, other));
        reject(() -> SignedMacInfo.verify(flip(info), key, sig, pin));
        reject(() -> SignedMacInfo.verify(info, flip(key), sig, pin));
        reject(() -> SignedMacInfo.verify(info, key, flip(sig), pin));
        reject(() -> SignedMacInfo.verify(info, key, "AA==", pin));
        reject(() -> SignedMacInfo.verify(info, "AA==", sig, pin));
        reject(() -> SignedMacInfo.verify(info, key, "!invalid!", pin));
        reject(() -> SignedMacInfo.verify("A".repeat(65537), key, sig, pin));
        reject(() -> SignedMacInfo.verify(info, key, sig, null));
        String changedWhitespace = Base64.getEncoder().encodeToString((new String(bytes, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8));
        reject(() -> SignedMacInfo.verify(changedWhitespace, key, sig, pin));
        check(SignedMacInfo.address("100.101.102.103", 7455).equals(new SavedMacs.Endpoint("100.101.102.103", 7455)), "VPN IPv4 accepted");
        check(SignedMacInfo.address("fd7a:115c:a1e0::1", 7455) != null, "VPN IPv6 accepted");
        check(SignedMacInfo.address("fearless.ts.net", 7455) != null, "DNS names are not classified by IPv6 prefix");
        for (String bad : List.of("localhost", "localhost.", "127.9.8.7", "169.254.1.4", "::1", "fe80::1", "::ffff:127.0.0.1", "fe80::1%en0",
                "https://example.com", "user@host", "name:7455", "host\nname", "0x7f000001", "255.1.1.999", "0.0.0.0", "ff02::1"))
            check(SignedMacInfo.address(bad, 7455) == null, "Unsafe advertised address rejected: " + bad);
        check(SignedMacInfo.address("example.com", 0) == null && SignedMacInfo.address("example.com", 65536) == null, "Invalid ports rejected");
        reject(() -> new SignedMacInfo.Update("id", "name", Double.NaN, true, List.of()));
        reject(() -> new SignedMacInfo.Update("id", "name", Double.POSITIVE_INFINITY, true, List.of()));
        reject(() -> new SignedMacInfo.Update("id", "name", 0, true, List.of()));
        System.out.println("Signed Mac info CryptoKit, tampering, session binding and address checks passed");
    }
    private static String flip(String encoded) { byte[] bytes = Base64.getDecoder().decode(encoded); bytes[bytes.length - 1] ^= 1; return Base64.getEncoder().encodeToString(bytes); }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); }
    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid Mac info accepted"); }
        catch (IllegalArgumentException | java.security.GeneralSecurityException expected) { }
    }
}
