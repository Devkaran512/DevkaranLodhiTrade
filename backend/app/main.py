from fastapi import FastAPI,Header,HTTPException
from .config import BACKEND_API_KEY
from .sources import candles, source_status, BANKNIFTY
from .strategy import final_signal
app=FastAPI(title="DevkaranLodhiTrade Public-Data Prediction API",version="1.0")

def auth(k):
    if k!=BACKEND_API_KEY: raise HTTPException(401,"Invalid backend API key")

@app.get("/health")
def health(): return {"status":"ok","mode":"prediction-only","groww":False,"demo":False,"service":"DevkaranLodhiTrade API"}

@app.get("/sources")
def sources(x_api_key:str=Header(default="")):
    auth(x_api_key); return source_status()

@app.get("/signal")
def signal(x_api_key:str=Header(default=""),interval:str="5m"):
    auth(x_api_key)
    try:
        df=candles(BANKNIFTY,interval=interval)
        s=final_signal(df)
        return {"underlying":"BANKNIFTY","action":s.action,"score":s.score,"entry":s.entry,"stop_loss":s.stop_loss,"target":s.target,"message":s.message,"timestamp":str(df.iloc[-1].timestamp),"groww":False,"demo":False}
    except Exception as e:
        return {"underlying":"BANKNIFTY","action":"WAIT","score":0,"entry":None,"stop_loss":None,"target":None,"message":"Live public data unavailable: "+str(e)[:180],"groww":False,"demo":False}
