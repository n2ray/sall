"""Apply the narrowly scoped live-probe changes to a temporary source copy only."""

def patch(source):
    old_flag = 'private static final boolean OVERLAY_EXTERNAL_ENABLED = false;'
    new_flag = 'private static final boolean OVERLAY_EXTERNAL_ENABLED = true;'
    anchor = '            overlayPairing.session(owner, owner.peerFingerprint()); overlayOwner = owner;'
    route = ('        if (connection != null) {\n'
             '            if (OVERLAY_EXTERNAL_ENABLED) openOverlayPair(link);\n'
             '            else { link.clearSecret(); show("已有连接，请先断开后重新打开配对链接。"); }\n'
             '            return;\n'
             '        }')
    session_bar = '        LinearLayout bar = new LinearLayout(this); sessionBar = bar; bar.setGravity(android.view.Gravity.CENTER_VERTICAL); sessionPanel.addView(bar);'
    stop = '@Override protected void onStop() { closeOverlayPair();'
    destroy = '@Override protected void onDestroy() {\n        closeOverlayPair();'
    method = '    private void openOverlayPair(PairLink supplied) {'
    intent = '    private void handlePairIntent(android.content.Intent intent) {\n'
    revoke = '        if (old != null) ui.post(() -> { if (overlayOwner == old) closeOverlayPair(); });'
    if any(source.count(x) != 1 for x in (old_flag, anchor, route, session_bar, stop, destroy, method, intent, revoke)):
        raise RuntimeError('Unexpected overlay source; refusing to build probe')
    guard = ('            if (!java.security.MessageDigest.isEqual(supplied.fingerprint(), owner.peerFingerprint())) {\n'
             '                supplied.clearSecret(); show("测试版只允许配对当前连接的 Mac。"); return;\n'
             '            }\n')
    probe_methods = '''    private android.app.AlertDialog probeInputDialog;
    private android.widget.EditText probeInput;
    private SillConnection probeInputOwner;
    private void closeProbeInput() { closeProbeInput(false); }
    private void closeProbeInput(boolean keepBlocked) {
        android.app.AlertDialog old = probeInputDialog; probeInputDialog = null;
        android.widget.EditText text = probeInput; probeInput = null;
        probeInputOwner = null;
        if (text != null) text.getText().clear();
        synchronized (outputLock) { if (!keepBlocked && overlayPairDialog == null) overlayPairBlocked = false; }
        if (old != null) old.dismiss();
    }
    private void openProbeInput() {
        SillConnection owner = connection;
        if (owner == null || closing || probeInputDialog != null || overlayPairDialog != null) return;
        dismissSettings(false); dismissMenu(); if (appDrawer != null) appDrawer.dismiss();
        pictureInput.cancel(); trackpad.cancelGestures(); releaseHardwareKeys(); keyboard.finishAndForget();
        synchronized (outputLock) { clipboardReceipt.cancel(); tourInputGeneration++; overlayPairBlocked = true; }
        android.widget.EditText text = new android.widget.EditText(this); probeInput = text; probeInputOwner = owner;
        text.setHint("请自行粘贴新配对链接"); text.setSaveEnabled(false);
        text.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        text.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        text.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle("粘贴配对链接（测试）")
                .setView(text).setNegativeButton("取消", (d, which) -> { if (probeInputDialog == d) closeProbeInput(); })
                .setPositiveButton("继续", null).create();
        probeInputDialog = dialog;
        dialog.setOnCancelListener(d -> { if (probeInputDialog == dialog) closeProbeInput(); });
        dialog.setOnDismissListener(d -> { if (probeInputDialog == dialog) closeProbeInput(); });
        dialog.show();
        dialog.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (probeInputDialog != dialog || probeInput != text) return;
            String raw = text.getText().toString();
            closeProbeInput(true);
            try {
                if (connection != owner || closing) return;
                handlePairIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(raw)));
            } catch (RuntimeException ignored) { show("无效的 Sill 配对链接。"); }
            finally { synchronized (outputLock) { if (overlayPairDialog == null) overlayPairBlocked = false; } }
        });
    }
'''
    probe_button = ('        Button probePaste = button(sessionPanel, "粘贴链接（测试）", v -> openProbeInput());\n'
                    '        probePaste.setTextSize(15); probePaste.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(44)));\n')
    return (source.replace(old_flag, new_flag, 1).replace(anchor, guard + anchor, 1)
            .replace(route, '        if (link != null) { openOverlayPair(link); return; }', 1)
            .replace(method, probe_methods + method, 1)
            .replace(intent, intent + '        if (probeInputDialog != null) closeProbeInput();\n', 1)
            .replace(revoke, '        if (old != null) ui.post(() -> { if (probeInputOwner == old) closeProbeInput(); if (overlayOwner == old) closeOverlayPair(); });', 1)
            .replace(session_bar, probe_button + session_bar, 1)
            .replace(stop, '@Override protected void onStop() { closeProbeInput(); closeOverlayPair();', 1)
            .replace(destroy, '@Override protected void onDestroy() {\n        closeProbeInput();\n        closeOverlayPair();', 1))
