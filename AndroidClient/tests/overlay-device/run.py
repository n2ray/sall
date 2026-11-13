#!/usr/bin/env python3
"""Build an isolated, network-free overlay probe; never replace the user's app."""
import hashlib
import os
import re
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import uuid

if len(sys.argv) != 2 or not os.environ.get("ANDROID_HOME"):
    raise SystemExit("Usage: ANDROID_HOME=<sdk> JAVA_HOME=<jdk> python3 run.py <adb-serial>")
root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ["ANDROID_HOME"])
adb = [str(sdk / "platform-tools/adb"), "-s", sys.argv[1]]
probe_id = "io.github.n2ray.sall.overlayprobe" + uuid.uuid4().hex
tmp = Path(tempfile.mkdtemp(prefix="sill-overlay-probe-"))
backup = tmp / "original.apk"
installed_probe = False

def run(args, timeout=180, cwd=None):
    try:
        return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)
    except subprocess.CalledProcessError as error:
        sys.stderr.write(error.output.decode(errors="replace"))
        raise

def original_path():
    output = run(adb + ["shell", "pm", "path", "io.github.n2ray.sall"]).decode().strip()
    if not output.startswith("package:/data/app/") or not output.endswith("/base.apk") or "\n" in output:
        raise RuntimeError("Expected one installed Sill APK")
    return output.removeprefix("package:")

def original_hash():
    return run(adb + ["shell", "sha256sum", original_path()]).decode().split()[0]

def interrupted(_sig, _frame):
    raise KeyboardInterrupt()

