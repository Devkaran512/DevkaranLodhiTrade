"""Public-data adapters.

The live adapter uses Yahoo Finance's public web data through yfinance. This is
intended as a practical no-login source; availability and terms can change.
NSE/TradingView are documented as reference sources and are not scraped here.
"""
from datetime import datetime, timezone
import pandas as pd
import yfinance as yf
from .config import GLOBAL_SYMBOLS, LOOKBACK_PERIOD

BANKNIFTY="^NSEBANK"
# Editable fallback basket. Keep this list synchronized with the official Nifty Bank constituent file.
BANK_CONSTITUENTS={
    "HDFCBANK.NS":"HDFC Bank", "ICICIBANK.NS":"ICICI Bank", "SBIN.NS":"SBI",
    "AXISBANK.NS":"Axis Bank", "KOTAKBANK.NS":"Kotak Mahindra Bank",
    "INDUSINDBK.NS":"IndusInd Bank", "BANKBARODA.NS":"Bank of Baroda",
    "PNB.NS":"Punjab National Bank", "CANBK.NS":"Canara Bank",
    "FEDERALBNK.NS":"Federal Bank", "IDFCFIRSTB.NS":"IDFC First Bank",
    "AUBANK.NS":"AU Small Finance Bank"
}

def candles(symbol=BANKNIFTY, period=LOOKBACK_PERIOD, interval="5m"):
    df=yf.download(symbol, period=period, interval=interval, auto_adjust=False, progress=False, threads=False)
    if df is None or df.empty: raise RuntimeError(f"No public candle data for {symbol}")
    if isinstance(df.columns,pd.MultiIndex): df=df.xs(symbol,axis=1,level=-1,drop_level=True)
    df=df.reset_index()
    df.columns=[str(c).lower() for c in df.columns]
    if "datetime" in df: df=df.rename(columns={"datetime":"timestamp"})
    if "date" in df: df=df.rename(columns={"date":"timestamp"})
    return df

def quote(symbol):
    t=yf.Ticker(symbol)
    fi=t.fast_info
    return float(fi.get("last_price")) if fi.get("last_price") is not None else None

def basket_snapshot():
    rows=[]
    for sym,name in BANK_CONSTITUENTS.items():
        try:
            d=candles(sym,period="2d",interval="15m")
            if len(d)<30: continue
            close=d.close.astype(float); prev=float(close.iloc[-2]); last=float(close.iloc[-1])
            rows.append({"symbol":sym,"name":name,"price":last,"change_pct":(last/prev-1)*100})
        except Exception: continue
    return rows

def global_snapshot():
    out=[]
    for sym in GLOBAL_SYMBOLS:
        try:
            d=candles(sym,period="5d",interval="1d")
            if len(d)>=2:
                a=float(d.close.iloc[-2]); b=float(d.close.iloc[-1]); out.append({"symbol":sym,"change_pct":(b/a-1)*100})
        except Exception: continue
    return out

def option_snapshot():
    """Best-effort public options snapshot. If unavailable, caller must treat it as missing."""
    try:
        t=yf.Ticker(BANKNIFTY)
        exps=t.options
        if not exps: return {"available":False,"reason":"Public option expiry list unavailable"}
        exp=exps[0]
        chain=t.option_chain(exp)
        calls=chain.calls; puts=chain.puts
        return {"available":True,"expiry":exp,
                "call_oi":float(calls.openInterest.fillna(0).sum()),
                "put_oi":float(puts.openInterest.fillna(0).sum()),
                "call_volume":float(calls.volume.fillna(0).sum()),
                "put_volume":float(puts.volume.fillna(0).sum())}
    except Exception as e:
        return {"available":False,"reason":str(e)[:180]}

def source_status():
    return {
        "public_market_data":"Yahoo Finance public web adapter (no login)",
        "nse":"Reference/manual or permitted published data; no automated scraping",
        "tradingview":"Reference source; no automated scraping",
        "bank_constituents":"Editable basket in sources.py; verify against current official Nifty Bank file",
        "options":"Best-effort public options adapter; missing data => WAIT",
        "generated_at":datetime.now(timezone.utc).isoformat()
    }
