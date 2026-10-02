"""Best-effort institutional-flow adapter using NSE's published FII/FPI & DII report page.
If the exchange changes the page or blocks automated access, the result is unavailable rather than guessed."""
from datetime import datetime, timezone
import pandas as pd
import requests

URL="https://www.nseindia.com/reports/fii-dii"
HEADERS={"User-Agent":"Mozilla/5.0 (DevkaranLodhiTrade research client)","Accept":"text/html,application/xhtml+xml"}

def flow_snapshot():
    try:
        r=requests.get(URL,headers=HEADERS,timeout=12)
        r.raise_for_status()
        tables=pd.read_html(r.text)
        rows=[]
        for t in tables:
            t=t.copy(); t.columns=[str(c).strip() for c in t.columns]
            text=" ".join(map(str,t.columns)).lower()+" "+t.astype(str).to_string().lower()
            if "fii" in text and "dii" in text:
                rows.append(t)
        if not rows: return {"available":False,"reason":"NSE FII/DII table not found"}
        t=rows[0]
        raw=t.astype(str).to_dict(orient="records")
        return {"available":True,"source":URL,"rows":raw[:25],"generated_at":datetime.now(timezone.utc).isoformat()}
    except Exception as e:
        return {"available":False,"reason":str(e)[:180],"source":URL}
