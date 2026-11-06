package com.uzair.securecap.engine;

import android.app.Application;
import android.annotation.SuppressLint;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/** Shell attribution and the Android 11 foreground workaround documented by scrcpy. */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public final class ShellAudio {
    public static final int RATE = 48000;
    public static final int CHANNELS = 2;
    private ShellAudio() {}

    public static void prepare() throws Exception {
        Class<?> threadType = Class.forName("android.app.ActivityThread");
        Object thread = threadType.getMethod("currentActivityThread").invoke(null);
        if (thread == null) {
            Constructor<?> constructor = threadType.getDeclaredConstructor();
            constructor.setAccessible(true);
            thread = constructor.newInstance();
            set(threadType, null, "sCurrentActivityThread", thread);
        }
        Class<?> bindType = Class.forName("android.app.ActivityThread$AppBindData");
        Constructor<?> constructor = bindType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object bind = constructor.newInstance();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = "com.android.shell";
        appInfo.uid = 2000;
        set(bindType, bind, "appInfo", appInfo);
        set(threadType, thread, "mBoundApplication", bind);
        Application app = new Application();
        set(ContextWrapper.class, app, "mBase", new ContextWrapper(null) {
            @Override public String getPackageName() { return "com.android.shell"; }
            @Override public String getOpPackageName() { return "com.android.shell"; }
        });
        set(threadType, thread, "mInitialApplication", app);
    }

    private static void set(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    // Executed only in the Shizuku shell process, whose RECORD_AUDIO permission is pre-granted.
    @SuppressLint({"MissingPermission", "WrongConstant"})
    public static AudioRecord start(String source) throws Exception {
        prepare();
        AudioRecord record = null;
        try {
            run("am", "start", "-n", "com.android.shell/.HeapDumpActivity");
            SystemClock.sleep(200);
            int channelMask = AudioFormat.CHANNEL_IN_STEREO;
            int minimum = AudioRecord.getMinBufferSize(RATE, channelMask, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Audio format unsupported: " + minimum);
            record = new AudioRecord.Builder()
                    .setAudioSource("mic".equals(source) ? MediaRecorder.AudioSource.MIC
                            : MediaRecorder.AudioSource.class.getField("REMOTE_SUBMIX").getInt(null))
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(channelMask).build())
                    .setBufferSizeInBytes(minimum * 8).build();
            record.startRecording();
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException("Audio capture did not start; unlock the screen and retry");
            return record;
        } catch (Exception e) {
            if (record != null) record.release();
            throw e;
        } finally {
            run("am", "force-stop", "com.android.shell");
        }
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroy();
            throw new IllegalStateException("Shell foreground workaround timed out");
        }
        if (process.exitValue() != 0) throw new IllegalStateException("Shell foreground workaround failed");
    }
}
