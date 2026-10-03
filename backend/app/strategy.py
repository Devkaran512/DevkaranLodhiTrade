from dataclasses import dataclass
import math
from .indicators import enrich
from .config import MIN_SIGNAL_SCORE

@dataclass
class Signal:
    action:str
    score:float
    entry:float|None=None
    stop_loss:float|None=None
    target:float|None=None
    message:str=""


def _safe(v):
    try: return float(v) if math.isfinite(float(v)) else None
    except Exception: return None


def analyse(df):
    """Technical-only baseline.

    This is intentionally NOT the final signal. The production endpoint combines
    this baseline with options, psychology, breadth, global markets, flows, news,
    historical context and reversal risk in intelligence.py.
    """
    x=enrich(df).dropna(subset=["ema21","ema50","rsi","atr","macd_hist","adx","vwap"])
    if len(x)<40: return Signal("WAIT",0,message="Insufficient public candle history")
    r=x.iloc[-1]; p=x.iloc[-2]
    bull=bear=0; reasons=[]
    def add(cond,label):
        nonlocal bull,bear
        if cond: bull+=1; reasons.append("+"+label)
        else: bear+=1; reasons.append("-"+label)
    add(r.close>r.ema9,"EMA9"); add(r.ema9>r.ema21,"EMA21"); add(r.ema21>r.ema50,"EMA50"); add(r.close>r.ema200,"EMA200")
    add(r.close>r.vwap,"VWAP"); add(r.macd_hist>0,"MACD")
    if r.rsi>=55: add(True,"RSI")
    elif r.rsi<=45: add(False,"RSI")
    if r.adx>=20: add(r.close>p.close,"ADX/momentum")
    if r.vol_ma20>0 and r.volume>r.vol_ma20: add(r.close>p.close,"volume")
    add(r.close>r.sma20,"SMA20")
    total=bull+bear
    if total<6 or bull==bear: return Signal("WAIT",50,message="Technical factors are mixed; multi-factor engine continues analysis")
    raw=50+50*abs(bull-bear)/total
    side="CALL" if bull>bear else "PUT"
    entry=float(r.close); av=float(r.atr)
    sl=entry-1.25*av if side=="CALL" else entry+1.25*av
    target=entry+2.0*(entry-sl) if side=="CALL" else entry-2.0*(sl-entry)
    if raw<MIN_SIGNAL_SCORE:
        return Signal("WAIT",round(raw,1),entry,sl,target,"Technical alignment below baseline threshold: "+", ".join(reasons[-5:]))
    return Signal(side,round(raw,1),entry,sl,target,"Technical baseline: "+", ".join(reasons[-5:]))


def final_signal(df):
    # Final action is deliberately assembled in intelligence.py so no single
    # factor can short-circuit the rest of the evidence stack.
    return analyse(df)
