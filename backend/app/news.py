"""Best-effort public news/event context.
No login or private data. Failure is represented as unavailable, never fabricated."""
from datetime import datetime, timezone
import xml.etree.ElementTree as ET
import requests
from urllib.parse import quote_plus

QUERIES = [
    "RBI banking India", "India inflation GDP jobs", "Federal Reserve US rates",
    "India crude oil rupee dollar", "Bank Nifty banking results", "geopolitics markets India"
]

def _classify(title: str) -> str:
    t = title.lower()
    if any(k in t for k in ("rbi", "banking regulation", "bank ownership")): return "RBI/BANKING"
    if any(k in t for k in ("inflation", "cpi", "gdp", "jobs", "employment")): return "MACRO"
    if any(k in t for k in ("fed", "federal reserve", "rate hike", "rate cut", "yield")): return "US RATES"
    if any(k in t for k in ("crude", "oil", "brent")): return "CRUDE"
    if any(k in t for k in ("war", "iran", "israel", "ukraine", "geopolit")): return "GEOPOLITICS"
    if any(k in t for k in ("bank nifty", "hdfc", "icici", "sbi", "axis bank", "kotak")): return "BANKING"
    if any(k in t for k in ("rupee", "inr", "dollar", "dxy")): return "FX"
    return "MARKET"

def fetch_news(limit=24):
    rows=[]
    for q in QUERIES:
        url="https://news.google.com/rss/search?q=" + quote_plus(q + " when:2d") + "&hl=en-IN&gl=IN&ceid=IN:en"
        try:
            r=requests.get(url, timeout=8, headers={"User-Agent":"DevkaranLodhiTrade/production"})
            r.raise_for_status()
            root=ET.fromstring(r.text)
            for item in root.findall("./channel/item"):
                title=(item.findtext("title") or "").strip()
                link=(item.findtext("link") or "").strip()
                pub=(item.findtext("pubDate") or "").strip()
                if title: rows.append({"title":title,"link":link,"published":pub,"category":_classify(title)})
        except Exception:
            continue
    seen=set(); out=[]
    for x in rows:
        key=x["title"].lower()
        if key in seen: continue
        seen.add(key); out.append(x)
        if len(out)>=limit: break
    return {"available":bool(out),"items":out,"generated_at":datetime.now(timezone.utc).isoformat()}