signal.signal(signal.SIGINT, interrupted)
signal.signal(signal.SIGTERM, interrupted)
try:
    packages = run(adb + ["shell", "pm", "list", "packages", probe_id]).decode().splitlines()
    if f"package:{probe_id}" in packages:
        raise RuntimeError("Probe package already installed; refusing to overwrite it")
    run(adb + ["pull", original_path(), backup])
    backup_hash = hashlib.sha256(backup.read_bytes()).hexdigest()
    if backup_hash != original_hash():
        raise RuntimeError("Installed APK changed during backup")
    project = tmp / "project"
    shutil.copytree(root, project, ignore=shutil.ignore_patterns("build", ".gradle", "artifacts"))
    build = project / "app/build.gradle"
    build_text = build.read_text()
    if build_text.count("applicationId 'io.github.n2ray.sall'") != 1:
        raise RuntimeError("Unexpected application ID declaration")
    build.write_text(build_text.replace("applicationId 'io.github.n2ray.sall'", f"applicationId '{probe_id}'"))
    manifest = project / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text()
    for permission in ("INTERNET", "CAMERA"):
        old = f'<uses-permission android:name="android.permission.{permission}" />'
        if text.count(old) != 1:
            raise RuntimeError(f"Unexpected {permission} permission declaration")
        text = text.replace(old, f'<uses-permission android:name="android.permission.{permission}" tools:node="remove" />')
    text = text.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
                        '<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">')
    if 'xmlns:tools=' not in text:
        raise RuntimeError("Failed to add manifest tools namespace")
    text = text.replace('io.github.n2ray.sall.permission.PIP_CONTROL', probe_id + '.permission.PIP_CONTROL')
    # Never register a second handler for the user's external pairing links.
    start = text.index('            <intent-filter>\n                <action android:name="android.intent.action.VIEW"')
    end = text.index('            </intent-filter>', start) + len('            </intent-filter>')
    text = text[:start] + text[end:]
    text = text.replace('</manifest>', f'    <instrumentation android:name="io.github.n2ray.sall.OverlayProbe" android:targetPackage="{probe_id}" />\n</manifest>')
    manifest.write_text(text)
    java = project / "app/src/main/java/io/github/n2ray/sall"
    activity = java / "MainActivity.java"
    activity_source = activity.read_text()
    if activity_source.count('discovery = new MacDiscovery(this, new MacDiscovery.Listener() {') != 1:
        raise RuntimeError("Unexpected discovery construction")
    if activity_source.count('discovery.start();') != 2:
        raise RuntimeError("Unexpected discovery start sites")
    activity.write_text(activity_source.replace('discovery.start();', '/* probe: discovery disabled */;'))
    discovery = java / "MacDiscovery.java"
    discovery_source = discovery.read_text()
    if discovery_source.count('MacDiscovery(Context context, Listener listener) {') != 1 or discovery_source.count('context.getSystemService(Context.NSD_SERVICE)') != 1:
        raise RuntimeError("Unexpected discovery implementation")
    shutil.copy(root / "tests/overlay-device/ProbeMacDiscovery.java", discovery)
    scanner = java / "InlinePairScanner.java"
    scanner_source = scanner.read_text()
    if scanner_source.count('boolean hardware = activity.getPackageManager()') != 1:
        raise RuntimeError("Unexpected camera probe site")
    scanner.write_text(scanner_source.replace('boolean hardware = activity.getPackageManager()', 'boolean hardware = false && activity.getPackageManager()'))
    connection = java / "SillConnection.java"
    source = connection.read_text()
    marker = '    static SillConnection connect(String host, int port, String alpn, byte[] pin) throws Exception {'
    if marker not in source:
        raise RuntimeError("Unexpected SillConnection source")
    source = source.replace(marker, '    static volatile boolean overlayProbeFakePair;\n    static SillConnection overlayProbeSession() { return new SillConnection(null, new byte[32], new byte[32]); }\n\n' + marker, 1)
    dial_marker = '                                  RemoteDial.Attempt attempt, android.net.Network network) throws Exception {\n        KeyStore keys'
    proof_marker = '    SavedMacs.Identity pairQrIdentity(byte[] secret, String deviceName, String model) throws Exception {\n        if (secret == null'
    close_marker = '    public void close() throws IOException { socket.close(); }'
    if source.count(dial_marker) != 1 or source.count(proof_marker) != 1 or source.count(close_marker) != 1:
        raise RuntimeError("Unexpected pairing probe seams")
    source = source.replace(dial_marker, '                                  RemoteDial.Attempt attempt, android.net.Network network) throws Exception {\n        if (overlayProbeFakePair && host.equals("192.0.2.1") && pin != null) return new SillConnection(null, new byte[32], pin.clone());\n        KeyStore keys', 1)
    source = source.replace(proof_marker, '    SavedMacs.Identity pairQrIdentity(byte[] secret, String deviceName, String model) throws Exception {\n        if (overlayProbeFakePair && socket == null) return SavedMacs.Identity.migrated(peerFingerprint);\n        if (secret == null', 1)
    source = source.replace(close_marker, '    public void close() throws IOException { if (socket != null) socket.close(); }', 1)
    connection.write_text(source)
    shutil.copy(root / "tests/overlay-device/OverlayProbe.java", java / "OverlayProbe.java")
    run([os.environ.get("SILL_GRADLE", "gradle"), "--no-daemon", "assembleDebug"], timeout=300, cwd=project)
    apk = project / "app/build/outputs/apk/debug/app-debug.apk"
    aapt = sdk / "build-tools/35.0.0/aapt"
    permissions = run([aapt, "dump", "permissions", apk]).decode()
    for permission in ("android.permission.INTERNET", "android.permission.CAMERA"):
        if permission in permissions:
            raise RuntimeError(f"Probe unexpectedly requests {permission}")
    badging = run([aapt, "dump", "badging", apk]).decode()
    if not re.search(r"^package: name='" + re.escape(probe_id) + r"'", badging, re.MULTILINE):
        raise RuntimeError("Probe APK has unexpected package name")
    xml = run([aapt, "dump", "xmltree", apk, "AndroidManifest.xml"]).decode()
    if re.search(r'android:scheme\([^\n]*\)="sill"', xml):
        raise RuntimeError("Probe APK unexpectedly handles sill links")
    if backup_hash != original_hash():
        raise RuntimeError("Installed Sill changed before probe")
    installed_probe = True
    run(adb + ["install", apk])
    output = run(adb + ["shell", "am", "instrument", "-w", f"{probe_id}/io.github.n2ray.sall.OverlayProbe"], timeout=90).decode()
    print(output, end="")
    if "result=PASS: synthetic overlay" not in output or "INSTRUMENTATION_CODE: -1" not in output:
        raise RuntimeError("Synthetic overlay probe failed")
    capture = os.environ.get("SILL_UI_CAPTURE")
    if capture:
        data = run(adb + ["exec-out", "run-as", probe_id, "cat", "files/synthetic-dialog.png"])
        Path(capture).write_bytes(data)
        print(f"Synthetic dialog capture: {capture}")
finally:
    cleanup_ok = True
    if installed_probe:
        try:
            run(adb + ["uninstall", probe_id])
        except BaseException:
            try:
                packages = run(adb + ["shell", "pm", "list", "packages", probe_id]).decode().splitlines()
                if f"package:{probe_id}" in packages:
                    raise RuntimeError("Probe package remains installed")
            except BaseException:
                cleanup_ok = False
                print(f"Probe cleanup failed; remove only {probe_id}; backup retained at {backup}", file=sys.stderr)
    try:
        if backup.exists() and hashlib.sha256(backup.read_bytes()).hexdigest() != original_hash():
            cleanup_ok = False
            print(f"Original APK changed; backup retained at {backup}", file=sys.stderr)
    except BaseException:
        cleanup_ok = False
        print(f"Could not verify original APK; backup retained at {backup}", file=sys.stderr)
    if cleanup_ok:
        shutil.rmtree(tmp)
    else:
        raise RuntimeError("Overlay probe cleanup or original verification failed")
