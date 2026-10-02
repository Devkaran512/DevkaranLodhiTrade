package com.devkaranlodhi.trade;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.Locale;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(242, 245, 249);
    private static final int CARD_TOP = Color.rgb(255, 255, 255);
    private static final int CARD_BOTTOM = Color.rgb(248, 250, 253);
    private static final int TEXT = Color.rgb(32, 42, 55);
    private static final int MUTED = Color.rgb(105, 115, 128);
    private static final int BORDER = Color.rgb(225, 230, 237);
    private static final int BLUE = Color.rgb(57, 105, 168);
    private static final int GREEN = Color.rgb(31, 132, 91);
    private static final int RED = Color.rgb(184, 73, 73);
    private static final int WAIT = Color.rgb(78, 88, 103);
    private static final int SOFT_BLUE = Color.rgb(239, 245, 252);

    LinearLayout root, holidayList;
    TextView action, market, updated, status, connectionDot, marketBadge, nextOpen;
    TextView entryValue, stopValue, targetValue;
    EditText url, key;
    Button start;
    ScheduledExecutorService scheduler;
    SharedPreferences prefs;

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(false); return v;
    }

    GradientDrawable gradient(int top, int bottom, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setCornerRadius(dp(radius)); return d;
    }

    GradientDrawable outlined(float radius) {
        GradientDrawable d = gradient(CARD_TOP, CARD_BOTTOM, radius);
        d.setStroke(dp(1), BORDER); return d;
    }

    GradientDrawable softBox() {
        GradientDrawable d = gradient(Color.rgb(250,251,253), Color.rgb(244,247,250), 14);
        d.setStroke(dp(1), Color.rgb(232,236,242)); return d;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        c.setBackground(outlined(20));
        c.setElevation(dp(3));
        return c;
    }

    LinearLayout.LayoutParams margin(float top, float bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(top), 0, dp(bottom)); return p;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        WindowCompat.setDecorFitsSystemWindows(w, false);
        w.setStatusBarColor(BG); w.setNavigationBarColor(BG);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        build();
    }

    void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setClipToPadding(false);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24)); root.setBackgroundColor(BG);
        scroll.addView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            WindowInsetsCompat bars = insets;
            int top = bars.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = bars.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(dp(16), top + dp(12), dp(16), bottom + dp(22));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        TextView title = text("DevkaranLodhiTrade", 24, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(title);
        TextView subtitle = text("BANKNIFTY  •  Prediction Only", 13, MUTED);
        subtitle.setPadding(0, dp(7), 0, 0); root.addView(subtitle);

        // Market status / holiday card
        LinearLayout ms = card();
        LinearLayout mh = new LinearLayout(this); mh.setGravity(Gravity.CENTER_VERTICAL);
        TextView mt = text("NSE MARKET STATUS", 11, MUTED); mt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        mh.addView(mt, new LinearLayout.LayoutParams(0, -2, 1));
        marketBadge = text("CHECKING", 10, BLUE); marketBadge.setGravity(Gravity.CENTER);
        marketBadge.setPadding(dp(11), dp(7), dp(11), dp(7)); marketBadge.setBackground(rounded(SOFT_BLUE, 20));
        mh.addView(marketBadge); ms.addView(mh);
        nextOpen = text("Checking market calendar…", 15, TEXT); nextOpen.setPadding(0, dp(12), 0, 0);
        ms.addView(nextOpen);
        TextView hours = text("BANKNIFTY derivatives: 09:15 AM – 03:40 PM IST", 11, MUTED);
        hours.setPadding(0, dp(5), 0, 0); ms.addView(hours);
        root.addView(ms, margin(18, 12));

        // Signal card
        LinearLayout signal = card(); signal.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout signalHead = new LinearLayout(this); signalHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView sh = text("CURRENT SIGNAL", 11, MUTED); sh.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        signalHead.addView(sh, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mode = text("PUBLIC DATA", 10, BLUE); mode.setGravity(Gravity.CENTER);
        mode.setPadding(dp(10), dp(6), dp(10), dp(6)); mode.setBackground(rounded(SOFT_BLUE, 20)); signalHead.addView(mode);
        signal.addView(signalHead);
        action = text("WAIT", 38, WAIT); action.setTypeface(Typeface.DEFAULT, Typeface.BOLD); action.setGravity(Gravity.CENTER);
        action.setPadding(0, dp(12), 0, dp(6)); signal.addView(action, new LinearLayout.LayoutParams(-1, -2));
        market = text("Waiting for backend data", 13, MUTED); market.setGravity(Gravity.CENTER); signal.addView(market);
        root.addView(signal, margin(0, 12));

        LinearLayout levels = card(); TextView lt = text("TRADE LEVELS", 11, MUTED); lt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        levels.addView(lt, margin(0, 12));
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        entryValue = valueText(); stopValue = valueText(); targetValue = valueText();
        row.addView(levelBox("ENTRY", entryValue), weight(1, 6)); row.addView(levelBox("STOP LOSS", stopValue), weight(1, 6)); row.addView(levelBox("TARGET", targetValue), weight(1, 0));
        levels.addView(row); updated = text("Updated: —", 11, MUTED); updated.setPadding(0, dp(13), 0, 0); levels.addView(updated);
        root.addView(levels, margin(0, 12));

        LinearLayout conn = card(); TextView ct = text("BACKEND CONNECTION", 11, MUTED); ct.setTypeface(Typeface.DEFAULT, Typeface.BOLD); conn.addView(ct, margin(0, 12));
        url = field("Backend URL", "https://your-service.onrender.com", false); key = field("Backend API Key", "Paste generated key", true);
        conn.addView(url, margin(0, 10)); conn.addView(key, margin(0, 12));
        start = new Button(this); start.setText("START LIVE SIGNAL"); start.setTextSize(14); start.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        start.setTextColor(Color.WHITE); start.setAllCaps(false); start.setMinHeight(dp(52)); start.setMinimumHeight(dp(52)); start.setPadding(dp(12),0,dp(12),0);
        start.setBackground(gradient(Color.rgb(73,119,178), Color.rgb(45,91,151), 15)); start.setElevation(dp(3));
        conn.addView(start, new LinearLayout.LayoutParams(-1, dp(52))); root.addView(conn, margin(0, 12));

        // Holiday calendar
        LinearLayout hc = card();
        LinearLayout hh = new LinearLayout(this); hh.setGravity(Gravity.CENTER_VERTICAL);
        TextView ht = text("NSE HOLIDAY CALENDAR 2026", 11, MUTED); ht.setTypeface(Typeface.DEFAULT, Typeface.BOLD); hh.addView(ht, new LinearLayout.LayoutParams(0,-2,1));
        TextView official = text("NSE", 10, MUTED); official.setPadding(dp(9),dp(5),dp(9),dp(5)); official.setBackground(rounded(Color.rgb(244,246,249),18)); hh.addView(official); hc.addView(hh);
        holidayList = new LinearLayout(this); holidayList.setOrientation(LinearLayout.VERTICAL); holidayList.setPadding(0,dp(9),0,0); hc.addView(holidayList);
        root.addView(hc, margin(0, 12));

        LinearLayout statusRow = new LinearLayout(this); statusRow.setGravity(Gravity.CENTER);
        connectionDot = text("●", 11, MUTED); statusRow.addView(connectionDot); status = text("  Not connected", 12, MUTED); statusRow.addView(status);
        root.addView(statusRow);

        url.setText(prefs.getString("url", "")); start.setOnClickListener(v -> startPolling());
        setDefaultHolidayCalendar(); setContentView(scroll);
    }

    void setDefaultHolidayCalendar() {
        String[][] h = {{"15 Jan","Municipal Corporation Election - Maharashtra"},{"26 Jan","Republic Day"},{"03 Mar","Holi"},{"26 Mar","Shri Ram Navami"},{"31 Mar","Shri Mahavir Jayanti"},{"03 Apr","Good Friday"},{"14 Apr","Dr. Baba Saheb Ambedkar Jayanti"},{"01 May","Maharashtra Day"},{"28 May","Bakri Id"},{"26 Jun","Muharram"},{"14 Sep","Ganesh Chaturthi"},{"02 Oct","Mahatma Gandhi Jayanti"},{"20 Oct","Dussehra"},{"10 Nov","Diwali-Balipratipada"},{"24 Nov","Prakash Gurpurb Sri Guru Nanak Dev"},{"25 Dec","Christmas"}};
        for (String[] x : h) addHoliday(x[0], x[1], "2026");
    }

    void addHoliday(String date, String name, String year) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(9),dp(8),dp(9),dp(8)); r.setBackground(softBox());
        TextView d=text(date+" "+year,11,TEXT); d.setTypeface(Typeface.DEFAULT,Typeface.BOLD); r.addView(d,new LinearLayout.LayoutParams(dp(85),-2));
        TextView n=text(name,11,MUTED); r.addView(n,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,0,0,dp(5)); holidayList.addView(r,p);
    }

    TextView valueText() { TextView v=text("—",17,TEXT); v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER); v.setSingleLine(true); return v; }
    LinearLayout.LayoutParams weight(float w,int right){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(62),w);p.setMargins(0,0,dp(right),0);return p;}
    LinearLayout levelBox(String name,TextView value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);b.setPadding(dp(3),dp(9),dp(3),dp(9));b.setBackground(softBox());TextView n=text(name,9.5f,MUTED);n.setGravity(Gravity.CENTER);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.addView(n);b.addView(value);b.setElevation(dp(1));return b;}

    EditText field(String hint,String example,boolean password){EditText e=new EditText(this);e.setHint(hint+"  •  "+example);e.setTextSize(14);e.setTextColor(TEXT);e.setHintTextColor(Color.rgb(145,153,163));e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(softBox());if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);return e;}

    void startPolling(){String base=url.getText().toString().trim().replaceAll("/$","");String k=key.getText().toString().trim();if(base.isEmpty()||k.isEmpty()){status.setText("  Enter Backend URL and API Key");connectionDot.setTextColor(RED);return;}if(!(base.startsWith("https://")||base.startsWith("http://"))){status.setText("  Backend URL must start with https://");connectionDot.setTextColor(RED);return;}prefs.edit().putString("url",base).apply();if(scheduler!=null)scheduler.shutdownNow();start.setText("LIVE SIGNAL RUNNING");status.setText("  Connecting…");connectionDot.setTextColor(BLUE);scheduler=Executors.newSingleThreadScheduledExecutor();scheduler.scheduleAtFixedRate(()->fetch(base,k),0,60,TimeUnit.SECONDS);}

    void fetch(String base,String k){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(base+"/signal?interval=5m").openConnection();c.setRequestProperty("X-API-Key",k);c.setConnectTimeout(10000);c.setReadTimeout(25000);c.setUseCaches(false);int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();BufferedReader br=new BufferedReader(new InputStreamReader(in));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);JSONObject j=new JSONObject(sb.toString());String a=j.optString("action","WAIT");String shown=a.equals("CALL")?"BUY CALL":a.equals("PUT")?"BUY PUT":a.equals("EXIT_CALL")?"EXIT CALL":a.equals("EXIT_PUT")?"EXIT PUT":"WAIT";String ts=j.optString("timestamp","—");boolean closed=j.optBoolean("market_closed",false);String session=j.optString("session","LIVE");String msg=j.optString("message","Public-data analysis");double entry=j.optDouble("entry",Double.NaN),sl=j.optDouble("stop_loss",Double.NaN),target=j.optDouble("target",Double.NaN);String holiday=j.optString("holiday","");String nextDate=j.optString("next_trading_date","");String nextOpenTime=j.optString("next_market_open","");String open=j.optString("market_open","09:15"),close=j.optString("market_close","15:40");JSONArray holidays=j.optJSONArray("upcoming_holidays");final int rc=code;runOnUiThread(()->{action.setText(shown);action.setTextColor(colorFor(shown));market.setText((closed?"MARKET CLOSED • "+session+"\n":"")+(holiday.isEmpty()?msg:"Today: "+holiday+"\n"+msg));setLevelValue("ENTRY",fmt(entry));setLevelValue("STOP LOSS",fmt(sl));setLevelValue("TARGET",fmt(target));updated.setText("Updated: "+ts);marketBadge.setText(closed?"MARKET CLOSED":"MARKET OPEN");marketBadge.setTextColor(closed?RED:GREEN);marketBadge.setBackground(rounded(closed?Color.rgb(251,241,241):Color.rgb(237,248,242),20));if(closed){nextOpen.setText("Next market start: "+prettyDate(nextDate)+" • "+formatTime(nextOpenTime)+" IST");}else{nextOpen.setText("Market hours: "+formatTime(open)+" – "+formatTime(close)+" IST");}if(holidays!=null)updateHolidayList(holidays);if(rc>=200&&rc<300){status.setText("  Connected • public-data engine");connectionDot.setTextColor(GREEN);}else{status.setText("  Backend HTTP "+rc);connectionDot.setTextColor(RED);}});}catch(Exception e){runOnUiThread(()->{action.setText("WAIT");action.setTextColor(WAIT);market.setText("Public market data unavailable");setLevelValue("ENTRY","—");setLevelValue("STOP LOSS","—");setLevelValue("TARGET","—");status.setText("  Backend unavailable • WAIT");connectionDot.setTextColor(RED);marketBadge.setText("DATA UNAVAILABLE");marketBadge.setTextColor(RED);});}finally{if(c!=null)c.disconnect();}}

    void updateHolidayList(JSONArray a){holidayList.removeAllViews();for(int i=0;i<a.length();i++){try{JSONObject o=a.getJSONObject(i);String ds=o.optString("date");addHoliday(prettyShort(ds),o.optString("name"),"2026");}catch(Exception ignored){}}}
    String prettyShort(String ds){try{String[] p=ds.split("-");String[] m={"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};return Integer.parseInt(p[2])+" "+m[Integer.parseInt(p[1])];}catch(Exception e){return ds;}}
    String prettyDate(String ds){try{String[] p=ds.split("-");String[] m={"","Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};java.util.Calendar c=java.util.Calendar.getInstance();c.set(Integer.parseInt(p[0]),Integer.parseInt(p[1])-1,Integer.parseInt(p[2]));return new java.text.SimpleDateFormat("EEE, dd "+m[Integer.parseInt(p[1])] +" yyyy",Locale.US).format(c.getTime());}catch(Exception e){return ds;}}
    String formatTime(String iso){try{if(iso==null||iso.isEmpty())return "09:15 AM";if(iso.contains("T")){String t=iso.substring(iso.indexOf('T')+1);String[] p=t.split(":");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);return String.format(Locale.US,"%02d:%02d %s",(h%12==0?12:h%12),m,h>=12?"PM":"AM");}String[] p=iso.split(":");int h=Integer.parseInt(p[0]),m=Integer.parseInt(p[1]);return String.format(Locale.US,"%02d:%02d %s",(h%12==0?12:h%12),m,h>=12?"PM":"AM");}catch(Exception e){return iso;}}
    int colorFor(String s){if(s.contains("CALL"))return GREEN;if(s.contains("PUT"))return RED;return WAIT;}
    void setLevelValue(String n,String v){if("ENTRY".equals(n))entryValue.setText(v);else if("STOP LOSS".equals(n))stopValue.setText(v);else if("TARGET".equals(n))targetValue.setText(v);}
    String fmt(double v){return Double.isNaN(v)?"—":String.format(Locale.US,"%.2f",v);}
    @Override protected void onDestroy(){if(scheduler!=null)scheduler.shutdownNow();super.onDestroy();}
    GradientDrawable rounded(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
}
