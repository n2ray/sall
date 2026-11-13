#!/usr/bin/env python3
"""Use a unique synthetic Keystore namespace, then restore the installed Sill APK."""
import hashlib
import argparse
import os
import re
from pathlib import Path
import secrets
import shutil
import signal
import subprocess
import sys
import tempfile
from probe_sequence import AUTH_REQUIRED, SAME_PROCESS_FAILED, CleanupFailure, run_sequence
from device_lock import device_package_lock

if not os.environ.get("ANDROID_HOME"):
    raise SystemExit("Set ANDROID_HOME and JAVA_HOME before running the probe")
parser = argparse.ArgumentParser(description="Run the isolated synthetic preview probe")
parser.add_argument("serial", help="adb device serial")
parser.add_argument("--authenticate", action="store_true", help="show a system prompt if the synthetic key needs authentication")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]
apksigner = sdk / "build-tools/35.0.0/apksigner"
gradle = os.environ.get("SILL_GRADLE", "gradle")
with device_package_lock(args.serial, "io.github.n2ray.sill"):
    tmp = Path(tempfile.mkdtemp(prefix="sill-preview-probe-"))
    backup = tmp / "original.apk"
    token = secrets.token_hex(16)
    owner_nonce = secrets.token_hex(16)
    replaced = restored = False
    cleanup_error = None


    def run(args, timeout=180, cwd=None):
        return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)


    def installed_path():
        output = run(adb + ["shell", "pm", "path", "io.github.n2ray.sill"]).decode().strip()
        if not output.startswith("package:/data/app/") or not output.endswith("/base.apk") or "\n" in output:
            raise RuntimeError("Expected one installed Sill APK")
        return output.removeprefix("package:")


    def installed_hash():
        return run(adb + ["shell", "sha256sum", installed_path()]).decode().split()[0]


    def signer(path):
        lines = run([apksigner, "verify", "--print-certs", path]).decode().splitlines()
        return next(line.split(": ", 1)[1] for line in lines if "certificate SHA-256 digest:" in line)


    def phase(name):
        run(adb + ["shell", "am", "force-stop", "io.github.n2ray.sill"])
        output = run(adb + ["shell", "am", "instrument", "-w", "-e", "phase", name, "-e", "token", token, "-e", "owner", owner_nonce,
                            "io.github.n2ray.sill/io.github.n2ray.sill.PreviewProbe"], timeout=120 if name == "auth" else 90).decode()
        fields = dict(re.findall(r"(?m)^INSTRUMENTATION_RESULT: (phase_ms|crypto_ms|auth_type|exception)=([A-Za-z0-9_]+)\s*$", output))
        if fields.get("auth_type") not in (None, "BIOMETRIC", "DEVICE_CREDENTIAL", "UNKNOWN"):
            fields.pop("auth_type")
        print("phase=" + name, *(key + "=" + fields[key] for key in ("phase_ms", "crypto_ms", "auth_type", "exception") if key in fields), flush=True)
        if "INSTRUMENTATION_CODE: -1" not in output:
            raise RuntimeError(f"Synthetic preview {name} failed")
        if "result=SKIP:" in output:
            return False
        if "result=AUTH_REQUIRED:" in output:
            return AUTH_REQUIRED
        if name == "auth" and "result=SAME_PROCESS_FAILED" in output:
            return SAME_PROCESS_FAILED
        if f"result=PASS: synthetic preview {name}" not in output:
            raise RuntimeError(f"Synthetic preview {name} did not pass")
        return True


    def interrupted(_signum, _frame):
        raise KeyboardInterrupt()


    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    try:
        run(adb + ["pull", installed_path(), backup])
        original_hash = hashlib.sha256(backup.read_bytes()).hexdigest()
        if original_hash != installed_hash():
            raise RuntimeError("Installed APK changed while backing it up")
        project = tmp / "project"
        shutil.copytree(root, project, ignore=shutil.ignore_patterns("build", ".gradle", "artifacts"))
        manifest = project / "app/src/main/AndroidManifest.xml"
        text = manifest.read_text().replace("</application>",
            '    <activity android:name="io.github.n2ray.sill.PreviewAuthActivity" android:exported="false" '
            'android:configChanges="orientation|screenSize|keyboardHidden" />\n</application>').replace("</manifest>",
            '    <uses-permission android:name="android.permission.USE_BIOMETRIC" />\n'
            '    <instrumentation android:name="io.github.n2ray.sill.PreviewProbe" '
            'android:targetPackage="io.github.n2ray.sill" />\n</manifest>')
        manifest.write_text(text)
        for name in ("PreviewProbe.java", "PreviewAuthActivity.java"):
            shutil.copy(root / "tests/preview-device" / name, project / "app/src/main/java/io/github/n2ray/sill" / name)
        run([gradle, "--no-daemon", "assembleDebug"], timeout=300, cwd=project)
        probe = project / "app/build/outputs/apk/debug/app-debug.apk"
        if signer(backup) != signer(probe) or installed_hash() != original_hash:
            raise RuntimeError("Probe signing identity or installed APK changed")
        replaced = True
        run(adb + ["install", "-r", probe])
        try:
            outcome = run_sequence(phase, authenticate=args.authenticate)
            print("Synthetic preview outcome:", outcome, flush=True)
        except CleanupFailure as error:
            cleanup_error = error
            print(f"Synthetic cleanup failed; probe retained at {tmp}, synthetic namespace {token}, owner nonce {owner_nonce}",
                  file=sys.stderr, flush=True)
            raise
    finally:
        if replaced:
            try:
                run(adb + ["install", "-r", backup])
                if installed_hash() != original_hash:
                    raise RuntimeError("Restored APK differs from the original")
                restored = True
                run(adb + ["shell", "am", "start", "-n", "io.github.n2ray.sill/.MainActivity"])
                print("Original Sill APK restored and verified", flush=True)
            except BaseException:
                print(f"Restore failed; original APK retained at {backup}", file=sys.stderr, flush=True)
                raise
        if (not replaced or restored) and cleanup_error is None:
            shutil.rmtree(tmp)
        if cleanup_error is not None:
            raise RuntimeError("Synthetic preview cleanup failed after restoring Sill") from cleanup_error
