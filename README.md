# DevkaranLodhiTrade — Production BANKNIFTY Prediction Engine

Prediction-only BANKNIFTY Android client + FastAPI Render backend. **Demo mode is not included in this production build.** No Groww login, order placement, or broker execution.

## Production engine
- Live/public BANKNIFTY candles with safe fallback (never fabricates data)
- EMA 9/20/21/50/200, VWAP, RSI, MACD, ATR, ADX, Stochastic, Bollinger bands, ROC, OBV
- Support/resistance and volume expansion
- Banking constituent breadth
- Public global-market context: US/Asia/VIX/crude/DXY/USDINR where available
- Public options context: call/put OI, volume, change-OI, IV and max-pain where the public adapter exposes them
- Best-effort NSE FII/FPI + DII report adapter; unavailable data is explicitly marked unavailable
- Public news/event classification: RBI/banking, macro, US rates, crude, FX and geopolitics
- Historical shock/crash knowledge base and similarity context
- Regime-change / reversal-risk checks
- Explainable evidence, warnings and invalidation context
- Multiple horizon labels and expected validity window
- 1-year historical technical backtest endpoint and in-app backtest button
- Manual position monitoring: CALL/PUT, strike, entry premium, quantity, underlying invalidation/target; thesis-reversal alert
- Strong-evidence sound/vibration-style alert while the app is active
- API URL and API key reconnect; API key is encrypted with Android Keystore before local persistence

## Important data limitation
This is a public-data decision-support system, not a guaranteed prediction system. Some exchange-grade real-time data, participant positioning, option-chain fields, news feeds, or historical series may be unavailable from public endpoints. The engine returns `WAIT`/unavailable states instead of inventing values. True continuous option-premium P&L requires a live option-price feed; Groww execution/login is intentionally not implemented.

## Render
`render.yaml` keeps the existing FastAPI service. The Android app calls `/signal`; `/analysis` exposes the richer analysis object; `/backtest` runs the historical technical test.

## Android
Set the actual Render HTTPS URL and generated `BACKEND_API_KEY`, then tap **START LIVE SIGNAL**. The app polls every 60 seconds while active.


## UI & Alert Settings update (v8)
- Premium dark 3D-depth trading dashboard with signal-state colors.
- Alert Settings: master switch, ringtone picker, alert volume, test alarm, vibration patterns, event toggles, evidence threshold, market-hours/all-day/quiet-hours schedule.
- Alert preferences are stored locally on the device.
- Production/live engine only; no demo mode.
