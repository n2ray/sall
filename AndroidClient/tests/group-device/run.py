#!/usr/bin/env python3
"""Test synthetic group-name graphemes on Android and restore the installed APK."""
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
tmp = Path(tempfile.mkdtemp(prefix="sill-groups-probe-"))
backup = tmp / "original.apk"
replaced = False
restored = False

def run(args, timeout=180, cwd=None):
    return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)

def installed_path():
    output = run(adb + ["shell", "pm", "path", "io.github.n2ray.sall"]).decode().strip()
    if not output.startswith("package:/data/app/") or not output.endswith("/base.apk") or "\n" in output:
        raise RuntimeError("Expected one installed Sill APK")
    return output.removeprefix("package:")

def installed_hash():
    return run(adb + ["shell", "sha256sum", installed_path()]).decode().split()[0]

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
        raise RuntimeError("Installed APK changed while backing it up")
    project = tmp / "project"
    shutil.copytree(root, project, ignore=shutil.ignore_patterns("build", ".gradle", "artifacts"))
    manifest = project / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text().replace('</manifest>',
        '    <instrumentation android:name="io.github.n2ray.sall.GroupProbe" '
        'android:targetPackage="io.github.n2ray.sall" />\n</manifest>')
    manifest.write_text(text)
    shutil.copy(root / "tests/group-device/GroupProbe.java", project / "app/src/main/java/io/github/n2ray/sall/GroupProbe.java")
    run([gradle, "--no-daemon", "assembleDebug"], timeout=300, cwd=project)
    probe = project / "app/build/outputs/apk/debug/app-debug.apk"
    if signer(backup) != signer(probe) or installed_hash() != original_hash:
        raise RuntimeError("Probe signing identity or installed APK changed")
    replaced = True
    run(adb + ["install", "-r", probe])
    for phase in ("check",):
        run(adb + ["shell", "am", "force-stop", "io.github.n2ray.sall"])
        output = run(adb + ["shell", "am", "instrument", "-w", "-e", "phase", phase,
                            "io.github.n2ray.sall/io.github.n2ray.sall.GroupProbe"], timeout=90).decode()
        print(output, end="")
        if f"result=PASS: group graphemes {phase}" not in output or "INSTRUMENTATION_CODE: -1" not in output:
            raise RuntimeError(f"Group graphemes {phase} probe failed")
finally:
    if replaced:
        try:
            run(adb + ["install", "-r", backup])
            if installed_hash() != original_hash:
                raise RuntimeError("Restored APK differs from the original")
            restored = True
            run(adb + ["shell", "am", "start", "-n", "io.github.n2ray.sall/.MainActivity"])
            print("Original Sill APK restored and verified")
        except BaseException:
            print(f"Restore failed; original APK retained at {backup}", file=sys.stderr)
            raise
    if not replaced or restored:
        shutil.rmtree(tmp)
