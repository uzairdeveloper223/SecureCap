package com.uzair.securecap;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.uzair.securecap.ui.IconView;
import com.uzair.securecap.ui.Ui;
import java.util.function.Consumer;

public final class FloatingControls {
    private final Context context;
    private final WindowManager manager;
    private final Consumer<String> commands;
    private LinearLayout view;
    private TextView timer;
    private IconView record, pause;
    private WindowManager.LayoutParams params;
    private boolean hiddenForShot;

    FloatingControls(Context context, Consumer<String> commands) {
        this.context = context; this.commands = commands; manager = context.getSystemService(WindowManager.class);
    }
    public boolean visible() { return view != null; }
    public void show() {
        if (view != null) return;
        view = Ui.row(context); Ui.pad(view,4,4,4,4); Ui.surface(view, Ui.HERO, 18); view.setElevation(Ui.dp(context,8));
        IconView drag = new IconView(context,"drag",0xff9AAAA1); drag.setContentDescription("Drag floating controls");
        drag.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES); view.addView(drag, Ui.lp(context,28,44));
        timer = Ui.text(context,"00:00",12,Ui.WHITE,true); timer.setTypeface(android.graphics.Typeface.MONOSPACE);
        timer.setGravity(Gravity.CENTER); view.addView(timer,Ui.lp(context,52,44));
        record = new IconView(context,"capture",Ui.WHITE); record.action("Start recording", () -> commands.accept(Bridge.session ? CaptureService.STOP : CaptureService.START));
        view.addView(record,Ui.lp(context,44,44));
        pause = new IconView(context,"pause",Ui.WHITE); pause.action("Pause recording", () -> commands.accept(CaptureService.PAUSE));
        view.addView(pause,Ui.lp(context,44,44));
        IconView camera = new IconView(context,"camera",Ui.WHITE); camera.action("Take screenshot", () -> commands.accept(CaptureService.SHOT));
        view.addView(camera,Ui.lp(context,44,44));
        IconView close = new IconView(context,"close",0xff9AAAA1); close.action("Hide floating controls", () -> commands.accept(CaptureService.HIDE));
        view.addView(close,Ui.lp(context,36,44));
        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        android.content.SharedPreferences prefs = context.getSharedPreferences("capture",Context.MODE_PRIVATE);
        params.x = prefs.getInt("overlayX",Ui.dp(context,12)); params.y = prefs.getInt("overlayY",Ui.dp(context,170));
        params.setTitle("SecureCap controls");
        drag.setOnTouchListener(new View.OnTouchListener() {
            float rawX, rawY; int x,y;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if(e.getAction()==MotionEvent.ACTION_DOWN){rawX=e.getRawX();rawY=e.getRawY();x=params.x;y=params.y;return true;}
                if(e.getAction()==MotionEvent.ACTION_MOVE && view!=null){
                    android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                    params.x=Math.max(0,Math.min(metrics.widthPixels-view.getWidth(),x+(int)(e.getRawX()-rawX)));
                    params.y=Math.max(0,Math.min(metrics.heightPixels-view.getHeight()-Ui.dp(context,32),y+(int)(e.getRawY()-rawY)));
                    manager.updateViewLayout(view,params);return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP){
                    context.getSharedPreferences("capture",Context.MODE_PRIVATE).edit().putInt("overlayX",params.x).putInt("overlayY",params.y).apply(); v.performClick(); return true;
                }
                return false;
            }
        });
        try { manager.addView(view,params); update(); }
        catch(RuntimeException e){ view=null; android.util.Log.e("SecureCap","Overlay",e); }
    }
    public void update() {
        if(view==null)return;
        timer.setText("countdown".equals(Bridge.state()) ? String.valueOf(Bridge.status.getInt("countdown")) : MediaFiles.duration(Bridge.status.getLong("elapsed")));
        record.setIcon(Bridge.session ? "stop" : "capture"); record.setColor(Bridge.session ? 0xffED9A88 : Ui.WHITE);
        record.setContentDescription(Bridge.session ? "Stop recording" : "Start recording");
        pause.setVisibility(Bridge.recording() ? View.VISIBLE : View.GONE);
        pause.setIcon("paused".equals(Bridge.state()) ? "play" : "pause");
        pause.setContentDescription("paused".equals(Bridge.state()) ? "Resume recording" : "Pause recording");
    }
    public void temporarilyHide() { if(view!=null){ hiddenForShot=true; view.setVisibility(View.INVISIBLE); } }
    public void restore() { if(view!=null && hiddenForShot) view.setVisibility(View.VISIBLE); hiddenForShot=false; }
    public void hide() { if(view!=null){ manager.removeView(view); view=null; } hiddenForShot=false; }
}
