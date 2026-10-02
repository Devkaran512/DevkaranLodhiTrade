# DevkaranLodhiTrade — Public Data Prediction v5 UI

Prediction-only BANKNIFTY Android client with a clean, responsive native UI.

## UI v5
- Clean card-based layout
- Large, readable current signal
- Responsive weighted trade-level cards
- Scrollable layout for small screens
- No overlapping text
- Clear backend connection section
- Live connection indicator
- Backend URL is remembered; API key is not stored
- Signal polling every 60 seconds after Start Live Signal

## Backend
Set the Render Web Service URL and the generated `BACKEND_API_KEY` in the Android app.

This app does not place trades and does not require Groww login, token, or order API.


## v6 UI/session update
- Header respects Android system-bar insets so `DevkaranLodhiTrade` does not overlap the status bar.
- On NSE holidays/weekends, the backend labels the result as a **next trading session** setup.
- If intraday public candles are unavailable, the backend attempts a longer daily-history fallback; it never fabricates candles.
- 02-Oct-2026 is an NSE holiday (Mahatma Gandhi Jayanti); 03-Oct-2026 is Saturday, so the next regular session is Monday 05-Oct-2026.
