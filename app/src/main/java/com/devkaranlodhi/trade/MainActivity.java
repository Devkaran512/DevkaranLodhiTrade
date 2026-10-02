package com.devkaranlodhi.trade;

import android.app.*;import android.os.*;import android.graphics.Color;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.net.*;import java.util.concurrent.*;

public class MainActivity extends Activity{
    LinearLayout root; TextView action,levels,status,time; EditText url,key; ScheduledExecutorService scheduler;
    TextView tv(String s,float z){ TextView v=new TextView(this); v.setText(s); v.setTextSize(z); v.setGravity(Gravity.CENTER); v.setPadding(12,12,12,12); return v; }
    public void onCreate(Bundle b){super.onCreate(b); build();}
    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,36,24,24); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(tv("DevkaranLodhiTrade",26)); root.addView(tv("BANKNIFTY • Prediction Only",16));
        action=tv("WAIT",42); root.addView(action,new LinearLayout.LayoutParams(-1,120));
        levels=tv("Entry: —\nSL: —\nTarget: —",19); root.addView(levels,new LinearLayout.LayoutParams(-1,130));
        time=tv("Updated: —",13); root.addView(time);
        status=tv("Configure backend and tap START",14); root.addView(status);
        url=new EditText(this); url.setHint("Backend URL e.g. http://192.168.1.10:8000"); root.addView(url);
        key=new EditText(this); key.setHint("Backend API key"); key.setInputType(0x81); root.addView(key);
        Button start=new Button(this); start.setText("START LIVE SIGNAL"); root.addView(start); start.setOnClickListener(v->startPolling());
        setContentView(root);
    }
    void startPolling(){String base=url.getText().toString().trim().replaceAll("/$",""); String k=key.getText().toString().trim(); if(base.isEmpty()||k.isEmpty()){status.setText("Backend URL and API key required");return;} if(scheduler!=null)scheduler.shutdownNow(); scheduler=Executors.newSingleThreadScheduledExecutor(); Runnable job=()->fetch(base,k); scheduler.scheduleAtFixedRate(job,0,60,TimeUnit.SECONDS);}
    void fetch(String base,String k){try{HttpURLConnection c=(HttpURLConnection)new URL(base+"/signal?interval=5m").openConnection();c.setRequestProperty("X-API-Key",k);c.setConnectTimeout(10000);c.setReadTimeout(25000);int code=c.getResponseCode(); InputStream in=code<400?c.getInputStream():c.getErrorStream(); BufferedReader br=new BufferedReader(new InputStreamReader(in));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line); JSONObject j=new JSONObject(sb.toString());String a=j.optString("action","WAIT");String shown=a.equals("CALL")?"BUY CALL":a.equals("PUT")?"BUY PUT":a.equals("EXIT_CALL")?"EXIT CALL":a.equals("EXIT_PUT")?"EXIT PUT":"WAIT";double sc=j.optDouble("score",0);String lv="Entry: "+fmt(j,"entry")+"\nSL: "+fmt(j,"stop_loss")+"\nTarget: "+fmt(j,"target"); runOnUiThread(()->{action.setText(shown);levels.setText(lv);time.setText("Updated: "+j.optString("timestamp","—"));status.setText(j.optString("message","Live public-data analysis"));});}catch(Exception e){runOnUiThread(()->{action.setText("WAIT");levels.setText("Entry: —\nSL: —\nTarget: —");status.setText("Public data unavailable — WAIT");});}}
    String fmt(JSONObject j,String k){return j.isNull(k)?"—":String.format(java.util.Locale.US,"%.2f",j.optDouble(k));}
    protected void onDestroy(){if(scheduler!=null)scheduler.shutdownNow();super.onDestroy();}
}
