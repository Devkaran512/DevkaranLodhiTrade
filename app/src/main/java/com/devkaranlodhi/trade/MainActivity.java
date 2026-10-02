package com.devkaranlodhi.trade;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
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
    static final int BG=Color.rgb(242,245,249), TEXT=Color.rgb(32,42,55), MUTED=Color.rgb(105,115,128), BORDER=Color.rgb(225,230,237);
    static final int GREEN=Color.rgb(31,132,91), RED=Color.rgb(184,73,73), WAIT=Color.rgb(78,88,103), BLUE=Color.rgb(57,105,168);
    LinearLayout root, holidayList, evidenceList, warningList, newsList;
    TextView action, market, updated, status, connectionDot, marketBadge, nextOpen, score, regime, reversal, horizons, optionSummary, breadthSummary, flowSummary, newsSummary, positionStatus, positionHealth;
    TextView entryValue, stopValue, targetValue;
    EditText url,key,posType,posStrike,posEntry,posQty,posSL,posTarget;
    Button start,savePosition,clearPosition,backtestButton;
    ScheduledExecutorService scheduler;
    SharedPreferences prefs;
    boolean firstStrongAlert=true;
    String lastAction="WAIT";

    int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String s,float size,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(c);v.setIncludeFontPadding(false);return v;}
    GradientDrawable box(int top,int bottom,float r){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,bottom});d.setCornerRadius(dp(r));return d;}
    GradientDrawable outlined(){GradientDrawable d=box(Color.WHITE,Color.rgb(248,250,253),20);d.setStroke(dp(1),BORDER);return d;}
    GradientDrawable rounded(int c,float r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(17),dp(18),dp(17));c.setBackground(outlined());c.setElevation(dp(3));return c;}
    LinearLayout.LayoutParams margin(float t,float b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(t),0,dp(b));return p;}
    LinearLayout.LayoutParams weight(float w,float r){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,w);p.setMargins(0,0,dp(r),0);return p;}

    @Override public void onCreate(Bundle b){super.onCreate(b);Window w=getWindow();WindowCompat.setDecorFitsSystemWindows(w,false);w.setStatusBarColor(BG);w.setNavigationBarColor(BG);prefs=getSharedPreferences("connection",MODE_PRIVATE);createNotificationChannel();if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7001);build();}

    void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(12),dp(16),dp(24));root.setBackgroundColor(BG);scroll.addView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,i)->{int top=i.getInsets(WindowInsetsCompat.Type.statusBars()).top,bottom=i.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;v.setPadding(dp(16),top+dp(12),dp(16),bottom+dp(22));return i;});
        TextView title=text("DevkaranLodhiTrade",24,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
        TextView sub=text("BANKNIFTY • Production Prediction Engine",13,MUTED);sub.setPadding(0,dp(7),0,0);root.addView(sub);

        LinearLayout ms=card();LinearLayout mh=new LinearLayout(this);mh.setGravity(Gravity.CENTER_VERTICAL);TextView mt=text("NSE MARKET STATUS",11,MUTED);mt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);mh.addView(mt,new LinearLayout.LayoutParams(0,-2,1));marketBadge=text("CHECKING",10,BLUE);marketBadge.setGravity(Gravity.CENTER);marketBadge.setPadding(dp(11),dp(7),dp(11),dp(7));marketBadge.setBackground(rounded(Color.rgb(239,245,252),20));mh.addView(marketBadge);ms.addView(mh);nextOpen=text("Checking market calendar…",15,TEXT);nextOpen.setPadding(0,dp(12),0,0);ms.addView(nextOpen);TextView hrs=text("BANKNIFTY derivatives: 09:15 AM – 03:40 PM IST",11,MUTED);hrs.setPadding(0,dp(5),0,0);ms.addView(hrs);root.addView(ms,margin(18,12));

        LinearLayout sig=card();LinearLayout sh=new LinearLayout(this);sh.setGravity(Gravity.CENTER_VERTICAL);TextView st=text("CURRENT SIGNAL",11,MUTED);st.setTypeface(Typeface.DEFAULT,Typeface.BOLD);sh.addView(st,new LinearLayout.LayoutParams(0,-2,1));TextView pd=text("PUBLIC DATA",10,BLUE);pd.setPadding(dp(10),dp(6),dp(10),dp(6));pd.setBackground(rounded(Color.rgb(239,245,252),20));sh.addView(pd);sig.addView(sh);
        action=text("WAIT",38,WAIT);action.setGravity(Gravity.CENTER);action.setTypeface(Typeface.DEFAULT,Typeface.BOLD);action.setPadding(0,dp(12),0,dp(4));sig.addView(action);score=text("Evidence score: —",13,MUTED);score.setGravity(Gravity.CENTER);sig.addView(score);market=text("Waiting for backend data",13,MUTED);market.setGravity(Gravity.CENTER);market.setPadding(0,dp(6),0,0);sig.addView(market);root.addView(sig,margin(0,12));

        LinearLayout lev=card();TextView lt=text("TRADE LEVELS",11,MUTED);lt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);lev.addView(lt,margin(0,12));LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);entryValue=valueText();stopValue=valueText();targetValue=valueText();row.addView(levelBox("ENTRY",entryValue),weight(1,6));row.addView(levelBox("STOP LOSS",stopValue),weight(1,6));row.addView(levelBox("TARGET",targetValue),weight(1,0));lev.addView(row);updated=text("Updated: —",11,MUTED);updated.setPadding(0,dp(13),0,0);lev.addView(updated);root.addView(lev,margin(0,12));

        LinearLayout intel=card();TextView it=text("MARKET INTELLIGENCE",11,MUTED);it.setTypeface(Typeface.DEFAULT,Typeface.BOLD);intel.addView(it,margin(0,10));regime=text("Regime: —",14,TEXT);intel.addView(regime,margin(0,8));breadthSummary=text("Bank breadth: —",12,MUTED);intel.addView(breadthSummary,margin(0,6));optionSummary=text("Options: —",12,MUTED);intel.addView(optionSummary,margin(0,6));flowSummary=text("Institutional flows: —",12,MUTED);intel.addView(flowSummary,margin(0,6));newsSummary=text("News/event context: —",12,MUTED);intel.addView(newsSummary,margin(0,6));reversal=text("Reversal risks: —",12,MUTED);intel.addView(reversal,margin(0,8));TextView evh=text("Supporting evidence",11,MUTED);evh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);intel.addView(evh);evidenceList=list();intel.addView(evidenceList);TextView wh=text("Warnings / invalidation",11,MUTED);wh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);wh.setPadding(0,dp(10),0,0);intel.addView(wh);warningList=list();intel.addView(warningList);horizons=text("Horizons: —",11,MUTED);horizons.setPadding(0,dp(10),0,0);intel.addView(horizons);root.addView(intel,margin(0,12));
        LinearLayout bt=card();TextView bth=text("OUT-OF-SAMPLE BACKTEST",11,MUTED);bth.setTypeface(Typeface.DEFAULT,Typeface.BOLD);bt.addView(bth,margin(0,8));TextView btNote=text("Runs the production technical rules on historical public candles. It measures historical samples; it does not guarantee future results.",11,MUTED);bt.addView(btNote,margin(0,8));backtestButton=new Button(this);backtestButton.setText("RUN 1Y BACKTEST");bt.addView(backtestButton);TextView btResult=text("Backtest: not run",12,TEXT);btResult.setPadding(0,dp(8),0,0);bt.addView(btResult);backtestButton.setOnClickListener(v->runBacktest(btResult));root.addView(bt,margin(0,12));

        LinearLayout hist=card();TextView hi=text("HISTORICAL SIMILARITY",11,MUTED);hi.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hist.addView(hi,margin(0,8));TextView ht=text("Matches are context only; they are not guaranteed forecasts.",11,MUTED);hist.addView(ht);newsList=list();hist.addView(newsList);root.addView(hist,margin(0,12));

        LinearLayout conn=card();TextView ct=text("BACKEND CONNECTION",11,MUTED);ct.setTypeface(Typeface.DEFAULT,Typeface.BOLD);conn.addView(ct,margin(0,12));url=field("Backend URL","https://your-service.onrender.com",false);key=field("Backend API Key","Paste generated key",true);conn.addView(url,margin(0,10));conn.addView(key,margin(0,12));start=new Button(this);start.setText("START LIVE SIGNAL");start.setTextSize(14);start.setTextColor(Color.WHITE);start.setAllCaps(false);start.setMinHeight(dp(52));start.setBackground(box(Color.rgb(73,119,178),Color.rgb(45,91,151),15));conn.addView(start,new LinearLayout.LayoutParams(-1,dp(52)));root.addView(conn,margin(0,12));

        LinearLayout pos=card();TextView pt=text("POSITION MONITORING (MANUAL GROWW ENTRY)",11,MUTED);pt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);pos.addView(pt,margin(0,8));TextView note=text("No Groww login/order API is used. The app monitors your manually entered thesis against the live BANKNIFTY signal.",11,MUTED);pos.addView(note,margin(0,10));
        posType=field("Option","CALL or PUT",false);posStrike=field("Strike","e.g. 55000",false);posEntry=field("Entry premium","e.g. 120",false);posQty=field("Quantity","e.g. 30",false);posSL=field("Underlying invalidation","optional",false);posTarget=field("Underlying target","optional",false);pos.addView(posType,margin(0,7));pos.addView(posStrike,margin(0,7));pos.addView(posEntry,margin(0,7));pos.addView(posQty,margin(0,7));pos.addView(posSL,margin(0,7));pos.addView(posTarget,margin(0,10));LinearLayout pb=new LinearLayout(this);savePosition=new Button(this);savePosition.setText("SAVE / MONITOR");clearPosition=new Button(this);clearPosition.setText("CLOSE POSITION");pb.addView(savePosition,weight(1,6));pb.addView(clearPosition,weight(1,0));pos.addView(pb);positionStatus=text("No active position",12,MUTED);positionStatus.setPadding(0,dp(10),0,0);pos.addView(positionStatus);positionHealth=text("Position health: —",12,MUTED);positionHealth.setPadding(0,dp(6),0,0);pos.addView(positionHealth);root.addView(pos,margin(0,12));

        LinearLayout hc=card();TextView hct=text("NSE HOLIDAY CALENDAR 2026",11,MUTED);hct.setTypeface(Typeface.DEFAULT,Typeface.BOLD);hc.addView(hct);holidayList=list();hc.addView(holidayList);root.addView(hc,margin(0,12));
        LinearLayout sr=new LinearLayout(this);sr.setGravity(Gravity.CENTER);connectionDot=text("●",11,MUTED);sr.addView(connectionDot);status=text("  Not connected",12,MUTED);sr.addView(status);root.addView(sr);

        url.setText(prefs.getString("url",""));key.setText(loadApiKey());loadPosition();start.setOnClickListener(v->startPolling());savePosition.setOnClickListener(v->savePosition());clearPosition.setOnClickListener(v->clearPosition());setDefaultHolidayCalendar();setContentView(scroll);
    }

    TextView valueText(){TextView v=text("—",16,TEXT);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    LinearLayout levelBox(String label,TextView val){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(rounded(Color.rgb(250,251,253),14));TextView l=text(label,9,MUTED);b.addView(l);b.addView(val,margin(6,0));return b;}
    EditText field(String hint,String placeholder,boolean secret){EditText e=new EditText(this);e.setHint(placeholder);e.setTextSize(14);e.setSingleLine(true);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setBackground(rounded(Color.WHITE,12));e.setPadding(dp(12),dp(9),dp(12),dp(9));e.setContentDescription(hint);if(secret)e.setInputType(0x81);return e;}
    LinearLayout list(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void addLine(LinearLayout l,String s){TextView t=text("• "+s,11,TEXT);t.setPadding(0,dp(4),0,dp(2));l.addView(t);}

    void startPolling(){String base=url.getText().toString().trim();if(base.endsWith("/"))base=base.substring(0,base.length()-1);final String baseUrl=base;final String apiKey=key.getText().toString().trim();if(baseUrl.isEmpty()||apiKey.isEmpty()){toast("Backend URL and API key required");return;}prefs.edit().putString("url",baseUrl).apply();saveApiKey(apiKey);if(scheduler!=null)scheduler.shutdownNow();scheduler=Executors.newSingleThreadScheduledExecutor();poll(baseUrl,apiKey);scheduler.scheduleAtFixedRate(()->poll(baseUrl,apiKey),60,60,TimeUnit.SECONDS);start.setText("LIVE SIGNAL RUNNING • 60s");}
    void poll(String base,String apiKey){HttpURLConnection c=null;try{URL u=new URL(base+"/signal?interval=5m");c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setRequestProperty("X-API-Key",apiKey);int code=c.getResponseCode();InputStream is=code>=200&&code<400?c.getInputStream():c.getErrorStream();String body=read(is);JSONObject j=new JSONObject(body);render(j,code);}catch(Exception e){runOnUiThread(()->{action.setText("WAIT");action.setTextColor(WAIT);market.setText("Backend/data unavailable: "+e.getMessage());status.setText("  Backend unavailable");connectionDot.setTextColor(RED);});}finally{if(c!=null)c.disconnect();}}
    String read(InputStream is)throws Exception{if(is==null)return "{}";BufferedReader r=new BufferedReader(new InputStreamReader(is));StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s);return b.toString();}

    void render(JSONObject j,int code){try{String a=j.optString("action","WAIT");double sc=j.optDouble("score",0);String msg=j.optString("message","");String ts=j.optString("timestamp","");boolean closed=j.optBoolean("market_closed",false);String session=j.optString("session","");String holiday=j.optString("holiday","");String next=j.optString("next_market_open","");JSONObject in=j.optJSONObject("intelligence");runOnUiThread(()->{action.setText(a);action.setTextColor(colorFor(a));score.setText(String.format(Locale.US,"Evidence score: %.1f / 100",sc));market.setText((closed?"MARKET CLOSED • "+session+"\n":"")+(holiday.isEmpty()?msg:"Today: "+holiday+"\n"+msg));setLevelValue(entryValue,j.optDouble("entry",Double.NaN));setLevelValue(stopValue,j.optDouble("stop_loss",Double.NaN));setLevelValue(targetValue,j.optDouble("target",Double.NaN));updated.setText("Updated: "+ts);marketBadge.setText(closed?"MARKET CLOSED":"MARKET OPEN");marketBadge.setTextColor(closed?RED:GREEN);nextOpen.setText(closed?"Next market start: "+prettyDate(j.optString("next_trading_date"))+" • "+formatTime(next)+" IST":"Market hours: 09:15 AM – 03:40 PM IST");if(in!=null)renderIntel(in);JSONArray hs=j.optJSONArray("upcoming_holidays");if(hs!=null)updateHolidayList(hs);if(code>=200&&code<300){status.setText("  Connected • production engine");connectionDot.setTextColor(GREEN);}else{status.setText("  Backend HTTP "+code);connectionDot.setTextColor(RED);}checkPosition(a,sc,j);strongAlert(a,sc);});}catch(Exception ignored){}}
    void renderIntel(JSONObject in){regime.setText("Regime: "+in.optString("regime","—")+" • Expected validity: "+in.optString("expected_validity","—"));JSONObject br=in.optJSONObject("breadth");if(br!=null)breadthSummary.setText("Bank breadth: "+br.optInt("up")+" up / "+br.optInt("down")+" down of "+br.optInt("total"));JSONObject op=in.optJSONObject("options");optionSummary.setText("Options: "+(op==null?"—":(op.optBoolean("available",false)?"OI data available • PCR "+op.optString("pcr_oi","—"):"unavailable")));JSONObject fl=in.optJSONObject("institutional_flows");flowSummary.setText("Institutional flows: "+(fl!=null&&fl.optBoolean("available",false)?"NSE FII/FPI + DII report available":"unavailable / exchange response not available"));JSONObject nw=in.optJSONObject("news");newsSummary.setText("News/event context: "+(nw!=null&&nw.optBoolean("available",false)?nw.optJSONArray("items").length()+" recent public headlines classified":"unavailable"));reversal.setText("Reversal risks: "+join(in.optJSONArray("reversal_risks")));evidenceList.removeAllViews();forEach(in.optJSONArray("evidence"),evidenceList);warningList.removeAllViews();forEach(in.optJSONArray("warnings"),warningList);horizons.setText("Horizons: "+in.optJSONObject("time_horizons"));newsList.removeAllViews();JSONArray sim=in.optJSONArray("historical_similarity");if(sim!=null)for(int i=0;i<Math.min(sim.length(),5);i++){JSONObject e=sim.optJSONObject(i);if(e!=null)addLine(newsList,e.optString("date")+" • "+e.optString("name")+" • similarity "+e.optInt("similarity"));}}
    void forEach(JSONArray a,LinearLayout l){if(a==null||a.length()==0){addLine(l,"None reported");return;}for(int i=0;i<a.length();i++)addLine(l,a.optString(i));}
    String join(JSONArray a){if(a==null||a.length()==0)return "none";StringBuilder b=new StringBuilder();for(int i=0;i<a.length();i++){if(i>0)b.append("; ");b.append(a.optString(i));}return b.toString();}


    void saveApiKey(String value){try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias("dlt_api_key")){KeyGenerator kg=KeyGenerator.getInstance("AES","AndroidKeyStore");kg.init(new android.security.keystore.KeyGenParameterSpec.Builder("dlt_api_key",android.security.keystore.KeyProperties.PURPOSE_ENCRYPT|android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build());kg.generateKey();}SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("dlt_api_key",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);String iv=Base64.encodeToString(c.getIV(),Base64.NO_WRAP);String ct=Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);prefs.edit().putString("key_iv",iv).putString("key_ct",ct).apply();}catch(Exception ignored){}}
    String loadApiKey(){try{String ivs=prefs.getString("key_iv","");String cts=prefs.getString("key_ct","");if(ivs.isEmpty()||cts.isEmpty())return "";KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);SecretKey k=((KeyStore.SecretKeyEntry)ks.getEntry("dlt_api_key",null)).getSecretKey();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,k,new GCMParameterSpec(128,Base64.decode(ivs,Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(cts,Base64.NO_WRAP)),"UTF-8");}catch(Exception e){return "";}}

    void runBacktest(TextView out){String base=url.getText().toString().trim();if(base.endsWith("/"))base=base.substring(0,base.length()-1);final String baseUrl=base;final String apiKey=key.getText().toString().trim();if(baseUrl.isEmpty()||apiKey.isEmpty()){toast("Connect backend first");return;}backtestButton.setText("RUNNING…");new Thread(()->{HttpURLConnection c=null;try{URL u=new URL(baseUrl+"/backtest?period=1y");c=(HttpURLConnection)u.openConnection();c.setConnectTimeout(12000);c.setReadTimeout(60000);c.setRequestProperty("X-API-Key",apiKey);int code=c.getResponseCode();String body=read(code<400?c.getInputStream():c.getErrorStream());JSONObject j=new JSONObject(body);JSONObject h=j.optJSONObject("horizons");StringBuilder b=new StringBuilder("Backtest samples: ").append(j.optInt("samples",0));if(h!=null){b.append("\n");for(String k:new String[]{"1d","3d","5d"}){JSONObject x=h.optJSONObject(k);if(x!=null)b.append(k).append(" win ").append(x.optDouble("win_rate_pct",0)).append("% • avg ").append(x.optDouble("avg_return_pct",0)).append("%\n");}}String result=b.toString();runOnUiThread(()->{out.setText(result);backtestButton.setText("RUN 1Y BACKTEST");});}catch(Exception e){runOnUiThread(()->{out.setText("Backtest unavailable: "+e.getMessage());backtestButton.setText("RUN 1Y BACKTEST");});}finally{if(c!=null)c.disconnect();}}).start();}
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
            notifyPosition("TARGET ALERT", "Your "+expected+" position reached the manual underlying target. Review the position on Groww.");
        } else if(stopHit || strongExit){
            health="🔴 EXIT WARNING • thesis/invalidation triggered";
            positionStatus.setText("⚠ EXIT REVIEW • Your "+expected+" vs current "+signal+" • score "+String.format(Locale.US,"%.1f",sc));
            positionStatus.setTextColor(RED);
            positionHealth.setText(health+" • reversal risks "+riskCount+" • warnings "+warnCount);
            positionHealth.setTextColor(RED);
            notifyPosition(stopHit?"STOP LOSS WARNING":"EXIT WARNING", stopHit?"Your "+expected+" position reached the manual underlying invalidation level. Review the position on Groww.":"Your "+expected+" position thesis has reversed. Review the position on Groww.");
        } else if(thesisWeak){
            health="🟠 THESIS WEAKENING";
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
    void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("position_alerts","Position Alerts",NotificationManager.IMPORTANCE_HIGH);c.setDescription("BANKNIFTY position reversal and exit alerts");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}}
    void notifyPosition(String title,String message){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=getPackageManager().PERMISSION_GRANTED)return;NotificationCompat.Builder b=new NotificationCompat.Builder(this,"position_alerts").setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(message).setStyle(new NotificationCompat.BigTextStyle().bigText(message)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2201,b.build());((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createOneShot(500,android.os.VibrationEffect.DEFAULT_AMPLITUDE));}
    void strongAlert(String a,double sc){if(!firstStrongAlert&&a.equals(lastAction)&&sc<85)return;if((a.equals("CALL")||a.equals("PUT"))&&sc>=85){firstStrongAlert=false;lastAction=a;((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(android.os.VibrationEffect.createOneShot(350,android.os.VibrationEffect.DEFAULT_AMPLITUDE));notifyPosition("Strong evidence alert",a+" signal • score "+String.format(Locale.US,"%.1f",sc)+". Review invalidation and news before acting.");new AlertDialog.Builder(this).setTitle("Strong evidence alert").setMessage(a+" signal • score "+String.format(Locale.US,"%.1f",sc)+"\nReview invalidation and news before acting.").setPositiveButton("OK",null).show();}else lastAction=a;}

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
