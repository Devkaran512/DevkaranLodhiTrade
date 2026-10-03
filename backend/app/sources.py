"""Public-data adapters and NSE session calendar."""
from datetime import datetime, date, time, timedelta, timezone
from zoneinfo import ZoneInfo
import pandas as pd
import yfinance as yf
import requests
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

def _download_yahoo_chart(symbol, period, interval):
    # Direct Yahoo chart API fallback. This avoids depending entirely on yfinance
    # when the Render runtime cannot complete yfinance's Yahoo query flow.
    url=f"https://query1.finance.yahoo.com/v8/finance/chart/{requests.utils.quote(symbol, safe='')}"
    params={"range":period,"interval":interval,"events":"history","includeAdjustedClose":"true"}
    r=requests.get(url,params=params,headers={"User-Agent":"Mozilla/5.0 DevkaranLodhiTrade/production"},timeout=20)
    r.raise_for_status()
    payload=r.json()
    result=((payload.get("chart") or {}).get("result") or [None])[0]
    if not result:
        err=((payload.get("chart") or {}).get("error") or {}).get("description") or "Yahoo chart returned no result"
        raise RuntimeError(str(err))
    ts=result.get("timestamp") or []
    q=(result.get("indicators") or {}).get("quote") or [{}]
    q=q[0] if q else {}
    if not ts or not q.get("close"):
        raise RuntimeError(f"No public candle data for {symbol}")
    n=min(len(ts),len(q.get("open",[])),len(q.get("high",[])),len(q.get("low",[])),len(q.get("close",[])),len(q.get("volume",[])))
    rows=[]
    for i in range(n):
        c=q["close"][i]
        if c is None: continue
        rows.append({
            "timestamp":pd.to_datetime(ts[i],unit="s",utc=True),
            "open":q["open"][i],"high":q["high"][i],"low":q["low"][i],
            "close":c,"volume":q["volume"][i] if q.get("volume") else 0
        })
    if not rows:
        raise RuntimeError(f"No public candle data for {symbol}")
    return pd.DataFrame(rows)

def _download(symbol, period, interval):
    last_error=None
    try:
        df=yf.download(symbol, period=period, interval=interval, auto_adjust=False, progress=False, threads=False)
        if df is None or df.empty:
            df=yf.Ticker(symbol).history(period=period, interval=interval, auto_adjust=False)
        if df is not None and not df.empty:
            if isinstance(df.columns,pd.MultiIndex):
                df=df.xs(symbol,axis=1,level=-1,drop_level=True)
            df=df.reset_index()
            df.columns=[str(c).lower() for c in df.columns]
            if "datetime" in df: df=df.rename(columns={"datetime":"timestamp"})
            if "date" in df: df=df.rename(columns={"date":"timestamp"})
            return df
    except Exception as e:
        last_error=e
    try:
        return _download_yahoo_chart(symbol,period,interval)
    except Exception as e:
        last_error=e
    raise RuntimeError(f"No public candle data for {symbol}: {str(last_error)[:180]}")

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
        call_oi=float(calls.openInterest.fillna(0).sum()); put_oi=float(puts.openInterest.fillna(0).sum())
        call_chg=float(calls.get("change",pd.Series(dtype=float)).fillna(0).sum()) if "change" in calls else 0.0
        put_chg=float(puts.get("change",pd.Series(dtype=float)).fillna(0).sum()) if "change" in puts else 0.0
        call_iv=float(calls.get("impliedVolatility",pd.Series(dtype=float)).replace([float("inf"),-float("inf")],pd.NA).dropna().mean()) if "impliedVolatility" in calls else None
        put_iv=float(puts.get("impliedVolatility",pd.Series(dtype=float)).replace([float("inf"),-float("inf")],pd.NA).dropna().mean()) if "impliedVolatility" in puts else None
        # Max pain: strike with minimum total intrinsic payout from current OI.
        strikes=sorted(set(calls.strike.dropna().tolist())|set(puts.strike.dropna().tolist()))
        max_pain=None
        if strikes:
            best=[]
            for k in strikes:
                pain=float((((k-calls.strike).clip(lower=0))*calls.openInterest.fillna(0)).sum()+(((puts.strike-k).clip(lower=0))*puts.openInterest.fillna(0)).sum())
                best.append((pain,k))
            max_pain=min(best)[1]
        try:
            spot=float(t.fast_info.get("last_price"))
        except Exception:
            spot=float(strikes[len(strikes)//2]) if strikes else 0.0
        atm=min(strikes,key=lambda k:abs(k-spot)) if strikes else None
        near=[]
        if atm is not None:
            near_strikes=[k for k in strikes if abs(k-atm)<=1000][:12]
            for k in near_strikes:
                cr=calls[calls.strike==k]; pr=puts[puts.strike==k]
                near.append({"strike":float(k),"call_oi":float(cr.openInterest.fillna(0).sum()),"put_oi":float(pr.openInterest.fillna(0).sum()),"call_volume":float(cr.volume.fillna(0).sum()),"put_volume":float(pr.volume.fillna(0).sum())})
        try:
            expiry_days=max(0,(datetime.fromisoformat(str(exp)).date()-datetime.now(IST).date()).days)
        except Exception: expiry_days=None
        return {"available":True,"expiry":exp,"expiry_days":expiry_days,"expiry_regime":"EXPIRY_DAY" if expiry_days==0 else ("NEAR_EXPIRY" if expiry_days<=2 else "NORMAL_EXPIRY"),"spot":spot,"atm_strike":atm,"near_atm":near,
                "call_oi":call_oi,"put_oi":put_oi,"call_volume":float(calls.volume.fillna(0).sum()),"put_volume":float(puts.volume.fillna(0).sum()),
                "call_change_oi":call_chg,"put_change_oi":put_chg,"avg_call_iv":call_iv,"avg_put_iv":put_iv,"max_pain":max_pain}
    except Exception as e:
        return {"available":False,"reason":str(e)[:180]}

def source_status():
    return {
        "public_market_data":"Yahoo Finance public web adapter (no login)",
        "nse":"Reference/public published data; exchange endpoints may be unavailable without licensed/member access",
        "tradingview":"Reference source; no automated scraping",
        "bank_constituents":"Editable basket in sources.py; verify against current official Nifty Bank file",
        "options":"Best-effort public options adapter with OI/change-OI/IV/max-pain when available; missing data => WAIT",
        "nse_holiday_calendar":NSE_HOLIDAYS_2026,
        "generated_at":datetime.now(timezone.utc).isoformat()
    }
