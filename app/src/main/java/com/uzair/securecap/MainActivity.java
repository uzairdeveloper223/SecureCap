package com.uzair.securecap;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import com.uzair.securecap.ui.IconView;
import com.uzair.securecap.ui.Ui;
import com.uzair.securecap.ui.ThemeActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends ThemeActivity {
    private Ui.Palette colors;
    private AlertDialog settingsDialog;
    private Preferences preferences;
    private LinearLayout root, content, latest;
    private TextView connection, recordTitle, recordDetail, recordButton, pauseButton, shotButton;
    private IconView connectionIcon;
    private boolean library, tileStart, loading, resumed;
    private List<MediaFiles.Item> files = new ArrayList<>();
    private long lastLoad;
    private final Runnable observer = this::updateState;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); preferences = new Preferences(this);
        if (state != null) library = state.getBoolean("library");
        tileStart = getIntent().getBooleanExtra("tileStart", false);
        render();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        tileStart = intent.getBooleanExtra("tileStart", false);
        updateState();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true; Bridge.observe(observer); Bridge.connect(); loadFiles();
    }
    @Override protected void onPause() { resumed = false; Bridge.remove(observer); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle b) { b.putBoolean("library", library); super.onSaveInstanceState(b); }

    private void render() {
        colors = Ui.colors(this);
        root = Ui.column(this); root.setBackgroundColor(colors.BG); setContentView(root);
        LinearLayout header = Ui.row(this); Ui.pad(header,24,12,16,12);
        IconView logo = new IconView(this,"capture",colors.TEAL); Ui.surface(logo,colors.LIGHT,12); header.addView(logo,Ui.lp(this,40,40));
        TextView brand = Ui.text(this,"SecureCap",21,colors.INK,true); Ui.pad(brand,12,0,0,0);
        header.addView(brand,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        IconView settings = new IconView(this,"settings",colors.INK); settings.action("Capture settings",this::settings); header.addView(settings,Ui.lp(this,48,48));
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content = Ui.column(this); Ui.pad(content,24,14,24,24); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        latest = null; recordTitle = null; recordDetail = null; recordButton = null; pauseButton = null; shotButton = null; connection = null;
        if (library) libraryPage(); else capturePage();
        LinearLayout nav = Ui.row(this); Ui.pad(nav,24,10,24,14);
        nav.addView(navItem("capture","Capture",!library,() -> {library=false;render();}),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        View gap = new View(this); nav.addView(gap,Ui.lp(this,10,1));
        nav.addView(navItem("folder","Library",library,() -> {library=true;render();loadFiles();}),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1));
        root.addView(nav); updateState();
    }
    private LinearLayout navItem(String icon,String label,boolean selected,Runnable action) {
        LinearLayout item = Ui.row(this); item.setGravity(Gravity.CENTER); Ui.ripple(item,selected?colors.LIGHT:colors.BG,14);
        IconView i=new IconView(this,icon,selected?colors.TEAL:colors.MUTED); item.addView(i,Ui.lp(this,38,38));
        item.addView(Ui.text(this,label,14,selected?colors.TEAL:colors.MUTED,selected)); item.setOnClickListener(v->action.run()); return item;
    }
    private void capturePage() {
        TextView eyebrow=Ui.text(this,"ANDROID 11  /  SHIZUKU",10,colors.MUTED,true); eyebrow.setLetterSpacing(.14f); content.addView(eyebrow);
        content.addView(Ui.space(this,12));
        content.addView(Ui.text(this,"Screen capture",30,colors.INK,true)); content.addView(Ui.space(this,8));
        TextView sub=Ui.text(this,"Record your display. Keep every detail.",14,colors.MUTED,false); content.addView(sub); content.addView(Ui.space(this,24));
        LinearLayout card=Ui.column(this); Ui.surface(card,Ui.HERO,22); Ui.pad(card,20,20,20,20);
        LinearLayout top=Ui.row(this);
        TextView label=Ui.text(this,"SECURE DISPLAY",10,0xffADBFAD,true); label.setLetterSpacing(.14f);
        top.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=Ui.text(this,"H.264",10,0xffDBE7DB,true); Ui.pad(badge,9,5,9,5); Ui.surface(badge,0xff2B4140,7); top.addView(badge); card.addView(top);
        card.addView(Ui.space(this,22));
        recordTitle=Ui.text(this,"Ready to record",25,Ui.WHITE,true); card.addView(recordTitle);
        card.addView(Ui.space(this,8));
        recordDetail=Ui.text(this,"Native resolution · 30 fps · Device audio",12,0xffADBFAD,false); card.addView(recordDetail);
        card.addView(Ui.space(this,24));
        recordButton=Ui.button(this,"Start recording",true,this::recordAction); card.addView(recordButton,Ui.lp(this,-1,54));
        pauseButton=Ui.button(this,"Pause recording",false,()->CaptureService.command(this,CaptureService.PAUSE));
        LinearLayout.LayoutParams pauseParams=Ui.lp(this,-1,48); pauseParams.topMargin=Ui.dp(this,10); card.addView(pauseButton,pauseParams); pauseButton.setVisibility(View.GONE);
        content.addView(card);
        content.addView(Ui.space(this,12));
        LinearLayout shortcuts=Ui.row(this);
        shotButton=Ui.button(this,"Screenshot",false,()-> {
            if (!ready()) return;
            moveTaskToBack(true); Bridge.main.postDelayed(()->CaptureService.command(this,CaptureService.SHOT),350);
        });
        shortcuts.addView(shotButton,new LinearLayout.LayoutParams(0,Ui.dp(this,50),1)); shortcuts.addView(new View(this),Ui.lp(this,10,1));
        shortcuts.addView(Ui.button(this,"Floating controls",false,()-> {
            if (!Settings.canDrawOverlays(this)) overlayPermission(); else CaptureService.command(this,CaptureService.SHOW);
        }),new LinearLayout.LayoutParams(0,Ui.dp(this,50),1)); content.addView(shortcuts);
        content.addView(Ui.space(this,18));
        LinearLayout config=Ui.row(this); Ui.surface(config,colors.SURFACE,16); Ui.pad(config,6,14,6,14);
        config.addView(configItem("AUDIO",preferences.audioLabel(),this::audioChoice),new LinearLayout.LayoutParams(0,-2,1));
        config.addView(configItem("RESOLUTION",preferences.sizeLabel(),this::resolutionChoice),new LinearLayout.LayoutParams(0,-2,1));
        config.addView(configItem("FRAME RATE",preferences.fps()+" fps",this::fpsChoice),new LinearLayout.LayoutParams(0,-2,1)); content.addView(config);
        content.addView(Ui.space(this,12));
        LinearLayout connectionRow=Ui.row(this); Ui.ripple(connectionRow,colors.LIGHT,12); Ui.pad(connectionRow,8,6,10,6);
        connectionIcon=new IconView(this,"shield",colors.TEAL); connectionRow.addView(connectionIcon,Ui.lp(this,36,36));
        connection=Ui.text(this,Bridge.connection,12,colors.TEAL,true); connectionRow.addView(connection,new LinearLayout.LayoutParams(0,-2,1));
        connectionRow.addView(new IconView(this,"chevron",colors.TEAL),Ui.lp(this,24,32)); connectionRow.setOnClickListener(v->Bridge.requestPermission()); content.addView(connectionRow);
        content.addView(Ui.space(this,26));
        LinearLayout recentHeader=Ui.row(this); recentHeader.addView(Ui.text(this,"Recent captures",17,colors.INK,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView all=Ui.text(this,"View all",12,colors.TEAL,true); Ui.pad(all,10,8,0,8); all.setOnClickListener(v->{library=true;render();loadFiles();}); recentHeader.addView(all); content.addView(recentHeader);
        content.addView(Ui.space(this,12)); latest=Ui.column(this);content.addView(latest);drawFiles();
        content.addView(Ui.space(this,24)); footer();
    }
    private LinearLayout configItem(String title,String value,Runnable action) {
        LinearLayout item=Ui.column(this); Ui.pad(item,10,0,4,0); Ui.ripple(item,colors.SURFACE,10);
        TextView label=Ui.text(this,title,9,colors.MUTED,true);label.setLetterSpacing(.08f);item.addView(label);item.addView(Ui.space(this,7));
        item.addView(Ui.text(this,value,12,colors.INK,true));item.setOnClickListener(v->action.run());return item;
    }
    private void libraryPage() {
        content.addView(Ui.text(this,"Your captures",30,colors.INK,true));content.addView(Ui.space(this,8));
        content.addView(Ui.text(this,"Saved on this device. Ready to share.",14,colors.MUTED,false));content.addView(Ui.space(this,24));
        latest=Ui.column(this);content.addView(latest);drawFiles();content.addView(Ui.space(this,24));footer();
    }
    private void footer() {
        TextView author=Ui.text(this,getString(R.string.author_credit),11,colors.MUTED,false);author.setGravity(Gravity.CENTER);content.addView(author);
        TextView website=Ui.text(this,"uzair.is-a.dev",11,colors.TEAL,true);website.setGravity(Gravity.CENTER);Ui.pad(website,8,10,8,10);
        website.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://uzair.is-a.dev"))));content.addView(website);
    }
    private void updateState() {
        if(root==null)return;
        if(connection!=null){connection.setText(Bridge.connection);connection.setTextColor(Bridge.engine!=null?colors.TEAL:colors.INK);connectionIcon.setColor(Bridge.engine!=null?colors.TEAL:colors.MUTED);}
        if(recordTitle!=null){
            String state=Bridge.state();boolean active=Bridge.session;
            String title="recording".equals(state)?MediaFiles.duration(Bridge.status.getLong("elapsed")):"paused".equals(state)?"Recording paused":"countdown".equals(state)?"Starting in "+Bridge.status.getInt("countdown"):"starting".equals(state)?"Preparing capture":"stopping".equals(state)?"Saving recording":"Ready to record";
            recordTitle.setText(title);
            recordDetail.setText(active?Bridge.status.getString("message",""):preferences.sizeLabel()+" · "+preferences.fps()+" fps · "+preferences.audioLabel());
            recordButton.setText(active?("stopping".equals(state)?"Saving…":"countdown".equals(state)?"Cancel recording":"Stop recording"):"Start recording");
            recordButton.setEnabled(!"stopping".equals(state));
            pauseButton.setVisibility(Bridge.recording()?View.VISIBLE:View.GONE);pauseButton.setText("paused".equals(state)?"Resume recording":"Pause recording");
            shotButton.setEnabled(!"starting".equals(state));
        }
        if(!Bridge.session && resumed && System.currentTimeMillis()-lastLoad>1500)loadFiles();
        if(tileStart && resumed && Bridge.engine!=null && !Bridge.session){tileStart=false;recordAction();}
    }
    private boolean ready() {
        if(android.os.Build.VERSION.SDK_INT!=30){new AlertDialog.Builder(this).setTitle("Android 11 required").setMessage("This capture engine uses the Android 11 secure-display API.").setPositiveButton("OK",null).show();return false;}
        if(Bridge.engine==null){Bridge.requestPermission();return false;}return true;
    }
    private void recordAction() {
        if(Bridge.session){CaptureService.command(this,CaptureService.STOP);return;}
        if(!ready())return;
        CaptureService.command(this,CaptureService.START);moveTaskToBack(true);
    }
    private void loadFiles() {
        if(loading)return;loading=true;lastLoad=System.currentTimeMillis();
        Bridge.io.execute(()-> {
            List<MediaFiles.Item> result;
            try{result=MediaFiles.list(this);}catch(Exception e){android.util.Log.e("SecureCap","Loading library",e);result=new ArrayList<>();}
            List<MediaFiles.Item> list=result;
            Bridge.main.post(()->{loading=false;files=list;if(!isDestroyed())drawFiles();});
        });
    }
    private void drawFiles() {
        if(latest==null)return;latest.removeAllViews();
        if(files.isEmpty()){
            LinearLayout empty=Ui.column(this);Ui.surface(empty,colors.SURFACE,16);Ui.pad(empty,20,24,20,24);
            IconView icon=new IconView(this,"folder",colors.MUTED);LinearLayout.LayoutParams ip=Ui.lp(this,48,48);ip.gravity=Gravity.CENTER;empty.addView(icon,ip);
            TextView title=Ui.text(this,"No captures yet",14,colors.INK,true);title.setGravity(Gravity.CENTER);empty.addView(title);empty.addView(Ui.space(this,8));
            TextView detail=Ui.text(this,"Recordings and screenshots will appear here.",11,colors.MUTED,false);detail.setGravity(Gravity.CENTER);empty.addView(detail);latest.addView(empty);return;
        }
        int limit=library?files.size():Math.min(2,files.size());
        SimpleDateFormat format=new SimpleDateFormat("MMM d · HH:mm",Locale.getDefault());
        for(int n=0;n<limit;n++){
            MediaFiles.Item file=files.get(n);LinearLayout row=Ui.row(this);Ui.ripple(row,colors.SURFACE,16);Ui.pad(row,10,10,8,10);
            ImageView thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);Ui.surface(thumbnail,colors.LIGHT,10);thumbnail.setClipToOutline(true);
            thumbnail.setTag(file.uri.toString());row.addView(thumbnail,Ui.lp(this,56,66));
            Bridge.io.execute(()-> {
                try{Bitmap bitmap=getContentResolver().loadThumbnail(file.uri,new android.util.Size(160,200),null);
                    Bridge.main.post(()->{if(file.uri.toString().equals(thumbnail.getTag())&&!isDestroyed())thumbnail.setImageBitmap(bitmap);});
                }catch(Exception ignored){}
            });
            LinearLayout text=Ui.column(this);Ui.pad(text,12,0,4,0);
            TextView title=Ui.text(this,file.video()?"Screen recording":"Screenshot",14,colors.INK,true);text.addView(title);text.addView(Ui.space(this,7));
            text.addView(Ui.text(this,format.format(new Date(file.date*1000)),11,colors.MUTED,false));text.addView(Ui.space(this,5));
            text.addView(Ui.text(this,(file.video()?MediaFiles.duration(file.duration)+" · ":"PNG · ")+MediaFiles.size(file.size),10,colors.MUTED,false));
            row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            IconView share=new IconView(this,"share",colors.TEAL);share.action("Share "+file.name,()->MediaFiles.share(this,file.uri,file.mime));row.addView(share,Ui.lp(this,44,48));
            row.setOnClickListener(v->startActivity(new Intent(this,PreviewActivity.class).setDataAndType(file.uri,file.mime).putExtra("name",file.name)));
            LinearLayout.LayoutParams rp=Ui.lp(this,-1,-2);rp.bottomMargin=Ui.dp(this,10);latest.addView(row,rp);
        }
    }
    private void overlayPermission() {
        new AlertDialog.Builder(this).setTitle("Floating controls").setMessage("Allow SecureCap to display its movable recording toolbar over other apps.")
                .setPositiveButton("Open settings",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())))).setNegativeButton("Later",null).show();
    }
    private void settings() {
        LinearLayout body=Ui.column(this);Ui.pad(body,24,8,24,16);
        settingRow(body,"Appearance",preferences.themeLabel(),this::themeChoice);
        settingRow(body,"Audio source",preferences.audioLabel(),this::audioChoice);
        settingRow(body,"Resolution",preferences.sizeLabel(),this::resolutionChoice);
        settingRow(body,"Frame rate",preferences.fps()+" fps",this::fpsChoice);
        settingRow(body,"Video bitrate",preferences.bitrate()/1000000+" Mbps",()->choose("Video bitrate",new String[]{"4 Mbps","8 Mbps","12 Mbps"},new int[]{4000000,8000000,12000000},"bitrate",preferences.bitrate()));
        settingRow(body,"Start delay",preferences.countdown()+" seconds",()->choose("Start delay",new String[]{"No delay","3 seconds","5 seconds"},new int[]{0,3,5},"countdown",preferences.countdown()));
        Switch toggle=new Switch(this);toggle.setText(R.string.floating_on_start);toggle.setTextSize(13);toggle.setTextColor(colors.INK);toggle.setChecked(preferences.floating());Ui.pad(toggle,0,18,0,18);
        toggle.setOnCheckedChangeListener((v,on)->{preferences.set("floating",on);if(on&&!Settings.canDrawOverlays(this))overlayPermission();});body.addView(toggle);
        TextView permission=Ui.text(this,Settings.canDrawOverlays(this)?"Overlay permission granted":"Grant overlay permission",12,colors.TEAL,true);Ui.pad(permission,0,8,0,16);permission.setOnClickListener(v->overlayPermission());body.addView(permission);
        TextView note=Ui.text(this,"Device audio uses remote submix. On Android 11, playback is redirected while recording and the shell popup briefly appears at start.\n\nRecordings: Movies/SecureCap\nScreenshots: Pictures/SecureCap\n\nPortrait and landscape changes fit inside the recording’s initial frame.",12,colors.MUTED,false);note.setLineSpacing(Ui.dp(this,3),1);body.addView(note);
        TextView test=Ui.text(this,"Open FLAG_SECURE test app",13,colors.TEAL,true);Ui.pad(test,0,20,0,8);test.setOnClickListener(v->{Intent launch=getPackageManager().getLaunchIntentForPackage("dev.uzair.flagtest");if(launch!=null)startActivity(launch);});body.addView(test);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        settingsDialog = new AlertDialog.Builder(this).setTitle("Capture settings").setView(scroll).setPositiveButton("Done",(d,w)->render()).create();
        settingsDialog.show();
    }
    private TextView settingRow(LinearLayout parent,String title,String value,Runnable action) {
        TextView row=Ui.text(this,title+"\n"+value,14,colors.INK,false);row.setLineSpacing(Ui.dp(this,5),1);Ui.pad(row,0,12,0,12);row.setOnClickListener(v->action.run());parent.addView(row);
        View divider=new View(this);divider.setBackgroundColor(colors.LINE);parent.addView(divider,Ui.lp(this,-1,1));return row;
    }
    private void themeChoice() {
        String[] values = {"system", "light", "dark"};
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(preferences.theme())) selected = i;
        new AlertDialog.Builder(this).setTitle("Appearance")
                .setSingleChoiceItems(new String[]{"System default", "Light", "Dark"}, selected, (dialog, which) -> {
                    preferences.set("theme", values[which]);
                    dialog.dismiss();
                    if (settingsDialog != null) settingsDialog.dismiss();
                    recreate();
                }).setNegativeButton("Cancel", null).show();
    }
    @Override protected void onDestroy() {
        if (settingsDialog != null) settingsDialog.dismiss();
        super.onDestroy();
    }
    private void audioChoice() {
        String[] ids={"device","mic","off"};int selected=0;for(int i=0;i<ids.length;i++)if(ids[i].equals(preferences.audio()))selected=i;
        new AlertDialog.Builder(this).setTitle("Audio source").setSingleChoiceItems(new String[]{"Device audio · remote submix","Microphone","No audio"},selected,(dialog,which)->{preferences.set("audio",ids[which]);dialog.dismiss();render();}).setNegativeButton("Cancel",null).show();
    }
    private void resolutionChoice() {choose("Longest edge",new String[]{"Native · up to 2400 px","1920 px","1600 px","1280 px"},new int[]{2400,1920,1600,1280},"maxSize",preferences.maxSize());}
    private void fpsChoice() {choose("Frame rate",new String[]{"30 fps","60 fps"},new int[]{30,60},"fps",preferences.fps());}
    private void choose(String title,String[] labels,int[] values,String key,int current) {
        int selected=0;for(int i=0;i<values.length;i++)if(values[i]==current)selected=i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels,selected,(dialog,which)->{preferences.set(key,values[which]);dialog.dismiss();render();}).setNegativeButton("Cancel",null).show();
    }
}
