"""Out-of-sample validation for the production decision stack.

The historical public candle source does not provide a trustworthy full historical
option-chain/news/FII-DII archive through the current app. Therefore this backtest
uses only factors that can be reconstructed without inventing history: technical,
market structure, volatility, gap and (when available) BANKNIFTY-vs-NIFTY relative
strength. The result explicitly reports its factor coverage.
"""
from .sources import candles, BANKNIFTY
from .indicators import enrich
from .regimes import structure_snapshot, volatility_snapshot
import numpy as np

COST_BPS = 8.0  # conservative round-trip proxy for charges + slippage on the underlying


def _signal_at(x, i):
    r=x.iloc[i]; p=x.iloc[i-1]
    checks={
        "EMA9": 1 if r.close>r.ema9 else -1,
        "EMA21": 1 if r.ema9>r.ema21 else -1,
        "EMA50": 1 if r.ema21>r.ema50 else -1,
        "EMA200": 1 if r.close>r.ema200 else -1,
        "VWAP": 1 if r.close>r.vwap else -1,
        "MACD": 1 if r.macd_hist>0 else -1,
        "RSI": 1 if r.rsi>=55 else (-1 if r.rsi<=45 else 0),
        "ADX/momentum": 1 if r.adx>=20 and r.close>p.close else (-1 if r.adx>=20 and r.close<p.close else 0),
        "Volume": 1 if r.vol_ma20>0 and r.volume>r.vol_ma20 and r.close>p.close else (-1 if r.vol_ma20>0 and r.volume>r.vol_ma20 and r.close<p.close else 0),
        "SMA20": 1 if r.close>r.sma20 else -1,
    }
    tech=sum(checks.values())/max(1,len(checks))*100
    try:
        sr=structure_snapshot(x.iloc[:i+1])
        structure=float(sr.get("score",0)) if sr.get("available") else 0.0
    except Exception:
        structure=0.0
    try:
        vr=volatility_snapshot(x.iloc[:i+1])
        vs=vr.get("state","NORMAL")
        # volatility itself is not directional; it changes the confidence/risk guard.
        vol_risk=100.0 if vs=="EXTREME" else (60.0 if vs=="HIGH" else (25.0 if vs=="NORMAL" else 10.0))
    except Exception:
        vol_risk=25.0
    gap=(float(r.open)/float(p.close)-1)*100 if float(p.close) else 0.0
    gap_score=30.0 if gap>0.35 and float(r.close)>float(r.open) else (-30.0 if gap<-0.35 and float(r.close)<float(r.open) else 0.0)
    # Current public historical source does not expose trustworthy historical options/news/flows.
    aggregate=(tech*0.70)+(structure*0.20)+(gap_score*0.10)
    action="CALL" if aggregate>=18 else ("PUT" if aggregate<=-18 else "WAIT")
    # High volatility is a risk guard, not a directional override.
    if vol_risk>=100 and abs(aggregate)<35:
        action="WAIT"
    return action, aggregate, checks, {"technical":tech,"structure":structure,"gap":gap_score,"volatility_risk":vol_risk}


def _metrics(trades):
    vals=[t["net_return_pct"] for t in trades]
    if not vals:
        return {"samples":0,"wins":0,"losses":0,"win_rate_pct":0,"avg_return_pct":0,"median_return_pct":0,"profit_factor":0,"max_drawdown_pct":0,"max_consecutive_losses":0}
    wins=[v for v in vals if v>0]; losses=[v for v in vals if v<0]
    equity=0.0; peak=0.0; max_dd=0.0; streak=0; max_streak=0
    for v in vals:
        equity+=v; peak=max(peak,equity); max_dd=max(max_dd,peak-equity)
        streak=streak+1 if v<0 else 0; max_streak=max(max_streak,streak)
    pf=sum(wins)/abs(sum(losses)) if losses else float("inf")
    return {"samples":len(vals),"wins":len(wins),"losses":len(losses),"win_rate_pct":round(100*len(wins)/len(vals),2),"avg_return_pct":round(float(np.mean(vals)),4),"median_return_pct":round(float(np.median(vals)),4),"profit_factor":None if pf==float("inf") else round(pf,3),"max_drawdown_pct":round(max_dd,4),"max_consecutive_losses":max_streak,"avg_win_pct":round(float(np.mean(wins)),4) if wins else 0,"avg_loss_pct":round(float(np.mean(losses)),4) if losses else 0}


