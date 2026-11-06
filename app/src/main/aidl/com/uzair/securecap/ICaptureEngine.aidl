package com.uzair.securecap;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;

interface ICaptureEngine {
    void destroy() = 16777114;
    void start(in ParcelFileDescriptor output, in Bundle options) = 0;
    void stop() = 1;
    void pause() = 2;
    void resume() = 3;
    Bundle status() = 4;
    String screenshot(in ParcelFileDescriptor output) = 5;
}
