#!/usr/bin/env python3
"""Build a random-package open-door probe; never replace or read the user's Sill data."""
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

if len(sys.argv) not in (2, 3) or (len(sys.argv) == 3 and sys.argv[2] != '--inspect') or not os.environ.get('ANDROID_HOME'):
    raise SystemExit('Usage: ANDROID_HOME=<sdk> JAVA_HOME=<jdk> python3 run.py <adb-serial>')
root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ['ANDROID_HOME'])
adb = [str(sdk / 'platform-tools/adb'), '-s', sys.argv[1]]
probe_id = 'io.github.n2ray.sall.openprobe' + uuid.uuid4().hex
tmp = Path(tempfile.mkdtemp(prefix='sill-open-probe-'))
backup = tmp / 'original.apk'
install_attempted = False

def run(args, timeout=180, cwd=None):
    return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT, timeout=timeout, cwd=cwd)

def installed_path():
    result = run(adb + ['shell', 'pm', 'path', 'io.github.n2ray.sall']).decode().strip()
    if not result.startswith('package:/data/app/') or not result.endswith('/base.apk') or '\n' in result:
        raise RuntimeError('Unexpected installed Sill APK')
    return result.removeprefix('package:')

def installed_hash():
    return run(adb + ['shell', 'sha256sum', installed_path()]).decode().split()[0]

def interrupted(_signal, _frame):
    raise KeyboardInterrupt()

signal.signal(signal.SIGINT, interrupted)
signal.signal(signal.SIGTERM, interrupted)
try:
    packages = run(adb + ['shell', 'pm', 'list', 'packages', probe_id]).decode().splitlines()
    if 'package:' + probe_id in packages:
        raise RuntimeError('Random probe package already exists')
    run(adb + ['pull', installed_path(), backup])
    original = hashlib.sha256(backup.read_bytes()).hexdigest()
    if original != installed_hash(): raise RuntimeError('Original APK changed during backup')
    project = tmp / 'project'
    shutil.copytree(root, project, ignore=shutil.ignore_patterns('build', '.gradle', 'artifacts', '__pycache__'))
    build = project / 'app/build.gradle'
    text = build.read_text()
    if text.count("applicationId 'io.github.n2ray.sall'") != 1: raise RuntimeError('Unexpected app ID')
    build.write_text(text.replace("applicationId 'io.github.n2ray.sall'", f"applicationId '{probe_id}'"))
    manifest = project / 'app/src/main/AndroidManifest.xml'
    text = manifest.read_text().replace('android:label="Sall"', 'android:label="Sall 隔离测试"')
    text = text.replace('io.github.n2ray.sall.permission.PIP_CONTROL', probe_id + '.permission.PIP_CONTROL')
    start = text.index('            <intent-filter>\n                <action android:name="android.intent.action.VIEW"')
    end = text.index('            </intent-filter>', start) + len('            </intent-filter>')
    text = text[:start] + text[end:]
    text = text.replace('</manifest>', f'    <instrumentation android:name="io.github.n2ray.sall.OpenProbe" android:targetPackage="{probe_id}" />\n</manifest>')
    manifest.write_text(text)
    shutil.copy(root / 'tests/open-device/OpenProbe.java', project / 'app/src/main/java/io/github/n2ray/sall/OpenProbe.java')
    run([os.environ.get('SILL_GRADLE', 'gradle'), '--no-daemon', 'assembleDebug'], timeout=300, cwd=project)
    apk = project / 'app/build/outputs/apk/debug/app-debug.apk'
    aapt = sdk / 'build-tools/35.0.0/aapt'
    info = run([aapt, 'dump', 'badging', apk]).decode()
    if not re.search(r"^package: name='" + re.escape(probe_id) + r"'", info, re.MULTILINE): raise RuntimeError('Unexpected probe package')
    xml = run([aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml']).decode()
    if re.search(r'android:scheme\([^\n]*\)="sill"', xml): raise RuntimeError('Probe handles real pairing links')
    if original != installed_hash(): raise RuntimeError('Original APK changed before test')
    print('READY: isolated package built; waiting for go or cancel', flush=True)
    if input().strip() != 'go':
        print('Cancelled before installation', flush=True)
    else:
        install_attempted = True
        run(adb + ['install', apk], timeout=90)
        out = run(adb + ['shell', 'am', 'instrument', '-w'] + (['-e', 'mode', 'inspect'] if len(sys.argv) == 3 else []) + [f'{probe_id}/io.github.n2ray.sall.OpenProbe'], timeout=120).decode()
        print(out, end='', flush=True)
        if ('result=PASS: inspect ' if len(sys.argv) == 3 else 'result=PASS: real local open TLS, presented frame, no saved identity') not in out or 'INSTRUMENTATION_CODE: -1' not in out:
            raise RuntimeError('Open-door probe did not pass')
finally:
    clean = True
    if install_attempted:
        try: run(adb + ['uninstall', probe_id])
        except BaseException:
            try:
                if 'package:' + probe_id in run(adb + ['shell', 'pm', 'list', 'packages', probe_id]).decode().splitlines():
                    raise RuntimeError('Probe remains installed')
            except BaseException:
                clean = False; print('Probe cleanup failed:', probe_id, file=sys.stderr, flush=True)
    try:
        if backup.exists() and hashlib.sha256(backup.read_bytes()).hexdigest() != installed_hash():
            clean = False; print('Original APK changed; backup retained:', backup, file=sys.stderr, flush=True)
    except BaseException:
        clean = False; print('Original APK verification failed; backup retained:', backup, file=sys.stderr, flush=True)
    if clean:
        shutil.rmtree(tmp)
        print('Isolated probe removed; original APK verified', flush=True)
    else: raise RuntimeError('Probe cleanup could not be verified')
