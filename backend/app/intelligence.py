import math
from .news import fetch_news
from .historical import similar_events
from .sources import basket_snapshot, global_snapshot, option_snapshot
from .flows import flow_snapshot
from .psychology import psychology_snapshot
from .learning import preview as learning_preview, record_cycle
from .regimes import gap_snapshot, structure_snapshot, volatility_snapshot, time_regime, event_risk
from .sources import candles

_LAST = {"action": None, "score": None, "factors": {}}


def _num(v, default=0.0):
    try:
        x=float(v); return x if math.isfinite(x) else default
    except Exception: return default


def _clamp(v, lo=-100.0, hi=100.0):
    return max(lo, min(hi, float(v)))


def _news_direction(items):
    positive=("surge","rally","strong","growth","beat","easing","cut","recovery","inflow","bullish","support")
    negative=("crash","fall","drop","weak","stress","war","inflation","hike","outflow","bearish","panic","selloff","shock","downgrade")
    score=0; count=0
    for n in items or []:
        t=str(n.get("title","" )).lower()
        p=sum(k in t for k in positive); q=sum(k in t for k in negative)
        if p or q:
            score += p-q; count += 1
    return _clamp(score/max(1,count)*35)


def _technical(x):
    r=x.iloc[-1]; p=x.iloc[-2]
    checks=[
        ("EMA9", 100 if r.close>r.ema9 else -100, "price vs EMA9"),
        ("EMA21", 100 if r.ema9>r.ema21 else -100, "EMA9 vs EMA21"),
        ("EMA50", 100 if r.ema21>r.ema50 else -100, "EMA21 vs EMA50"),
        ("EMA200", 100 if r.close>r.ema200 else -100, "price vs EMA200"),
        ("VWAP", 100 if r.close>r.vwap else -100, "price vs VWAP"),
        ("MACD", 100 if r.macd_hist>0 else -100, "MACD histogram"),
        ("RSI", 100 if r.rsi>=55 else (-100 if r.rsi<=45 else 0), "RSI zone"),
        ("ADX/momentum", 100 if r.adx>=20 and r.close>p.close else (-100 if r.adx>=20 and r.close<p.close else 0), "ADX >= 20 + price momentum"),
        ("Volume", 100 if r.vol_ma20>0 and r.volume>r.vol_ma20 and r.close>p.close else (-100 if r.vol_ma20>0 and r.volume>r.vol_ma20 and r.close<p.close else 0), "volume expansion + direction"),
        ("SMA20", 100 if r.close>r.sma20 else -100, "price vs SMA20"),
    ]
    score=sum(v for _,v,_ in checks)/len(checks)
    why=[f"{name}: {'bullish' if value>0 else ('bearish' if value<0 else 'neutral')} ({desc})" for name,value,desc in checks]
    return score, checks, why


def _options_direction(opt):
    if not opt.get("available") or not opt.get("call_oi"):
        return 0.0, ["Options data unavailable"]
    pcr=_num(opt.get("put_oi"))/max(_num(opt.get("call_oi")),1)
    opt["pcr_oi"]=round(pcr,3)
    s=0; why=[]
    if pcr>1.4: s+=30; why.append(f"PCR {pcr:.2f} is elevated")
    elif pcr<0.7: s-=30; why.append(f"PCR {pcr:.2f} is low")
    else: why.append(f"PCR {pcr:.2f} is neutral")
    pc=_num(opt.get("put_change_oi")); cc=_num(opt.get("call_change_oi"))
    if pc>cc*1.15: s+=20; why.append("Put OI addition exceeds Call OI addition")
    elif cc>pc*1.15: s-=20; why.append("Call OI addition exceeds Put OI addition")
    if _num(opt.get("avg_put_iv"))>_num(opt.get("avg_call_iv"))*1.15: s-=5; why.append("Put IV premium is elevated")
    elif _num(opt.get("avg_call_iv"))>_num(opt.get("avg_put_iv"))*1.15: s+=5; why.append("Call IV premium is elevated")
    if _num(opt.get("put_volume"))>_num(opt.get("call_volume"))*1.2: s+=8; why.append("Put volume exceeds Call volume")
    elif _num(opt.get("call_volume"))>_num(opt.get("put_volume"))*1.2: s-=8; why.append("Call volume exceeds Put volume")
    er=opt.get("expiry_regime")
    if er=="EXPIRY_DAY": why.append("Expiry-day regime: reversal/gamma sensitivity elevated")
    elif er=="NEAR_EXPIRY": why.append("Near-expiry regime")
    return _clamp(s), why


