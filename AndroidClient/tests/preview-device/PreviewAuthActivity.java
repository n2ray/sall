package io.github.n2ray.sall;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Probe-only system authentication. Credentials are handled entirely by Android. */
public final class PreviewAuthActivity extends Activity {
    private static CountDownLatch completion = new CountDownLatch(1);
    private static boolean accepted;
    private static String authenticationType = "UNKNOWN";
    private static String exceptionType = "";
    private final CancellationSignal cancellation = new CancellationSignal();
    private boolean started;

    static synchronized void prepare() { accepted = false; authenticationType = "UNKNOWN"; exceptionType = ""; completion = new CountDownLatch(1); }
    static boolean await() throws InterruptedException { return completion.await(70, TimeUnit.SECONDS); }
    static synchronized boolean accepted() { return accepted; }
    static synchronized String authenticationType() { return authenticationType; }
    static synchronized String exceptionType() { return exceptionType; }
    private static synchronized void complete(boolean value) {
        if (completion.getCount() == 0) return;
        accepted = value; completion.countDown();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
    }
    @Override protected void onResume() {
        super.onResume();
        if (started) return;
        started = true;
        try {
            boolean credentialOnly = getIntent().getBooleanExtra("credential_only", false);
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("验证预览加密测试")
                    .setSubtitle("仅用于验证临时合成测试密钥")
                    .setAllowedAuthenticators(credentialOnly ? BiometricManager.Authenticators.DEVICE_CREDENTIAL
                            : BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            prompt.authenticate(cancellation, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    synchronized (PreviewAuthActivity.class) {
                        int type = result.getAuthenticationType();
                        authenticationType = type == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC ? "BIOMETRIC"
                                : type == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL ? "DEVICE_CREDENTIAL" : "UNKNOWN";
                    }
                    complete(!credentialOnly || "DEVICE_CREDENTIAL".equals(authenticationType())); finish();
                }
                @Override public void onAuthenticationError(int code, CharSequence message) {
                    complete(false); finish();
                }
            });
        } catch (RuntimeException error) {
            synchronized (PreviewAuthActivity.class) { exceptionType = error.getClass().getSimpleName(); }
            complete(false); finish();
        }
    }
    @Override protected void onDestroy() {
        cancellation.cancel(); complete(false); super.onDestroy();
    }
}
