"""Advanced decision guards and diagnostics.
These features are deliberately advisory/guard-rail layers, not independent predictors.
"""
import math


def _n(v, d=0.0):
    try:
        x=float(v)
        return x if math.isfinite(x) else d
    except Exception:
        return d


def regime_snapshot(r, p, volreg, structure, psych, evrisk, vix_change=0.0):
    adx=_n(getattr(r,"adx",0)); atr=_n(getattr(r,"atr",0)); close=max(_n(getattr(r,"close",0)),1)
    vr=volreg.get("state","UNKNOWN") if volreg else "UNKNOWN"
    ps=psych.get("state","UNKNOWN") if psych else "UNKNOWN"
    if ps in ("PANIC","PANIC_EXHAUSTION_WATCH") or vr=="EXTREME": regime="PANIC/EXTREME_VOL"
    elif ps in ("FOMO","FOMO_EXHAUSTION_WATCH") and vr in ("HIGH","EXTREME"): regime="FOMO/HIGH_VOL"
    elif adx>=25 and structure.get("breakout") in ("UP","DOWN"): regime="BREAKOUT_TREND"
    elif adx>=20: regime="TREND"
    else: regime="RANGE"
    return {"regime":regime,"adx":round(adx,2),"atr_pct":round(atr/close*100,3),"vix_change_pct":round(_n(vix_change),2),"event_risk":evrisk.get("level","NORMAL") if evrisk else "NORMAL"}


def factor_conflict(factors):
    bull=[f for f in factors if _n(f.get("score"))>=20]
    bear=[f for f in factors if _n(f.get("score"))<=-20]
    b=sum(_n(f.get("weight")) for f in bull); s=sum(_n(f.get("weight")) for f in bear)
    spread=abs(b-s)
    level="HIGH" if bull and bear and spread<12 else ("MEDIUM" if bull and bear else "LOW")
    return {"level":level,"bullish_factors":[f.get("name") for f in bull],"bearish_factors":[f.get("name") for f in bear],"bullish_weight":round(b,1),"bearish_weight":round(s,1),"message":"Conflicting strong factors detected" if level!="LOW" else "No major strong-factor conflict"}


def false_signal_risk(r, volreg, structure, evrisk, psych, rel):
    risks=[]
    score=0
    if _n(getattr(r,"adx",0))<18: score+=20; risks.append("weak trend strength")
    if _n(getattr(r,"volume",0))<=_n(getattr(r,"vol_ma20",0)): score+=10; risks.append("breakout lacks volume expansion")
    if structure.get("breakout") in ("UP","DOWN") and _n(getattr(r,"volume",0))<=_n(getattr(r,"vol_ma20",0)): score+=15; risks.append("breakout without volume confirmation")
    if volreg.get("state") in ("HIGH","EXTREME"): score+=10; risks.append("high volatility increases whipsaw risk")
    if evrisk.get("level") in ("HIGH","ELEVATED"): score+=10; risks.append("event risk can invalidate technical setup")
    if psych.get("state") in ("PANIC_EXHAUSTION_WATCH","FOMO_EXHAUSTION_WATCH"): score+=10; risks.append("crowd exhaustion/reversal watch")
    if rel.get("direction") in ("OUTPERFORMING","UNDERPERFORMING"): score+=0
    return {"score":min(100,score),"level":"HIGH" if score>=45 else ("MEDIUM" if score>=25 else "LOW"),"risks":risks}


def shock_snapshot(r, p, glob, news, volreg, breadth):
    triggers=[]
    move=abs(_n(getattr(r,"close",0))- _n(getattr(p,"close",0)))/max(_n(getattr(p,"close",0)),1)*100
    if move>=1.0: triggers.append(f"BANKNIFTY move {move:.2f}%")
    if volreg.get("state")=="EXTREME": triggers.append("extreme realized volatility")
    vix=next((_n(z.get("change_pct")) for z in (glob or []) if z.get("symbol")=="^VIX"),0)
    if vix>=10: triggers.append(f"VIX +{vix:.1f}%")
    if breadth.get("total",0) and breadth.get("down",0)>=breadth.get("up",0)*2: triggers.append("banking breadth shock")
    if news and len(news.get("items",[]))>=1:
        titles=" ".join(str(x.get("title","")) for x in news.get("items",[])[:8]).lower()
        if any(k in titles for k in ("war","crash","emergency","rbi","fomc","rate decision","sanction","default")): triggers.append("major-risk headline detected")
    return {"active":len(triggers)>=2,"severity":"HIGH" if len(triggers)>=3 else ("MEDIUM" if len(triggers)==2 else "LOW"),"triggers":triggers}


def risk_snapshot(action, r, volreg, false_risk, conflict, evrisk):
    if action=="WAIT": return {"level":"LOW","reasons":["No directional position signal"]}
    score=0; reasons=[]
    if volreg.get("state") in ("HIGH","EXTREME"): score+=25; reasons.append("high volatility")
    if false_risk.get("level")=="HIGH": score+=25; reasons.append("false-signal risk high")
    elif false_risk.get("level")=="MEDIUM": score+=12; reasons.append("false-signal risk medium")
    if conflict.get("level")=="HIGH": score+=20; reasons.append("factor conflict")
    if evrisk.get("level")=="HIGH": score+=20; reasons.append("high event risk")
    if _n(getattr(r,"adx",0))<18: score+=10; reasons.append("weak trend")
    return {"level":"HIGH" if score>=50 else ("MEDIUM" if score>=25 else "LOW"),"score":min(100,score),"reasons":reasons}


def horizon_snapshot(action, regime, risk_level):
    if action not in ("CALL","PUT"):
        return {"intraday":"WAIT/REASSESS","next_day":"WAIT/REASSESS","swing":"WAIT/REASSESS","medium":"WAIT/REASSESS"}
    suffix=" (higher reversal risk)" if risk_level=="HIGH" else (" (monitor closely)" if risk_level=="MEDIUM" else "")
    return {"intraday":action+suffix,"next_day":action if regime in ("TREND","BREAKOUT_TREND") else "MIXED/REASSESS","swing":"REASSESS with fresh data","medium":"REASSESS with fresh data"}
