#!/usr/bin/env python3
"""Replay identical stimuli into unchanged upstream Swift and Java, compare every decision.
Scenario families follow Tests/checks/audio-playout/main.swift; this is not a replacement
for its simulated output device or 5,000 randomized device runs. Output is pure scheduling.
"""
import math
import pathlib
import random
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[3]
out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else root / '.omo/evidence/audio-playout').resolve()
out.mkdir(parents=True, exist_ok=True)
commands = []
labels = []

def emit(*parts):
    commands.append(' '.join(map(str, parts)))

# The upstream cases: steady 480/512, both drift directions, clock/path steps,
# stalls, duplicates during handover, late joins, mute and engine restart.
for case in range(24):
    rng = random.Random(44219 + case)
    frames = 480 if case % 2 == 0 else 512
    dt = frames / 48000
    prime = frames // 2 if case % 3 else 0
    emit('new'); emit('format', 1, frames, prime, 9)
    emit('latency', [.011, .08, .15][case % 3], .005, 1 / 60, 60)
    emit('away', case % 2); emit('rtt', .012)
    emit('played', 0, 100, 100)
    seq = 0xfffffff0 if case == 0 else 0
    for k in range(12000 if case in (2, 3) else 3600):
        clock = 100 + k * dt
        stamp = clock - .02
        # Long enough to forget windows and converge the drift correction.
        if case in (4, 5) and k >= 1000: stamp += .8 if case == 4 else -.8
        if case in (6, 7) and k >= 1000: stamp += .08 if case == 6 else -.08
        jitter = rng.random() * (.025 if case >= 12 else .001)
        if case == 8 and k % 250 == 0: jitter += .09
        arrival = clock + jitter
        now = arrival
        if case == 9 and 1000 <= k < 1100: now += (1100 - k) * dt
        if case >= 12 and k % 797 == 0: now += .08  # engine queue, distinct arrival
        if k % 2 == 0:
            video_raw = .03 + (.12 if case == 10 and 1000 <= k < 2000 else 0)
            emit('frame', stamp, clock + video_raw)
        if k % 100 == 0:
            # Feed a changing actual render clock, as the native adapter must do.
            emit('played', (clock - 100) * 48000 * (1.0004 if case == 2 else .9996 if case == 3 else 1), clock, now)
        if k == 900 and case == 11: emit('reset', 'JUMP')
        if k == 1000 and case >= 12: emit('mute', 1)
        if k == 1100 and case >= 12:
            emit('mute', 0); emit('reset', 'PLAYBACK'); emit('played', 0, now, now)
        if k == 1700 and case >= 12: emit('failed')
        if k == 1800 and case >= 12: seq = (seq + 3) & 0xffffffff
        if k == 2000: emit('format', 1, frames, prime, 9)
        start = k == 0 or k == 2500
        emit('packet', 1, seq, stamp, int(start), arrival, now)
        if k % 250 == 50:
            emit('packet', 1, seq, stamp - 3, int(start), arrival + 1, now + 1)
        if k % 97 == 0: emit('tick', now)
        if k % 100 == 99:
            emit('stats', now); emit('jump'); emit('read', now)
        seq = (seq + 1) & 0xffffffff
    emit('tick', clock + 1); emit('stats', clock + 1); emit('read', clock + 15)
    # Idle belongs to AudioOutput: drive its playback reset, retain estimates and dedupe.
    emit('reset', 'PLAYBACK'); emit('read', clock + 15)
    emit('packet', 1, seq, clock + 15 - .02, 0, clock + 15, clock + 15)
    emit('played', 0, clock + 15, clock + 15)
    emit('packet', 1, (seq + 1) & 0xffffffff, clock + 15 + dt - .02, 0, clock + 15 + dt, clock + 15 + dt)
    emit('tick', clock + 16); emit('reset', 'SESSION'); emit('read', clock + 16)
    labels.append(f'case {case}: F={frames} prime={prime} completed')

# Startup queue bounds, wrong epoch, same epoch/cookie repeat, long priming spanning packets.
for prime in (240, 960, 1025):
    emit('new'); emit('format', 2, 480, prime, 8)
    for k in range(130): emit('packet', 2, k, k * .01, int(k == 0), k * .01 + .01, k * .01 + .01)
    emit('packet', 3, 0, 0, 1, 1.4, 1.4)
    emit('played', 0, 1.3, 1.3); emit('tick', 2); emit('stats', 2)
    emit('format', 2, 480, prime, 7); emit('packet', 2, 131, 2, 0, 2.01, 2.01)
    emit('reset', 'SESSION')

# Actual Swift PCM oracle, including unequal channels and unequal lengths.
for delta in (-1, 0, 1):
    for left, right in [([8, 6, 4, 0, 1, 3, 5, 7], [80, 60, 40, 0, 2, 30, 50, 70]),
                        ([1, 2, 3], [4, 5]),
                        ([math.sin(k * .1) for k in range(480)], [math.cos(k * .17) * .2 for k in range(512)])]:
        emit('change', delta, ','.join(map(str, left)), ','.join(map(str, right)))
for direction in (0, 1):
    for length in (100, 240, 480): emit('fade', direction, 240, ','.join(['1'] * length))

stimulus = out / 'commands.txt'
stimulus.write_text('\n'.join(commands) + '\n')
java_home = pathlib.Path('/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home')
classes = out / 'classes'; classes.mkdir(exist_ok=True)
subprocess.run([str(java_home / 'bin/javac'), '-d', str(classes),
                str(root / 'AndroidClient/app/src/main/java/io/github/n2ray/sill/AudioPlayout.java'),
                str(root / 'AndroidClient/tests/AudioPlayoutTrace.java')], check=True)
subprocess.run(['swiftc', '-module-cache-path', str(out / 'swift-module-cache'), '-O', str(root / 'iOSClient/AudioPlayout.swift'), str(pathlib.Path(__file__).with_name('main.swift')), '-o', str(out / 'swift-reference')], check=True)
for name, cmd in [('swift', [str(out / 'swift-reference')]), ('java', [str(java_home / 'bin/java'), '-cp', str(classes), 'io.github.n2ray.sill.AudioPlayoutTrace'])]:
    with stimulus.open() as stdin, (out / (name + '.txt')).open('w') as stdout:
        subprocess.run(cmd, stdin=stdin, stdout=stdout, check=True)

coverage = {key: 0 for key in ['placed', 'late', 'duplicates', 'added', 'dropped', 'jumps', 'joins', 'jumpDrops']}
with (out / 'swift.txt').open() as a, (out / 'java.txt').open() as b:
    for line_no, command in enumerate(commands, 1):
        aa, bb = a.readline().split(), b.readline().split()
        assert aa and len(aa) == len(bb), (line_no, command, aa, bb)
        for x, y in zip(aa, bb):
            if x == y: continue
            try:
                fx, fy = float(x), float(y)
                # Integer schedule fields are exactly equal; floating clocks tolerate representation.
                assert fx == fy or abs(fx - fy) <= 1e-9, (x, y)
            except (ValueError, AssertionError):
                raise AssertionError((line_no, command, aa, bb))
        if command.startswith('stats '):
            for idx, key in enumerate(coverage, 1): coverage[key] += int(bb[idx])
    assert not a.readline() and not b.readline(), 'unexpected extra output'
assert all(v > 0 for v in coverage.values()), coverage
report = '\n'.join(labels) + f'\nPASS: {len(commands)} commands; all schedule fields, plans, stats, jumps and observations match upstream Swift.\nCoverage totals: {coverage}\n'
(out / 'differential-result.txt').write_text(report)
print(report)
