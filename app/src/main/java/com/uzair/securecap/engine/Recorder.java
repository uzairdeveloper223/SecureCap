package com.uzair.securecap.engine;

import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import java.nio.ByteBuffer;

public final class Recorder {
    public volatile String state = "starting", message = "Preparing secure display";
    public volatile boolean valid;
    public volatile long frames, audioBytes, audioPeak;
    public int width, height;
    private volatile boolean stopRequested, awaitKeyFrame;
    private volatile Throwable audioFailure;
    private volatile Timeline timeline;
    private MediaCodec video;
    private final ParcelFileDescriptor output;
    private final Bundle options;

    public Recorder(ParcelFileDescriptor output, Bundle options) { this.output = output; this.options = options; }
    public void stop() {
        stopRequested = true;
        Timeline clock = timeline;
        if (clock != null) clock.stop();
        if (active()) state = "stopping";
    }
    public boolean active() { return !"idle".equals(state) && !"error".equals(state); }
    public synchronized void pause() {
        if ("recording".equals(state)) { timeline.pause(); state = "paused"; }
    }
    public synchronized void resume() {
        if ("paused".equals(state)) {
            awaitKeyFrame = true;
            timeline.resume();
            Bundle request = new Bundle();
            request.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            video.setParameters(request);
            state = "recording";
        }
    }
    public Bundle status() {
        Bundle b = new Bundle();
        b.putString("state", state); b.putString("message", message);
        b.putLong("elapsed", timeline == null ? 0 : timeline.elapsedMs());
        b.putLong("frames", frames); b.putLong("audioBytes", audioBytes); b.putLong("audioPeak", audioPeak);
        b.putInt("width", width); b.putInt("height", height); b.putBoolean("valid", valid);
        return b;
    }

