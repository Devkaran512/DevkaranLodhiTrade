"""Free-plan-safe rolling event/outcome memory.

This is intentionally an in-process, bounded memory. Render Free can restart or
sleep a service, so this module never claims that its data is durable. It records
compact signal-change events, waits for later market observations, measures
outcomes, and reports factor-level historical evidence without silently changing
strategy weights from a tiny sample.
"""
from collections import deque
from datetime import datetime, timezone
import math

MAX_EVENTS = 180
MAX_COMPLETED = 500
_events = deque(maxlen=MAX_EVENTS)
_completed = deque(maxlen=MAX_COMPLETED)


def _num(v, default=0.0):
    try:
        x = float(v)
        return x if math.isfinite(x) else default
    except Exception:
        return default


def _now():
    return datetime.now(timezone.utc).isoformat()


def _direction(score):
    return 1 if _num(score) > 8 else (-1 if _num(score) < -8 else 0)


def _factor_snapshot(factors):
    return {str(f.get("name")): _num(f.get("score")) for f in (factors or []) if f.get("name")}


def _technical_snapshot(indicators):
    return {str(z.get("name")): _num(z.get("score")) for z in (indicators or []) if z.get("name")}


def _event_key(action, factors):
    vals = tuple(sorted((k, round(v / 20) * 20) for k, v in factors.items()))
    return action, vals


def _similarity(current_factors, current_action):
    """Return compact prior events with comparable action/factor direction."""
    out = []
    for e in reversed(_events):
        if e.get("action") != current_action:
            continue
        ef = e.get("factors", {})
        names = set(current_factors) & set(ef)
        if not names:
            continue
        matches = sum(1 for n in names if _direction(current_factors[n]) == _direction(ef[n]))
        sim = round(100.0 * matches / max(1, len(names)), 1)
        if sim >= 70:
            out.append({"event_id": e["id"], "time": e["time"], "similarity": sim,
                        "action": e["action"], "price": e["price"],
                        "signal_change": e.get("signal_change", False),
                        "outcomes": e.get("outcomes", {})})
        if len(out) >= 5:
            break
    return out


def _update_outcomes(current_price):
    """Complete horizons when a later observation is available."""
    now = datetime.now(timezone.utc).timestamp()
    horizons = {"5m": 300, "15m": 900, "30m": 1800, "1h": 3600, "1d": 86400, "3d": 259200, "5d": 432000}
    for e in list(_events):
        if e.get("completed_all"):
            continue
        try:
            age = now - float(e["epoch"])
        except Exception:
            continue
        entry = _num(e.get("price"), 0)
        if entry <= 0:
            continue
        for name, seconds in horizons.items():
            if name in e["outcomes"] or age < seconds:
                continue
            ret = (float(current_price) - entry) / entry * 100.0
            e["outcomes"][name] = {"return_pct": round(ret, 4),
                                    "direction_correct": (_direction(e.get("score")) == (1 if ret > 0 else -1) if _direction(e.get("score")) else None),
                                    "observed_at": _now()}
        if all(h in e["outcomes"] for h in horizons):
            e["completed_all"] = True
        # Move newly completed horizon records into the bounded completed journal.
        for h, result in list(e["outcomes"].items()):
            marker = f"{e['id']}:{h}"
            if marker not in e.setdefault("recorded", []):
                e["recorded"].append(marker)
                _completed.append({"event_id": e["id"], "horizon": h,
                                   "factors": e.get("factors", {}),
                                   "action": e.get("action"), "outcome": result})


def _factor_reliability():
    stats = {}
    for row in _completed:
        outcome = row.get("outcome", {})
        ret = _num(outcome.get("return_pct"))
        actual = 1 if ret > 0 else (-1 if ret < 0 else 0)
        if actual == 0:
            continue
        for name, score in row.get("factors", {}).items():
            d = _direction(score)
            if d == 0:
                continue
            s = stats.setdefault(name, {"samples": 0, "correct": 0})
            s["samples"] += 1
            if d == actual:
                s["correct"] += 1
    result = {}
    for name, s in stats.items():
        result[name] = {"samples": s["samples"],
                        "hit_rate_pct": round(100 * s["correct"] / max(1, s["samples"]), 1)}
    return result


def record_cycle(action, score, price, factors, indicators, signal_changed=False, drivers=None):
    """Update prior outcomes and record only meaningful new market states."""
    price = _num(price)
    if price <= 0:
        return snapshot(action, score, factors)
    _update_outcomes(price)
    fs = _factor_snapshot(factors)
    ins = _technical_snapshot(indicators)
    key = _event_key(action, fs)
    last = _events[-1] if _events else None
    meaningful = signal_changed
    if last:
        old = last.get("factors", {})
        meaningful = meaningful or any(abs(fs.get(k, 0) - old.get(k, 0)) >= 20 for k in fs)
        if last.get("action") != action:
            meaningful = True
    if meaningful or not last:
        epoch = datetime.now(timezone.utc).timestamp()
        event = {
            "id": f"E{int(epoch * 1000)}",
            "time": _now(), "epoch": epoch, "action": action, "score": _num(score),
            "price": price, "factors": fs, "indicators": ins,
            "signal_change": bool(signal_changed), "drivers": drivers or [],
            "outcomes": {}, "recorded": [], "key": str(key), "completed_all": False,
        }
        _events.append(event)
    return snapshot(action, score, fs)


def preview(action, factors):
    """Compute advisory learned direction from prior similar events before recording current state."""
    fs = _factor_snapshot(factors or []) if isinstance(factors, list) else (factors or {})
    similar = _similarity(fs, action) if action else []
    returns = []
    for e in similar:
        r = (e.get("outcomes", {}).get("30m") or {}).get("return_pct")
        if r is not None:
            returns.append(_num(r))
    if len(returns) < 5:
        return {"available": False, "sample_size": len(returns), "direction_score": 0.0, "reason": "Need at least 5 comparable completed 30m outcomes"}
    avg = sum(returns) / len(returns)
    score = max(-100.0, min(100.0, avg * 35.0))
    return {"available": True, "sample_size": len(returns), "direction_score": round(score, 1),
            "average_30m_return_pct": round(avg, 4), "similar_events": similar}


def snapshot(action=None, score=None, factors=None):
    fs = _factor_snapshot(factors or []) if isinstance(factors, list) else (factors or {})
    return {
        "available": bool(_events),
        "storage": "bounded in-process memory; not durable on Render Free restart/spin-down",
        "events_kept": len(_events),
        "completed_observations": len(_completed),
        "factor_reliability": _factor_reliability(),
        "similar_events": _similarity(fs, action) if action else [],
        "learning_policy": "Historical evidence is advisory; no factor is permanently ignored and weights are not auto-changed from small samples.",
        "last_event": _events[-1] if _events else None,
    }
