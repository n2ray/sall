#!/usr/bin/env python3
"""Exercise backup/restore behavior with fake commands; never runs adb or audio."""
import contextlib
import hashlib
import io
import os
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

RUNNER = Path(__file__).with_name("run.py")


class RunnerTests(unittest.TestCase):
    def exercise(self, *, bad_probe=False, bad_restore=False, instrument_error=False, instrument_timeout=False, bad_anchor=False):
        original = b"installed apk"
        probe = b"temporary probe"
        state = {"apk": original, "installs": 0, "commands": [], "backup_before_install": False, "muted_before_play": False}
        output = io.StringIO()
        with tempfile.TemporaryDirectory(prefix="audio-runner-test-") as parent:
            work = Path(parent) / "work"
            work.mkdir()
            backup = work / "original.apk"

            def copytree(_source, target, **_kwargs):
                target = Path(target)
                dest = target / "app/src/main/java/io/github/n2ray/sall"
                dest.mkdir(parents=True)
                (target / "app/src/main/AndroidManifest.xml").write_text("<manifest></manifest>")
                (dest / "AndroidAudioDevice.java").write_text("track.unexpected();" if bad_anchor else "track.play(); route = null;")
                return str(target)

            def command(args, **kwargs):
                args = list(map(str, args))
                state["commands"].append(args)
                if args[0].endswith("apksigner"):
                    return b"Signer #1 certificate SHA-256 digest: same-signer\n"
                if args[0] == "swiftc" or args[0] == str(work / "fixture"):
                    return b""
                if args[0] == "fake-gradle":
                    source = (Path(kwargs["cwd"]) / "app/src/main/java/io/github/n2ray/sall/AndroidAudioDevice.java").read_text()
                    state["muted_before_play"] = "track.setVolume(0f)" in source and source.index("track.setVolume(0f)") < source.index("track.play()")
                    apk = Path(kwargs["cwd"]) / "app/build/outputs/apk/debug/app-debug.apk"
                    apk.parent.mkdir(parents=True)
                    apk.write_bytes(probe)
                    return b""
                if "pm" in args:
                    return b"package:/data/app/example/base.apk\n"
                if "sha256sum" in args:
                    return (hashlib.sha256(state["apk"]).hexdigest() + "  base.apk\n").encode()
                if "pull" in args:
                    Path(args[-1]).write_bytes(state["apk"])
                    return b""
                if "exec-out" in args:
                    return b"<map/>"
                if "install" in args:
                    state["installs"] += 1
                    if state["installs"] == 1:
                        state["backup_before_install"] = str(backup) in output.getvalue() and backup.read_bytes() == original
                    elif bad_restore:
                        raise subprocess.CalledProcessError(1, args)
                    state["apk"] = Path(args[-1]).read_bytes()
                    return b"Success\n"
                if "instrument" in args:
                    if instrument_error:
                        raise subprocess.CalledProcessError(1, args, output=b"INSTRUMENTATION_RESULT: command error\n")
                    if instrument_timeout:
                        raise subprocess.TimeoutExpired(args, 90, output=b"INSTRUMENTATION_RESULT: partial timeout output\n")
                    if bad_probe:
                        return (b"INSTRUMENTATION_RESULT: result=FAIL: silent AudioTrack FAIL: rate; "
                                b"AAC-ELD 480 PASS: decoded; AAC-ELD 512 PASS: decoded\nINSTRUMENTATION_CODE: -1\n")
                    return b"INSTRUMENTATION_RESULT: result=PASS: silent AudioTrack; AAC-ELD UNSUPPORTED\nINSTRUMENTATION_CODE: -1\n"
                raise AssertionError("Unexpected command: " + " ".join(args))

            report = Path(parent) / "report.log"
            env = {"ANDROID_HOME": "/fake/sdk", "JAVA_HOME": "/fake/jdk", "SILL_GRADLE": "fake-gradle",
                   "SILL_AUDIO_REPORT": str(report)}
            error = None
            with patch.dict(os.environ, env, clear=False), patch.object(sys, "argv", [str(RUNNER), "fake-device"]), \
                    patch("subprocess.check_output", side_effect=command), patch("shutil.copytree", side_effect=copytree), \
                    patch("tempfile.mkdtemp", return_value=str(work)), contextlib.redirect_stdout(output), \
                    contextlib.redirect_stderr(output):
                try:
                    runpy.run_path(str(RUNNER), run_name="__main__")
                except Exception as failure:
                    error = failure
            self.assertFalse(any("am" in c and "start" in c for c in state["commands"]))
            if bad_anchor:
                self.assertIsInstance(error, RuntimeError)
                self.assertEqual(state["installs"], 0)
                self.assertEqual(state["apk"], original)
                self.assertFalse(report.exists())
                return
            self.assertTrue(state["backup_before_install"])
            self.assertTrue(state["muted_before_play"])
            self.assertIn("INSTRUMENTATION_RESULT", report.read_text())
            self.assertIn(str(report), output.getvalue())
            if bad_restore:
                self.assertIsInstance(error, subprocess.CalledProcessError)
                self.assertTrue(backup.exists())
                self.assertIn(str(backup), output.getvalue())
            else:
                self.assertEqual(state["apk"], original)
                self.assertEqual(work.exists(), False)
                if instrument_error:
                    self.assertIsInstance(error, subprocess.CalledProcessError)
                    self.assertIn("command error", report.read_text())
                elif instrument_timeout:
                    self.assertIsInstance(error, subprocess.TimeoutExpired)
                    self.assertIn("partial timeout output", report.read_text())
                elif bad_probe:
                    self.assertIsInstance(error, RuntimeError)
                else:
                    self.assertIsNone(error)
                    self.assertIn("Original Sill APK restored and verified", output.getvalue())

    def test_pass_restores_without_launching(self):
        self.exercise()

    def test_failed_probe_still_restores(self):
        self.exercise(bad_probe=True)

    def test_failed_restore_retains_backup(self):
        self.exercise(bad_restore=True)

    def test_instrumentation_error_saves_output_and_restores(self):
        self.exercise(instrument_error=True)

    def test_instrumentation_timeout_saves_partial_output_and_restores(self):
        self.exercise(instrument_timeout=True)

    def test_missing_silent_track_anchor_never_installs(self):
        self.exercise(bad_anchor=True)


if __name__ == "__main__":
    unittest.main()
