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

## V19: Free-plan-safe event memory and outcome learning

V19 adds a bounded Market Event Memory and Outcome Learning layer. It records meaningful signal changes, factor states, and later 5m/15m/30m/1h/1d/3d/5d market outcomes while the Render process remains alive. It reports factor-level historical hit rates and comparable prior setups.

Important: Render Free web services can restart/spin down, so this memory is intentionally **not treated as durable storage**. No prediction depends on data surviving a restart. The learning layer is advisory and does not permanently ignore any factor or auto-change weights from a small sample.

## V20 market-analysis additions
- Market structure: breakout/retest and recent swing structure.
- Volatility regime: realized range regime plus VIX change.
- Gap and time-of-day regime context.
- Structured macro/banking/geopolitical event-risk layer.
- BANKNIFTY relative strength versus NIFTY.
- Expanded public option-chain context: ATM strike, near-ATM OI/volume, expiry regime, and Max Pain.
- All new layers are supporting evidence only; no single factor can force a CALL/PUT signal.


## V24 review-driven improvements
- Market Intelligence UI is grouped into Market State, Signal Evidence, Risk & Decision Guard, History & Learning, and Supporting Details.
- Public option-chain `change` is treated as option-price change, never as change in OI.
- Historical change-in-OI remains explicitly unavailable unless a source provides prior OI snapshots.
- Data Quality exposes per-factor availability and calibration limitations.
- Learning factor reliability is marked PROVISIONAL/DEVELOPING/STABLE by sample count and does not automatically alter weights.
- Backtest messaging explicitly distinguishes reconstructable historical factors from live-only factors.
