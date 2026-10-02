"""Public-data adapters and NSE session calendar."""
from datetime import datetime, date, time, timedelta, timezone
from zoneinfo import ZoneInfo
import pandas as pd
import yfinance as yf
from .config import GLOBAL_SYMBOLS, LOOKBACK_PERIOD

BANKNIFTY="^NSEBANK"
IST=ZoneInfo("Asia/Kolkata")

# NSE 2026 equity/F&O trading holidays. Source: NSE official 2026 calendar.
NSE_HOLIDAYS_2026={
    "2026-01-15":"Municipal Corporation Election - Maharashtra",
    "2026-01-26":"Republic Day",
    "2026-03-03":"Holi",
    "2026-03-26":"Shri Ram Navami",
    "2026-03-31":"Shri Mahavir Jayanti",
    "2026-04-03":"Good Friday",
    "2026-04-14":"Dr. Baba Saheb Ambedkar Jayanti",
    "2026-05-01":"Maharashtra Day",
    "2026-05-28":"Bakri Id",
    "2026-06-26":"Muharram",
    "2026-09-14":"Ganesh Chaturthi",
    "2026-10-02":"Mahatma Gandhi Jayanti",
    "2026-10-20":"Dussehra",
    "2026-11-10":"Diwali-Balipratipada",
    "2026-11-24":"Prakash Gurpurb Sri Guru Nanak Dev",
    "2026-12-25":"Christmas",
}

BANK_CONSTITUENTS={
    "HDFCBANK.NS":"HDFC Bank", "ICICIBANK.NS":"ICICI Bank", "SBIN.NS":"SBI",
    "AXISBANK.NS":"Axis Bank", "KOTAKBANK.NS":"Kotak Mahindra Bank",
    "INDUSINDBK.NS":"IndusInd Bank", "BANKBARODA.NS":"Bank of Baroda",
    "PNB.NS":"Punjab National Bank", "CANBK.NS":"Canara Bank",
    "FEDERALBNK.NS":"Federal Bank", "IDFCFIRSTB.NS":"IDFC First Bank",
    "AUBANK.NS":"AU Small Finance Bank"
}

def _download(symbol, period, interval):
    df=yf.download(symbol, period=period, interval=interval, auto_adjust=False, progress=False, threads=False)
    if df is None or df.empty:
        df=yf.Ticker(symbol).history(period=period, interval=interval, auto_adjust=False)
    if df is None or df.empty:
        raise RuntimeError(f"No public candle data for {symbol}")
    if isinstance(df.columns,pd.MultiIndex):
        df=df.xs(symbol,axis=1,level=-1,drop_level=True)
    df=df.reset_index()
    df.columns=[str(c).lower() for c in df.columns]
    if "datetime" in df: df=df.rename(columns={"datetime":"timestamp"})
    if "date" in df: df=df.rename(columns={"date":"timestamp"})
    return df

def candles(symbol=BANKNIFTY, period=LOOKBACK_PERIOD, interval="5m"):
    try:
        return _download(symbol, period, interval)
    except Exception as intraday_error:
        if interval != "1d":
            try:
                daily=_download(symbol, "1y", "1d")
                daily.attrs["fallback_reason"]=str(intraday_error)[:180]
                daily.attrs["fallback_interval"]="1d"
                return daily
            except Exception:
                pass
        raise RuntimeError(f"No public candle data for {symbol}")

def _is_holiday(d: date):
    return d.strftime("%Y-%m-%d") in NSE_HOLIDAYS_2026

def _next_trading_day(d: date):
    n=d + timedelta(days=1)
    while n.weekday() >= 5 or _is_holiday(n):
        n += timedelta(days=1)
    return n

def _prev_trading_day(d: date):
    n=d - timedelta(days=1)
    while n.weekday() >= 5 or _is_holiday(n):
        n -= timedelta(days=1)
    return n

def market_state():
    now=datetime.now(IST)
    today=now.date()
    key=today.strftime("%Y-%m-%d")
    holiday_name=NSE_HOLIDAYS_2026.get(key)
    weekday_closed=today.weekday() >= 5
    holiday_closed=holiday_name is not None
    # BANKNIFTY equity-derivatives normal market is 09:15-15:40 IST.
    market_open=now.replace(hour=9,minute=15,second=0,microsecond=0)
    market_close=now.replace(hour=15,minute=40,second=0,microsecond=0)
    regular_closed=now < market_open or now > market_close
    closed=weekday_closed or holiday_closed or regular_closed

    if holiday_closed or weekday_closed:
        next_day=_next_trading_day(today)
        reason=holiday_name or ("Saturday" if today.weekday()==5 else "Sunday")
        session="NEXT TRADING SESSION"
    elif now < market_open:
        next_day=today
        reason="Pre-market"
        session="PRE-MARKET"
    elif now <= market_close:
        next_day=today
        reason="Market open"
        session="LIVE SESSION"
    else:
        next_day=_next_trading_day(today)
        reason="Market closed"
        session="NEXT TRADING SESSION"

    next_open=datetime.combine(next_day,time(9,15),tzinfo=IST)
    upcoming=[]
    for ds,name in sorted(NSE_HOLIDAYS_2026.items()):
        d=date.fromisoformat(ds)
        if d >= today:
            upcoming.append({"date":ds,"name":name,"day":d.strftime("%A")})
        if len(upcoming)>=8: break

    return {
        "market_closed":closed,
        "date":key,
        "session":session,
        "reason":reason,
        "holiday":holiday_name,
        "next_trading_date":next_day.isoformat(),
        "next_market_open":next_open.isoformat(),
        "market_open":"09:15",
        "market_close":"15:40",
        "upcoming_holidays":upcoming,
    }

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
        "nse_holiday_calendar":NSE_HOLIDAYS_2026,
        "generated_at":datetime.now(timezone.utc).isoformat()
    }
