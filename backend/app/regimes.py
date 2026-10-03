"""Additional market-regime features used as supporting evidence only."""
from datetime import datetime
from zoneinfo import ZoneInfo
import math
IST=ZoneInfo("Asia/Kolkata")
def _n(v,d=0.0):
    try:
        x=float(v); return x if math.isfinite(x) else d
    except Exception: return d
def gap_snapshot(df):
    if df is None or len(df)<2:return {"available":False,"reason":"Insufficient candles"}
    prev=_n(df.iloc[-2].close); op=_n(df.iloc[-1].open)
    if prev<=0:return {"available":False,"reason":"Invalid previous close"}
    gap=(op/prev-1)*100
    return {"available":True,"gap_pct":round(gap,3),"type":"GAP_UP" if gap>0.15 else ("GAP_DOWN" if gap<-0.15 else "FLAT_OPEN")}
def structure_snapshot(df):
    if df is None or len(df)<25:return {"available":False,"reason":"Insufficient candles"}
    h=df.high.astype(float); l=df.low.astype(float); c=df.close.astype(float)
    hi20=float(h.iloc[-21:-1].max()); lo20=float(l.iloc[-21:-1].min()); last=float(c.iloc[-1])
    breakout_up=last>hi20; breakout_dn=last<lo20
    prev=float(c.iloc[-2]); retest_up=prev>hi20 and last<=hi20 and last>lo20; retest_dn=prev<lo20 and last>=lo20 and last<hi20
    hh=float(h.iloc[-1])>float(h.iloc[-5:-1].max()); hl=float(l.iloc[-1])>float(l.iloc[-5:-1].min()); lh=float(h.iloc[-1])<float(h.iloc[-5:-1].max()); ll=float(l.iloc[-1])<float(l.iloc[-5:-1].min())
    score=60 if (breakout_up or retest_up) else (40 if hh and hl else (-60 if (breakout_dn or retest_dn) else (-40 if lh and ll else 0)))
    return {"available":True,"score":score,"breakout":"UP" if breakout_up else ("DOWN" if breakout_dn else "NONE"),"retest":"UP" if retest_up else ("DOWN" if retest_dn else "NONE"),"higher_high":hh,"higher_low":hl,"lower_high":lh,"lower_low":ll,"support_20":round(lo20,2),"resistance_20":round(hi20,2),"description":"Breakout/retest and recent swing structure"}
def volatility_snapshot(df,vix=None):
    if df is None or len(df)<25:return {"available":False,"reason":"Insufficient candles"}
    vals=(df.high.astype(float)-df.low.astype(float))/df.close.astype(float)*100; baseline=float(vals.iloc[-21:-1].mean()) if len(vals)>=22 else float(vals.mean()); ratio=float(vals.iloc[-1]/baseline) if baseline>0 else 1
    state="EXTREME" if ratio>2 else ("HIGH" if ratio>1.35 else ("LOW" if ratio<0.7 else "NORMAL"))
    atr=_n(df.iloc[-1].atr); close=max(_n(df.iloc[-1].close),1)
    return {"available":True,"state":state,"atr_pct":round(atr/close*100,3),"range_ratio":round(ratio,2),"vix_change_pct":_n(vix),"description":"Realized range versus recent baseline"}
def time_regime():
    now=datetime.now(IST); mins=now.hour*60+now.minute
    label="PRE_OPEN" if mins<570 else "OPENING_VOLATILITY" if mins<600 else "EARLY_SESSION" if mins<630 else "MORNING_TREND" if mins<720 else "MIDDAY" if mins<840 else "AFTERNOON" if mins<900 else "CLOSING_WINDOW" if mins<=940 else "POST_CLOSE"
    return {"label":label,"minute_ist":mins,"description":"Time-of-day regime; not a directional signal by itself"}
def event_risk(news):
    items=(news or {}).get("items",[]); keys=("rbi","fed","fomc","inflation","cpi","gdp","employment","jobs","budget","election","war","geopolit","crude","oil","rate decision","policy")
    hits=[x.get("title","")[:120] for x in items if any(k in str(x.get("title","")).lower() for k in keys)]
    level="HIGH" if len(hits)>=4 else ("ELEVATED" if len(hits)>=2 else "NORMAL")
    return {"level":level,"headline_count":len(hits),"headlines":hits[:6],"description":"Structured macro/banking/geopolitical headline risk"}
