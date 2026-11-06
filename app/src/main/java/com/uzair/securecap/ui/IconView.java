package com.uzair.securecap.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A consistent 24-unit, rounded-stroke icon set. No font glyphs or emoji. */
public class IconView extends View {
    private String icon;
    private int color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public IconView(Context context) { this(context, "capture", Ui.colors(context).INK); }
    public IconView(Context context, String icon, int color) {
        super(context); this.icon = icon; this.color = color;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setIcon(String value) { icon = value; invalidate(); }
    public void setColor(int value) { color = value; invalidate(); }
    @Override public boolean performClick() { super.performClick(); return true; }
    public void action(String description, Runnable action) {
        setContentDescription(description); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setOnClickListener(v -> action.run()); Ui.ripple(this, android.graphics.Color.TRANSPARENT, 14);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float size = Math.min(getWidth(), getHeight()) * .52f;
        c.save(); c.translate((getWidth()-size)/2, (getHeight()-size)/2); c.scale(size/24,size/24);
        paint.setColor(color); paint.setStrokeWidth(1.8f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
        switch (icon) {
            case "capture":
                line(c,8,3,3,3,3,8); line(c,16,3,21,3,21,8); line(c,21,16,21,21,16,21); line(c,8,21,3,21,3,16);
                paint.setStyle(Paint.Style.FILL); c.drawCircle(12,12,4,paint); break;
            case "camera":
                line(c,3,7,7,7,9,4,15,4,17,7,21,7,21,20,3,20,3,7); c.drawCircle(12,13,4,paint); break;
            case "stop": paint.setStyle(Paint.Style.FILL); c.drawRoundRect(5,5,19,19,2,2,paint); break;
            case "pause": paint.setStyle(Paint.Style.FILL); c.drawRoundRect(6,4,10,20,1,1,paint); c.drawRoundRect(14,4,18,20,1,1,paint); break;
            case "play": paint.setStyle(Paint.Style.FILL); line(c,7,4,20,12,7,20,7,4); break;
            case "close": line(c,6,6,18,18); line(c,18,6,6,18); break;
            case "back": line(c,14,5,7,12,14,19); line(c,7,12,21,12); break;
            case "chevron": line(c,9,6,15,12,9,18); break;
            case "share": c.drawCircle(18,5,3,paint); c.drawCircle(5,12,3,paint); c.drawCircle(18,19,3,paint); line(c,8,11,15,6); line(c,8,13,15,18); break;
            case "trash": line(c,3,6,21,6); line(c,9,6,9,3,15,3,15,6); line(c,5,6,6,21,18,21,19,6); line(c,10,10,10,17); line(c,14,10,14,17); break;
            case "folder": line(c,3,7,3,4,10,4,13,7,21,7,21,20,3,20,3,7); break;
            case "audio": line(c,4,9,8,9,13,4,13,20,8,15,4,15,4,9); c.drawArc(12,4,22,20,-55,110,false,paint); break;
            case "check": line(c,5,12,10,17,20,6); break;
            case "shield": line(c,12,2,21,6,20,15,16,20,12,22,8,20,4,15,3,6,12,2); line(c,8,12,11,15,16,9); break;
            case "drag": paint.setStyle(Paint.Style.FILL); for(int x=9;x<=15;x+=6) for(int y=6;y<=18;y+=6) c.drawCircle(x,y,1,paint); break;
            case "settings":
                c.drawCircle(12,12,6.5f,paint); c.drawCircle(12,12,2.5f,paint);
                for(int i=0;i<8;i++){ c.save(); c.rotate(i*45,12,12); line(c,12,2,12,5); c.restore(); } break;
            default: c.drawCircle(12,12,8,paint);
        }
        c.restore();
    }
    private void line(Canvas c, float... points) {
        Path p = new Path(); p.moveTo(points[0],points[1]); for(int i=2;i<points.length;i+=2) p.lineTo(points[i],points[i+1]); c.drawPath(p,paint);
    }
}
