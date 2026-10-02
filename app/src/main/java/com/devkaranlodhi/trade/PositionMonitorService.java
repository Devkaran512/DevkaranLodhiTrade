package com.devkaranlodhi.trade;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Base64;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.*;

public class PositionMonitorService extends Service {
    static final String CHANNEL="position_alerts";
    Handler handler=new Handler(Looper.getMainLooper());
    Runnable loop=this::poll;
    SharedPreferences prefs;
    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("connection",MODE_PRIVATE);createChannel();startForeground(2200,notification("Position monitoring active","BANKNIFTY position thesis monitoring is running"));handler.post(loop);}
    void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Position Alerts",NotificationManager.IMPORTANCE_HIGH);c.setDescription("BANKNIFTY position monitoring alerts");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    String key(){try{String ivs=prefs.getString("key_iv","");String cts=prefs.getString("key_ct","");if(ivs.isEmpty()||cts.isEmpty())return "";KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("dlt_api_key",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,Base64.decode(ivs,Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(cts,Base64.NO_WRAP)),"UTF-8");}catch(Exception e){return "";}}
    Notification notification(String title,String msg){return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(msg).setStyle(new NotificationCompat.BigTextStyle().bigText(msg)).setOngoing(true).setPriority(NotificationCompat.PRIORITY_LOW).build();}
    void alert(String title,String msg){NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.notify(2201,new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(msg).setStyle(new NotificationCompat.BigTextStyle().bigText(msg)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build());if(Build.VERSION.SDK_INT>=26){}((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(500,VibrationEffect.DEFAULT_AMPLITUDE));}
    void poll(){
        String pt=prefs.getString("pt","").toUpperCase(Locale.US); String base=prefs.getString("url",""); String api=key();
        if(!pt.isEmpty()&&!base.isEmpty()&&!api.isEmpty()) new Thread(()->fetch(pt,base,api)).start();
        handler.postDelayed(loop,60000);
    }
    void fetch(String pt,String base,String api){HttpURLConnection c=null;try{base=base.replaceAll("/$","");c=(HttpURLConnection)new URL(base+"/signal?interval=5m").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setRequestProperty("X-API-Key",api);int code=c.getResponseCode();if(code>=400)return;JSONObject j=new JSONObject(read(c.getInputStream()));String a=j.optString("action","WAIT");double sc=j.optDouble("score",0);JSONObject in=j.optJSONObject("intelligence");int risks=in==null||in.optJSONArray("reversal_risks")==null?0:in.optJSONArray("reversal_risks").length();String expected=pt.contains("CALL")?"CALL":"PUT";boolean opposite=("CALL".equals(a)||"PUT".equals(a))&&!a.equals(expected);double price=j.optDouble("market_price",Double.NaN);double stop=parse(prefs.getString("pstop",""));double target=parse(prefs.getString("ptarget",""));boolean stopHit=Double.isFinite(price)&&Double.isFinite(stop)&&((expected.equals("CALL")&&price<=stop)||(expected.equals("PUT")&&price>=stop));boolean targetHit=Double.isFinite(price)&&Double.isFinite(target)&&((expected.equals("CALL")&&price>=target)||(expected.equals("PUT")&&price<=target));boolean strong=stopHit||targetHit||opposite&&(risks>=2||sc<60)||(!opposite&&risks>=3&&sc<55);String last=prefs.getString("last_position_alert","");String marker=(stopHit?"STOP":targetHit?"TARGET":opposite?"REVERSAL":"WEAK")+":"+risks+":"+(int)sc;if(strong&&!marker.equals(last)){prefs.edit().putString("last_position_alert",marker).apply();String reason=stopHit?"Underlying invalidation level reached":targetHit?"Underlying target reached":opposite?"Current signal reversed to "+a:"Multiple reversal risks are active";alert(stopHit?"STOP LOSS WARNING":targetHit?"TARGET ALERT":"EXIT WARNING","Your "+expected+" position: "+reason+". Review the position on Groww.");}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}}
    double parse(String v){try{return Double.parseDouble(v.trim());}catch(Exception e){return Double.NaN;}}
    String read(InputStream in)throws Exception{BufferedReader b=new BufferedReader(new InputStreamReader(in));StringBuilder s=new StringBuilder();String l;while((l=b.readLine())!=null)s.append(l);return s.toString();}
    @Override public int onStartCommand(Intent i,int f,int id){return START_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacks(loop);super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
