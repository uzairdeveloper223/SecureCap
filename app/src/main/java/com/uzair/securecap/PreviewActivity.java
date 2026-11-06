package com.uzair.securecap;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import com.uzair.securecap.ui.IconView;
import com.uzair.securecap.ui.Ui;
import com.uzair.securecap.ui.ThemeActivity;

public class PreviewActivity extends ThemeActivity {
    private VideoView video;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.Palette colors = Ui.colors(this);
        Uri uri=getIntent().getData();String mime=getIntent().getType();String name=getIntent().getStringExtra("name");
        if(uri==null||mime==null){finish();return;}
        LinearLayout root=Ui.column(this);root.setBackgroundColor(colors.BG);setContentView(root);
        LinearLayout header=Ui.row(this);Ui.pad(header,8,8,8,8);
        IconView back=new IconView(this,"back",colors.INK);back.action("Back",this::finish);header.addView(back,Ui.lp(this,48,48));
        TextView title=Ui.text(this,mime.startsWith("video/")?"Screen recording":"Screenshot",18,colors.INK,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        IconView share=new IconView(this,"share",colors.TEAL);share.action("Share capture",()->MediaFiles.share(this,uri,mime));header.addView(share,Ui.lp(this,48,48));
        IconView delete=new IconView(this,"trash",colors.RED);delete.action("Delete capture",()->new AlertDialog.Builder(this).setTitle("Delete capture?").setMessage("This removes the file from this device.").setPositiveButton("Delete",(d,w)->{getContentResolver().delete(uri,null,null);finish();}).setNegativeButton("Cancel",null).show());header.addView(delete,Ui.lp(this,48,48));root.addView(header);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Ui.HERO);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        if(mime.startsWith("video/")){
            video=new VideoView(this);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1);vp.gravity=android.view.Gravity.CENTER;frame.addView(video,vp);
            MediaController controls=new MediaController(this);controls.setAnchorView(frame);video.setMediaController(controls);video.setVideoURI(uri);
            video.setOnPreparedListener(player->{if(state!=null)video.seekTo(state.getInt("position"));video.start();});
            video.setOnErrorListener((player,what,extra)->{new AlertDialog.Builder(this).setTitle("Unable to play recording").setMessage("Player error "+what+" / "+extra).setPositiveButton("OK",null).show();return true;});
        }else{
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("Captured screenshot");frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
            Bridge.io.execute(()->{
                try{Bitmap bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(),uri),(decoder,info,source)->{int max=Math.max(info.getSize().getWidth(),info.getSize().getHeight());if(max>2400)decoder.setTargetSampleSize(2);});Bridge.main.post(()->{if(!isDestroyed())image.setImageBitmap(bitmap);});}
                catch(Exception e){Bridge.main.post(()->{if(!isDestroyed())new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("OK",null).show();});}
            });
        }
        TextView filename=Ui.text(this,name==null?"SecureCap capture":name,11,colors.MUTED,false);Ui.pad(filename,20,16,20,16);root.addView(filename);
    }
    @Override protected void onSaveInstanceState(Bundle b){if(video!=null)b.putInt("position",video.getCurrentPosition());super.onSaveInstanceState(b);}
    @Override protected void onPause(){if(video!=null)video.pause();super.onPause();}
    @Override protected void onDestroy(){if(video!=null)video.stopPlayback();super.onDestroy();}
}
