#!/usr/bin/env python3
"""Run silent local audio-device checks and restore the installed Sill APK."""
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
repo = root.parent
sdk = Path(os.environ["ANDROID_HOME"])
adb = [str(sdk / "platform-tools/adb"), "-s", sys.argv[1]]
apksigner = sdk / "build-tools/35.0.0/apksigner"
gradle = os.environ.get("SILL_GRADLE", "gradle")
tmp = Path(tempfile.mkdtemp(prefix="sill-audio-device-"))
backup = tmp / "original.apk"
replaced = False
restored = False


def run(args, timeout=180, cwd=None, env=None):
    return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT,
                                   timeout=timeout, cwd=cwd, env=env)


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


def save_report(raw):
    wanted = os.environ.get("SILL_AUDIO_REPORT")
    if wanted:
        path = Path(wanted)
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    else:
        fd, name = tempfile.mkstemp(prefix="sill-audio-device-", suffix=".log")
        path = Path(name)
    with os.fdopen(fd, "wb") as file:
        file.write(raw or b"")
    print(f"Full device report: {path}", flush=True)


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

    # Compile the repository's encoder and generate only zero-valued synthetic audio.
    main = tmp / "main.swift"
    shutil.copy(root / "tests/audio-device/SilenceFixture.swift", main)
    generator = tmp / "fixture"
    env = dict(os.environ, CLANG_MODULE_CACHE_PATH=str(tmp / "clang-cache"))
    run(["swiftc", "-O", repo / "Sources/SillHost/AudioEncoder.swift", main, "-o", generator], env=env, timeout=180)
    assets = project / "app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    run([generator, assets / "audio-silence.properties"], timeout=60)

    manifest = project / "app/src/main/AndroidManifest.xml"
    text = manifest.read_text()
    marker = "</manifest>"
    if text.count(marker) != 1:
        raise RuntimeError("Cannot add instrumentation")
    manifest.write_text(text.replace(marker, '    <instrumentation android:name="io.github.n2ray.sall.AudioDeviceProbe" '
                                     'android:targetPackage="io.github.n2ray.sall" />\n</manifest>'))
    dest = project / "app/src/main/java/io/github/n2ray/sall"
    for name in ("AudioDeviceProbe.java", "AudioProbeChecks.java", "AudioProbeClock.java", "AudioRuntimeProbe.java", "AudioPlayerProbe.java", "EsdsDecoderSpecificInfo.java"):
        shutil.copy(root / "tests/audio-device" / name, dest / name)
    device = dest / "AndroidAudioDevice.java"
    source = device.read_text()
    anchor = "track.play(); route = null;"
    if source.count(anchor) != 1:
        raise RuntimeError("Cannot guarantee silent AudioRuntime test track")
    device.write_text(source.replace(anchor,
        'if (track.setVolume(0f) != AudioTrack.SUCCESS) throw new IllegalStateException("silent track failed");\n'
        '        ' + anchor))
    run([gradle, "--no-daemon", "assembleDebug"], timeout=300, cwd=project)
    probe = project / "app/build/outputs/apk/debug/app-debug.apk"
    if signer(backup) != signer(probe) or installed_hash() != original_hash:
        raise RuntimeError("Probe signing identity or installed APK changed")
    print(f"Original APK backup: {backup}", flush=True)
    replaced = True
    run(adb + ["install", "-r", probe])
    try:
        raw = run(adb + ["shell", "am", "instrument", "-w", "io.github.n2ray.sall/io.github.n2ray.sall.AudioDeviceProbe"], timeout=90)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as failure:
        save_report(failure.output)
        raise
    save_report(raw)
    output = raw.decode(errors="replace")
    print(output, end="")
    if preferences_hash() != before:
        raise RuntimeError("Sill preferences changed during the probe")
    if "result=PASS: silent AudioTrack" not in output or "INSTRUMENTATION_CODE: -1" not in output:
        raise RuntimeError("Silent device audio test failed")
finally:
    if replaced:
        try:
            run(adb + ["install", "-r", backup])
            if installed_hash() != original_hash:
                raise RuntimeError("Restored APK does not match the original")
            restored = True
            print("Original Sill APK restored and verified")
        except BaseException:
            print(f"Restore failed; original APK retained at {backup}", file=sys.stderr)
            raise
    if not replaced or restored:
        shutil.rmtree(tmp)