def _run_slice(x, horizons, start, end, cost_bps=COST_BPS):
    trades=[]; max_h=max(horizons)
    for i in range(max(50,start), min(end-max_h, len(x)-max_h)):
        action, aggregate, checks, diag=_signal_at(x,i)
        if action=="WAIT": continue
        now=float(x.iloc[i].close)
        for h in horizons:
            future=float(x.iloc[i+h].close)
            raw=(future/now-1)*100*(1 if action=="CALL" else -1)
            net=raw-(2*cost_bps/100.0)  # round-trip bps expressed as percentage
            trades.append({"index":i,"horizon":h,"action":action,"raw_return_pct":round(raw,5),"net_return_pct":round(net,5),"aggregate":round(aggregate,2),"diagnostics":diag,"factors":checks})
    return trades


def run_backtest(period="1y", interval="1d", horizons=(1,3,5), walk_forward=True):
    try:
        df=candles(BANKNIFTY,period=period,interval=interval)
        if df is None or len(df)==0:
            return {"available":False,"reason":"No historical BANKNIFTY data returned by the public data source"}
        x=enrich(df).dropna(subset=["ema9","ema21","ema50","ema200","rsi","atr","macd_hist","adx","vwap","sma20"]).reset_index(drop=True)
        if len(x)<100:
            return {"available":False,"reason":f"Insufficient clean history for backtest ({len(x)} rows; need at least 100)"}

        results={}
        for h in horizons:
            trades=_run_slice(x,(h,),50,len(x))
            results[f"{h}d"]=_metrics(trades)

        # A chronological 70/30 walk-forward report prevents the displayed result
        # from being only an in-sample statistic.
        wf=None
        if walk_forward:
            split=max(60,int(len(x)*0.70))
            train=_run_slice(x,horizons,50,split)
            test=_run_slice(x,horizons,split,len(x))
            wf={"train_rows":split,"test_rows":len(x)-split,"train":{},"test":{}}
            for h in horizons:
                wf["train"][f"{h}d"]=_metrics([t for t in train if t["horizon"]==h])
                wf["test"][f"{h}d"]=_metrics([t for t in test if t["horizon"]==h])

        # Factor attribution on all completed trades: how often each directional
        # factor agreed with the realized outcome.
        all_trades=_run_slice(x,(1,),50,len(x))
        factor_stats={}
        for t in all_trades:
            actual=1 if t["raw_return_pct"]>0 else (-1 if t["raw_return_pct"]<0 else 0)
            if actual==0: continue
            for name,val in t["factors"].items():
                if val==0: continue
                d=factor_stats.setdefault(name,{"samples":0,"aligned":0})
                d["samples"]+=1
                if val==actual:d["aligned"]+=1
        for name,d in factor_stats.items(): d["alignment_rate_pct"]=round(100*d["aligned"]/max(1,d["samples"]),2)

        return {
            "available":True,"period":period,"interval":interval,"samples":sum(v["samples"] for v in results.values()),
            "engine":"production_validation_v22",
            "factor_coverage":["Technical","Market Structure","Volatility Regime","Gap"],
            "unavailable_historical_factors":["Historical option-chain/OI/IV","Historical news/event feed","Historical FII/DII flow archive","Historical psychology inputs"],
            "data_quality":{"history_rows":len(df),"clean_rows":len(x),"coverage_pct":round(100*len(x)/max(1,len(df)),2),"cost_model":"8 bps each way proxy; underlying directional test, not option P&L"},
            "cost_model":{"round_trip_bps":2*COST_BPS,"slippage_included":True,"note":"Conservative proxy; replace with broker/exchange-specific costs for exact trading P&L"},
            "horizons":results,"walk_forward":wf,"factor_attribution_1d":factor_stats,
            "methodology":"Chronological out-of-sample validation of candle-reconstructable production factors. Live-only factors are not backfilled with invented data. This is not an option-premium backtest and is not a guarantee of future results."
        }
    except Exception as e:
        return {"available":False,"reason":"Backtest data/engine error: "+str(e)[:220]}
