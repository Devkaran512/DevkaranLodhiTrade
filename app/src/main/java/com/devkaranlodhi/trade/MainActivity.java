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
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.Locale;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(20, 28, 38);
    private static final int MUTED = Color.rgb(98, 108, 121);
    private static final int BORDER = Color.rgb(224, 229, 236);
    private static final int BLUE = Color.rgb(30, 91, 169);
    private static final int GREEN = Color.rgb(16, 128, 76);
    private static final int RED = Color.rgb(194, 61, 61);
    private static final int WAIT = Color.rgb(70, 81, 95);

    LinearLayout root;
    TextView action, market, updated, status, connectionDot;
    TextView entryValue, stopValue, targetValue;
    EditText url, key;
    Button start;
    ScheduledExecutorService scheduler;
    SharedPreferences prefs;

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    TextView text(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        return v;
    }

    GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    GradientDrawable outlined(int fill, int stroke, float radius) {
        GradientDrawable d = rounded(fill, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        c.setBackground(outlined(CARD, BORDER, 18));
        return c;
    }

    LinearLayout.LayoutParams margin(float top, float bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(top), 0, dp(bottom));
        return p;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("connection", MODE_PRIVATE);
        build();
    }

    void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        // Header
        TextView title = text("DevkaranLodhiTrade", 25, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("BANKNIFTY  •  Prediction Only", 13, MUTED);
        subtitle.setPadding(0, dp(6), 0, 0);
        root.addView(subtitle);

        // Signal card
        LinearLayout signal = card();
        signal.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout signalHead = new LinearLayout(this);
        signalHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView sh = text("CURRENT SIGNAL", 11, MUTED);
        sh.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        signalHead.addView(sh, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mode = text("LIVE", 10, BLUE);
        mode.setGravity(Gravity.CENTER);
        mode.setPadding(dp(10), dp(6), dp(10), dp(6));
        mode.setBackground(rounded(Color.rgb(235, 242, 252), 20));
        signalHead.addView(mode);
        signal.addView(signalHead);

        action = text("WAIT", 38, WAIT);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(0, dp(12), 0, dp(6));
        signal.addView(action, new LinearLayout.LayoutParams(-1, -2));

        market = text("Waiting for backend data", 13, MUTED);
        market.setGravity(Gravity.CENTER);
        market.setGravity(Gravity.CENTER_HORIZONTAL);
        signal.addView(market, new LinearLayout.LayoutParams(-1, -2));
        root.addView(signal, margin(18, 12));

        // Trade levels
        LinearLayout levels = card();
        TextView lt = text("TRADE LEVELS", 11, MUTED);
        lt.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        levels.addView(lt, margin(0, 12));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        entryValue = valueText();
        stopValue = valueText();
        targetValue = valueText();
        row.addView(levelBox("ENTRY", entryValue), weight(1, 6));
        row.addView(levelBox("STOP LOSS", stopValue), weight(1, 6));
        row.addView(levelBox("TARGET", targetValue), weight(1, 0));
        levels.addView(row);

        updated = text("Updated: —", 11, MUTED);
        updated.setPadding(0, dp(13), 0, 0);
        levels.addView(updated);
        root.addView(levels, margin(0, 12));

        // Connection card
        LinearLayout conn = card();
        TextView ct = text("BACKEND CONNECTION", 11, MUTED);
        ct.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        conn.addView(ct, margin(0, 12));

        url = field("Backend URL", "https://your-service.onrender.com", false);
        key = field("Backend API Key", "Paste your generated key", true);
        conn.addView(url, margin(0, 10));
        conn.addView(key, margin(0, 12));

        start = new Button(this);
        start.setText("START LIVE SIGNAL");
        start.setTextSize(14);
        start.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        start.setTextColor(Color.WHITE);
        start.setAllCaps(false);
        start.setMinHeight(dp(52));
        start.setMinimumHeight(dp(52));
        start.setPadding(dp(12), 0, dp(12), 0);
        start.setBackground(rounded(BLUE, 13));
        conn.addView(start, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(conn, margin(0, 12));

        // Status footer
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER);
        connectionDot = text("●", 11, MUTED);
        statusRow.addView(connectionDot);
        status = text("  Not connected", 12, MUTED);
        statusRow.addView(status);
        root.addView(statusRow, margin(0, 0));

        url.setText(prefs.getString("url", ""));
        start.setOnClickListener(v -> startPolling());
        setContentView(scroll);
    }

    TextView valueText() {
        TextView v = text("—", 17, TEXT);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        return v;
    }

    LinearLayout.LayoutParams weight(float w, int right) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(62), w);
        p.setMargins(0, 0, dp(right), 0);
        return p;
    }

    LinearLayout levelBox(String name, TextView value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(3), dp(9), dp(3), dp(9));
        box.setBackground(rounded(Color.rgb(248, 249, 251), 12));
        TextView n = text(name, 9.5f, MUTED);
        n.setGravity(Gravity.CENTER);
        n.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(n, new LinearLayout.LayoutParams(-1, -2));
        box.addView(value, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    EditText field(String hint, String example, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint + "  •  " + example);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(145, 153, 163));
        e.setSingleLine(true);
        e.setSelectAllOnFocus(false);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(outlined(Color.rgb(248, 249, 251), BORDER, 12));
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        }
        return e;
    }

    void startPolling() {
        String base = url.getText().toString().trim().replaceAll("/$", "");
        String k = key.getText().toString().trim();
        if (base.isEmpty() || k.isEmpty()) {
            status.setText("  Enter Backend URL and API Key");
            connectionDot.setTextColor(RED);
            return;
        }
        if (!(base.startsWith("https://") || base.startsWith("http://"))) {
            status.setText("  Backend URL must start with https://");
            connectionDot.setTextColor(RED);
            return;
        }
        prefs.edit().putString("url", base).apply();
        if (scheduler != null) scheduler.shutdownNow();
        start.setText("LIVE SIGNAL RUNNING");
        status.setText("  Connecting…");
        connectionDot.setTextColor(BLUE);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        Runnable job = () -> fetch(base, k);
        scheduler.scheduleAtFixedRate(job, 0, 60, TimeUnit.SECONDS);
    }

    void fetch(String base, String k) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base + "/signal?interval=5m").openConnection();
            c.setRequestProperty("X-API-Key", k);
            c.setConnectTimeout(10000);
            c.setReadTimeout(25000);
            c.setUseCaches(false);
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            JSONObject j = new JSONObject(sb.toString());
            String a = j.optString("action", "WAIT");
            String shown = a.equals("CALL") ? "BUY CALL" : a.equals("PUT") ? "BUY PUT" :
                    a.equals("EXIT_CALL") ? "EXIT CALL" : a.equals("EXIT_PUT") ? "EXIT PUT" : "WAIT";
            String ts = j.optString("timestamp", "—");
            String msg = j.optString("message", "Live public-data analysis");
            double entry = j.optDouble("entry", Double.NaN);
            double sl = j.optDouble("stop_loss", Double.NaN);
            double target = j.optDouble("target", Double.NaN);
            final int responseCode = code;
            runOnUiThread(() -> {
                action.setText(shown);
                action.setTextColor(colorFor(shown));
                market.setText(msg);
                setLevelValue("ENTRY", fmt(entry));
                setLevelValue("STOP LOSS", fmt(sl));
                setLevelValue("TARGET", fmt(target));
                updated.setText("Updated: " + ts);
                if (responseCode >= 200 && responseCode < 300) {
                    status.setText("  Connected • public-data engine");
                    connectionDot.setTextColor(GREEN);
                } else {
                    status.setText("  Backend HTTP " + responseCode);
                    connectionDot.setTextColor(RED);
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                action.setText("WAIT");
                action.setTextColor(WAIT);
                market.setText("Live data unavailable");
                setLevelValue("ENTRY", "—");
                setLevelValue("STOP LOSS", "—");
                setLevelValue("TARGET", "—");
                status.setText("  Backend unavailable • WAIT");
                connectionDot.setTextColor(RED);
            });
        } finally {
            if (c != null) c.disconnect();
        }
    }

    int colorFor(String s) {
        if (s.contains("CALL")) return GREEN;
        if (s.contains("PUT")) return RED;
        return WAIT;
    }

    void setLevelValue(String name, String value) {
        if ("ENTRY".equals(name)) entryValue.setText(value);
        else if ("STOP LOSS".equals(name)) stopValue.setText(value);
        else if ("TARGET".equals(name)) targetValue.setText(value);
    }

    String fmt(double v) { return Double.isNaN(v) ? "—" : String.format(Locale.US, "%.2f", v); }

    @Override protected void onDestroy() {
        if (scheduler != null) scheduler.shutdownNow();
        super.onDestroy();
    }
}
