package com.uzair.securecap.engine;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.uzair.securecap.ICaptureEngine;

/** Loaded by Shizuku as UID 2000, never as an ordinary Android Service. */
public class CaptureEngine extends ICaptureEngine.Stub {
    private Recorder recorder;
    public CaptureEngine() {}
    @Override public synchronized void start(ParcelFileDescriptor output, Bundle options) {
        if (recorder != null && recorder.active()) {
            try { output.close(); } catch (Exception ignored) {}
            throw new IllegalStateException("Already recording");
        }
        recorder = new Recorder(output, options);
        new Thread(recorder::run, "SecureCap-video").start();
    }
    @Override public synchronized void stop() { if (recorder != null) recorder.stop(); }
    @Override public synchronized void pause() { if (recorder != null) recorder.pause(); }
    @Override public synchronized void resume() { if (recorder != null) recorder.resume(); }
    @Override public synchronized Bundle status() {
        if (recorder != null) return recorder.status();
        Bundle b = new Bundle(); b.putString("state", "idle"); b.putString("message", "Secure display ready"); return b;
    }
    @Override public String screenshot(ParcelFileDescriptor output) {
        try { Screenshots.capture(output); return ""; }
        catch (Throwable e) {
            try { output.close(); } catch (Exception ignored) {}
            android.util.Log.e("SecureCap", "Screenshot failed", e);
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
    @Override public synchronized void destroy() {
        if (recorder != null) recorder.stop();
        new Thread(() -> { android.os.SystemClock.sleep(7000); System.exit(0); }).start();
    }
}
