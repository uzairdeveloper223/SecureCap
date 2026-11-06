package com.uzair.securecap.engine;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.nio.ByteBuffer;

public final class Screenshots {
    private Screenshots() {}
    public static void capture(ParcelFileDescriptor output) throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare();
        SecureDisplay.Info info = SecureDisplay.info();
        Bitmap padded = null, cropped = null;
        try (ImageReader reader = ImageReader.newInstance(info.width, info.height, PixelFormat.RGBA_8888, 3);
             SecureDisplay display = new SecureDisplay(reader.getSurface(), info.width, info.height)) {
            Image image = null;
            long deadline = SystemClock.elapsedRealtime() + 4000;
            while (image == null && SystemClock.elapsedRealtime() < deadline) {
                image = reader.acquireLatestImage();
                if (image == null) SystemClock.sleep(30);
            }
            if (image == null) throw new IllegalStateException("No screenshot frame from secure display");
            try {
                Image.Plane plane = image.getPlanes()[0];
                ByteBuffer buffer = plane.getBuffer();
                int paddedWidth = plane.getRowStride() / plane.getPixelStride();
                padded = Bitmap.createBitmap(paddedWidth, info.height, Bitmap.Config.ARGB_8888);
                padded.copyPixelsFromBuffer(buffer);
                cropped = Bitmap.createBitmap(padded, 0, 0, info.width, info.height);
                try (ParcelFileDescriptor.AutoCloseOutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
                    if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new IllegalStateException("PNG compression failed");
                }
            } finally { image.close(); }
        } finally {
            if (cropped != null && cropped != padded) cropped.recycle();
            if (padded != null) padded.recycle();
            output.close();
        }
    }
}
