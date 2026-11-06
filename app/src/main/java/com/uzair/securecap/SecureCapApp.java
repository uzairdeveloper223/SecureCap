package com.uzair.securecap;

import android.app.Application;

public class SecureCapApp extends Application {
    @Override public void onCreate() { super.onCreate(); Bridge.init(this); }
}
