package io.github.n2ray.sall;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioDecodeMappingTest {
    static AudioPlayout.Plan plan(int id, int drop, boolean reset) {
        AudioPlayout.Plan p = new AudioPlayout.Plan(); p.decode = true; p.resetFirst = reset;
        p.id = id < 0 ? null : id; p.dropFront = drop; return p;
    }
    static AudioDecodeMapping.Packet packet(int frames, long seq) {
        return new AudioDecodeMapping.Packet(1, seq, 1000 + seq * frames / 48000.0, seq == 0, frames, new byte[] {1, 2});
    }
    static ByteBuffer pcm(int start, int frames) {
        ByteBuffer b = ByteBuffer.allocate(frames * 8 + 12).order(ByteOrder.LITTLE_ENDIAN);
        b.position(8);
        for (int i = start; i < start + frames; i++) b.putFloat(i / 4096f).putFloat(-i / 8192f);
        b.limit(b.position()); b.position(3);
        return b;
    }
    static AudioDecodeMapping.Result deliver(AudioDecodeMapping m, long gen, int start, int frames) {
        ByteBuffer b = pcm(start, frames);
        int pos = b.position(), limit = b.limit();
        var result = m.output(gen, AudioDecodeMapping.frameToUs(start), b, 8, frames * 8, AudioDecodeMapping.Encoding.FLOAT32_LE);
        assert b.position() == pos && b.limit() == limit;
        return result;
    }
    static void expectFailure(Runnable action) {
        boolean caught = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException e) { caught = true; }
        assert caught;
    }
    /** Fake asynchronous decoder returns arbitrary splits/merges in a deliberately different order. */
    static final class FakeDecoder {
        final AudioDecodeMapping mapping;
        final List<AudioDecodeMapping.Input> queued = new ArrayList<>();
        FakeDecoder(AudioDecodeMapping mapping) { this.mapping = mapping; }
        void queue(AudioDecodeMapping.Input input) { if (input != null) queued.add(input); }
        AudioDecodeMapping.Result output(int start, int frames) {
            return deliver(mapping, queued.get(0).generation(), start, frames);
        }
    }
    static void randomizedChunks() {
        java.util.Random random = new java.util.Random(81372);
        for (int run = 0; run < 100; run++) {
            int frames = run % 2 == 0 ? 480 : 512;
            AudioDecodeMapping mapping = new AudioDecodeMapping();
            for (int k = 0; k < 20; k++) mapping.queue(packet(frames, k), plan(k + 1, k == 0 ? frames / 2 : 0, k == 0));
            List<int[]> chunks = new ArrayList<>();
            for (int at = 0; at < frames * 20;) {
                int n = Math.min(frames * 20 - at, 1 + random.nextInt(701));
                chunks.add(new int[] {at, n}); at += n;
            }
            java.util.Collections.shuffle(chunks, random);
            java.util.Set<Integer> done = new java.util.HashSet<>();
            for (int[] chunk : chunks) {
                var result = deliver(mapping, mapping.generation(), chunk[0], chunk[1]);
                assert result.failure() == null;
                for (var decoded : result.decoded()) {
                    assert done.add(decoded.id());
                    int k = decoded.id() - 1, drop = k == 0 ? frames / 2 : 0;
                    assert decoded.left().length == frames - drop;
                    for (int j = 0; j < decoded.left().length; j++) {
                        assert decoded.left()[j] == (k * frames + drop + j) / 4096f;
                        assert decoded.right()[j] == -(k * frames + drop + j) / 8192f;
                    }
                }
            }
            assert done.size() == 20 && mapping.pendingPackets() == 0;
        }
    }
    public static void main(String[] args) {
        byte[] aac = {4, 5};
        var batch = new AudioPackets.Batch(3, 0xffffffffL, true, List.of(aac, new byte[] {6}, new byte[] {7}));
        var expanded = AudioDecodeMapping.expand(batch, 1700000000.25, 512);
        assert expanded.get(0).sequence() == 0xffffffffL && expanded.get(1).sequence() == 0 && expanded.get(2).sequence() == 1;
        assert expanded.get(0).segmentStart() && !expanded.get(1).segmentStart();
        assert expanded.get(2).stamp() == 1700000000.25 + 1024 / 48000.0;
        aac[0] = 99; byte[] copy = expanded.get(0).data(); copy[1] = 99;
        assert Arrays.equals(expanded.get(0).data(), new byte[] {4, 5});
        for (long frame = 0; frame < 512L * 100000; frame += 512) {
            assert AudioDecodeMapping.usToFrame(AudioDecodeMapping.frameToUs(frame)) == frame;
        }
        assert AudioDecodeMapping.frameToUs(512 * 3) == 32000; // no 10666 us cumulative truncation
        assert AudioDecodeMapping.usToFrame(AudioDecodeMapping.frameToUs(48000L * 3600 * 72 + 511)) == 48000L * 3600 * 72 + 511;

        // Model → queued AAC identity → asynchronous PCM → model-kept id and exact priming slice.
        AudioPlayout model = new AudioPlayout(); model.format(1, 512, 256, new byte[] {9}); model.played(0, 10, 10);
        AudioDecodeMapping m = new AudioDecodeMapping(); FakeDecoder fake = new FakeDecoder(m);
        List<Integer> ids = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            var p = packet(512, k);
            var decision = model.packet(1, k, p.stamp(), k == 0, 10 + k * 512 / 48000.0);
            ids.add(decision.id); fake.queue(m.queue(p, decision));
        }
        assert fake.queued.get(2).presentationUs() == 21333;
        var later = fake.output(800, 736); assert later.failure() == null && later.decoded().size() == 1;
        assert later.decoded().get(0).id().equals(ids.get(2));
        var early = fake.output(0, 300); assert early.decoded().isEmpty();
        var middle = fake.output(300, 500); assert middle.decoded().size() == 2;
        List<AudioDecodeMapping.Decoded> completed = new ArrayList<>(middle.decoded()); completed.addAll(later.decoded());
        for (int k = 0; k < 3; k++) {
            var decoded = completed.get(k); int drop = k == 0 ? 256 : 0;
            assert decoded.id().equals(ids.get(k)); assert decoded.packet().sequence() == k;
            assert decoded.left().length == 512 - drop && decoded.right().length == 512 - drop;
            assert decoded.keptStamp() == decoded.packet().stamp() + drop / 48000.0;
            for (int j = 0; j < decoded.left().length; j++) {
                assert decoded.left()[j] == (k * 512 + drop + j) / 4096f;
                assert decoded.right()[j] == -(k * 512 + drop + j) / 8192f;
            }
        }
        assert m.pendingPackets() == 0 && m.drained(m.generation()).failure() == null;
        assert !model.tick(11).schedules.isEmpty(); // model tail still resolves independently of callback boundaries

        // Reset generation retains no stale samples even when codec PTS starts again at zero.
        m = new AudioDecodeMapping(); long old = m.generation();
        m.queue(packet(480, 0), plan(1, 240, true)); deliver(m, old, 0, 100);
        var flushed = m.flush(); assert flushed.discardedIds().equals(List.of(1));
        var current = m.queue(packet(480, 0), plan(2, 240, true));
        assert current.presentationUs() == 0 && current.generation() != old;
        assert deliver(m, old, 100, 380).stale(); assert m.pendingPackets() == 1;
        var fresh = deliver(m, current.generation(), 0, 480); assert fresh.decoded().get(0).id() == 2;
        assert m.drained(old).stale();

        // Clean segment priming may span packets; a late join is decoded and dropped whole.
        m = new AudioDecodeMapping();
        m.queue(packet(480, 0), plan(-1, 480, true));
        m.queue(packet(480, 1), plan(8, 100, false));
        var merged = deliver(m, m.generation(), 0, 960);
        assert merged.decoded().get(0).id() == null && merged.decoded().get(0).left().length == 0;
        assert merged.decoded().get(1).left().length == 380;
        assert merged.decoded().get(1).left()[0] == 580 / 4096f;

        // S16 stereo deinterleave and bounds honour BufferInfo offset, without clipping float output.
        m = new AudioDecodeMapping(); m.queue(packet(4, 0), plan(9, 1, true));
        ByteBuffer shorts = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
        shorts.position(4); for (short s : new short[] {0, 1, Short.MIN_VALUE, Short.MAX_VALUE, 16384, -8192, 32767, -32768}) shorts.putShort(s);
        shorts.limit(20);
        var s16 = m.output(m.generation(), 0, shorts, 4, 16, AudioDecodeMapping.Encoding.S16_LE).decoded().get(0);
        assert Arrays.equals(s16.left(), new float[] {-1, .5f, 32767 / 32768f});
        assert Arrays.equals(s16.right(), new float[] {32767 / 32768f, -.25f, -1});

        // Invalid callbacks fail the generation atomically and expose every abandoned kept id.
        m = new AudioDecodeMapping(); m.queue(packet(480, 0), plan(1, 0, true));
        var overrun = deliver(m, m.generation(), 0, 481);
        assert overrun.failure() == AudioDecodeMapping.Failure.UNKNOWN_PTS && overrun.decoded().isEmpty();
        assert overrun.discardedIds().equals(List.of(1));
        m.queue(packet(480, 0), plan(2, 0, true)); deliver(m, m.generation(), 0, 100);
        var overlap = deliver(m, m.generation(), 99, 2);
        assert overlap.failure() == AudioDecodeMapping.Failure.OVERLAP && overlap.discardedIds().equals(List.of(2));
        m.queue(packet(480, 0), plan(3, 0, true));
        ByteBuffer invalid = pcm(0, 480); invalid.putFloat(8 + 479 * 8, Float.NaN);
        var nan = m.output(m.generation(), 0, invalid, 8, 480 * 8, AudioDecodeMapping.Encoding.FLOAT32_LE);
        assert nan.failure() == AudioDecodeMapping.Failure.MALFORMED_PCM && nan.decoded().isEmpty();
        m.queue(packet(480, 0), plan(4, 0, true));
        var unaligned = m.output(m.generation(), 0, pcm(0, 480), 8, 3, AudioDecodeMapping.Encoding.S16_LE);
        assert unaligned.failure() == AudioDecodeMapping.Failure.MALFORMED_PCM;
        m.queue(packet(480, 0), plan(5, 0, true)); deliver(m, m.generation(), 0, 479);
        var drained = m.drained(m.generation());
        assert drained.failure() == AudioDecodeMapping.Failure.INCOMPLETE && drained.discardedIds().equals(List.of(5));

        AudioDecodeMapping bounded = new AudioDecodeMapping(1);
        bounded.queue(packet(480, 0), plan(1, 0, true));
        expectFailure(() -> bounded.queue(packet(480, 1), plan(2, 0, false)));
        assert bounded.pendingPackets() == 1; assert bounded.queue(packet(480, 1), new AudioPlayout.Plan()) == null;
        deliver(bounded, bounded.generation(), 0, 480);
        expectFailure(() -> bounded.queue(packet(480, 1), plan(2, 0, true)));
        bounded.flush(); assert bounded.queue(packet(480, 1), plan(2, 0, true)) != null;
        randomizedChunks();
        System.out.println("AudioDecodeMapping passed: exact PTS, async split/merge/reorder, model IDs/priming, generations, PCM layouts, atomic failures and bounds");
    }
}
