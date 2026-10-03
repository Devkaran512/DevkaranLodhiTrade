from .sources import candles, BANKNIFTY
from .indicators import enrich
import numpy as np

def run_backtest(period="1y", interval="1d", horizons=(1,3,5)):
    try:
        df=candles(BANKNIFTY,period=period,interval=interval)
        if df is None or len(df)==0:
            return {"available":False,"reason":"No historical BANKNIFTY data returned by the public data source"}
        x=enrich(df).dropna(subset=["ema9","ema21","ema50","ema200","rsi","atr","macd_hist","vwap"]).reset_index(drop=True)
        if len(x)<80: return {"available":False,"reason":f"Insufficient clean history for backtest ({len(x)} rows; need at least 80)"}
        signals=[]
        max_h=max(horizons)
        for i in range(50,len(x)-max_h-1):
            r=x.iloc[i]; p=x.iloc[i-1]
            bull=sum([r.close>r.ema9,r.ema9>r.ema21,r.ema21>r.ema50,r.close>r.ema200,r.close>r.vwap,r.macd_hist>0,r.rsi>55,r.adx>=20 and r.close>p.close,r.close>r.sma20])
            bear=9-bull
            if bull==bear: continue
            action="CALL" if bull>bear else "PUT"
            if max(bull,bear)<6: continue
            row={"action":action}
            for h in horizons:
                future=float(x.iloc[i+h].close); now=float(r.close)
                row[f"ret_{h}d_pct"]=(future/now-1)*100 if action=="CALL" else (now/future-1)*100
            signals.append(row)
        out={"available":True,"samples":len(signals),"period":period,"interval":interval,"horizons":{}}
        for h in horizons:
            vals=[float(s[f"ret_{h}d_pct"]) for s in signals]
            wins=sum(1 for v in vals if v>0)
            out["horizons"][f"{h}d"]={"samples":len(vals),"wins":wins,"win_rate_pct":round(100*wins/len(vals),2) if vals else 0,"avg_return_pct":round(sum(vals)/len(vals),3) if vals else 0,"median_return_pct":round(float(np.median(vals)),3) if vals else 0}
        return out
    except Exception as e:
        return {"available":False,"reason":"Backtest data/engine error: "+str(e)[:220]}
