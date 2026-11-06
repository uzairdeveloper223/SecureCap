package com.uzair.securecap.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int WHITE = 0xffFFFFFF, HERO = 0xff182A2A;
    public static final class Palette {
        public final int BG, SURFACE, INK, MUTED, TEAL, LINE, LIGHT, RED, ON_ACCENT;
        private Palette(int bg, int surface, int ink, int muted, int teal, int line, int light, int red, int onAccent) {
            BG = bg; SURFACE = surface; INK = ink; MUTED = muted; TEAL = teal;
            LINE = line; LIGHT = light; RED = red; ON_ACCENT = onAccent;
        }
    }
    private static final Palette DAY = new Palette(0xffF5F4EF, 0xffFFFFFF, 0xff182A2A,
            0xff74807A, 0xff117568, 0xffE2E5DC, 0xffE8EFE7, 0xffBB4E3E, WHITE);
    private static final Palette NIGHT = new Palette(0xff121A1B, 0xff1D2728, 0xffEDF2EC,
            0xffA4B0AA, 0xff73CAB9, 0xff33423D, 0xff233A34, 0xffED9A88, 0xff102E26);
    public static Palette colors(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES ? NIGHT : DAY;
    }
    private Ui() {}
    public static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radius)); return d;
    }
    public static void surface(View view, int color, int radius) { view.setBackground(shape(view.getContext(), color, radius)); }
    public static void ripple(View view, int color, int radius) {
        Context c = view.getContext(); view.setBackground(new RippleDrawable(ColorStateList.valueOf((colors(c).TEAL & 0x00FFFFFF) | 0x28000000), shape(c, color, radius), shape(c, WHITE, radius)));
    }
    public static TextView text(Context c, String text, int size, int color, boolean bold) {
        TextView v = new TextView(c); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return v;
    }
    public static LinearLayout column(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static LinearLayout row(Context c) { LinearLayout l = new LinearLayout(c); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    public static LinearLayout.LayoutParams lp(Context c, int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(c, w), h < 0 ? h : dp(c, h));
    }
    public static void pad(View v, int l, int t, int r, int b) { Context c = v.getContext(); v.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b)); }
    public static View space(Context c, int height) { View v = new View(c); v.setLayoutParams(lp(c,1,height)); return v; }
    public static TextView button(Context c, String label, boolean primary, Runnable action) {
        Palette p = colors(c);
        TextView button = text(c, label, 15, primary ? p.ON_ACCENT : p.INK, true);
        button.setGravity(Gravity.CENTER); pad(button,16,0,16,0); ripple(button, primary ? p.TEAL : p.LIGHT, 14);
        button.setMinHeight(dp(c,52)); button.setOnClickListener(v -> action.run());
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        }); return button;
    }
}
