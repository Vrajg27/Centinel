"""
Celery app for offloading the slowest scans (Website Scanner's live page
fetch + redirect following, URL Scanner's WHOIS lookup) to a background
worker instead of blocking the request/response cycle.

This is opt-in: none of the /scan/* endpoints require Celery to be running.
Every scan still works exactly as it did in Phases 1-3, synchronously, by
default. Celery only comes into play for the new "async" variants of the
slow endpoints (POST /scan/url/async, POST /scan/website/async), which
return a task ID immediately; the client then polls
GET /scan/tasks/{task_id} for the result.

Needs a message broker — Redis (already optional infra from this same
phase, see redis_client.py) doubles as the broker here. Both read
CENTINEL_REDIS_URL, so setting it once turns on caching AND background
tasks together.

Importing this module requires the `celery` package to be installed.
Callers that want to treat "Celery not installed" as a soft/optional
feature (rather than a hard crash) should import it inside a try/except —
see the top of routers/scan_router.py for that guard.
"""
import os
from celery import Celery

REDIS_URL = os.environ.get("CENTINEL_REDIS_URL", "redis://localhost:6379/0")

celery_app = Celery("centinel", broker=REDIS_URL, backend=REDIS_URL)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    result_expires=3600,  # task results are kept for 1 hour, then GET /scan/tasks/{id} 404s
    task_track_started=True,
    # Fail fast rather than hanging a worker forever if the broker/backend
    # is unreachable when a task actually tries to run.
    broker_connection_timeout=5,
)
