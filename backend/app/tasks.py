"""
Background counterparts to the synchronous /scan/url and /scan/website
endpoints. Each task opens its own short-lived DB session — Celery tasks
run in a separate worker process from the FastAPI app, so they can't reuse
the request-scoped `get_db` dependency; SQLAlchemy sessions aren't safe to
share across processes anyway.

Reuses the exact same analysis engines and persistence/caching helpers as
the synchronous endpoints (scan_helpers.py, redis_client.py) — a task and
its synchronous counterpart produce identically-shaped results and write
identically-shaped rows to the Scans table. The only difference is where
the work happens and how the caller gets the result back.
"""
from .celery_app import celery_app
from .database import SessionLocal
from . import redis_client
from .engine import url_analysis, website_analysis
from .scan_helpers import persist_scan, scan_to_out, assessment_to_dict, dict_to_assessment, CACHE_TTL_SECONDS


@celery_app.task(name="centinel.scan_url_task", bind=True, max_retries=1)
def scan_url_task(self, user_id: str, url: str) -> dict:
    cache_key = f"scan:url:{url.strip().lower()}"
    cached = redis_client.cache_get(cache_key)
    if cached:
        assessment = dict_to_assessment(cached)
    else:
        assessment = url_analysis.analyze_url(url)
        redis_client.cache_set(cache_key, assessment_to_dict(assessment), CACHE_TTL_SECONDS)

    db = SessionLocal()
    try:
        scan = persist_scan(db, user_id, "url", url, {"url": url}, assessment)
        return scan_to_out(scan).model_dump(mode="json")
    finally:
        db.close()


@celery_app.task(name="centinel.scan_website_task", bind=True, max_retries=1)
def scan_website_task(self, user_id: str, url: str) -> dict:
    cache_key = f"scan:website:{url.strip().lower()}"
    cached = redis_client.cache_get(cache_key)
    if cached:
        assessment = dict_to_assessment(cached["assessment"])
        extracted = cached["extracted"]
    else:
        assessment, extracted = website_analysis.analyze_website(url)
        redis_client.cache_set(
            cache_key, {"assessment": assessment_to_dict(assessment), "extracted": extracted}, CACHE_TTL_SECONDS
        )

    db = SessionLocal()
    try:
        scan = persist_scan(db, user_id, "website", url, {"url": url}, assessment)
        result = scan_to_out(scan).model_dump(mode="json")
        result["extracted"] = extracted
        return result
    finally:
        db.close()
