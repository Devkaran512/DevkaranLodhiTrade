from fastapi import FastAPI, Header, HTTPException
from .config import BACKEND_API_KEY
from .sources import candles, source_status, BANKNIFTY, market_state
from .strategy import final_signal
from .intelligence import build_intelligence
from .backtest import run_backtest

app=FastAPI(title="DevkaranLodhiTrade Production Prediction API",version="2.0")

def auth(k):
    if k!=BACKEND_API_KEY: raise HTTPException(401,"Invalid backend API key")

def make_signal(interval="5m"):
    state=market_state(); df=candles(BANKNIFTY,interval=interval)
    s=final_signal(df)
    intel=build_intelligence(df,s)
    action=intel.get("action",s.action); score=intel.get("score",s.score)
    entry=s.entry; stop_loss=s.stop_loss; target=s.target
    # The multi-factor engine can produce a confirmed CALL/PUT even when the
    # technical-only layer was neutral. Build transparent underlying levels.
    if action in ("CALL","PUT") and entry is None:
        r=df.iloc[-1]
        entry=float(r.close)
        try:
            from .indicators import enrich
            er=enrich(df).iloc[-1]
            av=float(er.atr)
            stop_loss=entry-1.25*av if action=="CALL" else entry+1.25*av
            target=entry+2.0*(entry-stop_loss) if action=="CALL" else entry-2.0*(stop_loss-entry)
        except Exception:
            entry=None; stop_loss=None; target=None
    msg=s.message
    if intel.get("signal_change",{}).get("changed"):
        drivers=intel.get("signal_change",{}).get("drivers",[])
        if drivers:
            msg += " | Signal changed mainly because: " + ", ".join(d["factor"] for d in drivers[:3])
    if intel.get("warnings"): msg += " | " + "; ".join(intel["warnings"][:3])
    if state["market_closed"] or getattr(df,"attrs",{}).get("fallback_interval"):
        msg="Next-session setup based on latest available public candles. " + msg
    return {"underlying":"BANKNIFTY","market_price":float(df.iloc[-1].close),"action":action,"score":score,"entry":entry,"stop_loss":stop_loss,"target":target,"message":msg,"timestamp":str(df.iloc[-1].timestamp),"market_closed":state["market_closed"],"session":state["session"],"data_interval":getattr(df,"attrs",{}).get("fallback_interval") or interval,"holiday":state["holiday"],"next_trading_date":state["next_trading_date"],"next_market_open":state["next_market_open"],"market_open":state["market_open"],"market_close":state["market_close"],"upcoming_holidays":state["upcoming_holidays"],"groww":False,"demo":False,"intelligence":intel}

@app.get("/health")
def health(x_api_key:str=Header(default="")):
    auth(x_api_key)
    return {"status":"ok","mode":"production-prediction","groww":False,"demo":False,"service":"DevkaranLodhiTrade API","engine_version":"2.0"}

@app.get("/market")
def market(x_api_key:str=Header(default="")):
    auth(x_api_key); return market_state()

@app.get("/sources")
def sources(x_api_key:str=Header(default="")):
    auth(x_api_key); return source_status()

@app.get("/signal")
def signal(x_api_key:str=Header(default=""), interval:str="5m"):
    auth(x_api_key)
    try: return make_signal(interval)
    except Exception as e:
        state=market_state()
        return {"underlying":"BANKNIFTY","action":"WAIT","score":0,"entry":None,"stop_loss":None,"target":None,"message":(("Market closed; no usable historical public candles are currently available" if state["market_closed"] else "Live public data unavailable")+": "+str(e)[:180]),"market_closed":state["market_closed"],"session":state["session"],"holiday":state["holiday"],"next_trading_date":state["next_trading_date"],"next_market_open":state["next_market_open"],"market_open":state["market_open"],"market_close":state["market_close"],"upcoming_holidays":state["upcoming_holidays"],"groww":False,"demo":False}

@app.get("/analysis")
def analysis(x_api_key:str=Header(default=""), interval:str="5m"):
    auth(x_api_key); return make_signal(interval)

@app.get("/backtest")
def backtest(x_api_key:str=Header(default=""), period:str="1y"):
    auth(x_api_key)
    try:
        result=run_backtest(period=period)
        return result
    except Exception as e:
        # Never turn a data-source failure into an opaque HTTP 500 for the mobile client.
        return {"available":False,"period":period,"reason":"Backtest service error: "+str(e)[:220]}
