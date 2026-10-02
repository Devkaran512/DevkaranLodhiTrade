import os
from dotenv import load_dotenv
load_dotenv()
BACKEND_API_KEY=os.getenv("BACKEND_API_KEY","change-this-key")
MIN_SIGNAL_SCORE=float(os.getenv("MIN_SIGNAL_SCORE","72"))
LOOKBACK_PERIOD=os.getenv("LOOKBACK_PERIOD","5d")
AUTO_REFRESH_SECONDS=int(os.getenv("AUTO_REFRESH_SECONDS","60"))
GLOBAL_SYMBOLS=[x.strip() for x in os.getenv("GLOBAL_SYMBOLS","^GSPC,^IXIC,^DJI,^VIX,^N225,^HSI,GC=F,CL=F,DX-Y.NYB,INR=X").split(",") if x.strip()]
