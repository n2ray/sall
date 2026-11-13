#!/usr/bin/env python3
"""Run a synthetic wire probe in Android's runtime without installing an app or reading user data."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid

if len(sys.argv) != 2 or not os.environ.get('ANDROID_HOME') or not os.environ.get('JAVA_HOME'):
    raise SystemExit('Usage: ANDROID_HOME=<sdk> JAVA_HOME=<jdk> python3 run.py <adb-serial>')
root = Path(__file__).resolve().parents[2]
sdk, java = Path(os.environ['ANDROID_HOME']), Path(os.environ['JAVA_HOME'])
adb = [str(sdk / 'platform-tools/adb'), '-s', sys.argv[1]]
classes = root / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
if not classes.is_dir(): raise SystemExit('Build the Android debug app first')
remote = '/data/local/tmp/sill-wire-' + uuid.uuid4().hex + '.dex'
def run(args, **kwargs):
    return subprocess.check_output([str(x) for x in args], stderr=subprocess.STDOUT, timeout=60, **kwargs)
with tempfile.TemporaryDirectory(prefix='sill-wire-') as directory:
    tmp = Path(directory); (tmp / 'classes').mkdir(); (tmp / 'dex').mkdir()
    api = sdk / 'platforms/android-35/android.jar'
    run([java / 'bin/javac', '-cp', str(classes) + os.pathsep + str(api), '-d', tmp / 'classes', root / 'tests/home-pairing-device/WireProbe.java'])
    run([java / 'bin/jar', 'cf', tmp / 'app.jar', '-C', classes, '.'])
    run([sdk / 'build-tools/35.0.0/d8', '--lib', api, '--min-api', '29', '--output', tmp / 'dex', tmp / 'app.jar', tmp / 'classes/io/github/n2ray/sall/WireProbe.class'])
    try:
        run(adb + ['push', tmp / 'dex/classes.dex', remote])
        output = run(adb + ['shell', 'dalvikvm', '-cp', remote, 'io.github.n2ray.sall.WireProbe']).decode()
        if 'PASS synthetic ask, cancel, bounded busy retry and unverified cable rejection' not in output: raise RuntimeError('Probe did not pass')
        print(output, end='')
    finally:
        run(adb + ['shell', 'rm', '-f', remote])
