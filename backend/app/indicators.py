import numpy as np
import pandas as pd

def ema(s,n): return s.ewm(span=n,adjust=False).mean()
def sma(s,n): return s.rolling(n).mean()
def rsi(s,n=14):
    d=s.diff(); up=d.clip(lower=0); dn=-d.clip(upper=0)
    au=up.ewm(alpha=1/n,adjust=False).mean(); ad=dn.ewm(alpha=1/n,adjust=False).mean()
    rs=au/ad.replace(0,np.nan); return 100-(100/(1+rs))
def true_range(x):
    return pd.concat([x.high-x.low,(x.high-x.close.shift()).abs(),(x.low-x.close.shift()).abs()],axis=1).max(axis=1)
def atr(x,n=14): return true_range(x).ewm(alpha=1/n,adjust=False).mean()
def macd(s):
    line=ema(s,12)-ema(s,26); sig=ema(line,9); return line,sig,line-sig
def stochastic(x,n=14):
    lo=x.low.rolling(n).min(); hi=x.high.rolling(n).max(); return 100*(x.close-lo)/(hi-lo).replace(0,np.nan)
def adx(x,n=14):
    tr=true_range(x); up=x.high.diff(); dn=-x.low.diff()
    plus=up.where((up>dn)&(up>0),0.0); minus=dn.where((dn>up)&(dn>0),0.0)
    atrv=tr.ewm(alpha=1/n,adjust=False).mean()
    pdi=100*plus.ewm(alpha=1/n,adjust=False).mean()/atrv.replace(0,np.nan)
    mdi=100*minus.ewm(alpha=1/n,adjust=False).mean()/atrv.replace(0,np.nan)
    dx=100*(pdi-mdi).abs()/(pdi+mdi).replace(0,np.nan)
    return dx.ewm(alpha=1/n,adjust=False).mean()
def enrich(df):
    x=df.copy(); x.columns=[str(c).lower() for c in x.columns]
    for c in ["open","high","low","close","volume"]:
        if c not in x: x[c]=0.0
        x[c]=pd.to_numeric(x[c],errors="coerce")
    x=x.dropna(subset=["close"])
    for n in (9,20,21,50,200): x[f"ema{n}"]=ema(x.close,n)
    x["sma20"]=sma(x.close,20); x["rsi"]=rsi(x.close); x["atr"]=atr(x)
    ml,ms,mh=macd(x.close); x["macd"]=ml; x["macd_signal"]=ms; x["macd_hist"]=mh
    x["stoch"]=stochastic(x); x["adx"]=adx(x)
    tp=(x.high+x.low+x.close)/3; x["vwap"]=(tp*x.volume).cumsum()/x.volume.replace(0,np.nan).cumsum()
    x["vol_ma20"]=x.volume.rolling(20).mean(); x["bb_mid"]=sma(x.close,20); x["bb_std"]=x.close.rolling(20).std(); x["bb_hi"]=x.bb_mid+2*x.bb_std; x["bb_lo"]=x.bb_mid-2*x.bb_std
    x["roc10"]=x.close.pct_change(10)*100; x["momentum10"]=x.close.diff(10)
    x["obv"]=(np.sign(x.close.diff()).fillna(0)*x.volume).cumsum()
    x["day_high"]=x.high.cummax(); x["day_low"]=x.low.cummin(); x["support20"]=x.low.rolling(20).min(); x["resistance20"]=x.high.rolling(20).max()
    return x
