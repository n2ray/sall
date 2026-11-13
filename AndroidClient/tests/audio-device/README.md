# Silent Android audio-device probe

This optional device test uses the repository's Mac `AudioEncoder` offline to generate 160
AAC-ELD packets for each of 480 and 512 frames from **zero-valued synthetic PCM**. It does
not access the Mac's microphone, system audio, Sill host, network or clipboard. The independent
decoder tests use the first 24 packets. A separate
`AudioTrack` receives only zero-valued float PCM and its own per-track volume is set to zero
before playback. The system volume is never changed.

The report compares three silent tracks: the original 960-frame/15-ms probe, a 240-frame/5-ms
fixed-delay approximation of the production pump, and the same cadence after prefill. It
retains the original first-two-timestamp rate check, and also reports stable-interval rate,
buffer size, playback head, underruns, start/first-write timing, and up to 16 timestamp samples.
Samples contain elapsed time, presented frame, timestamp age before and after the call, written
stereo frames, call duration and the production `AudioPresentationClock` state. The clock observes
the post-call time, matching the runtime. The two 240-frame scenarios call
the production bootstrap policy but do not reproduce the complete runtime or real decoded PCM;
their state is diagnostic evidence, not a full runtime verdict. It records no PCM.
The report also records whether the device advertises AAC-ELD and uses the same decoder selection
as the production player. For each packet size, it tests both the original complete Mac ESDS
cookie and the strictly parsed DecoderSpecificInfo/ASC inside
that descriptor. Each reports output frame and buffer counts and whether output buffer PTS are
contiguous at 48 kHz. A discontinuity or fewer than two decoded buffers is reported as a failure
to establish PTS continuity. An AAC-ELD decoder
that is not advertised is explicitly reported `UNSUPPORTED`, not considered verified.
A claimed decoder that fails to configure or decode is a test failure. These measurements
do **not** establish actual audible latency, acoustic sync, all route behavior, or the
decoder's behavior on non-silent media.

Four additional scenarios run the production `AudioRuntime` and `AndroidAudioDevice` with all
160 synthetic packets for each packet-size/CSD combination. They report packet and PCM counts,
production clock and placement state, written frames, underruns, playback head, buffer size and
timestamp samples. The input arrival and video timing are simulated: this does not establish
real-network behavior or audible synchronization. Decoded output is never passed to an audio
track: the device wrapper substitutes zero PCM on every write. The temporary project copy also
inserts a fail-closed per-track `setVolume(0)` before playback; a missing insertion anchor or a
failed mute aborts the test. The production source in the repository is not changed.

An additional probe exercises `AudioPlayer` itself over two synthetic sessions, including mute,
resume, and malformed or oversized packet input. It checks that the real playback clock returns to
READY and no player error is reported; it still does not verify audible sound or a real network.

Track diagnostics, all four AAC packet-size/CSD checks and all four runtime checks run independently, so a failed track
timestamp check does not hide decoder capabilities (and one failed configuration does not hide
another). Any failing check still makes the overall instrumentation result fail. Decoder
failures record the operation stage, bounded stack and any suppressed cleanup failure.

The script copies the project to a temporary directory and adds a local instrumentation
runner. It backs up the installed Sill APK, checks the temporary APK's signing identity,
compares Sill preference hashes without printing them, restores the original APK, and
verifies its hash on exit without launching Sill. The backup path is printed immediately before
the temporary replacement. Restore failure retains the original APK at that path.
The full raw instrumentation output is saved to a separate temporary report path and printed;
set `SILL_AUDIO_REPORT` to a new, non-existent path to choose its location. The report survives
probe failure and APK restoration.
Installations interrupt any Sill session. A process kill, device shutdown or lost wireless
connection can defeat automatic restoration; recover the retained APK with `adb install -r`.
Run only on an authorized debug device, with its original debug signing key available and
no concurrent Sill builds or device testing. The phone must remain awake.

```sh
ANDROID_HOME=/path/to/sdk JAVA_HOME=/path/to/jdk SILL_GRADLE=/path/to/gradle \
  python3 AndroidClient/tests/audio-device/run.py <adb-serial>
```

Generating AAC-ELD requires macOS AudioToolbox's encoder and may require running outside a
restricted sandbox. The script fails before touching the installed APK if the encoder or
any other preparation step is unavailable. The test is not part of `tests/run.sh`: it
requires the authorized physical device and can interrupt its app session.
The failure-aggregation logic can be checked without a device using
`sh AndroidClient/tests/audio-device/test-checks.sh`.
