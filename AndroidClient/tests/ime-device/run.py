#!/usr/bin/env python3
"""Run real InputConnection tests on an authorized phone, then restore its original APK."""
import hashlib
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile

if len(sys.argv) != 2 or not os.environ.get("ANDROID_HOME"):
    raise SystemExit("Usage: ANDROID_HOME=<sdk> JAVA_HOME=<jdk> python3 run.py <adb-serial>")

root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
adb = [str(sdk / "platform-tools/adb"), "-s", sys.argv[1]]
apksigner = sdk / "build-tools/35.0.0/apksigner"
gradle = os.environ.get("SILL_GRADLE", "gradle")
tmp = Path(tempfile.mkdtemp(prefix="sill-ime-device-"))
backup = tmp / "original.apk"
replaced = False
restored = False

def run(args, timeout=180, cwd=None):
    return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)

def installed_path():
    value = run(adb + ["shell", "pm", "path", "io.github.n2ray.sall"]).decode().strip()
    if not value.startswith("package:/data/app/") or not value.endswith("/base.apk") or "\n" in value:
        raise RuntimeError("Expected one installed Sill APK")
    return value.removeprefix("package:")

def installed_hash():
    return run(adb + ["shell", "sha256sum", installed_path()]).decode().split()[0]

def preferences_hash():
    data = run(adb + ["exec-out", "run-as", "io.github.n2ray.sall", "cat", "shared_prefs/MainActivity.xml"])
    return hashlib.sha256(data).hexdigest()

def signer(path):
    lines = run([apksigner, "verify", "--print-certs", path]).decode().splitlines()
    return next(line.split(": ", 1)[1] for line in lines if "certificate SHA-256 digest:" in line)

def interrupted(_signum, _frame):
    raise KeyboardInterrupt()

signal.signal(signal.SIGTERM, interrupted)
signal.signal(signal.SIGINT, interrupted)
try:
    run(adb + ["pull", installed_path(), backup])
    original_hash = hashlib.sha256(backup.read_bytes()).hexdigest()
    if original_hash != installed_hash():
        raise RuntimeError("Installed APK changed while being backed up")
    before = preferences_hash()
    project = tmp / "project"
    shutil.copytree(root, project, ignore=shutil.ignore_patterns("build", ".gradle", "artifacts"))
    manifest = project / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text()
    text = text.replace('<activity android:name=".PairScannerActivity"',
                        '<activity android:name=".ImeProbeActivity" android:exported="false" />\n'
                        '        <activity android:name=".PairScannerActivity"')
    text = text.replace('</manifest>',
                        '    <instrumentation android:name="io.github.n2ray.sall.ImeProbe" '
                        'android:targetPackage="io.github.n2ray.sall" />\n</manifest>')
    manifest.write_text(text)
    dest = project / "app/src/main/java/io/github/n2ray/sall"
    for name in ("ImeProbe.java", "ImeProbeActivity.java"):
        source = (root / "tests/ime-device" / name).read_text()
        (dest / name).write_text(source.replace("io.github.n2ray.sall.imeprobe", "io.github.n2ray.sall"))
    run([gradle, "--no-daemon", "assembleDebug"], timeout=300, cwd=project)
    probe = project / "app/build/outputs/apk/debug/app-debug.apk"
    if signer(backup) != signer(probe) or installed_hash() != original_hash:
        raise RuntimeError("Probe signing identity or installed APK changed")
    replaced = True  # Restore even if adb times out after installing.
    run(adb + ["install", "-r", probe])
    output = run(adb + ["shell", "am", "instrument", "-w", "io.github.n2ray.sall/io.github.n2ray.sall.ImeProbe"], timeout=90).decode()
    print(output, end="")
    if preferences_hash() != before:
        raise RuntimeError("Sill preferences changed during the probe")
    if ("result=PASS: real Android InputConnection replay and trim" not in output
            or "INSTRUMENTATION_CODE: -1" not in output):
        raise RuntimeError("Real Android InputConnection test failed")
finally:
    if replaced:
        try:
            run(adb + ["install", "-r", backup])
            if installed_hash() != original_hash:
                raise RuntimeError("Restored APK does not match the original")
            restored = True
            run(adb + ["shell", "am", "start", "-n", "io.github.n2ray.sall/.MainActivity"])
            print("Original Sill APK restored and verified")
        except BaseException:
            print(f"Restore failed; original APK retained at {backup}", file=sys.stderr)
            raise
    if not replaced or restored:
        shutil.rmtree(tmp)
