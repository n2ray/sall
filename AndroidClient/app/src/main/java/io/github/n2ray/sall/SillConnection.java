package io.github.n2ray.sall;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** A pairing exchange authenticates the temporary peer key before it can be saved. */
final class SillConnection implements Closeable {
    private static final String ALIAS = "sill-client-identity-v2";
    private final SSLSocket socket;
    private final byte[] deviceFingerprint;
    private final byte[] peerFingerprint;

    private SillConnection(SSLSocket socket, byte[] deviceFingerprint, byte[] peerFingerprint) {
        this.socket = socket; this.deviceFingerprint = deviceFingerprint; this.peerFingerprint = peerFingerprint;
    }

    static SillConnection connect(String host, int port, String alpn, byte[] pin) throws Exception {
        return connect(host, port, alpn, pin, 10000, 15000);
    }

    static SillConnection connect(String host, int port, String alpn, byte[] pin, int connectTimeout, int readTimeout) throws Exception {
        return connect(host, port, alpn, pin, connectTimeout, readTimeout, null);
    }

    static SillConnection connect(String host, int port, String alpn, byte[] pin, int connectTimeout, int readTimeout,
                                  RemoteDial.Attempt attempt) throws Exception {
        return connect(host, port, alpn, pin, connectTimeout, readTimeout, attempt, null);
    }
    static SillConnection connect(String host, int port, String alpn, byte[] pin, int connectTimeout, int readTimeout,
                                  RemoteDial.Attempt attempt, android.net.Network network) throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        if (!keys.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(new java.security.spec.ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256).build());
            generator.generateKeyPair();
        }
        byte[] own = MessageDigest.getInstance("SHA-256").digest(keys.getCertificate(ALIAS).getPublicKey().getEncoded());
        KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, null);
        final byte[][] observed = new byte[1][];
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { throw new CertificateException("Unexpected client certificate"); }
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                android.util.Log.i("Sall", "Checking server certificate (" + authType + ")");
                if (chain == null || chain.length == 0 || !(chain[0].getPublicKey() instanceof ECPublicKey)
                        || ((ECPublicKey) chain[0].getPublicKey()).getParams().getCurve().getField().getFieldSize() != 256)
                    throw new CertificateException("Invalid Sill identity");
                try {
                    byte[] fp = MessageDigest.getInstance("SHA-256").digest(chain[0].getPublicKey().getEncoded());
                    if (pin != null && !MessageDigest.isEqual(pin, fp)) throw new CertificateException("Mac identity changed");
                    observed[0] = fp;
                } catch (java.security.GeneralSecurityException e) { throw new CertificateException(e); }
            }
        };
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(km.getKeyManagers(), new TrustManager[] {trust}, new SecureRandom());
        Socket tcp = new Socket();
        try {
            if (attempt != null) attempt.attach(tcp);
            if (network != null) try { network.bindSocket(tcp); }
            catch (IOException denied) { throw new IOException("系统不允许使用本地网络，请检查 VPN 的局域网访问设置，或配对后连接。", denied); }
            tcp.connect(new InetSocketAddress(host, port), connectTimeout); tcp.setTcpNoDelay(true); tcp.setSoTimeout(readTimeout);
            SSLSocket tls = (SSLSocket) context.getSocketFactory().createSocket(tcp, host, port, true);
            tls.setSoTimeout(readTimeout);
            SSLParameters params = tls.getSSLParameters();
            params.setProtocols(new String[] {"TLSv1.3"}); params.setApplicationProtocols(new String[] {alpn});
            tls.setSSLParameters(params);
            android.util.Log.i("Sall", "Starting TLS handshake for " + alpn);
            tls.startHandshake();
            android.util.Log.i("Sall", "TLS handshake completed for " + alpn);
            if (!alpn.equals(tls.getApplicationProtocol()) || observed[0] == null) throw new IOException("Sill TLS negotiation failed");
            return new SillConnection(tls, own, observed[0]);
        } catch (Exception e) { tcp.close(); throw e; }
    }

    record HomePairReply(String reason, long retryMillis) { }
    static HomePairReply homePairingCommand(java.io.InputStream input, java.io.OutputStream output, String method, String name, String model) throws Exception {
        if (!method.equals("ask") && !method.equals("cancel")) throw new IllegalArgumentException("Invalid home pairing command");
        JSONObject request = new JSONObject().put("v", 1).put("method", method).put("proof", "")
                .put("name", name).put("model", model);
        StreamWire.write(output, 19, request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StreamWire.Message reply = StreamWire.read(input, 4096);
        if (reply.kind() != 20 || reply.payload().length == 0) throw new IOException("Invalid home pairing response");
        org.json.JSONTokener decoder = new org.json.JSONTokener(new String(reply.payload(), java.nio.charset.StandardCharsets.UTF_8));
        Object object = decoder.nextValue();
        if (!(object instanceof JSONObject result) || decoder.nextClean() != 0 || !(result.opt("ok") instanceof Boolean ok) || ok)
            throw new IOException("Invalid home pairing response");
        Object reason = result.opt("reason");
        if (!(reason instanceof String text) || text.isEmpty() || text.length() > 100) throw new IOException("Invalid home pairing reason");
        return new HomePairReply(text, HomeAskState.retryMillis(result.opt("retryAfter")));
    }
    HomePairReply homePairingCommand(String method, String name, String model) throws Exception {
        return homePairingCommand(socket.getInputStream(), socket.getOutputStream(), method, name, model);
    }

    byte[] pair(String code, String deviceName, String model) throws Exception {
        return pairIdentity(code, deviceName, model).pin();
    }

    SavedMacs.Identity pairIdentity(String code, String deviceName, String model) throws Exception {
        android.util.Log.i("Sall", "Deriving pairing proof");
        byte[] key = PairingProof.codeKey(code, peerFingerprint);
        try { return pairWithKey(key, "code", deviceName, model); }
        finally { java.util.Arrays.fill(key, (byte) 0); }
    }

    byte[] pairQr(byte[] secret, String deviceName, String model) throws Exception {
        return pairQrIdentity(secret, deviceName, model).pin();
    }

    SavedMacs.Identity pairQrIdentity(byte[] secret, String deviceName, String model) throws Exception {
        if (secret == null || secret.length != 16) throw new IllegalArgumentException("Invalid QR secret");
        socket.setSoTimeout(15000);
        return pairWithKey(secret, "qr", deviceName, model);
    }

    private SavedMacs.Identity pairWithKey(byte[] key, String method, String deviceName, String model) throws Exception {
        byte[] proof = PairingProof.proof(key, peerFingerprint, deviceFingerprint, true);
        String encoded = Base64.encodeToString(proof, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        JSONObject request = new JSONObject().put("v", 1).put("method", method).put("proof", encoded)
                .put("name", deviceName).put("model", model);
        StreamWire.write(socket.getOutputStream(), 19, request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StreamWire.Message response = StreamWire.read(socket.getInputStream(), 4096);
        if (response.kind() != 20 || response.payload().length == 0 || response.payload().length > 4096)
            throw new IOException("Invalid pairing response");
        JSONObject result = new JSONObject(new String(response.payload(), java.nio.charset.StandardCharsets.UTF_8));
        if (!result.optBoolean("ok")) throw new IOException("Pairing refused: " + result.optString("reason", "unknown"));
        byte[] answer = Base64.decode(result.getString("proof"), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        if (!PairingProof.verifyMac(PairingProof.proof(key, peerFingerprint, deviceFingerprint, false), answer))
            throw new IOException("Mac pairing proof failed");
        return SavedMacs.Identity.pairingResult(peerFingerprint, result.optString("macID", null), result.optString("name", ""),
                result.optString("recognitionKey", null));
    }

    StreamWire.Message read() throws IOException { return StreamWire.read(socket.getInputStream()); }
    StreamWire.Message readUntil(long deadlineNanos) throws IOException {
        return StreamWire.read(new java.io.FilterInputStream(socket.getInputStream()) {
            private void remaining() throws IOException {
                long nanos = deadlineNanos - System.nanoTime();
                if (nanos <= 0) throw new java.net.SocketTimeoutException("Mac did not send its window list");
                socket.setSoTimeout((int) Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(nanos)));
            }
            @Override public int read() throws IOException { remaining(); return in.read(); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException { remaining(); return in.read(bytes, offset, length); }
        });
    }
    byte[] peerFingerprint() { return peerFingerprint.clone(); }
    void send(int kind, byte[] data) throws IOException { StreamWire.write(socket.getOutputStream(), kind, data); }
    void sessionTimeout() throws IOException { socket.setSoTimeout(0); }
    public void close() throws IOException { socket.close(); }
}
