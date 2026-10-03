package com.devkaranlodhi.trade;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.net.Uri;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import androidx.core.app.NotificationCompat;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(9,13,22), SURFACE=Color.rgb(18,25,38), SURFACE2=Color.rgb(24,33,49),
            TEXT=Color.rgb(238,244,255), MUTED=Color.rgb(148,163,184), BORDER=Color.rgb(48,61,82);
    static final int GREEN=Color.rgb(41,205,126), RED=Color.rgb(255,82,102), WAIT=Color.rgb(246,183,72), BLUE=Color.rgb(76,151,255), PURPLE=Color.rgb(145,102,255);
    LinearLayout root, holidayList, evidenceList, warningList, newsList;
    LinearLayout signalCard, connectionCard;
    TextView action, market, updated, status, connectionDot, marketBadge, nextOpen, score, regime, reversal, horizons, optionSummary, breadthSummary, flowSummary, newsSummary, positionStatus, positionHealth;
    TextView entryValue, stopValue, targetValue;
    boolean connected=false;
    EditText url,key,posType,posStrike,posEntry,posQty,posSL,posTarget;
    Button start,savePosition,clearPosition,backtestButton,settingsButton,infoButton,connectButton;
    ScheduledExecutorService scheduler;
    SharedPreferences prefs;
    boolean firstStrongAlert=true;
    String lastAction="WAIT";

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,float size,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(c);v.setIncludeFontPadding(false);return v;}
    GradientDrawable box(int top,int bottom,float r){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});d.setCornerRadius(dp(r));return d;}
    GradientDrawable outlined(){GradientDrawable d=box(SURFACE2,SURFACE,20);d.setStroke(dp(1),BORDER);return d;}
    GradientDrawable rounded(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(17),dp(18),dp(17));c.setBackground(outlined());c.setElevation(dp(10));return c;}
    LinearLayout.LayoutParams margin(float t,float b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(t),0,dp(b));return p;}
    LinearLayout.LayoutParams weight(float w,float r){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,w);p.setMargins(0,0,dp(r),0);return p;}

    @Override public void onCreate(Bundle b){super.onCreate(b);Window w=getWindow();WindowCompat.setDecorFitsSystemWindows(w,false);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);prefs=getSharedPreferences("connection",MODE_PRIVATE);createNotificationChannel();if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7001);build();setDisconnectedVisual();if(!prefs.getString("url","").isEmpty()&&!loadApiKey().isEmpty()){new Handler(Looper.getMainLooper()).postDelayed(this::autoConnect,350);}}

    void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(24));root.setBackgroundColor(BG);scroll.addView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{int top=i.getInsets(WindowInsetsCompat.Type.statusBars()).top,bottom=i.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;v.setPadding(dp(16),top+dp(12),dp(16),bottom+dp(22));return i;});

        TextView title=text("DevkaranLodhiTrade",25,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
        TextView sub=text("BANKNIFTY • PRODUCTION PREDICTION ENGINE",11,MUTED);sub.setPadding(0,dp(6),0,0);root.addView(sub);

        LinearLayout topActions=new LinearLayout(this);topActions.setGravity(Gravity.CENTER_VERTICAL);topActions.setPadding(0,dp(12),0,dp(4));
        TextView liveTag=text("● LIVE ENGINE",10,GREEN);liveTag.setTypeface(Typeface.DEFAULT,Typeface.BOLD);topActions.addView(liveTag,new LinearLayout.LayoutParams(0,dp(42),1));
        infoButton=topButton("ⓘ  INFO",BLUE);infoButton.setOnClickListener(v->showInfo());topActions.addView(infoButton,new LinearLayout.LayoutParams(dp(76),dp(42)));
        connectButton=topButton("↗  CONNECT",GREEN);connectButton.setOnClickListener(v->showConnectionSettings());topActions.addView(connectButton,new LinearLayout.LayoutParams(dp(96),dp(42)));
        settingsButton=topButton("⚙  SETTINGS",PURPLE);settingsButton.setOnClickListener(v->showAlertSettings());topActions.addView(settingsButton,new LinearLayout.LayoutParams(dp(104),dp(42)));
        root.addView(topActions);

        LinearLayout sr=new LinearLayout(this);sr.setGravity(Gravity.CENTER_VERTICAL);connectionDot=text("●",11,RED);sr.addView(connectionDot);status=text("  Not connected",12,RED);sr.addView(status,new LinearLayout.LayoutParams(0,-2,1));TextView hint=text("Auto-reconnect ON",10,MUTED);sr.addView(hint);root.addView(sr,margin(0,12));

        LinearLayout ms=card();LinearLayout mh=new LinearLayout(this);mh.setGravity(Gravity.CENTER_VERTICAL);TextView mt=text("NSE MARKET STATUS",11,MUTED);mt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);mh.addView(mt,new LinearLayout.LayoutParams(0,-2,1));marketBadge=text("CHECKING",10,BLUE);marketBadge.setGravity(Gravity.CENTER);marketBadge.setPadding(dp(11),dp(7),dp(11),dp(7));marketBadge.setBackground(rounded(Color.rgb(28,43,66),20));mh.addView(marketBadge);ms.addView(mh);nextOpen=text("Checking market calendar…",15,TEXT);nextOpen.setPadding(0,dp(12),0,0);ms.addView(nextOpen);TextView hrs=text("BANKNIFTY derivatives: 09:15 AM – 03:40 PM IST",11,MUTED);hrs.setPadding(0,dp(5),0,0);ms.addView(hrs);root.addView(ms,margin(0,12));

        signalCard=card();LinearLayout sh=new LinearLayout(this);sh.setGravity(Gravity.CENTER_VERTICAL);TextView st=text("CURRENT SIGNAL",11,MUTED);st.setTypeface(Typeface.DEFAULT,Typeface.BOLD);sh.addView(st,new LinearLayout.LayoutParams(0,-2,1));TextView pd=text("PUBLIC DATA",10,BLUE);pd.setPadding(dp(10),dp(6),dp(10),dp(6));pd.setBackground(rounded(Color.rgb(28,43,66),20));sh.addView(pd);signalCard.addView(sh);action=text("WAIT",38,WAIT);action.setGravity(Gravity.CENTER);action.setTypeface(Typeface.DEFAULT,Typeface.BOLD);action.setPadding(0,dp(14),0,dp(5));signalCard.addView(action);score=text("Evidence score: —",13,MUTED);score.setGravity(Gravity.CENTER);signalCard.addView(score);market=text("Waiting for backend data",13,MUTED);market.setGravity(Gravity.CENTER);market.setPadding(0,dp(7),0,0);signalCard.addView(market);root.addView(signalCard,margin(0,12));

        LinearLayout lev=card();TextView lt=text("TRADE LEVELS",11,MUTED);lt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);lev.addView(lt,margin(0,12));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);entryValue=valueText();stopValue=valueText();targetValue=valueText();row.addView(levelBox("ENTRY",entryValue),weight(1,6));row.addView(levelBox("STOP LOSS",stopValue),weight(1,6));row.addView(levelBox("TARGET",targetValue),weight(1,0));lev.addView(row);updated=text("Updated: —",11,MUTED);updated.setPadding(0,dp(13),0,0);lev.addView(updated);root.addView(lev,margin(0,12));

        LinearLayout pos=card();TextView pt=text("POSITION MONITORING • MANUAL GROWW ENTRY",11,MUTED);pt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);pos.addView(pt,margin(0,8));TextView note=text("Enter the position you actually bought. No Groww login/order API is used; the app monitors the live thesis and alerts you.",11,MUTED);pos.addView(note,margin(0,10));
        posType=field("Option","CALL or PUT",false);posStrike=field("Strike","e.g. 55000",false);posEntry=field("Entry premium","e.g. 120",false);posQty=field("Quantity","e.g. 30",false);posSL=field("Underlying invalidation","optional",false);posTarget=field("Underlying target","optional",false);pos.addView(posType,margin(0,7));pos.addView(posStrike,margin(0,7));pos.addView(posEntry,margin(0,7));pos.addView(posQty,margin(0,7));pos.addView(posSL,margin(0,7));pos.addView(posTarget,margin(0,10));LinearLayout pb=new LinearLayout(this);savePosition=new Button(this);savePosition.setText("SAVE / MONITOR");clearPosition=new Button(this);clearPosition.setText("CLOSE POSITION");pb.addView(savePosition,weight(1,6));pb.addView(clearPosition,weight(1,0));pos.addView(pb);positionStatus=text("No active position",12,MUTED);positionStatus.setPadding(0,dp(10),0,0);pos.addView(positionStatus);positionHealth=text("Position health: —",12,MUTED);positionHealth.setPadding(0,dp(6),0,0);pos.addView(positionHealth);root.addView(pos,margin(0,12));

        LinearLayout hist=card();TextView hi=text("HISTORICAL SIMILARITY",11,MUTED);hi.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hist.addView(hi,margin(0,8));TextView ht=text("Available from INFO • matches are context only, not guaranteed forecasts.",11,MUTED);hist.addView(ht);newsList=list();hist.addView(newsList);hist.setVisibility(View.GONE);root.addView(hist,margin(0,0));

        LinearLayout bt=card();TextView bth=text("OUT-OF-SAMPLE BACKTEST",11,MUTED);bth.setTypeface(Typeface.DEFAULT,Typeface.BOLD);bt.addView(bth,margin(0,8));TextView btNote=text("Historical measurement of the production rules. It does not guarantee future results.",11,MUTED);bt.addView(btNote,margin(0,8));backtestButton=new Button(this);backtestButton.setText("RUN 1Y BACKTEST");bt.addView(backtestButton);TextView btResult=text("Backtest: not run",12,TEXT);btResult.setPadding(0,dp(8),0,0);bt.addView(btResult);backtestButton.setOnClickListener(v->runBacktest(btResult));root.addView(bt,margin(0,18));

        // Connection fields are kept off the main screen. They are edited only from CONNECT.
        url=field("Backend URL","https://your-service.onrender.com",false);key=field("Backend API Key","Paste generated key",true);url.setVisibility(View.GONE);key.setVisibility(View.GONE);root.addView(url,new LinearLayout.LayoutParams(1,1));root.addView(key,new LinearLayout.LayoutParams(1,1));
        connectionCard=null;
        url.setText(prefs.getString("url",""));key.setText(loadApiKey());holidayList=list();setDefaultHolidayCalendar();loadPosition();savePosition.setOnClickListener(v->savePosition());clearPosition.setOnClickListener(v->clearPosition());setDefaultHolidayCalendar();setContentView(scroll);
    }

    Button topButton(String label,int accent){Button b=new Button(this);b.setText(label);b.setTextSize(10);b.setTextColor(TEXT);b.setAllCaps(false);b.setPadding(dp(3),0,dp(3),0);b.setMinHeight(dp(42));b.setBackground(gradientStrokeButton(accent));return b;}

    void showConnectionSettings(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(4),dp(2),dp(4),dp(2));
        EditText u=field("Backend URL","https://your-service.onrender.com",false);EditText k=field("Backend API Key","Paste generated key",true);u.setText(prefs.getString("url",""));k.setText(loadApiKey());panel.addView(u,margin(0,10));panel.addView(k,margin(0,12));
        TextView n=text("API key is stored encrypted on this phone. It is not shown on the main screen.",11,MUTED);panel.addView(n,margin(0,12));
        LinearLayout actions=new LinearLayout(this);Button save=new Button(this);save.setText("SAVE & CONNECT");save.setAllCaps(false);save.setTextColor(Color.WHITE);save.setBackground(box(Color.rgb(69,124,220),Color.rgb(45,82,165),14));Button cancel=new Button(this);cancel.setText("CANCEL");cancel.setAllCaps(false);cancel.setBackground(gradientStrokeButton(BORDER));actions.addView(save,weight(1,6));actions.addView(cancel,weight(1,0));panel.addView(actions);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("🔗 BACKEND CONNECTION").setView(panel).create();cancel.setOnClickListener(v->d.dismiss());save.setOnClickListener(v->{String base=u.getText().toString().trim();String api=k.getText().toString().trim();if(base.endsWith("/"))base=base.substring(0,base.length()-1);if(base.isEmpty()||api.isEmpty()){toast("Enter Backend URL and API Key");return;}url.setText(base);key.setText(api);prefs.edit().putString("url",base).apply();saveApiKey(api);toast("Connection saved • connecting…");d.dismiss();startPolling();});d.show();
    }

    void showInfo(){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(4),dp(2),dp(4),dp(2));ScrollView sv=new ScrollView(this);sv.addView(panel);
        TextView intro=text("BANKNIFTY INFORMATION CENTER",19,TEXT);intro.setTypeface(Typeface.DEFAULT,Typeface.BOLD);panel.addView(intro,margin(0,6));TextView desc=text("Future-reference information is kept here so the main trading screen stays focused.",11,MUTED);panel.addView(desc,margin(0,14));
        LinearLayout intel=card();TextView it=text("MARKET INTELLIGENCE",12,MUTED);it.setTypeface(Typeface.DEFAULT,Typeface.BOLD);intel.addView(it,margin(0,8));TextView ir=text("Regime: —",14,TEXT);TextView ib=text("Bank breadth: —",12,MUTED);TextView io=text("Options: —",12,MUTED);TextView iff=text("Institutional flows: —",12,MUTED);TextView inn=text("News/event context: —",12,MUTED);TextView irv=text("Reversal risks: —",12,MUTED);intel.addView(ir);intel.addView(ib,margin(0,6));intel.addView(io,margin(0,6));intel.addView(iff,margin(0,6));intel.addView(inn,margin(0,6));intel.addView(irv,margin(0,6));
        TextView eh=text("Supporting evidence",11,MUTED);eh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);intel.addView(eh,margin(0,10));LinearLayout ev=list();intel.addView(ev);TextView wh=text("Warnings / invalidation",11,MUTED);wh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);intel.addView(wh,margin(0,10));LinearLayout wa=list();intel.addView(wa);TextView hz=text("Horizons: —",11,MUTED);intel.addView(hz,margin(0,10));
        TextView simh=text("Historical similarity",11,MUTED);simh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);intel.addView(simh,margin(0,12));LinearLayout sim=list();intel.addView(sim);panel.addView(intel,margin(0,8));
        LinearLayout hc=card();TextView h=text("NSE HOLIDAY CALENDAR 2026",12,MUTED);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hc.addView(h,margin(0,8));holidayList=list();hc.addView(holidayList);setDefaultHolidayCalendar();panel.addView(hc,margin(0,8));
        TextView footer=text("Tip: use SETTINGS for alerts and CONNECT for backend URL/API key.",11,MUTED);panel.addView(footer,margin(0,8));
        AlertDialog d=new AlertDialog.Builder(this).setView(sv).setPositiveButton("CLOSE",null).create();d.show();
        // Copy the latest intelligence into this dialog when available.
        if(regime!=null){ir.setText(regime.getText());ib.setText(breadthSummary.getText());io.setText(optionSummary.getText());iff.setText(flowSummary.getText());inn.setText(newsSummary.getText());irv.setText(reversal.getText());hz.setText(horizons.getText());copyLines(evidenceList,ev);copyLines(warningList,wa);copyLines(newsList,sim);}
    }

    void copyLines(LinearLayout src,LinearLayout dst){if(src==null||dst==null)return;for(int i=0;i<src.getChildCount();i++){View v=src.getChildAt(i);if(v instanceof TextView){TextView t=(TextView)v;TextView n=text(t.getText().toString(),t.getTextSize()/getResources().getDisplayMetrics().scaledDensity,t.getCurrentTextColor());dst.addView(n,margin(0,5));}}}

    GradientDrawable gradientStrokeButton(int accent){GradientDrawable d=box(Color.rgb(38,31,65),Color.rgb(25,29,48),14);d.setStroke(dp(1),accent);return d;}
    void showAlertSettings(){
        final LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(4),dp(2),dp(4),dp(2));
        ScrollView sv=new ScrollView(this);sv.addView(panel);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(18),dp(20),dp(8));box.setBackground(rounded(SURFACE,24));sv.setBackgroundColor(SURFACE);
        TextView title=text("⚙  ALERT CONTROL CENTER",20,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(title,margin(0,4));
        TextView note=text("Alarm, sound, vibration and alert timing are saved locally on this phone.",11,MUTED);box.addView(note,margin(0,14));

        Switch master=new Switch(this);master.setText("Master alerts");master.setTextColor(TEXT);master.setTextSize(14);master.setChecked(prefs.getBoolean("alert_enabled",true));box.addView(master,margin(0,8));

        TextView tone=text("Ringtone",12,MUTED);box.addView(tone,margin(0,4));
        LinearLayout toneRow=new LinearLayout(this);toneRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView toneName=text("Default notification",13,TEXT);toneName.setPadding(dp(8),0,dp(8),0);
        String savedTone=prefs.getString("alert_tone_uri","");
        if(!savedTone.isEmpty()){try{toneName.setText(RingtoneManager.getRingtone(this,Uri.parse(savedTone)).getTitle(this));}catch(Exception ignored){}}
        Button chooseTone=new Button(this);chooseTone.setText("CHOOSE");chooseTone.setTextColor(TEXT);chooseTone.setAllCaps(false);chooseTone.setBackground(gradientStrokeButton(BLUE));
        toneRow.addView(toneName,new LinearLayout.LayoutParams(0,dp(46),1));toneRow.addView(chooseTone,new LinearLayout.LayoutParams(dp(105),dp(46)));box.addView(toneRow,margin(0,10));

        TextView volLabel=text("Alert volume: "+prefs.getInt("alert_volume",80)+"%",12,MUTED);box.addView(volLabel);
        SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setProgress(prefs.getInt("alert_volume",80));volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){volLabel.setText("Alert volume: "+p+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});box.addView(volume,margin(0,8));
        Button test=new Button(this);test.setText("🔊 TEST ALARM");test.setAllCaps(false);test.setTextColor(TEXT);test.setBackground(gradientStrokeButton(GREEN));box.addView(test,margin(0,12));

        TextView vt=text("Vibration",12,MUTED);box.addView(vt,margin(0,2));
        Switch vibration=new Switch(this);vibration.setText("Vibration enabled");vibration.setTextColor(TEXT);vibration.setChecked(prefs.getBoolean("alert_vibration",true));box.addView(vibration,margin(0,8));
        Spinner pattern=new Spinner(this);String[] patterns={"Short","Medium","Long","Strong"};ArrayAdapter<String> pa=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,patterns);pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);pattern.setAdapter(pa);pattern.setSelection(Math.max(0,Arrays.asList(patterns).indexOf(prefs.getString("alert_vibration_pattern","Medium"))));box.addView(pattern,margin(0,12));

        TextView ev=text("ALERT EVENTS",12,MUTED);ev.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(ev,margin(0,4));
        String[] labels={"Strong CALL","Strong PUT","CALL → Neutral","PUT → Neutral","CALL → PUT reversal","PUT → CALL reversal","Position Stop / Invalidation","Position Target","Position Thesis Weakening"};
        String[] keys={"strong_call","strong_put","call_neutral","put_neutral","call_put","put_call","position_stop","position_target","position_weak"};
        ArrayList<Switch> eventSwitches=new ArrayList<>();
        for(int i=0;i<labels.length;i++){Switch sw=new Switch(this);sw.setText(labels[i]);sw.setTextColor(TEXT);sw.setTextSize(13);sw.setChecked(prefs.getBoolean("alert_event_"+keys[i],true));box.addView(sw,margin(0,2));eventSwitches.add(sw);}

        TextView th=text("Strong-signal evidence threshold",12,MUTED);box.addView(th,margin(0,12));Spinner threshold=new Spinner(this);String[] ts={"70","80","85","90"};ArrayAdapter<String> ta=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,ts);ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);threshold.setAdapter(ta);int savedTh=prefs.getInt("alert_threshold",85);threshold.setSelection(Math.max(0,Arrays.asList(ts).indexOf(String.valueOf(savedTh))));box.addView(threshold,margin(0,10));

        TextView sch=text("ALERT SCHEDULE",12,MUTED);sch.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(sch,margin(0,4));
        Spinner schedule=new Spinner(this);String[] schedules={"Market hours only (09:15–15:40)","All day","Custom quiet hours"};ArrayAdapter<String> sa=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,schedules);sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);schedule.setAdapter(sa);schedule.setSelection(Math.max(0,Arrays.asList(schedules).indexOf(prefs.getString("alert_schedule","Market hours only (09:15–15:40)"))));box.addView(schedule,margin(0,10));
        EditText quietStart=field("Quiet start","22:00",false),quietEnd=field("Quiet end","07:00",false);quietStart.setText(prefs.getString("quiet_start","22:00"));quietEnd.setText(prefs.getString("quiet_end","07:00"));box.addView(quietStart,margin(0,5));box.addView(quietEnd,margin(0,10));

        LinearLayout actions=new LinearLayout(this);Button save=new Button(this);save.setText("SAVE SETTINGS");save.setTextColor(Color.WHITE);save.setAllCaps(false);save.setBackground(box(GREEN,Color.rgb(22,139,82),14));Button cancel=new Button(this);cancel.setText("CANCEL");cancel.setTextColor(TEXT);cancel.setAllCaps(false);cancel.setBackground(gradientStrokeButton(BORDER));actions.addView(save,weight(1,6));actions.addView(cancel,weight(1,0));box.addView(actions,margin(0,12));
        panel.addView(box);

        final AlertDialog dialog=new AlertDialog.Builder(this).setView(sv).create();
        chooseTone.setOnClickListener(v->{Intent i=new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_NOTIFICATION);i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,"Choose trading alert ringtone");i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,savedTone.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):Uri.parse(savedTone));startActivityForResult(i,901);});
        test.setOnClickListener(v->playTestTone(volume.getProgress(),savedTone));
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{SharedPreferences.Editor e=prefs.edit();e.putBoolean("alert_enabled",master.isChecked()).putBoolean("alert_vibration",vibration.isChecked()).putString("alert_vibration_pattern",patterns[pattern.getSelectedItemPosition()]).putInt("alert_volume",volume.getProgress()).putInt("alert_threshold",Integer.parseInt(ts[threshold.getSelectedItemPosition()])).putString("alert_schedule",schedules[schedule.getSelectedItemPosition()]).putString("quiet_start",quietStart.getText().toString().trim()).putString("quiet_end",quietEnd.getText().toString().trim());for(int i=0;i<keys.length;i++)e.putBoolean("alert_event_"+keys[i],eventSwitches.get(i).isChecked());e.apply();toast("Alert settings saved");dialog.dismiss();});
        dialog.getWindow();
        dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(rounded(SURFACE,24));dialog.getWindow().setLayout(-1,-2);}
    }
    void playTestTone(int volumePct,String savedTone){try{Uri uri=savedTone.isEmpty()?RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION):Uri.parse(savedTone);MediaPlayer mp=MediaPlayer.create(this,uri);if(mp!=null){float v=Math.max(0,Math.min(100,volumePct))/100f;mp.setVolume(v,v);mp.setOnCompletionListener(MediaPlayer::release);mp.start();}}catch(Exception e){toast("Unable to play test ringtone");}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==901&&resultCode==RESULT_OK&&data!=null){Uri uri=data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);if(uri!=null){prefs.edit().putString("alert_tone_uri",uri.toString()).apply();toast("Ringtone selected");}}}