def _flow_direction(flows):
    # NSE page formats can change. We only derive a direction when numeric FII/DII
    # values can be safely recognised; otherwise the factor stays neutral.
    if not flows.get("available"): return 0.0, ["FII/DII data unavailable"]
    rows=flows.get("rows") or []
    text=" ".join(str(r) for r in rows).lower()
    # Direction is deliberately conservative because table schemas change.
    if "net purchase" in text or "net buy" in text: return 20.0,["NSE flow table indicates net buying language"]
    if "net sale" in text or "net sell" in text: return -20.0,["NSE flow table indicates net selling language"]
    return 0.0,["FII/DII table available; no safely parsed directional value"]


def _global_direction(glob):
    if not glob: return 0.0,["Global market data unavailable"]
    risk=sum(1 for z in glob if z.get("symbol") in ("^GSPC","^IXIC","^DJI","^N225","^HSI") and _num(z.get("change_pct"))<0)
    positive=sum(1 for z in glob if z.get("symbol") in ("^GSPC","^IXIC","^DJI","^N225","^HSI") and _num(z.get("change_pct"))>0)
    s=(positive-risk)/max(1,positive+risk)*70
    why=[f"Global breadth {positive} positive / {risk} negative"]
    dxy=next((_num(z.get("change_pct")) for z in glob if z.get("symbol")=="DX-Y.NYB"),0)
    oil=next((_num(z.get("change_pct")) for z in glob if z.get("symbol")=="CL=F"),0)
    if oil>3: s-=10; why.append(f"Crude +{oil:.1f}% risk pressure")
    if dxy>1: s-=8; why.append(f"Dollar index +{dxy:.1f}% risk pressure")
    return _clamp(s),why


def _historical_direction(sim, risk_off):
    if not sim: return 0.0,["No historical similarity context"]
    top=sim[0]
    if top.get("similarity",0)<2: return 0.0,["No strong historical match"]
    if risk_off: return -25.0,[f"Historical match: {top.get('name','event')} in risk-off regime"]
    return 0.0,[f"Historical context: {top.get('name','event')} (non-directional) "]


def _factor(name, score, weight, why):
    return {"name":name,"score":round(float(score),1),"weight":weight,"contribution":round(float(score)*weight/100,1),"direction":"BULLISH" if score>8 else ("BEARISH" if score<-8 else "NEUTRAL"),"reasons":why}


