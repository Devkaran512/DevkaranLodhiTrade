from fastapi import FastAPI,Header,HTTPException
from .config import BACKEND_API_KEY
from .sources import candles, source_status, BANKNIFTY, market_state
from .strategy import final_signal
app=FastAPI(title="DevkaranLodhiTrade Public-Data Prediction API",version="1.1")

def auth(k):
    if k!=BACKEND_API_KEY: raise HTTPException(401,"Invalid backend API key")

@app.get("/health")
def health(): return {"status":"ok","mode":"prediction-only","groww":False,"demo":False,"service":"DevkaranLodhiTrade API"}

@app.get("/market")
def market(x_api_key:str=Header(default="")):
    auth(x_api_key); return market_state()

@app.get("/sources")
def sources(x_api_key:str=Header(default="")):
    auth(x_api_key); return source_status()

@app.get("/signal")
def signal(x_api_key:str=Header(default=""),interval:str="5m"):
    auth(x_api_key)
    state=market_state()
    try:
        df=candles(BANKNIFTY,interval=interval)
        s=final_signal(df)
        fallback=getattr(df, "attrs", {}).get("fallback_interval")
        if state["market_closed"] or fallback:
            msg=("Next-session setup based on the latest available public candles"
                 if fallback or state["market_closed"] else s.message)
        else: msg=s.message
        return {"underlying":"BANKNIFTY","action":s.action,"score":s.score,"entry":s.entry,"stop_loss":s.stop_loss,"target":s.target,"message":msg,"timestamp":str(df.iloc[-1].timestamp),"market_closed":state["market_closed"],"session":state["session"],"data_interval":fallback or interval,"holiday":state["holiday"],"next_trading_date":state["next_trading_date"],"next_market_open":state["next_market_open"],"market_open":state["market_open"],"market_close":state["market_close"],"upcoming_holidays":state["upcoming_holidays"],"groww":False,"demo":False}
    except Exception as e:
        return {"underlying":"BANKNIFTY","action":"WAIT","score":0,"entry":None,"stop_loss":None,"target":None,"message":(("Market closed; no usable historical public candles are currently available" if state["market_closed"] else "Live public data unavailable")+": "+str(e)[:180]),"market_closed":state["market_closed"],"session":state["session"],"holiday":state["holiday"],"next_trading_date":state["next_trading_date"],"next_market_open":state["next_market_open"],"market_open":state["market_open"],"market_close":state["market_close"],"upcoming_holidays":state["upcoming_holidays"],"groww":False,"demo":False}
