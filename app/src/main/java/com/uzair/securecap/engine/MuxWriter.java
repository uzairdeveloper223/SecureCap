package com.uzair.securecap.engine;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

final class MuxWriter {
    private final MediaMuxer muxer;
    private final boolean audioExpected;
    private int videoTrack = -1, audioTrack = -1;
    private boolean started;
    private long lastVideo = -1, lastAudio = -1;
    private int queuedBytes;
    private final List<Sample> pending = new ArrayList<>();
    long videoFrames;

    private static final class Sample {
        boolean audio;
        ByteBuffer bytes;
        MediaCodec.BufferInfo info;
    }

    MuxWriter(FileDescriptor fd, boolean audioExpected) throws Exception {
        muxer = new MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        this.audioExpected = audioExpected;
    }

    synchronized void format(boolean audio, MediaFormat format) {
        if (audio) audioTrack = muxer.addTrack(format); else videoTrack = muxer.addTrack(format);
        if (videoTrack >= 0 && (!audioExpected || audioTrack >= 0)) {
            muxer.start();
            started = true;
            for (Sample sample : pending) write(sample.audio, sample.bytes, sample.info);
            pending.clear();
            queuedBytes = 0;
        }
    }

    synchronized void sample(boolean audio, ByteBuffer bytes, MediaCodec.BufferInfo info, long pts) {
        if (pts < 0 || info.size == 0 || (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return;
        MediaCodec.BufferInfo mapped = new MediaCodec.BufferInfo();
        mapped.set(info.offset, info.size, pts, info.flags);
        if (started) { write(audio, bytes, mapped); return; }
        if (queuedBytes + info.size > 8 * 1024 * 1024) throw new IllegalStateException("Timed out waiting for encoder tracks");
        Sample sample = new Sample();
        sample.audio = audio;
        ByteBuffer copy = bytes.duplicate();
        copy.position(info.offset).limit(info.offset + info.size);
        sample.bytes = ByteBuffer.allocateDirect(info.size);
        sample.bytes.put(copy).flip();
        mapped.offset = 0;
        sample.info = mapped;
        pending.add(sample);
        queuedBytes += info.size;
    }

    private void write(boolean audio, ByteBuffer bytes, MediaCodec.BufferInfo info) {
        long last = audio ? lastAudio : lastVideo;
        info.presentationTimeUs = Math.max(info.presentationTimeUs, last + 1);
        muxer.writeSampleData(audio ? audioTrack : videoTrack, bytes, info);
        if (audio) lastAudio = info.presentationTimeUs;
        else { lastVideo = info.presentationTimeUs; videoFrames++; }
    }

    synchronized void finish() {
        try {
            if (!started || videoFrames == 0) throw new IllegalStateException("No video frames were captured");
            muxer.stop();
        } finally { muxer.release(); }
    }
}
