# Asynchronous decoder mapping contract

`AudioDecodeMapping` is an independent, pure-Java adapter building block. It is
not connected to `AudioPlayer`, `MediaCodec`, `AudioTrack` or the UI.

1. Expand a parsed batch with its wire header's Mac timestamp. The packet stamp
   is `header + index * framesPerPacket / 48000`; sequence wraps at UInt32 and
   only the batch's first packet inherits the segment-start flag.
2. Feed each packet to `AudioPlayout.packet` and retain the resulting plan. A
   decode-false plan consumes no decoder input or PTS. For reset-first plans,
   finish the previous decoder segment, or explicitly flush and account for
   abandoned IDs. Flush both the real decoder and mapping. Queue the new plan
   only after that boundary. The model owns priming: the mapping never guesses
   a second priming amount or drops the same priming twice.
3. `queue` returns an input with compressed bytes, packet identity, generation
   and rational sample-clock PTS. The PTS is an opaque decoder timeline, not a
   converted Mac wall-clock timestamp. Sample cursor conversion avoids drift
   from repeatedly truncating the duration of 512-frame packets.
4. Each output callback must carry the generation **captured by its decoder
   session** and the PTS of its first PCM frame. Forward the codec's absolute
   buffer offset/size and negotiated encoding (after verifying 48 kHz stereo). Split buffers, merged packets and
   reordered nonoverlapping PCM spans are supported. Completed packet PCM is
   returned with model ID and kept-frame stamp, after dropping plan.dropFront.
5. The caller owns returned PCM and later schedule execution. Completion order
   need not be packet order: store by model ID, never assume a FIFO callback.
   Decoder codec-config buffers are not PCM and must be filtered by the caller.
6. On mapping failure, every still-pending kept ID is reported discarded and the
   generation advances. Reset/flush the real decoder before resuming, inform the
   playout model of decode loss, and resolve abandoned IDs. Capacity errors are
   explicit exceptions and leave the existing queue intact; they are never a
   silent discard. `drained` is only for a confirmed decoder EOS/drain, not a
   transient empty output queue.

The contract requires a decoder producing exactly the format's decoded frames
per packet with meaningful first-sample PTS. A codec that silently strips
priming, shifts timestamps, or emits padding needs a separately verified
normalization adapter; this class will not infer packet identity from arrival
order. Reusing the current generation for callbacks queued before a flush is
invalid: callback/session ownership must prevent that in the Android adapter.

`AudioDecodeMappingTest` provides a fake asynchronous decoder closed loop with
AudioPlayout plans and checks fixed PCM values, arbitrary split/merge/reordering,
100 seeded random chunk partitions, metadata/wrap, 72-hour PTS round trips,
priming/join drops, generation invalidation, S16/F32 layouts, immutable input
metadata, overflow, malformed/overlapping/unknown PCM and incomplete drain.
Run it through `AndroidClient/tests/run.sh`; no SDK or device is required.
Real MediaCodec/AAC-ELD output behavior and AudioTrack synchronization remain
unvalidated by these pure tests.

Reference inspection: `iOSClient/AudioOutput.swift:340` expands the wire timestamp,
resets the synchronous decoder and slices exactly F - dropFront frames;
`iOSClient/AudioDecoder.swift:10` defines the one-packet-to-F-frame contract.
The current Android AudioPlayer uses an incrementally truncated synthetic PTS
and does not retain BufferInfo.presentationTimeUs; this component is not wired
into that path and therefore does not change current playback behavior.
