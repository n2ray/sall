import unittest
from pathlib import Path
from controlled_build import patch

class ControlledBuildTest(unittest.TestCase):
    def test_only_explicit_flag_and_pinned_guard_changed(self):
        root = Path(__file__).resolve().parents[2]
        source = (root / 'app/src/main/java/io/github/n2ray/sall/MainActivity.java').read_text()
        result = patch(source)
        self.assertIn('OVERLAY_EXTERNAL_ENABLED = true;', result)
        self.assertEqual(source.count('OverlayPairingAuthorization.matchesSession(owner.peerFingerprint(), supplied.fingerprint())'), 1)
        self.assertEqual(result.count('OverlayPairingAuthorization.matchesSession(owner.peerFingerprint(), supplied.fingerprint())'), 2)
        self.assertLess(result.index('OverlayPairingAuthorization.matchesSession(owner.peerFingerprint()'), result.index('overlayPairing.session(owner'))
        self.assertIn('supplied.clearSecret(); show(', result)
        route = 'if (link != null) { openOverlayPair(link); return; }'
        self.assertEqual(result.count(route), 1)
        self.assertNotIn('if (OVERLAY_EXTERNAL_ENABLED) openOverlayPair(link);', result)
        self.assertLess(result.index(route), result.index('confirmationLink = link;'))
        self.assertIn('if (owner == null || owner != connection || closing) { if (supplied != null) supplied.clearSecret(); return; }', result)
        self.assertEqual(source.count('OVERLAY_EXTERNAL_ENABLED = true;'), 1)
        self.assertNotIn('openProbeInput()', source)
        self.assertEqual(result.count('v -> openProbeInput()'), 1)
        self.assertIn('粘贴链接（测试）', result)
        self.assertIn('text.setSaveEnabled(false)', result)
        self.assertIn('IME_FLAG_NO_PERSONALIZED_LEARNING', result)
        self.assertIn('PasswordTransformationMethod.getInstance()', result)
        self.assertIn('FLAG_SECURE', result)
        self.assertIn('text.getText().clear()', result)
        self.assertIn('if (connection != owner || closing) return;', result)
        self.assertIn('handlePairIntent(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(raw)))', result)
        self.assertIn('@Override protected void onStop() { closeProbeInput();', result)
        self.assertIn('@Override protected void onDestroy() {\n        closeProbeInput();', result)
        self.assertIn('if (probeInputOwner == old) closeProbeInput(); if (homeAsk != null && homeAsk.owner == old) closeHomeAsk(); if (pairLinkInputOwner == old) closePairLinkInput(); if (overlayOwner == old) closeOverlayPair();', result)
        self.assertIn('probeInput = text; probeInputOwner = owner;', result)
        self.assertIn('probeInputOwner = null;', result)
        probe = result[result.index('private android.app.AlertDialog probeInputDialog;'):result.index('private void openOverlayPair(PairLink supplied)')]
        self.assertNotIn('ClipboardManager', probe)
        self.assertNotIn('Log.', probe)
        self.assertLess(probe.index('closeProbeInput(true);'), probe.index('handlePairIntent(new android.content.Intent'))
        self.assertIn('if (!keepBlocked && overlayPairDialog == null) overlayPairBlocked = false;', probe)

    def test_unexpected_source_rejected(self):
        with self.assertRaises(RuntimeError): patch('')
        with self.assertRaises(RuntimeError): patch('private static final boolean OVERLAY_EXTERNAL_ENABLED = false;')

if __name__ == '__main__': unittest.main()