    public void run() {
        MediaCodec audio = null;
        AudioRecord record = null;
        Surface surface = null;
        SecureDisplay display = null;
        MuxWriter writer = null;
        Thread audioThread = null;
        Throwable failure = null;
        try {
            if (android.os.Looper.myLooper() == null) android.os.Looper.prepare();
            SecureDisplay.Info info = SecureDisplay.info();
            int max = options.getInt("maxSize", 2400);
            float scale = Math.min(1f, (float) max / Math.max(info.width, info.height));
            width = Math.max(8, ((int) (info.width * scale)) & ~7);
            height = Math.max(8, ((int) (info.height * scale)) & ~7);
            MediaFormat vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            vf.setInteger(MediaFormat.KEY_BIT_RATE, options.getInt("bitrate", 8000000));
            int fps = options.getInt("fps", 30);
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            vf.setFloat("max-fps-to-encoder", fps);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            vf.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100000);
            video = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            video.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = video.createInputSurface();
            String source = options.getString("audio", "device");
            boolean hasAudio = !"off".equals(source);
            if (hasAudio) {
                message = "Starting audio; keep the screen unlocked";
                record = ShellAudio.start(source);
                MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, ShellAudio.RATE, ShellAudio.CHANNELS);
                af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                af.setInteger(MediaFormat.KEY_BIT_RATE, 192000);
                af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);
                audio = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
                audio.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                audio.start();
            }
            writer = new MuxWriter(output.getFileDescriptor(), hasAudio);
            timeline = new Timeline();
            video.start();
            display = new SecureDisplay(surface, width, height);
            if (hasAudio) {
                final AudioRecord ar = record;
                final MediaCodec ac = audio;
                final MuxWriter mw = writer;
                audioThread = new Thread(() -> {
                    try { encodeAudio(ar, ac, mw); }
                    catch (Throwable e) { audioFailure = e; stopRequested = true; }
                }, "SecureCap-audio");
                audioThread.start();
            }
            state = stopRequested ? "stopping" : "recording";
            message = hasAudio ? ("mic".equals(source) ? "Microphone · AAC" : "Device audio · AAC") : "Audio off";
            MediaCodec.BufferInfo buffer = new MediaCodec.BufferInfo();
            boolean eosSent = false, ended = false;
            long stopAt = 0, lastFrameAt = System.nanoTime(), nextDisplayCheck = 0;
            while (!ended) {
                long now = System.nanoTime();
                if (stopRequested && !eosSent) {
                    state = "stopping";
                    video.signalEndOfInputStream(); eosSent = true; stopAt = now;
                }
                if (eosSent && now - stopAt > 5_000_000_000L) throw new IllegalStateException("Video encoder did not finish");
                if (!eosSent && now - lastFrameAt > 10_000_000_000L) throw new IllegalStateException("No frames from secure display");
                if (!eosSent && now > nextDisplayCheck) {
                    SecureDisplay.Info current = SecureDisplay.info();
                    if (current.rotation != info.rotation || current.width != info.width || current.height != info.height) {
                        display.project(surface, width, height); info = current;
                    }
                    nextDisplayCheck = now + 500_000_000L;
                }
                int index = video.dequeueOutputBuffer(buffer, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) writer.format(false, video.getOutputFormat());
                else if (index >= 0) {
                    try {
                        if (buffer.size > 0) lastFrameAt = now;
                        boolean key = (buffer.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0;
                        long pts = timeline.map(buffer.presentationTimeUs);
                        if (pts >= 0 && (!awaitKeyFrame || key)) {
                            if (key) awaitKeyFrame = false;
                            writer.sample(false, video.getOutputBuffer(index), buffer, pts);
                            frames = writer.videoFrames;
                        }
                        ended = (buffer.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } finally { video.releaseOutputBuffer(index, false); }
                }
            }
        } catch (Throwable e) { failure = e; }
        finally {
            stopRequested = true;
            if (timeline != null) timeline.stop();
            if (audioThread != null) {
                try { audioThread.join(6000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); failure = e; }
                if (audioThread.isAlive()) { failure = new IllegalStateException("Audio encoder did not finish"); audioThread.interrupt(); }
            }
            if (audioFailure != null) failure = audioFailure;
            if (display != null) display.close();
            release(video); release(audio);
            if (surface != null) surface.release();
            if (record != null) record.release();
            if (writer != null) {
                try { writer.finish(); valid = true; }
                catch (Throwable e) { if (failure == null) failure = e; }
            }
            try { output.close(); } catch (Exception ignored) {}
            if (failure != null) {
                android.util.Log.e("SecureCap", "Recording failed", failure);
                message = failure.getClass().getSimpleName() + ": " + failure.getMessage();
                state = "error";
            } else { message = "Recording saved"; state = "idle"; }
        }
    }

    private void encodeAudio(AudioRecord record, MediaCodec codec, MuxWriter writer) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputEnded = false, ended = false;
        long framesRead = 0, anchorUs = 0, stopAt = 0;
        while (!ended && !Thread.currentThread().isInterrupted()) {
            if (stopRequested && stopAt == 0) stopAt = System.nanoTime();
            if (stopAt != 0 && System.nanoTime() - stopAt > 5_000_000_000L) throw new IllegalStateException("Audio EOS timeout");
            if (!inputEnded) {
                int input = codec.dequeueInputBuffer(1000);
                if (input >= 0) {
                    ByteBuffer bytes = codec.getInputBuffer(input);
                    bytes.clear();
                    if (stopRequested) {
                        codec.queueInputBuffer(input, 0, 0, anchorUs + framesRead * 1000000 / ShellAudio.RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputEnded = true;
                    } else {
                        int size = record.read(bytes, Math.min(bytes.capacity(), 4096), AudioRecord.READ_BLOCKING);
                        if (size <= 0) throw new IllegalStateException("Audio read failed: " + size);
                        size -= size % 4;
                        if (anchorUs == 0) anchorUs = System.nanoTime() / 1000 - (size / 4L) * 1000000 / ShellAudio.RATE;
                        long pts = anchorUs + framesRead * 1000000 / ShellAudio.RATE;
                        framesRead += size / 4;
                        audioBytes += size;
                        bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                        for (int i = 0; i < size; i += 2) audioPeak = Math.max(audioPeak, Math.abs((int) bytes.getShort(i)));
                        codec.queueInputBuffer(input, 0, size, pts, 0);
                    }
                }
            }
            int output;
            do {
                output = codec.dequeueOutputBuffer(info, 0);
                if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) writer.format(true, codec.getOutputFormat());
                else if (output >= 0) {
                    try {
                        writer.sample(true, codec.getOutputBuffer(output), info, timeline.map(info.presentationTimeUs));
                        ended = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    } finally { codec.releaseOutputBuffer(output, false); }
                }
            } while (output >= 0 && !ended);
        }
    }

    private static void release(MediaCodec codec) {
        if (codec == null) return;
        try { codec.stop(); } catch (Exception ignored) {}
        try { codec.release(); } catch (Exception ignored) {}
    }
}
