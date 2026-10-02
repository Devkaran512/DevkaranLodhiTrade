from dataclasses import dataclass
import math
from .indicators import enrich
from .sources import basket_snapshot, global_snapshot, option_snapshot
from .config import MIN_SIGNAL_SCORE

@dataclass
class Signal:
    action:str; score:float; entry:float|None=None; stop_loss:float|None=None; target:float|None=None; message:str=""

def _safe(v):
    try: return float(v) if math.isfinite(float(v)) else None
    except Exception: return None

def analyse(df):
    x=enrich(df).dropna(subset=["ema21","ema50","rsi","atr","macd_hist","adx","vwap"])
    if len(x)<40: return Signal("WAIT",0,message="Insufficient public candle history")
    r=x.iloc[-1]; p=x.iloc[-2]
    bull=bear=0; reasons=[]
    def add(cond,label):
        nonlocal bull,bear
        if cond: bull+=1; reasons.append("+")
        else: bear+=1; reasons.append("-")
    add(r.close>r.ema9,"EMA9"); add(r.ema9>r.ema21,"EMA21"); add(r.ema21>r.ema50,"EMA50")
    add(r.close>r.vwap,"VWAP"); add(r.macd_hist>0,"MACD"); add(r.rsi>55,"RSI") if r.rsi>=45 else add(False,"RSI")
    add(r.adx>=20 and r.close>p.close,"ADX/momentum") if r.adx>=20 else None
    if r.volume>r.vol_ma20 if r.vol_ma20>0 else False: add(r.close>p.close,"volume")
    add(r.close>r.sma20,"SMA20")
    total=bull+bear
    if total<5 or bull==bear: return Signal("WAIT",50,message="Factors are mixed")
    raw=50+50*abs(bull-bear)/total
    if raw<MIN_SIGNAL_SCORE: return Signal("WAIT",round(raw,1),message="No sufficiently aligned setup")
    side="CALL" if bull>bear else "PUT"; entry=float(r.close); av=float(r.atr)
    sl=entry-1.25*av if side=="CALL" else entry+1.25*av
    target=entry+2.0*(entry-sl) if side=="CALL" else entry-2.0*(sl-entry)
    return Signal(side,round(raw,1),entry,sl,target,"Technical factors aligned")

def final_signal(df):
    base=analyse(df)
    if base.action=="WAIT": return base
    # Cross-check with current banking breadth.
    basket=basket_snapshot(); g=global_snapshot(); opt=option_snapshot()
    breadth=(sum(1 for x in basket if x["change_pct"]>0),sum(1 for x in basket if x["change_pct"]<0)) if basket else (0,0)
    side=base.action
    if basket:
        up,down=breadth
        if side=="CALL" and down>up: return Signal("WAIT",base.score,message="BANK constituent breadth conflicts")
        if side=="PUT" and up>down: return Signal("WAIT",base.score,message="BANK constituent breadth conflicts")
    # Options are confirmation only; absence never becomes fake data.
    if opt.get("available"):
        pcr=opt["put_oi"]/opt["call_oi"] if opt["call_oi"] else None
        if pcr is not None:
            if side=="CALL" and pcr<0.70: return Signal("WAIT",base.score,message="Options positioning does not confirm CALL")
            if side=="PUT" and pcr>1.45: return Signal("WAIT",base.score,message="Options positioning does not confirm PUT")
    return base
