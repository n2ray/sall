package io.github.n2ray.sill;

import java.util.Base64;
import java.util.HexFormat;
import java.util.Arrays;

public class PairingProofTest {
    public static void main(String[] args) throws Exception {
        var hex = HexFormat.of();
        byte[] mac = hex.parseHex("348a629f5ceed032c3e8706ec47d9bfafb00fb4250b018dd965435ca50cb836e");
        byte[] device = hex.parseHex("263a4dbe41488fb87214b0032339dbb9f0c8da14c16dfcf13084bf3c2552eca5");
        assert PairingProof.normalizeCode("4829 1355 7208").equals("482913557208");
        try { PairingProof.normalizeCode("482913557209"); throw new AssertionError("accepted typo"); }
        catch (IllegalArgumentException expected) { }
        byte[] key = PairingProof.codeKey("482913557208", mac);
        assert hex.formatHex(key).equals("e9baf879a1cc4ac8b21554df480b6c1621671319107c91e7cb05d154f649d31d");
        var b64 = Base64.getUrlEncoder().withoutPadding();
        assert b64.encodeToString(PairingProof.proof(key, mac, device, true)).equals("dk7PZj4vHsAxj--1u9Vg7ehSn8a1tWc0RCG46YpFVO8");
        byte[] proof = PairingProof.proof(key, mac, device, false);
        assert b64.encodeToString(proof).equals("6BmGWC9XT8J8OY8phhq9bASgoSnoWpRxBeqZtvRrWmk");
        byte[] wrong = proof.clone(); wrong[0] ^= 1;
        assert PairingProof.verifyMac(proof, proof) && !PairingProof.verifyMac(proof, wrong);
        byte[] qr = new byte[16]; Arrays.fill(qr, (byte) 0x22);
        assert b64.encodeToString(PairingProof.proof(qr, mac, device, true)).equals("AyZAJqNTU5nSKEaHx6RZOf0K5dOeygR_8JSrCV_gNOo");
        assert b64.encodeToString(PairingProof.proof(qr, mac, device, false)).equals("XAX-uZPsq_Ax8fvI_sV7Ex0a-ROi19rI-FW-ykPh9TE");
        System.out.println("Sill pairing vectors passed");
    }
}
