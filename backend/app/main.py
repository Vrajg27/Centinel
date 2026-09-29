import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.httpsredirect import HTTPSRedirectMiddleware
from slowapi.middleware import SlowAPIMiddleware

from . import models
from .database import engine, run_lightweight_migrations
from .errors import install_error_handlers
from .limiter import limiter
from .request_context import RequestIDMiddleware
from .routers import (
    auth_router, scan_router, history_router,
    analytics_router, notification_router, report_router, admin_router,
)

run_lightweight_migrations()
models.Base.metadata.create_all(bind=engine)

API_V1_PREFIX = "/api/v1"

app = FastAPI(
    title="Centinel API",
    description="AI-powered cybersecurity threat detection & explanation platform (local build).",
    version="1.1.0",
)
app.state.limiter = limiter
install_error_handlers(app)  # also registers the RateLimitExceeded handler,
                              # now returning the standardized error envelope
                              # instead of slowapi's default plain response.
app.add_middleware(SlowAPIMiddleware)
# Runs after routing/error handling in the middleware stack (added last =
# outermost), so it sees — and stamps an X-Request-ID header onto — every
# response this app produces, success or error alike.
app.add_middleware(RequestIDMiddleware)

# Off by default so local development over plain http://localhost:8000
# keeps working with zero config. Turn on once this is deployed behind a
# real domain with a TLS certificate (e.g. behind nginx/Caddy or a managed
# load balancer) so any stray http:// request gets redirected to https://.
if os.environ.get("CENTINEL_FORCE_HTTPS", "false").lower() == "true":
    app.add_middleware(HTTPSRedirectMiddleware)

app.add_middleware(
    CORSMiddleware,
    # Configurable via CENTINEL_CORS_ORIGINS (comma-separated) — defaults to
    # "*" so local dev (the Android emulator, a loaded-unpacked extension,
    # `python -m http.server` for quick testing, etc.) keeps working with
    # zero config. Tighten this to your extension's real origin and app's
    # domain before deploying anywhere reachable by untrusted users.
    #
    # allow_credentials is intentionally False (not the CORSMiddleware
    # default's opposite — it's explicit here because leaving it True
    # while allow_origins is "*" was a real misconfiguration: browsers
    # (and Starlette's own CORSMiddleware, to accommodate them) treat
    # `origins=["*"]` + `credentials=True` by echoing back whatever Origin
    # the request sent rather than truly restricting it, which defeats the
    # purpose of an origin allowlist. It's also unnecessary: every client
    # here (Android app, browser extension) authenticates with a Bearer
    # token in the Authorization header, never cookies, so CORS
    # "credentials" mode (which governs cookies/HTTP-auth, not custom
    # headers) was never actually needed.
    allow_origins=[o.strip() for o in os.environ.get("CENTINEL_CORS_ORIGINS", "*").split(",") if o.strip()],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

_ROUTERS = (
    auth_router.router,
    scan_router.router,
    history_router.router,
    analytics_router.router,
    notification_router.router,
    report_router.router,
    admin_router.router,
)

# Phase 2: every router now lives under /api/v1/*, per the target
# architecture. The existing Android app and browser extension both call
# the *unprefixed* paths (/auth/login, /scan/url, etc.) and are not updated
# until Phase 12/13 — so each router is mounted TWICE, at its legacy
# unprefixed path and again under /api/v1, both routes backed by the exact
# same router/endpoint objects (not a duplicate implementation to keep in
# sync). This is intentionally temporary back-compat scaffolding, not a
# permanent dual-API surface: once the extension and Android client are
# migrated to call /api/v1/* (Phase 12/13), the unprefixed
# `app.include_router(router)` line below should be deleted.
for router in _ROUTERS:
    app.include_router(router)                       # legacy, unprefixed
    app.include_router(router, prefix=API_V1_PREFIX)  # target


@app.get("/", tags=["Health"])
def health():
    return {"status": "ok", "service": "Centinel API", "mode": "offline/heuristic (no external API keys configured)"}
