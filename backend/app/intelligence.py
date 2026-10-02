import math
from .news import fetch_news
from .historical import similar_events
from .sources import basket_snapshot, global_snapshot, option_snapshot
from .flows import flow_snapshot


def _num(v, default=0.0):
    try:
        x=float(v); return x if math.isfinite(x) else default
    except Exception: return default

def build_intelligence(df, base_signal):
    from .indicators import enrich
    x=enrich(df).dropna(subset=["ema9","ema21","ema50","ema200","rsi","atr","macd_hist","adx","vwap"])
    if x.empty:
        return {"available":False,"reason":"Insufficient indicator history"}
    r=x.iloc[-1]; p=x.iloc[-2] if len(x)>1 else r
    tags=[]; warnings=[]; evidence=[]
    if r.close>r.ema9>r.ema21>r.ema50: tags += ["trend_up","ema_alignment"]; evidence.append("Price above EMA 9/21/50")
    if r.close<r.ema9<r.ema21<r.ema50: tags += ["trend_down","ema_alignment"]; evidence.append("Price below EMA 9/21/50")
    if r.close>r.vwap: tags.append("above_vwap")
    else: tags.append("below_vwap")
    if r.close>r.ema20: evidence.append("Price above EMA 20")
    else: evidence.append("Price below EMA 20")
    if r.close<=r.support20: warnings.append("Price is at/below 20-bar support")
    if r.close>=r.resistance20: warnings.append("Price is at/near 20-bar resistance")
    if r.rsi>=70: warnings.append("RSI is overbought")
    if r.rsi<=30: warnings.append("RSI is oversold")
    if r.adx<18: warnings.append("Trend strength is weak")
    if r.volume>0 and r.vol_ma20>0 and r.volume>1.8*r.vol_ma20: tags.append("volume_expansion"); evidence.append("Volume expansion")
    if r.atr>0 and abs(r.close-p.close)>1.5*r.atr: tags.append("large_move"); warnings.append("Large move versus ATR")

    basket=basket_snapshot(); up=sum(1 for z in basket if z["change_pct"]>0); down=sum(1 for z in basket if z["change_pct"]<0)
    breadth={"up":up,"down":down,"total":len(basket)}
    if basket and up>=down*1.5: tags.append("bank_breadth_up"); evidence.append(f"Bank breadth {up} up / {down} down")
    elif basket and down>=up*1.5: tags.append("bank_breadth_down"); evidence.append(f"Bank breadth {down} down / {up} up")
    else: warnings.append("Bank constituent breadth is mixed")

    glob=global_snapshot(); gmap={z["symbol"]:z["change_pct"] for z in glob}
    risk_off = sum(1 for s in ("^GSPC","^IXIC","^DJI","^N225","^HSI") if gmap.get(s,0)<0) >= 3
    if risk_off: tags.append("risk_off"); warnings.append("Several tracked global indices are down")
    if gmap.get("CL=F",0)>3: tags.append("oil_shock"); warnings.append("Tracked crude move is elevated")
    if gmap.get("^VIX",0)>10: tags.append("vix_spike"); warnings.append("Tracked VIX change is elevated")

    opt=option_snapshot()
    if opt.get("available") and opt.get("call_oi"):
        pcr=opt["put_oi"]/opt["call_oi"]
        opt["pcr_oi"]=round(pcr,3)
        if pcr<0.7: warnings.append("Low put/call OI ratio")
        elif pcr>1.4: warnings.append("High put/call OI ratio")

    flows=flow_snapshot()
    news=fetch_news(18)
    news_risk=[n for n in news.get("items",[]) if n["category"] in ("RBI/BANKING","MACRO","US RATES","CRUDE","GEOPOLITICS","FX")]
    if len(news_risk)>=5: warnings.append("Multiple macro/banking/geopolitical headlines are active")

    sim=similar_events({"tags":tags,"risk_off":risk_off,"vix_spike":"vix_spike" in tags,"oil_shock":"oil_shock" in tags})
    reversal=[]
    if base_signal.action=="CALL":
        if "risk_off" in tags: reversal.append("Global risk-off can invalidate a bullish setup")
        if "bank_breadth_down" in tags: reversal.append("Banking breadth has turned negative")
        if "below_vwap" in tags: reversal.append("Price is below VWAP")
        if "vix_spike" in tags: reversal.append("Volatility expansion can trigger reversal")
    elif base_signal.action=="PUT":
        if "bank_breadth_up" in tags: reversal.append("Banking breadth has turned positive")
        if "above_vwap" in tags: reversal.append("Price is above VWAP")
        if "vix_spike" in tags: reversal.append("Volatility expansion can reverse an intraday move")

    score=float(base_signal.score)
    if warnings: score -= min(20, len(warnings)*2.5)
    if evidence: score += min(8, len(evidence)*1.5)
    score=max(0,min(100,score))
    action=base_signal.action
    if reversal and len(reversal)>=2 and score<72: action="WAIT"
    return {
        "available":True,"action":action,"base_action":base_signal.action,"score":round(score,1),
        "evidence":evidence,"warnings":warnings,"reversal_risks":reversal,
        "breadth":breadth,"global":glob,"options":opt,"institutional_flows":flows,"news":news,"historical_similarity":sim,
        "regime":"RISK-OFF" if risk_off else ("TREND" if r.adx>=20 else "RANGE"),
        "expected_validity":"5-15 min to next major data/news shock" if base_signal.action in ("CALL","PUT") else "Reassess on next signal cycle",
        "time_horizons":{"intraday":"5-15m / 30m-few hours","next_day":"next trading session","swing":"2-5 days","medium":"1-4 weeks"}
    }
