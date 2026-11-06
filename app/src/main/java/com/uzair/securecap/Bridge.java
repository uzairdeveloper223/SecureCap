package com.uzair.securecap;

import android.content.ComponentName;
import android.app.Application;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.uzair.securecap.engine.CaptureEngine;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public final class Bridge {
    public static final Handler main = new Handler(Looper.getMainLooper());
    public static final ExecutorService io = Executors.newSingleThreadExecutor();
    public static volatile ICaptureEngine engine;
    public static volatile Bundle status = new Bundle();
    public static volatile boolean session;
    public static String connection = "Waiting for Shizuku";
    private static Application context;
    private static boolean binding;
    private static final Set<Runnable> listeners = new HashSet<>();
    private static Shizuku.UserServiceArgs args;

    public static void init(Context app) {
        context = (Application) app.getApplicationContext();
        status.putString("state", "idle");
        args = new Shizuku.UserServiceArgs(new ComponentName(app, CaptureEngine.class))
                .daemon(false).processNameSuffix("capture").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderReceivedListenerSticky(() -> main.post(Bridge::connect));
        Shizuku.addBinderDeadListener(() -> main.post(() -> {
            engine = null; binding = false; connection = "Shizuku stopped"; notifyChanged();
        }));
        Shizuku.addRequestPermissionResultListener((request, result) -> main.post(Bridge::connect));
    }

    public static boolean authorized() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (RuntimeException e) { return false; }
    }

    public static void connect() {
        if (!Shizuku.pingBinder()) { connection = "Start Shizuku to connect"; notifyChanged(); return; }
        if (!authorized()) { connection = "Shizuku permission needed"; notifyChanged(); return; }
        if (engine != null || binding) return;
        binding = true; connection = "Connecting to shell"; notifyChanged();
        try {
            Shizuku.bindUserService(args, serviceConnection);
            main.postDelayed(() -> {
                if (engine == null && binding) { binding = false; connection = "Connection timed out · tap to retry"; notifyChanged(); }
            }, 10000);
        } catch (RuntimeException e) { binding = false; connection = e.getMessage(); notifyChanged(); }
    }

    private static final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            main.post(() -> {
                binding = false;
                engine = ICaptureEngine.Stub.asInterface(binder);
                connection = "Shell connected · UID " + Shizuku.getUid();
                notifyChanged();
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            main.post(() -> { binding = false; engine = null; connection = "Capture service disconnected"; notifyChanged(); });
        }
    };

    public static void observe(Runnable listener) { listeners.add(listener); listener.run(); }
    public static void remove(Runnable listener) { listeners.remove(listener); }
    public static void update(Bundle value) { status = value; notifyChanged(); }
    public static void notifyChanged() {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(Bridge::notifyChanged); return; }
        for (Runnable listener : new HashSet<>(listeners)) listener.run();
    }
    public static String state() { return status.getString("state", "idle"); }
    public static boolean recording() { return "recording".equals(state()) || "paused".equals(state()); }
    public static void requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                android.content.Intent launch = context.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (launch != null) { launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(launch); }
                return;
            }
            if (authorized()) connect(); else Shizuku.requestPermission(41);
        } catch (RuntimeException e) { connection = e.getMessage(); notifyChanged(); }
    }
}