def build_intelligence(df, base_signal):
    from .indicators import enrich
    x=enrich(df).dropna(subset=["ema9","ema21","ema50","ema200","rsi","atr","macd_hist","adx","vwap","sma20"])
    if len(x)<40:
        return {"available":False,"reason":"Insufficient indicator history"}
    r=x.iloc[-1]; p=x.iloc[-2]
    gap=gap_snapshot(x); structure=structure_snapshot(x); timereg=time_regime()

    # Gather all observable factors first. None is allowed to silently become a fake signal.
    basket=basket_snapshot(); up=sum(1 for z in basket if _num(z.get("change_pct"))>0); down=sum(1 for z in basket if _num(z.get("change_pct"))<0)
    breadth={"up":up,"down":down,"total":len(basket)}
    breadth_score=((up-down)/max(1,len(basket)))*100 if basket else 0.0
    glob=global_snapshot();
    try:
        nifty_df=candles("^NSEI",period="2d",interval="15m")
        b0=float(r.close); b1=float(r.close); n0=float(nifty_df.iloc[-2].close); n1=float(nifty_df.iloc[-1].close)
        rel_pct=(b1/float(p.close)-1)*100-(n1/n0-1)*100 if n0>0 and float(p.close)>0 else 0.0
        rel={"available":True,"relative_pct":round(rel_pct,3),"direction":"OUTPERFORMING" if rel_pct>0.1 else ("UNDERPERFORMING" if rel_pct<-0.1 else "INLINE")}
    except Exception:
        rel={"available":False,"reason":"NIFTY relative-strength data unavailable"}
    vix_change=next((_num(z.get("change_pct")) for z in glob if z.get("symbol")=="^VIX"),0.0); volreg=volatility_snapshot(x,vix_change); gscore,gwhy=_global_direction(glob)
    opt=option_snapshot(); oscore,owhy=_options_direction(opt)
    flows=flow_snapshot(); fscore,fwhy=_flow_direction(flows)
    news=fetch_news(18); nscore=_news_direction(news.get("items",[])); nwhy=[f"News sentiment proxy score {nscore:.1f}"] if news.get("items") else ["News unavailable"]
    evrisk=event_risk(news)
    psych=psychology_snapshot(df,opt,breadth,glob,news)
    pscore=_num(psych.get("direction_score")) if psych.get("available") else 0.0
    pwhy=[psych.get("state","UNAVAILABLE"),psych.get("crowd_vs_price","unknown")]

    tscore,tchecks,twhy=_technical(x)
    # Breadth factor is independent from the global and technical factors.
    bwhy=[f"Bank breadth {up} up / {down} down"] if basket else ["Bank constituent data unavailable"]
    sim=similar_events({"tags":[],"risk_off":sum(1 for z in glob if z.get("symbol") in ("^GSPC","^IXIC","^DJI","^N225","^HSI") and _num(z.get("change_pct"))<0)>=3,"vix_spike":next((_num(z.get("change_pct"))>10 for z in glob if z.get("symbol")=="^VIX"),False),"oil_shock":next((_num(z.get("change_pct"))>3 for z in glob if z.get("symbol")=="CL=F"),False)})
    risk_off=sum(1 for z in glob if z.get("symbol") in ("^GSPC","^IXIC","^DJI","^N225","^HSI") and _num(z.get("change_pct"))<0)>=3
    hscore,hwhy=_historical_direction(sim,risk_off)

    indicator_scores=[{"name":name,"score":value,"description":desc} for name,value,desc in tchecks]
    struct_score=_num(structure.get("score")) if structure.get("available") else 0.0
    vol_score=0.0 if not volreg.get("available") else (15 if volreg.get("state")=="NORMAL" else (-10 if volreg.get("state")=="EXTREME" else 5 if volreg.get("state")=="HIGH" else 0))
    gap_score=30 if gap.get("available") and gap.get("type")=="GAP_UP" and r.close>p.close else (-30 if gap.get("available") and gap.get("type")=="GAP_DOWN" and r.close<p.close else 0)
    event_score=0.0 if evrisk.get("level")=="NORMAL" else (-8.0 if evrisk.get("level")=="HIGH" else -3.0)
    factors=[
        _factor("Technical",tscore,18,twhy),
        _factor("Options",oscore,14,owhy),
        _factor("Market Psychology",pscore,14,pwhy),
        _factor("Market Structure",struct_score,10,[structure.get("description","Structure unavailable"),f"Breakout {structure.get('breakout','NONE')} • retest {structure.get('retest','NONE')}"]),
        _factor("Banking Breadth",breadth_score,7,bwhy),
        _factor("Relative Strength",(35 if rel.get("direction")=="OUTPERFORMING" else (-35 if rel.get("direction")=="UNDERPERFORMING" else 0)),4,[f"BANKNIFTY vs NIFTY: {rel.get("direction","UNAVAILABLE")}"]),
        _factor("Global Markets",gscore,7,gwhy),
        _factor("Institutional Flows",fscore,7,fwhy),
        _factor("News & Events",nscore,5,nwhy),
        _factor("Volatility Regime",vol_score,4,[f"Volatility state {volreg.get('state','UNAVAILABLE')}",f"VIX change {vix_change:+.2f}%"]),
        _factor("Gap & Time Regime",gap_score,3,[f"Open {gap.get('type','UNAVAILABLE')} {gap.get('gap_pct',0):+.2f}%",f"Time regime {timereg.get('label','UNKNOWN')}"]),
        _factor("Event Risk",event_score,2,[f"Event risk {evrisk.get('level','NORMAL')}"]),
        _factor("Historical Similarity",hscore,2,hwhy),
    ]
    # Learned behavior is advisory and only gets a small weight after enough
    # completed comparable events exist. It never replaces current evidence.
    provisional_side = "CALL" if (tscore + oscore + pscore + breadth_score + gscore + fscore + nscore + hscore) >= 0 else "PUT"
    learning = learning_preview(provisional_side, factors)
    learned_score = _num(learning.get("direction_score")) if learning.get("available") else 0.0
    factors.append(_factor("Learned Market Behavior", learned_score, 3, [
        f"Comparable completed events: {learning.get('sample_size', 0)}",
        (f"Average comparable 30m return: {learning.get('average_30m_return_pct'):.4f}%" if learning.get("available") else learning.get("reason", "Learning evidence unavailable")),
    ]))

    total_weight=sum(f["weight"] for f in factors if f["name"] not in ("Options",) or opt.get("available"))
    if not opt.get("available"):
        # Re-normalise weights across available factors rather than penalising with a fake zero.
        available=[f for f in factors if not (f["name"]=="Options")]
    else: available=factors
    total_weight=sum(f["weight"] for f in available)
    aggregate=sum(f["score"]*f["weight"] for f in available)/max(1,total_weight)

    # Reversal risk is a separate brake. It never creates a signal by itself.
    warnings=[]; reversal=[]
    if r.close<=r.support20: warnings.append("Price is at/below 20-bar support")
    if r.close>=r.resistance20: warnings.append("Price is at/near 20-bar resistance")
    if r.rsi>=70: warnings.append("RSI is overbought")
    if r.rsi<=30: warnings.append("RSI is oversold")
    if r.adx<18: warnings.append("Trend strength is weak")
    if r.volume>0 and r.vol_ma20>0 and r.volume>1.8*r.vol_ma20: warnings.append("Volume expansion")
    if abs(r.close-p.close)>1.5*r.atr: warnings.append("Large move versus ATR")
    if risk_off: warnings.append("Several tracked global indices are down")
    if next((_num(z.get("change_pct"))>3 for z in glob if z.get("symbol")=="CL=F"),False): warnings.append("Tracked crude move is elevated")
    if next((_num(z.get("change_pct"))>10 for z in glob if z.get("symbol")=="^VIX"),False): warnings.append("Tracked VIX change is elevated")
    if not basket: warnings.append("Bank constituent breadth unavailable")
    elif down>=up*1.5: warnings.append(f"Banking breadth is negative ({down} down / {up} up)")
    elif up>=down*1.5: warnings.append(f"Banking breadth is positive ({up} up / {down} down)")
    if news.get("items") and len([n for n in news["items"] if n.get("category") in ("RBI/BANKING","MACRO","US RATES","CRUDE","GEOPOLITICS","FX")])>=5: warnings.append("Multiple macro/banking/geopolitical headlines are active")

    action="CALL" if aggregate>=18 else ("PUT" if aggregate<=-18 else "WAIT")
    # Require cross-factor confirmation: at least 3 non-identical factors must support the side.
    supporting=[f for f in available if (f["score"]>=12 if action=="CALL" else f["score"]<=-12 if action=="PUT" else abs(f["score"])<12)]
    if action in ("CALL","PUT") and len(supporting)<3: action="WAIT"
    score=50+abs(aggregate)/2
    if action=="WAIT": score=min(score,69)

    if action=="CALL":
        if risk_off: reversal.append("Global risk-off can invalidate a bullish setup")
        if down>up*1.5: reversal.append("Banking breadth is negative")
        if r.close<r.vwap: reversal.append("Price is below VWAP")
        if psych.get("state")=="PANIC_EXHAUSTION_WATCH": reversal.append("Extreme fear with price stabilisation: reversal watch")
    elif action=="PUT":
        if up>down*1.5: reversal.append("Banking breadth is positive")
        if r.close>r.vwap: reversal.append("Price is above VWAP")
        if psych.get("state")=="FOMO_EXHAUSTION_WATCH": reversal.append("Extreme greed with price stabilisation: reversal watch")

    # Compare against the previous completed cycle. This explains signal changes instead of hiding them.
    global _LAST
    previous=_LAST.copy()
    current_factors={f["name"]:f["score"] for f in factors}
    current_indicators={z["name"]:z["score"] for z in indicator_scores}
    change_drivers=[]
    if previous.get("action") and previous.get("action")!=action:
        for name,cur in current_factors.items():
            old=previous.get("factors",{}).get(name)
            if old is not None:
                delta=cur-old
                if abs(delta)>=8:
                    change_drivers.append({"factor":name,"previous":round(old,1),"current":round(cur,1),"delta":round(delta,1),"reason":"material change in factor score"})
        change_drivers.sort(key=lambda z:abs(z["delta"]),reverse=True)
    elif previous.get("action"):
        for name,cur in current_factors.items():
            old=previous.get("factors",{}).get(name)
            if old is not None and abs(cur-old)>=12:
                change_drivers.append({"factor":name,"previous":round(old,1),"current":round(cur,1),"delta":round(cur-old,1),"reason":"material factor movement"})
        change_drivers.sort(key=lambda z:abs(z["delta"]),reverse=True)
    # Drill down into individual technical indicators so a signal change can be
    # attributed to EMA/VWAP/MACD/RSI/ADX/volume rather than only "Technical".
    old_indicators=previous.get("indicators",{})
    for name,cur in current_indicators.items():
        old=old_indicators.get(name)
        if old is not None and abs(cur-old)>=100:
            change_drivers.append({"factor":name,"previous":round(old,1),"current":round(cur,1),"delta":round(cur-old,1),"reason":"technical indicator state changed"})
    change_drivers.sort(key=lambda z:abs(z["delta"]),reverse=True)
    _LAST={"action":action,"score":score,"factors":current_factors,"indicators":current_indicators}
    learning_context=record_cycle(action, score, float(r.close), factors, indicator_scores, bool(previous.get("action") and previous.get("action")!=action), change_drivers)
    learning_context["current_preview"]=learning

    evidence=[]
    for f in sorted(available,key=lambda z:abs(z["contribution"]),reverse=True):
        if f["direction"]!="NEUTRAL": evidence.append(f"{f['name']}: {f['direction']} ({f['score']:+.1f})")
    evidence += [f"Signal requires multi-factor confirmation: {len(supporting)} supporting factors"]

    return {
        "available":True,"action":action,"base_action":base_signal.action,"score":round(_clamp(score,0,100),1),
        "aggregate_direction":round(aggregate,1),"factor_scores":factors,"technical_indicators":indicator_scores,
        "supporting_factor_count":len(supporting),"evidence":evidence,"warnings":warnings,"reversal_risks":reversal,
        "signal_change":{"changed":bool(previous.get("action") and previous.get("action")!=action),"from":previous.get("action"),"to":action,"drivers":change_drivers},
        "breadth":breadth,"global":glob,"options":opt,"institutional_flows":flows,"news":news,"historical_similarity":sim,"psychology":psych,"learning":learning_context,
        "market_structure":structure,"gap":gap,"volatility_regime":volreg,"time_regime":timereg,"event_risk":evrisk,"relative_strength":rel,
        "regime":"RISK-OFF" if risk_off else ("TREND" if r.adx>=20 else "RANGE"),
        "expected_validity":"5-15 min to next major data/news shock" if action in ("CALL","PUT") else "Reassess on next signal cycle",
        "time_horizons":{"intraday":"5-15m / 30m-few hours","next_day":"next trading session","swing":"2-5 days","medium":"1-4 weeks"}
    }
