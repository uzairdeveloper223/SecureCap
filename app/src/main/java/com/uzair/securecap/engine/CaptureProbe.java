package com.uzair.securecap.engine;

import android.os.Bundle;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import java.io.File;

/** ADB-only integration probe for testing this exact engine independently of the UI. */
public final class CaptureProbe {
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        ParcelFileDescriptor fd = ParcelFileDescriptor.open(new File(args[1]),
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_READ_WRITE);
        if ("screenshot".equals(args[0])) { Screenshots.capture(fd); System.out.println("PNG saved"); System.exit(0); }
        Bundle options = new Bundle();
        options.putString("audio", args.length > 2 ? args[2] : "device");
        options.putInt("maxSize", 1600);
        Recorder recorder = new Recorder(fd, options);
        Thread thread = new Thread(recorder::run); thread.start();
        for (int i = 0; i < 10 && recorder.active(); i++) {
            Thread.sleep(1000); System.out.println(recorder.status());
            if (i == 3 && args.length > 3) recorder.pause();
            if (i == 5 && args.length > 3) recorder.resume();
        }
        recorder.stop(); thread.join(12000);
        System.out.println(recorder.status());
        System.exit(recorder.valid && !"error".equals(recorder.state) ? 0 : 1);
    }
}
