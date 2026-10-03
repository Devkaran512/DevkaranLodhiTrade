"""Observable market-psychology inference.

This module does not claim to read individual minds. It estimates collective behaviour
from price/volume/volatility/options/breadth/news proxies and returns transparent scores.
"""
from .indicators import enrich


def _clamp(v, lo=-100.0, hi=100.0):
    return max(lo, min(hi, float(v)))


def _sign(v):
    return 1 if v > 0 else (-1 if v < 0 else 0)


def _news_sentiment(items):
    pos = ("surge", "rally", "strong", "growth", "beat", "easing", "cut", "recovery", "inflow", "bullish", "support")
    neg = ("crash", "fall", "drop", "weak", "stress", "war", "inflation", "hike", "outflow", "bearish", "panic", "selloff", "shock", "downgrade")
    score = 0
    hits = 0
    for item in items or []:
        t=str(item.get("title", "")).lower()
        p=sum(1 for k in pos if k in t)
        n=sum(1 for k in neg if k in t)
        if p or n:
            score += p-n; hits += 1
    return (score / max(1, hits)) if hits else 0.0


def psychology_snapshot(df, option=None, breadth=None, global_rows=None, news=None):
    x=enrich(df)
    if len(x)<25:
        return {"available":False,"reason":"Insufficient history for psychology inference"}
    r=x.iloc[-1]; p=x.iloc[-2]
    price_dir=1 if r.close>p.close else (-1 if r.close<p.close else 0)
    vol_ratio=(float(r.volume)/float(r.vol_ma20)) if float(r.vol_ma20 or 0)>0 else 1.0
    vix_change=0.0
    gmap={z.get("symbol"):float(z.get("change_pct",0)) for z in (global_rows or [])}
    vix_change=gmap.get("^VIX",0.0)

    panic=0.0; greed=0.0
    if price_dir<0: panic += min(30, abs(float(r.roc10))*2.0 + max(0, -float(r.roc10)))
    if price_dir>0: greed += min(30, max(0, float(r.roc10))*2.0)
    if vol_ratio>1.2:
        if price_dir<0: panic += min(25,(vol_ratio-1)*25)
        elif price_dir>0: greed += min(25,(vol_ratio-1)*25)
    if vix_change>5: panic += min(30,vix_change*2)
    if vix_change<-5: greed += min(15,abs(vix_change))

    opt_confirm=0
    if option and option.get("available") and option.get("call_oi"):
        pcr=float(option.get("put_oi",0))/max(float(option.get("call_oi",0)),1.0)
        if pcr<0.7: greed += 8 if price_dir>0 else 4; opt_confirm += 1
        elif pcr>1.4: panic += 8 if price_dir<0 else 4; opt_confirm += 1
        if float(option.get("avg_call_iv") or 0)>float(option.get("avg_put_iv") or 0)*1.12 and price_dir>0: greed += 5
        if float(option.get("avg_put_iv") or 0)>float(option.get("avg_call_iv") or 0)*1.12 and price_dir<0: panic += 5

    breadth_score=0
    if breadth:
        up=float(breadth.get("up",0)); down=float(breadth.get("down",0)); total=max(1,float(breadth.get("total",0)))
        breadth_score=(up-down)/total*100
        if breadth_score<-35: panic += 10
        elif breadth_score>35: greed += 10

    news_score=_news_sentiment((news or {}).get("items",[]))
    if news_score<-0.7: panic += 8
    elif news_score>0.7: greed += 8

    panic=_clamp(panic,0,100); greed=_clamp(greed,0,100)
    uncertainty=min(100, abs(panic-greed)*0.15 + (35 if vol_ratio>1.5 else 0) + (25 if abs(news_score)<0.15 and len((news or {}).get("items",[]))>=5 else 0))
    crowd_bias=_clamp(greed-panic)
    # Psychology direction is deliberately modest: it cannot override all other evidence.
    direction=_clamp(crowd_bias + breadth_score*0.20 + news_score*10)
    if panic>75 and price_dir>=0: state="PANIC_EXHAUSTION_WATCH"
    elif greed>75 and price_dir<=0: state="FOMO_EXHAUSTION_WATCH"
    elif panic>greed+15: state="FEAR"
    elif greed>panic+15: state="GREED"
    elif uncertainty>55: state="UNCERTAINTY"
    else: state="BALANCED"

    return {
        "available":True,
        "state":state,
        "fear":round(panic,1),"greed":round(greed,1),"uncertainty":round(uncertainty,1),
        "direction_score":round(direction,1),"volume_ratio":round(vol_ratio,2),
        "breadth_score":round(breadth_score,1),"news_sentiment":round(news_score,2),
        "option_confirmation":opt_confirm,
        "crowd_vs_price": "crowd/price divergence watch" if ((greed>70 and price_dir<0) or (panic>70 and price_dir>0)) else "aligned",
        "interpretation": {
            "FEAR":"Observable selling/defensive behaviour is elevated.",
            "GREED":"Observable buying/FOMO-like behaviour is elevated.",
            "UNCERTAINTY":"Volatility and conflicting evidence suggest hesitation.",
            "PANIC_EXHAUSTION_WATCH":"Fear is extreme but price is no longer confirming fresh downside.",
            "FOMO_EXHAUSTION_WATCH":"Greed is extreme but price is no longer confirming fresh upside.",
            "BALANCED":"No dominant collective-behaviour regime detected."
        }[state]
    }
