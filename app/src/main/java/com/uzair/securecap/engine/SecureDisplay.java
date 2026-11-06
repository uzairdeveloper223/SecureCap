package com.uzair.securecap.engine;

import android.graphics.Rect;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;
import java.lang.reflect.Method;

/** Android 11's shell-accessible secure virtual display, as used by scrcpy. */
@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
public final class SecureDisplay implements AutoCloseable {
    private final Class<?> surfaceControl;
    private final IBinder token;

    public static final class Info {
        public int width, height, rotation, layerStack;
    }

    public static Info info() throws Exception {
        Class<?> manager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) manager.getMethod("getService", String.class).invoke(null, "display");
        Class<?> stub = Class.forName("android.hardware.display.IDisplayManager$Stub");
        Object service = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
        Method get = Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo", int.class);
        Object displayInfo = get.invoke(service, 0);
        if (displayInfo == null) throw new IllegalStateException("Primary display unavailable");
        Class<?> type = displayInfo.getClass();
        Info i = new Info();
        i.width = type.getField("logicalWidth").getInt(displayInfo);
        i.height = type.getField("logicalHeight").getInt(displayInfo);
        i.rotation = type.getField("rotation").getInt(displayInfo);
        i.layerStack = type.getField("layerStack").getInt(displayInfo);
        return i;
    }

    public SecureDisplay(Surface surface, int width, int height) throws Exception {
        if (Build.VERSION.SDK_INT != 30 || "S".equals(Build.VERSION.CODENAME))
            throw new UnsupportedOperationException("Secure capture requires Android 11 (API 30)");
        surfaceControl = Class.forName("android.view.SurfaceControl");
        token = (IBinder) call("createDisplay", new Class<?>[]{String.class, boolean.class}, "SecureCap", true);
        if (token == null) throw new SecurityException("The ROM denied creation of a secure display");
        try { project(surface, width, height); }
        catch (Exception e) { close(); throw e; }
    }

    public void project(Surface surface, int width, int height) throws Exception {
        Info i = info();
        float scale = Math.min((float) width / i.width, (float) height / i.height);
        int w = Math.round(i.width * scale), h = Math.round(i.height * scale);
        int x = (width - w) / 2, y = (height - h) / 2;
        call("openTransaction", new Class<?>[]{});
        try {
            call("setDisplaySurface", new Class<?>[]{IBinder.class, Surface.class}, token, surface);
            call("setDisplayProjection", new Class<?>[]{IBinder.class, int.class, Rect.class, Rect.class},
                    token, 0, new Rect(0, 0, i.width, i.height), new Rect(x, y, x + w, y + h));
            call("setDisplayLayerStack", new Class<?>[]{IBinder.class, int.class}, token, i.layerStack);
        } finally { call("closeTransaction", new Class<?>[]{}); }
    }

    private Object call(String name, Class<?>[] types, Object... args) throws Exception {
        return surfaceControl.getMethod(name, types).invoke(null, args);
    }

    @Override public void close() {
        try { call("destroyDisplay", new Class<?>[]{IBinder.class}, token); }
        catch (Exception e) { android.util.Log.w("SecureCap", "Display cleanup", e); }
    }
}
