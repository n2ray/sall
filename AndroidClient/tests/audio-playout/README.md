# Audio playout port checks

`AudioPlayout.java` is a pure port of `iOSClient/AudioPlayout.swift`, not an active
AudioTrack adapter. It expects one packet's Mac timestamp (batch header timestamp
plus packet index × framesPerPacket / 48000), and a monotonic render timeline
supplied through `played`. All commands must be serialized by its future caller.

The ordinary Java suite runs `AudioPlayoutTest`, which transcribes the upstream
numeric floor/cover/picture-lag and delayed-tail scenarios, and checks sequence
wrap, gaps, mute, reset, waiting bounds, and channel-paired PCM edits.

On a Mac with Swift and JDK 21 installed:

```
python3 AndroidClient/tests/audio-playout/differential.py .omo/evidence/audio-playout
```

This compiles the **unchanged production Swift source** with a small command
adapter, sends identical seeded stimuli to Swift and Java, and checks every
returned field (plans, body/tail schedules, discards, observations, stats, jumps).
It saves the complete command stream and both output streams for reproduction.
Its scenario families follow `Tests/checks/audio-playout/main.swift`: 480/512,
priming, drift in both directions, positive/negative clock/path steps, stalls,
video-lag changes, duplicate handover, mute, gaps, decode failures, route jump,
engine startup queue and playback/session reset. The comparator requires nonzero
coverage of placement, late drops, dedupe, frame adds/drops, jumps, joins and jump
drops, so a vacuous match is a failure.

The original simulated output device and its 5,000 randomized runs have **not**
been ported here. This check establishes decision parity on the saved stimuli,
not actual Android audio/video synchronization. Android integration still needs
AudioTrack render-position/monotonic-time mapping, scheduled PCM buffering,
packet metadata retention, tail deadline dispatch, and the original
AudioOutput's 10-second engine-idle policy. Idle reset recovery is tested by
explicitly issuing PLAYBACK reset; the model must not invent an idle timer.