TextView valueText(){TextView v=text("—",16,TEXT);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    LinearLayout levelBox(String label,TextView val){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(rounded(Color.rgb(27,37,54),14));TextView l=text(label,9,MUTED);b.addView(l);b.addView(val,margin(6,0));return b;}
    EditText field(String hint,String placeholder,boolean secret){EditText e=new EditText(this);e.setHint(placeholder);e.setTextSize(14);e.setSingleLine(true);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setBackground(box(Color.rgb(29,40,59),Color.rgb(21,29,44),12));e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setContentDescription(hint);if(secret)e.setInputType(0x81);return e;}
    LinearLayout list(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void addLine(LinearLayout l,String s){TextView t=text("• "+s,11,TEXT);t.setPadding(0,dp(4),0,dp(2));l.addView(t);}

    void startPolling(){String base=url.getText().toString().trim();if(base.endsWith("/"))base=base.substring(0,base.length()-1);final String baseUrl=base;final String apiKey=key.getText().toString().trim();if(baseUrl.isEmpty()||apiKey.isEmpty()){toast("Backend URL and API key required");setDisconnectedVisual();return;}prefs.edit().putString("url",baseUrl).apply();saveApiKey(apiKey);if(scheduler!=null)scheduler.shutdownNow();scheduler=Executors.newSingleThreadScheduledExecutor();setConnectingVisual();poll(baseUrl,apiKey);scheduler.scheduleAtFixedRate(()->poll(baseUrl,apiKey),60,60,TimeUnit.SECONDS);if(connectButton!=null)connectButton.setText("✓ CONNECTED");}
    void poll(String base,String apiKey){HttpURLConnection c=null;try{URL u=new URL(base+"/signal?interval=5m");c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setRequestProperty("X-API-Key",apiKey);int code=c.getResponseCode();InputStream is=code>=200&&code<400?c.getInputStream():c.getErrorStream();String body=read(is);JSONObject j=new JSONObject(body);render(j,code);}catch(Exception e){runOnUiThread(()->{action.setText("WAIT");action.setTextColor(WAIT);market.setText("Backend/data unavailable: "+e.getMessage());status.setText("  Backend unavailable • retrying automatically");connectionDot.setTextColor(RED);setDisconnectedVisual();});}finally{if(c!=null)c.disconnect();}}
    String read(InputStream is)throws Exception{if(is==null)return "{}";BufferedReader r=new BufferedReader(new InputStreamReader(is));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);return b.toString();}

    void render(JSONObject j,int code){try{String a=j.optString("action","WAIT");double sc=j.optDouble("score",0);String msg=j.optString("message","");String ts=j.optString("timestamp","");boolean closed=j.optBoolean("market_closed",false);String session=j.optString("session","");String holiday=j.optString("holiday","");String next=j.optString("next_market_open","");JSONObject in=j.optJSONObject("intelligence");runOnUiThread(()->{action.setText(a);action.setTextColor(colorFor(a));applySignalVisual(a);score.setText(String.format(Locale.US,"Evidence score: %.1f / 100",sc));market.setText((closed?"MARKET CLOSED • "+session+"\n":"")+(holiday.isEmpty()?msg:"Today: "+holiday+"\n"+msg));setLevelValue(entryValue,j.optDouble("entry",Double.NaN));setLevelValue(stopValue,j.optDouble("stop_loss",Double.NaN));setLevelValue(targetValue,j.optDouble("target",Double.NaN));updated.setText("Updated: "+ts);marketBadge.setText(closed?"MARKET CLOSED":"MARKET OPEN");marketBadge.setTextColor(closed?RED:GREEN);nextOpen.setText(closed?"Next market start: "+prettyDate(j.optString("next_trading_date"))+" • "+formatTime(next)+" IST":"Market hours: 09:15 AM – 03:40 PM IST");if(in!=null)renderIntel(in);signalTransitionAlert(a);JSONArray hs=j.optJSONArray("upcoming_holidays");if(hs!=null)updateHolidayList(hs);if(code>=200&&code<300){setConnectedVisual();status.setText("  Connected • production engine • auto-reconnect ON");connectionDot.setTextColor(GREEN);}else{status.setText("  Backend HTTP "+code+" • retrying");connectionDot.setTextColor(RED);setDisconnectedVisual();}checkPosition(a,sc,j);strongAlert(a,sc);});}catch(Exception ignored){}}
    void renderIntel(JSONObject in){regime.setText("Regime: "+in.optString("regime","—")+" • Expected validity: "+in.optString("expected_validity","—"));JSONObject br=in.optJSONObject("breadth");if(br!=null)breadthSummary.setText("Bank breadth: "+br.optInt("up")+" up / "+br.optInt("down")+" down of "+br.optInt("total"));JSONObject op=in.optJSONObject("options");optionSummary.setText("Options: "+(op==null?"—":(op.optBoolean("available",false)?"OI data available • PCR "+op.optString("pcr_oi","—"):"unavailable")));JSONObject fl=in.optJSONObject("institutional_flows");flowSummary.setText("Institutional flows: "+(fl!=null&&fl.optBoolean("available",false)?"NSE FII/FPI + DII report available":"unavailable / exchange response not available"));JSONObject nw=in.optJSONObject("news");newsSummary.setText("News/event context: "+(nw!=null&&nw.optBoolean("available",false)?nw.optJSONArray("items").length()+" recent public headlines classified":"unavailable"));reversal.setText("Reversal risks: "+join(in.optJSONArray("reversal_risks")));evidenceList.removeAllViews();forEach(in.optJSONArray("evidence"),evidenceList);warningList.removeAllViews();forEach(in.optJSONArray("warnings"),warningList);horizons.setText("Horizons: "+in.optJSONObject("time_horizons"));newsList.removeAllViews();JSONArray sim=in.optJSONArray("historical_similarity");if(sim!=null)for(int i=0;i<Math.min(sim.length(),5);i++){JSONObject e=sim.optJSONObject(i);if(e!=null)addLine(newsList,e.optString("date")+" • "+e.optString("name")+" • similarity "+e.optInt("similarity"));}}
    void forEach(JSONArray a,LinearLayout l){if(a==null||a.length()==0){addLine(l,"None reported");return;}for(int i=0;i<a.length();i++)addLine(l,a.optString(i));}
    String join(JSONArray a){if(a==null||a.length()==0)return "none";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)b.append("; ");b.append(a.optString(i));}return b.toString();}


    void saveApiKey(String value){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("dlt_api_key")){KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(new android.security.keystore.KeyGenParameterSpec.Builder("dlt_api_key",android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build());kg.generateKey();}SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("dlt_api_key",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);String iv=Base64.encodeToString(c.getIV(),Base64.NO_WRAP);String ct=Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);prefs.edit().putString("key_iv",iv).putString("key_ct",ct).apply();}catch(Exception ignored){}}
    String loadApiKey(){try{String ivs=prefs.getString("key_iv","");String cts=prefs.getString("key_ct","");if(ivs.isEmpty()||cts.isEmpty())return "";KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("dlt_api_key",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,Base64.decode(ivs,Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(cts,Base64.NO_WRAP)),"UTF-8");}catch(Exception e){return "";}}

    void runBacktest(TextView out){String base=url.getText().toString().trim();if(base.endsWith("/"))base=base.substring(0,base.length()-1);final String baseUrl=base;final String apiKey=key.getText().toString().trim();if(baseUrl.isEmpty()||apiKey.isEmpty()){toast("Connect backend first");return;}backtestButton.setText("RUNNING…");new Thread(()->{HttpURLConnection c=null;try{URL u=new URL(baseUrl+"/backtest?period=1y");c=(HttpURLConnection)u.openConnection();c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(60000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("X-API-Key",apiKey);int code=c.getResponseCode();String body=read(code<400?c.getInputStream():c.getErrorStream());if(body==null||body.trim().isEmpty())throw new IOException("Empty backend response (HTTP "+code+")");String result=parseBacktestResponse(body,code);runOnUiThread(()->{out.setText(result);backtestButton.setText("RUN 1Y BACKTEST");});}catch(Exception e){runOnUiThread(()->{out.setText("Backtest unavailable: "+friendlyBacktestError(e));backtestButton.setText("RUN 1Y BACKTEST");});}finally{if(c!=null)c.disconnect();}}).start();}

    String parseBacktestResponse(String body,int code)throws Exception{String s=body.trim();if(code>=400){try{JSONObject err=new JSONObject(s);String detail=err.optString("detail",s);throw new IOException("HTTP "+code+": "+detail);}catch(JSONException je){throw new IOException("HTTP "+code+": "+s);}}Object rootJson=new JSONTokener(s).nextValue();if(rootJson instanceof String){String inner=((String)rootJson).trim();if(inner.startsWith("{")||inner.startsWith("[")){rootJson=new JSONTokener(inner).nextValue();}else{return "Backtest unavailable: "+inner;}}if(!(rootJson instanceof JSONObject))return "Backtest unavailable: backend returned non-object JSON";JSONObject j=(JSONObject)rootJson;if(!j.optBoolean("available",true))return "Backtest unavailable: "+j.optString("reason","insufficient clean history");StringBuilder b=new StringBuilder("Backtest samples: ").append(j.optInt("samples",0));Object horizonsObj=j.opt("horizons");JSONObject h=null;if(horizonsObj instanceof JSONObject)h=(JSONObject)horizonsObj;else if(horizonsObj instanceof String){String hs=((String)horizonsObj).trim();if(hs.startsWith("{"))try{h=new JSONObject(hs);}catch(JSONException ignored){}}if(h!=null){b.append("\n");for(String k:new String[]{"1d","3d","5d"}){Object hv=h.opt(k);JSONObject x=null;if(hv instanceof JSONObject)x=(JSONObject)hv;else if(hv instanceof String){String xs=((String)hv).trim();if(xs.startsWith("{"))try{x=new JSONObject(xs);}catch(JSONException ignored){}}if(x!=null)b.append(k).append(" win ").append(x.optDouble("win_rate_pct",0)).append("% • avg ").append(x.optDouble("avg_return_pct",0)).append("%\n");}}return b.toString().trim();}

    String friendlyBacktestError(Exception e){String m=e.getMessage();if(m==null)m=e.toString();if(m.contains("String cannot be converted to JSONObject"))return "Backend returned horizons in an unexpected format. The app now accepts both JSON object and JSON-string responses; please retry.";return m;}
    void savePosition(){prefs.edit().putString("pt",posType.getText().toString()).putString("ps",posStrike.getText().toString()).putString("pe",posEntry.getText().toString()).putString("pq",posQty.getText().toString()).putString("pstop",posSL.getText().toString()).putString("ptarget",posTarget.getText().toString()).apply();positionStatus.setText("Active position • "+posType.getText()+" "+posStrike.getText()+" • entry premium "+posEntry.getText()+" • qty "+posQty.getText());positionHealth.setText("Position health: monitoring live thesis");positionHealth.setTextColor(GREEN);prefs.edit().remove("last_position_alert").apply();Intent i=new Intent(this,PositionMonitorService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}
    void loadPosition(){posType.setText(prefs.getString("pt",""));posStrike.setText(prefs.getString("ps",""));posEntry.setText(prefs.getString("pe",""));posQty.setText(prefs.getString("pq",""));posSL.setText(prefs.getString("pstop",""));posTarget.setText(prefs.getString("ptarget",""));if(!posType.getText().toString().isEmpty()){positionStatus.setText("Active position • "+posType.getText()+" "+posStrike.getText());positionHealth.setText("Position health: monitoring live thesis");positionHealth.setTextColor(GREEN);Intent i=new Intent(this,PositionMonitorService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);}}
    void clearPosition(){prefs.edit().remove("pt").remove("ps").remove("pe").remove("pq").remove("pstop").remove("ptarget").apply();posType.setText("");posStrike.setText("");posEntry.setText("");posQty.setText("");posSL.setText("");posTarget.setText("");positionStatus.setText("Position closed / no active position");positionHealth.setText("Position health: —");positionHealth.setTextColor(MUTED);stopService(new Intent(this,PositionMonitorService.class));prefs.edit().remove("last_position_alert").apply();}
    void checkPosition(String signal,double sc,JSONObject j){
        String pt=prefs.getString("pt","").toUpperCase(Locale.US); if(pt.isEmpty()) return;
        String expected=pt.contains("CALL")?"CALL":"PUT";
        JSONObject intel=j.optJSONObject("intelligence");
        JSONArray risks=intel==null?null:intel.optJSONArray("reversal_risks");
        JSONArray warns=intel==null?null:intel.optJSONArray("warnings");
        int riskCount=risks==null?0:risks.length();
        int warnCount=warns==null?0:warns.length();
        boolean opposite=("CALL".equals(signal)||"PUT".equals(signal))&&!signal.equals(expected);
        boolean thesisWeak = opposite || riskCount>=2 || (sc>0 && sc<55);
        double marketPrice=j.optDouble("market_price",Double.NaN);double userStop=parseNum(prefs.getString("pstop",""));double userTarget=parseNum(prefs.getString("ptarget",""));boolean stopHit=Double.isFinite(marketPrice)&&Double.isFinite(userStop)&&((expected.equals("CALL")&&marketPrice<=userStop)||(expected.equals("PUT")&&marketPrice>=userStop));boolean targetHit=Double.isFinite(marketPrice)&&Double.isFinite(userTarget)&&((expected.equals("CALL")&&marketPrice>=userTarget)||(expected.equals("PUT")&&marketPrice<=userTarget));boolean strongExit=stopHit||targetHit||opposite&&(riskCount>=2 || sc<60);
        String health;
        if(targetHit){
            health="🟢 TARGET LEVEL REACHED";
            positionStatus.setText("🎯 TARGET REVIEW • underlying "+String.format(Locale.US,"%.2f",marketPrice));
            positionStatus.setTextColor(GREEN);
            positionHealth.setText(health+" • review Groww position");
            positionHealth.setTextColor(GREEN);
            if(prefs.getBoolean("alert_event_position_target",true))notifyPosition("TARGET ALERT", "Your "+expected+" position reached the manual underlying target. Review the position on Groww.");
        } else if(stopHit || strongExit){
            health="🔴 EXIT WARNING • thesis/invalidation triggered";
            positionStatus.setText("⚠ EXIT REVIEW • Your "+expected+" vs current "+signal+" • score "+String.format(Locale.US,"%.1f",sc));
            positionStatus.setTextColor(RED);
            positionHealth.setText(health+" • reversal risks "+riskCount+" • warnings "+warnCount);
            positionHealth.setTextColor(RED);
            if(stopHit ? prefs.getBoolean("alert_event_position_stop",true) : (prefs.getBoolean("alert_event_call_put",true)||prefs.getBoolean("alert_event_put_call",true))) notifyPosition(stopHit?"STOP LOSS WARNING":"EXIT WARNING", stopHit?"Your "+expected+" position reached the manual underlying invalidation level. Review the position on Groww.":"Your "+expected+" position thesis has reversed. Review the position on Groww.");
        } else if(thesisWeak){
            health="🟠 THESIS WEAKENING";
            if(prefs.getBoolean("alert_event_position_weak",true)) notifyPosition("THESIS WEAKENING","Your "+expected+" position thesis is weakening. Review reversal risks and current signal.");
            positionStatus.setText("⚠ MONITOR • "+expected+" thesis weakening • score "+String.format(Locale.US,"%.1f",sc));
            positionStatus.setTextColor(Color.rgb(190,120,20));
            positionHealth.setText(health+" • reversal risks "+riskCount+" • warnings "+warnCount);
            positionHealth.setTextColor(Color.rgb(190,120,20));
        } else {
            health="🟢 THESIS ALIGNED";
            positionStatus.setText("Active "+expected+" position • thesis aligned • score "+String.format(Locale.US,"%.1f",sc));
            positionStatus.setTextColor(GREEN);
            positionHealth.setText(health+" • reversal risks "+riskCount+" • warnings "+warnCount);
            positionHealth.setTextColor(GREEN);
        }
    }
    void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("position_alerts_v3","Position Alerts",NotificationManager.IMPORTANCE_HIGH);c.setDescription("BANKNIFTY strong-signal, reversal and position alerts");c.enableVibration(false);c.setSound(null,null);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    void notifyPosition(String title,String message){
        if(!prefs.getBoolean("alert_enabled",true)||!shouldAlertNow())return;
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,"position_alerts_v3").setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title).setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setSilent(true).setAutoCancel(true);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2201,b.build());
        playTestTone(prefs.getInt("alert_volume",80),prefs.getString("alert_tone_uri",""));
        if(prefs.getBoolean("alert_vibration",true)&&Build.VERSION.SDK_INT>=26){
            String p=prefs.getString("alert_vibration_pattern","Medium");
            long[] pattern="Short".equals(p)?new long[]{0,180}:"Long".equals(p)?new long[]{0,450,150,450}:"Strong".equals(p)?new long[]{0,300,120,600,150,600}:new long[]{0,280,160,420};
            ((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createWaveform(pattern,-1));
        }
    }
    boolean shouldAlertNow(){
        String mode=prefs.getString("alert_schedule","Market hours only (09:15–15:40)");
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));int mins=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);
        if("All day".equals(mode))return true;
        if("Custom quiet hours".equals(mode)){int qs=parseTimePref(prefs.getString("quiet_start","22:00")),qe=parseTimePref(prefs.getString("quiet_end","07:00"));boolean quiet=qs<=qe?mins>=qs&&mins<qe:mins>=qs||mins<qe;return !quiet;}
        return mins>=555&&mins<=940;
    }
    int parseTimePref(String x){try{String[] q=x.split(":");return Integer.parseInt(q[0])*60+Integer.parseInt(q[1]);}catch(Exception e){return 0;}}
    void signalTransitionAlert(String current){
        String prev=prefs.getString("last_signal_state","");
        if(prev.isEmpty()){prefs.edit().putString("last_signal_state",current).apply();return;}
        boolean enabled=true;String event=null;
        if("CALL".equals(prev)&&"WAIT".equals(current))event="call_neutral";
        else if("PUT".equals(prev)&&"WAIT".equals(current))event="put_neutral";
        else if("CALL".equals(prev)&&"PUT".equals(current))event="call_put";
        else if("PUT".equals(prev)&&"CALL".equals(current))event="put_call";
        if(event!=null&&prefs.getBoolean("alert_event_"+event,true)&&prefs.getBoolean("alert_enabled",true)&&shouldAlertNow()){
            long now=System.currentTimeMillis(),last=prefs.getLong("transition_alert_time",0);
            if(now-last>2*60*1000L){prefs.edit().putLong("transition_alert_time",now).apply();String msg;
                if(event.equals("call_put"))msg="Signal reversed from CALL to PUT. Review invalidation and reversal risks.";
                else if(event.equals("put_call"))msg="Signal reversed from PUT to CALL. Review invalidation and reversal risks.";
                else msg="Signal moved to NEUTRAL. Review the current thesis before acting.";
                notifyPosition("Signal change alert",msg);
            }
        }
        prefs.edit().putString("last_signal_state",current).apply();
    }

    void strongAlert(String a,double sc){if(!(a.equals("CALL")||a.equals("PUT")))return;int threshold=prefs.getInt("alert_threshold",85);if(sc<threshold)return;if(!prefs.getBoolean("alert_enabled",true)||!shouldAlertNow())return;if(a.equals("CALL")&&!prefs.getBoolean("alert_event_strong_call",true))return;if(a.equals("PUT")&&!prefs.getBoolean("alert_event_strong_put",true))return;long now=System.currentTimeMillis();String lastA=prefs.getString("last_strong_action","");long lastT=prefs.getLong("last_strong_time",0);if(a.equals(lastA)&&now-lastT<10*60*1000L)return;prefs.edit().putString("last_strong_action",a).putLong("last_strong_time",now).apply();lastAction=a;notifyPosition("Strong "+a+" signal",a+" signal • evidence score "+String.format(Locale.US,"%.1f",sc)+"/100. Review invalidation before acting.");new AlertDialog.Builder(this).setTitle("Strong "+a+" signal").setMessage(a+" signal • evidence score "+String.format(Locale.US,"%.1f",sc)+"/100\\nReview reversal risks before acting.").setPositiveButton("OK",null).show();}

    void autoConnect(){String savedUrl=prefs.getString("url","");String savedKey=loadApiKey();if(savedUrl.isEmpty()||savedKey.isEmpty())return;url.setText(savedUrl);key.setText(savedKey);startPolling();}
    void setupConnectionFields(){if(connectButton!=null){boolean saved=!prefs.getString("url","").isEmpty()&&!loadApiKey().isEmpty();connectButton.setText(saved?"✓ CONNECT":"↗ CONNECT");}}
    void setConnectingVisual(){connected=false;if(root!=null)root.setBackgroundColor(Color.rgb(18,24,37));status.setText("  Connecting to Render…");status.setTextColor(BLUE);connectionDot.setTextColor(BLUE);}
    void setDisconnectedVisual(){connected=false;if(root!=null)root.setBackgroundColor(Color.rgb(18,24,37));if(connectionCard!=null)connectionCard.setBackground(rounded(Color.rgb(37,31,54),20));if(status!=null){status.setText("  Not connected • waiting for Render");status.setTextColor(RED);}if(connectionDot!=null)connectionDot.setTextColor(RED);if(action!=null){action.setTextColor(WAIT);action.setBackground(rounded(Color.rgb(38,46,61),18));}}
    void setConnectedVisual(){connected=true;if(root!=null)root.setBackgroundColor(BG);if(connectionCard!=null)connectionCard.setBackground(rounded(Color.rgb(20,48,39),20));if(connectionDot!=null)connectionDot.setTextColor(GREEN);if(status!=null){status.setText("  Connected to Render • production engine");status.setTextColor(GREEN);}}
    void applySignalVisual(String a){if(action==null)return;if(a.contains("CALL")){action.setTextColor(Color.rgb(20,120,75));action.setBackground(rounded(Color.rgb(20,72,52),18));}else if(a.contains("PUT")){action.setTextColor(Color.rgb(170,45,45));action.setBackground(rounded(Color.rgb(82,30,42),18));}else{action.setTextColor(WAIT);action.setBackground(rounded(Color.rgb(38,46,61),18));}}

    void setDefaultHolidayCalendar(){String[][] h={{"15 Jan","Municipal Corporation Election - Maharashtra"},{"26 Jan","Republic Day"},{"03 Mar","Holi"},{"26 Mar","Shri Ram Navami"},{"31 Mar","Shri Mahavir Jayanti"},{"03 Apr","Good Friday"},{"14 Apr","Dr. Baba Saheb Ambedkar Jayanti"},{"01 May","Maharashtra Day"},{"28 May","Bakri Id"},{"26 Jun","Muharram"},{"14 Sep","Ganesh Chaturthi"},{"02 Oct","Mahatma Gandhi Jayanti"},{"20 Oct","Dussehra"},{"10 Nov","Diwali-Balipratipada"},{"24 Nov","Prakash Gurpurb Sri Guru Nanak Dev"},{"25 Dec","Christmas"}};holidayList.removeAllViews();for(String[] x:h)addHoliday(x[0],x[1],"2026");}
    void updateHolidayList(JSONArray a){holidayList.removeAllViews();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)addHoliday(prettyShort(o.optString("date")),o.optString("name"),"2026");}}
    void addHoliday(String d,String n,String y){LinearLayout r=new LinearLayout(this);r.setPadding(0,dp(8),0,dp(8));TextView a=text(d,12,TEXT);a.setTypeface(Typeface.DEFAULT,Typeface.BOLD);r.addView(a,new LinearLayout.LayoutParams(dp(70),-2));r.addView(text(n,12,MUTED),new LinearLayout.LayoutParams(0,-2,1));holidayList.addView(r);}
    String prettyShort(String ds){try{String[]p=ds.split("-");String[]m={"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};return Integer.parseInt(p[2])+" "+m[Integer.parseInt(p[1])];}catch(Exception e){return ds;}}
    String prettyDate(String ds){try{String[]p=ds.split("-");String[]m={"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};return p[2]+" "+m[Integer.parseInt(p[1])]+" "+p[0];}catch(Exception e){return ds;}}
    String formatTime(String iso){try{if(iso==null||iso.isEmpty())return "09:15 AM";String t=iso.contains("T")?iso.substring(iso.indexOf('T')+1):iso;String[]p=t.split(":");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);return String.format(Locale.US,"%02d:%02d %s",h%12==0?12:h%12,m,h>=12?"PM":"AM");}catch(Exception e){return iso;}}
    double parseNum(String v){try{return Double.parseDouble(v.trim());}catch(Exception e){return Double.NaN;}}
    void setLevelValue(TextView v,double d){v.setText(Double.isNaN(d)?"—":String.format(Locale.US,"%.2f",d));}
    int colorFor(String s){if(s.contains("CALL"))return GREEN;if(s.contains("PUT"))return RED;return WAIT;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){if(scheduler!=null)scheduler.shutdownNow();super.onDestroy();}
}
