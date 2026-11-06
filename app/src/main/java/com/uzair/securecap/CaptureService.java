package com.uzair.securecap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class CaptureService extends Service {
    public static final String START = "start", STOP = "stop", PAUSE = "pause", SHOT = "screenshot", SHOW = "show", HIDE = "hide";
    private static final int NOTIFICATION = 70;
    private Preferences preferences;
    private FloatingControls floating;
    private Uri pendingVideo;
    private boolean starting, polling, shotBusy, destroyed;
    private int countdown;
    private PowerManager.WakeLock wakeLock;

    public static void command(Context context, String action) {
        context.startForegroundService(new Intent(context, CaptureService.class).setAction(action));
    }
    @Override public void onCreate() {
        super.onCreate(); preferences = new Preferences(this);
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel("capture", "Capture controls", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Recording status and capture controls"); manager.createNotificationChannel(channel);
        startForeground(NOTIFICATION, notification("SecureCap is ready"));
        floating = new FloatingControls(this, action -> command(this, action));
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecureCap:recording");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (STOP.equals(action)) stopRecording();
        else if (PAUSE.equals(action)) togglePause();
        else if (HIDE.equals(action)) { floating.hide(); if (!Bridge.session && !shotBusy) stopSelf(); }
        else if (SHOW.equals(action)) { showFloating(); updateNotification(); }
        else if (START.equals(action) || SHOT.equals(action)) ready(action, 0);
        return START_NOT_STICKY;
    }
    private void ready(String action, int attempt) {
        if (Bridge.engine != null) { if (START.equals(action)) startRecording(); else screenshot(); return; }
        Bridge.connect();
        if (!Bridge.authorized() || attempt >= 25) {
            toast("Connect Shizuku in SecureCap first");
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            if (!Bridge.session) stopSelf(); return;
        }
        Bridge.main.postDelayed(() -> { if (!destroyed) ready(action, attempt + 1); }, 200);
    }
    private void startRecording() {
        if (Bridge.session || starting) return;
        if (android.os.Build.VERSION.SDK_INT != 30) { toast("Secure capture requires Android 11"); stopSelf(); return; }
        starting = true; Bridge.session = true;
        countdown = preferences.countdown();
        if (preferences.floating()) showFloating();
        tickCountdown();
    }
    private final Runnable countdownTick = this::tickCountdown;
    private void tickCountdown() {
        if (!starting || destroyed) return;
        if (countdown > 0) {
            Bundle b = new Bundle(); b.putString("state", "countdown"); b.putString("message", "Starting in " + countdown);
            b.putInt("countdown", countdown--); Bridge.update(b); updateNotification(); floating.update();
            Bridge.main.postDelayed(countdownTick, 1000); return;
        }
        Bundle b = new Bundle(); b.putString("state", "starting"); b.putString("message", "Preparing capture"); Bridge.update(b);
        Bundle options = preferences.options();
        wakeLock.acquire(10 * 60 * 1000L);
        Bridge.io.execute(() -> {
            Uri uri = null;
            try {
                uri = MediaFiles.create(this, true);
                try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "rw")) {
                    ICaptureEngine engine = Bridge.engine;
                    if (engine == null) throw new IllegalStateException("Shizuku disconnected");
                    engine.start(fd, options);
                }
                Uri output = uri;
                Bridge.main.post(() -> {
                    pendingVideo = output; starting = false;
                    getSharedPreferences("capture", MODE_PRIVATE).edit().putString("pending", output.toString()).apply();
                    poll();
                });
            } catch (Throwable e) {
                if (uri != null) getContentResolver().delete(uri, null, null);
                Bridge.main.post(() -> finish(false, e.getMessage()));
            }
        });
    }
    private final Runnable pollTick = this::poll;
    private void poll() {
        if (polling || destroyed) return;
        polling = true;
        Bridge.io.execute(() -> {
            Bundle result;
            try {
                ICaptureEngine engine = Bridge.engine;
                if (engine == null) throw new IllegalStateException("Shizuku disconnected");
                result = engine.status();
            } catch (Throwable e) {
                result = new Bundle(); result.putString("state", "error"); result.putString("message", e.getMessage());
            }
            Bundle status = result;
            Bridge.main.post(() -> {
                polling = false;
                if (destroyed) return;
                Bridge.update(status); floating.update(); updateNotification(); updateTile();
                if (Bridge.session) wakeLock.acquire(10 * 60 * 1000L);
                String state = status.getString("state");
                if ("idle".equals(state) || "error".equals(state)) finish(status.getBoolean("valid"), status.getString("message"));
                else Bridge.main.postDelayed(pollTick, 500);
            });
        });
    }
    private void stopRecording() {
        if (starting && "countdown".equals(Bridge.state())) {
            starting = false; Bridge.main.removeCallbacks(countdownTick); finish(false, "Recording cancelled"); return;
        }
        if (!Bridge.session) { if (!floating.visible()) stopSelf(); return; }
        Bridge.io.execute(() -> {
            try { if (Bridge.engine != null) Bridge.engine.stop(); }
            catch (Exception e) { Bridge.main.post(() -> toast(e.getMessage())); }
        });
    }
    private void togglePause() {
        if (!Bridge.recording()) return;
        boolean paused = "paused".equals(Bridge.state());
        Bridge.io.execute(() -> {
            try { if (Bridge.engine != null) { if (paused) Bridge.engine.resume(); else Bridge.engine.pause(); } }
            catch (Exception e) { Bridge.main.post(() -> toast(e.getMessage())); }
        });
    }
    private void finish(boolean valid, String message) {
        starting = false; Bridge.session = false;
        if (wakeLock.isHeld()) wakeLock.release();
        Uri output = pendingVideo; pendingVideo = null;
        Bridge.io.execute(() -> {
            try {
                if (output != null) { if (valid) MediaFiles.publish(this, output); else getContentResolver().delete(output, null, null); }
                getSharedPreferences("capture", MODE_PRIVATE).edit().remove("pending").apply();
            } catch (Exception e) { android.util.Log.e("SecureCap", "Publishing recording", e); }
            Bridge.main.post(() -> {
                if (destroyed) return;
                Bundle b = new Bundle(); b.putString("state", valid ? "idle" : "error"); b.putString("message", message);
                Bridge.update(b); toast(valid ? "Saved to Movies/SecureCap" : message);
                floating.update(); updateNotification(); updateTile();
                if (!floating.visible() && !shotBusy) stopSelf();
            });
        });
    }
    private void screenshot() {
        if (shotBusy) return;
        shotBusy = true;
        floating.temporarilyHide();
        Bridge.main.postDelayed(() -> Bridge.io.execute(() -> {
            Uri uri = null;
            String error = "";
            try {
                uri = MediaFiles.create(this, false);
                try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "rw")) {
                    ICaptureEngine engine = Bridge.engine;
                    if (engine == null) throw new IllegalStateException("Shizuku disconnected");
                    error = engine.screenshot(fd);
                }
                if (!error.isEmpty()) throw new IllegalStateException(error);
                MediaFiles.publish(this, uri);
            } catch (Throwable e) {
                error = e.getMessage();
                if (uri != null) getContentResolver().delete(uri, null, null);
            }
            String result = error;
            Bridge.main.post(() -> {
                shotBusy = false;
                if (destroyed) return;
                floating.restore();
                toast(result == null || result.isEmpty() ? "Saved to Pictures/SecureCap" : result);
                Bridge.notifyChanged();
                if (!Bridge.session && !floating.visible()) stopSelf();
            });
        }), 250);
    }
    private void showFloating() {
        if (Settings.canDrawOverlays(this)) floating.show();
        else toast("Enable floating controls in SecureCap settings");
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, "capture").setSmallIcon(R.drawable.ic_capture)
                .setContentTitle(Bridge.session ? "SecureCap · " + ("paused".equals(Bridge.state()) ? "Paused" : "Recording") : "SecureCap")
                .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
        if (Bridge.session) {
            if (Bridge.recording()) builder.addAction(new Notification.Action.Builder(null, "paused".equals(Bridge.state()) ? "Resume" : "Pause", actionIntent(PAUSE, 1)).build());
            builder.addAction(new Notification.Action.Builder(null, "Stop", actionIntent(STOP, 2)).build());
        } else {
            builder.addAction(new Notification.Action.Builder(null, "Record", actionIntent(START, 3)).build());
            builder.addAction(new Notification.Action.Builder(null, "Dismiss", actionIntent(HIDE, 5)).build());
        }
        builder.addAction(new Notification.Action.Builder(null, "Screenshot", actionIntent(SHOT, 4)).build());
        return builder.build();
    }
    private PendingIntent actionIntent(String action, int id) {
        return PendingIntent.getForegroundService(this, id, new Intent(this, CaptureService.class).setAction(action), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private void updateNotification() {
        String text = Bridge.session ? MediaFiles.duration(Bridge.status.getLong("elapsed")) + " · " + Bridge.status.getString("message", "") : "Floating capture controls";
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(text));
    }
    private void updateTile() { TileService.requestListeningState(this, new ComponentName(this, CaptureTileService.class)); }
    private void toast(String message) { Toast.makeText(this, message == null ? "Capture failed" : message, Toast.LENGTH_LONG).show(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true;
        Bridge.main.removeCallbacks(countdownTick); Bridge.main.removeCallbacks(pollTick);
        floating.hide();
        if (wakeLock.isHeld()) wakeLock.release();
        if (Bridge.session) {
            Bridge.session = false;
            Uri orphan = pendingVideo;
            Bridge.io.execute(() -> {
                try {
                    ICaptureEngine engine = Bridge.engine;
                    if (engine != null) {
                        engine.stop();
                        for (int i = 0; i < 30; i++) {
                            Bundle status = engine.status();
                            if ("idle".equals(status.getString("state")) || "error".equals(status.getString("state"))) {
                                if (orphan != null) { if (status.getBoolean("valid")) MediaFiles.publish(this, orphan); else getContentResolver().delete(orphan, null, null); }
                                break;
                            }
                            Thread.sleep(200);
                        }
                    }
                } catch (Exception e) { android.util.Log.e("SecureCap", "Service shutdown", e); }
            });
        }
        stopForeground(true); updateTile(); super.onDestroy();
    }
}
