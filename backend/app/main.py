import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.httpsredirect import HTTPSRedirectMiddleware
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.middleware import SlowAPIMiddleware

from . import models
from .database import engine
from .limiter import limiter
from .routers import (
    auth_router, scan_router, history_router,
    analytics_router, notification_router, report_router, admin_router,
)

models.Base.metadata.create_all(bind=engine)

app = FastAPI(
    title="Centinel API",
    description="AI-powered cybersecurity threat detection & explanation platform (local build).",
    version="1.0.0",
)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.add_middleware(SlowAPIMiddleware)

# Off by default so local development over plain http://localhost:8000
# keeps working with zero config. Turn on once this is deployed behind a
# real domain with a TLS certificate (e.g. behind nginx/Caddy or a managed
# load balancer) so any stray http:// request gets redirected to https://.
if os.environ.get("CENTINEL_FORCE_HTTPS", "false").lower() == "true":
    app.add_middleware(HTTPSRedirectMiddleware)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # tighten this to your extension/app origins in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth_router.router)
app.include_router(scan_router.router)
app.include_router(history_router.router)
app.include_router(analytics_router.router)
app.include_router(notification_router.router)
app.include_router(report_router.router)
app.include_router(admin_router.router)


@app.get("/", tags=["Health"])
def health():
    return {"status": "ok", "service": "Centinel API", "mode": "offline/heuristic (no external API keys configured)"}
