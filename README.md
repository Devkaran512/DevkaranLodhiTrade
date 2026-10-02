# DevkaranLodhiTrade — Public Data Prediction v1

Prediction-only BANKNIFTY Android app + FastAPI backend. **Groww is completely removed.** No login, broker token, order placement, deposits, positions, or execution.

## Data design
- Public Yahoo Finance web adapter through `yfinance` for BANKNIFTY candles, bank constituent candles, global indices/commodities and best-effort public option-chain data.
- NSE is treated as an official reference/permitted-data source; this project does **not** scrape or bypass NSE protections/terms.
- TradingView is treated as a reference source; this project does **not** scrape TradingView.
- Bank-constituent basket is editable in `backend/app/sources.py` and should be synchronized with the latest official Nifty Bank constituent file before production use.

## Analysis
Technical indicators include EMA 9/21/50/200, SMA20, RSI, ATR, MACD, ADX, stochastic, VWAP, Bollinger Bands, ROC, momentum and OBV. The signal also cross-checks bank breadth and public option positioning when available. Missing/conflicting data results in WAIT rather than fabricated values.

## Run backend
```bash
cd backend
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# Linux/WSL: source .venv/bin/activate
pip install -r requirements.txt
copy .env.example .env   # Windows
# cp .env.example .env   # Linux/WSL
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Test: `GET /health`, then call `/signal` with header `X-API-Key`.

## Android
The GitHub Actions workflow builds a debug APK with the known working Android SDK setup. Set the backend URL to the computer/server reachable from the phone, e.g. `http://192.168.1.10:8000`.

## Important
This is a prediction/research system, not a profit guarantee. Public web data availability, latency, symbol mappings and website terms can change. Do not use a public web adapter as a substitute for a licensed real-time market-data feed if production-grade latency/reliability is required.
