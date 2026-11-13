#!/usr/bin/env python3
"""Temporarily install a same-Mac-only external-link build and restore the APK.

No QR payload, fingerprint, clipboard, device logs, or screen contents are read.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import tempfile
import threading

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / 'preview-device'))
from device_lock import device_package_lock
from controlled_build import patch

PACKAGE = 'io.github.n2ray.sill'
parser = argparse.ArgumentParser(description='Temporarily test a same-Mac-only external pairing link')
parser.add_argument('serial', help='adb USB serial')
parser.add_argument('--seconds', type=int, default=300, help='test window, 30–600 seconds')
args = parser.parse_args()
if not 30 <= args.seconds <= 600 or not os.environ.get('ANDROID_HOME'):
    raise SystemExit('Set ANDROID_HOME and use --seconds between 30 and 600')
sdk = Path(os.environ['ANDROID_HOME'])
adb = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
apksigner = sdk / 'build-tools/35.0.0/apksigner'
aapt = sdk / 'build-tools/35.0.0/aapt'
root = HERE.parents[1]

def run(command, timeout=180, cwd=None):
    # Capture output; never print adb output, device logs, or shell content.
    return subprocess.check_output([str(x) for x in command], stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)

def installed_path():
    output = run(adb + ['shell', 'pm', 'path', PACKAGE]).decode().strip()
    if not output.startswith('package:/data/app/') or not output.endswith('/base.apk') or '\n' in output:
        raise RuntimeError('Expected exactly one installed Sill APK')
    return output.removeprefix('package:')

def installed_hash():
    value = run(adb + ['shell', 'sha256sum', installed_path()]).decode().split()[0]
    if not re.fullmatch('[0-9a-fA-F]{64}', value):
        raise RuntimeError('Could not verify installed APK')
    return value.lower()

def signer(path):
    output = run([apksigner, 'verify', '--print-certs', path]).decode().splitlines()
    values = [line.split(': ', 1)[1].lower() for line in output if 'certificate SHA-256 digest:' in line]
    if len(values) != 1 or not re.fullmatch('[0-9a-f]{64}', values[0]):
        raise RuntimeError('Could not verify a single APK signer')
    return values[0]

def apk_identity(path):
    badging = run([aapt, 'dump', 'badging', path]).decode()
    match = re.search(r"^package: name='([^']+)' versionCode='([0-9]+)'", badging, re.MULTILINE)
    if not match:
        raise RuntimeError('Could not verify APK package and version')
    return match.group(1), int(match.group(2))

def interrupted(_sig, _frame):
    raise KeyboardInterrupt()

with device_package_lock(args.serial, PACKAGE):
    tmp = Path(tempfile.mkdtemp(prefix='sill-live-overlay-'))
    backup = tmp / 'original.apk'
    replacement_attempted = False
    original_hash = None
    restore_verified = False
    signal.signal(signal.SIGINT, interrupted)
    signal.signal(signal.SIGTERM, interrupted)
    try:
        run(adb + ['pull', installed_path(), backup])
        original_hash = hashlib.sha256(backup.read_bytes()).hexdigest()
        if original_hash != installed_hash():
            raise RuntimeError('Installed APK changed during backup')
        print('Original APK backup:', backup, flush=True)
        print('If interrupted or disconnected, reconnect the phone and run:', flush=True)
        print(' '.join(shlex.quote(str(x)) for x in adb + ['install', '-r', backup]), flush=True)
        project = tmp / 'project'
        shutil.copytree(root, project, ignore=shutil.ignore_patterns('build', '.gradle', 'artifacts', '__pycache__'))
        activity = project / 'app/src/main/java/io/github/n2ray/sill/MainActivity.java'
        activity.write_text(patch(activity.read_text()))
        run([os.environ.get('SILL_GRADLE', 'gradle'), '--no-daemon', 'assembleDebug'], timeout=300, cwd=project)
        probe = project / 'app/build/outputs/apk/debug/app-debug.apk'
        original_package, original_version = apk_identity(backup)
        probe_package, probe_version = apk_identity(probe)
        if original_package != PACKAGE or probe_package != PACKAGE:
            raise RuntimeError('Unexpected APK package')
        if probe_version > original_version:
            raise RuntimeError('Probe version is newer; normal restore could be blocked')
        if signer(backup) != signer(probe):
            raise RuntimeError('Probe is not signed by the installed APK signer')
        if original_hash != installed_hash():
            raise RuntimeError('Installed APK changed during build')
        probe_hash = hashlib.sha256(probe.read_bytes()).hexdigest()
        # Set before install: even an interrupted or ambiguous install must trigger restore.
        replacement_attempted = True
        run(adb + ['install', '-r', probe], timeout=120)
        if installed_hash() != probe_hash:
            raise RuntimeError('Probe installation could not be verified')
        print('Temporary same-Mac-only build installed. Open Sill, connect the Mac, then use the system camera to open its pairing link and confirm it yourself.', flush=True)
        print(f'Original APK restoration starts after {args.seconds} seconds; Ctrl-C starts it sooner.', flush=True)
        threading.Event().wait(args.seconds)
    finally:
        # Further Ctrl-C/SIGTERM must not interrupt the restoration attempt.
        signal.signal(signal.SIGINT, signal.SIG_IGN)
        signal.signal(signal.SIGTERM, signal.SIG_IGN)
        if replacement_attempted:
            try:
                run(adb + ['install', '-r', backup], timeout=120)
                if installed_hash() != original_hash:
                    raise RuntimeError('Restored APK hash differs')
                restore_verified = True
                print('Original APK restored and verified.', flush=True)
            except BaseException:
                print('RESTORE FAILED: original APK retained at', backup, file=sys.stderr, flush=True)
                print('Reconnect the same phone and run:', ' '.join(shlex.quote(str(x)) for x in adb + ['install', '-r', backup]), file=sys.stderr, flush=True)
        else:
            restore_verified = True
        if restore_verified:
            shutil.rmtree(tmp)
        else:
            raise RuntimeError('Could not verify restoration; retain the backup and reconnect the phone')
