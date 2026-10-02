"""Small, transparent historical event knowledge base used for similarity context.
It is deliberately qualitative until enough clean OHLC/options/flow history is available for
proper out-of-sample backtesting. The engine never treats these rows as guaranteed forecasts."""
EVENTS = [
 {"date":"2008-09-15","name":"Global Financial Crisis / Lehman","regime":"risk-off","drivers":["credit stress","global deleveraging","banking stress"],"signals":["high volatility","risk-off global markets","financial-sector weakness"]},
 {"date":"2013-08-28","name":"India rupee / taper shock","regime":"risk-off","drivers":["US taper expectations","rupee weakness","capital outflows"],"signals":["USDINR spike","foreign outflows","rate/yield pressure"]},
 {"date":"2016-11-09","name":"Demonetisation shock","regime":"event-shock","drivers":["domestic policy shock","liquidity disruption"],"signals":["banking volatility","volume spike","risk repricing"]},
 {"date":"2020-03-12","name":"COVID global crash","regime":"risk-off","drivers":["pandemic shock","global shutdown","oil shock"],"signals":["VIX spike","global selloff","volume expansion"]},
 {"date":"2020-03-24","name":"COVID panic low / policy response","regime":"capitulation","drivers":["extreme risk aversion","policy response"],"signals":["extreme volatility","oversold conditions","large volume"]},
 {"date":"2022-02-24","name":"Russia-Ukraine shock","regime":"risk-off","drivers":["geopolitical shock","energy prices","inflation pressure"],"signals":["crude spike","global risk-off","volatility expansion"]},
 {"date":"2022-10-13","name":"Global rates / inflation stress","regime":"macro-risk","drivers":["inflation","rate expectations","strong dollar"],"signals":["bond-yield pressure","DXY strength","equity volatility"]},
 {"date":"2024-04-15","name":"Middle East risk / oil repricing","regime":"event-shock","drivers":["geopolitical risk","oil sensitivity"],"signals":["crude move","defensive positioning","volatility"]},
]

def _distance(features, event):
    score=0
    tags=set(features.get("tags",[]))
    for s in event["signals"]:
        if any(w in tags for w in s.split()): score += 1
    if features.get("vix_spike") and "high volatility" in event["signals"]: score += 2
    if features.get("oil_shock") and "crude spike" in event["signals"]: score += 2
    if features.get("risk_off") and event["regime"] in ("risk-off","macro-risk"): score += 2
    return score

def similar_events(features, limit=5):
    rows=[dict(e, similarity=_distance(features,e)) for e in EVENTS]
    rows.sort(key=lambda x:x["similarity"], reverse=True)
    return rows[:limit]
