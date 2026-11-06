package com.uzair.securecap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public final class Preferences {
    private final SharedPreferences prefs;
    public Preferences(Context context) { prefs = context.getSharedPreferences("capture", Context.MODE_PRIVATE); }
    public String audio() { return prefs.getString("audio", "device"); }
    public int maxSize() { return prefs.getInt("maxSize", 2400); }
    public int fps() { return prefs.getInt("fps", 30); }
    public int bitrate() { return prefs.getInt("bitrate", 8000000); }
    public int countdown() { return prefs.getInt("countdown", 3); }
    public boolean floating() { return prefs.getBoolean("floating", true); }
    public String theme() { return prefs.getString("theme", "system"); }
    public String themeLabel() { return "dark".equals(theme()) ? "Dark" : "light".equals(theme()) ? "Light" : "System default"; }
    public void set(String key, String value) { prefs.edit().putString(key, value).apply(); }
    public void set(String key, int value) { prefs.edit().putInt(key, value).apply(); }
    public void set(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    public Bundle options() {
        Bundle b = new Bundle(); b.putString("audio", audio()); b.putInt("maxSize", maxSize());
        b.putInt("fps", fps()); b.putInt("bitrate", bitrate()); return b;
    }
    public String audioLabel() { return "device".equals(audio()) ? "Device audio" : "mic".equals(audio()) ? "Microphone" : "No audio"; }
    public String sizeLabel() { return maxSize() == 2400 ? "Native" : maxSize() == 1920 ? "1920 px" : maxSize() == 1600 ? "1600 px" : "1280 px"; }
}
