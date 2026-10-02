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
    if total<6 or bull==bear: return Signal("WAIT",50,message="Factors are mixed")
    raw=50+50*abs(bull-bear)/total
    if raw<MIN_SIGNAL_SCORE: return Signal("WAIT",round(raw,1),message="No sufficiently aligned setup")
    side="CALL" if bull>bear else "PUT"; entry=float(r.close); av=float(r.atr)
    sl=entry-1.25*av if side=="CALL" else entry+1.25*av
    target=entry+2.0*(entry-sl) if side=="CALL" else entry-2.0*(sl-entry)
    return Signal(side,round(raw,1),entry,sl,target,"Technical factors aligned: "+", ".join(reasons[-5:]))

def final_signal(df):
    base=analyse(df)
    if base.action=="WAIT": return base
    basket=basket_snapshot(); opt=option_snapshot()
    if basket:
        up=sum(1 for x in basket if x["change_pct"]>0); down=sum(1 for x in basket if x["change_pct"]<0)
        if base.action=="CALL" and down>up*1.4: return Signal("WAIT",base.score,message="BANK constituent breadth conflicts")
        if base.action=="PUT" and up>down*1.4: return Signal("WAIT",base.score,message="BANK constituent breadth conflicts")
    if opt.get("available") and opt.get("call_oi"):
        pcr=opt["put_oi"]/opt["call_oi"]
        if base.action=="CALL" and pcr<0.60: return Signal("WAIT",base.score,message="Options positioning does not confirm CALL")
        if base.action=="PUT" and pcr>1.60: return Signal("WAIT",base.score,message="Options positioning does not confirm PUT")
    return base
