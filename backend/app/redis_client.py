"""
Caches the *results* of expensive scan analyses (WHOIS lookups, live website
fetches, TLS handshakes) so repeated scans of the same target don't redo
that network work. This does NOT cache per-user scan history — every scan
still gets its own row in the Scans table for the requesting user, exactly
as before; only the underlying analysis (the slow part) is reused.

Follows the same graceful-degradation pattern as the rest of the engine
(whois/tldextract/httpx in url_analysis.py etc.): if the `redis` package
isn't installed, or the Redis server isn't reachable, every function here
just silently no-ops — every scan runs at full (uncached) speed rather than
erroring out. Caching is a performance optimization, not a correctness
requirement, so it should never be able to break a scan.
"""
import os
import json
import logging
from typing import Optional

logger = logging.getLogger("centinel.cache")

try:
    import redis
except ImportError:  # pragma: no cover - optional dependency
    redis = None

REDIS_URL = os.environ.get("CENTINEL_REDIS_URL", "redis://localhost:6379/0")
CACHE_ENABLED = os.environ.get("CENTINEL_ENABLE_CACHE", "true").lower() != "false"

_client = None
_connection_attempted = False


def _get_client():
    """Lazily connects on first use (not at import time) so importing this
    module never fails or blocks even if Redis isn't running — only the
    first actual cache operation pays that cost, and every call after a
    failed attempt short-circuits immediately rather than retrying."""
    global _client, _connection_attempted
    if not CACHE_ENABLED or redis is None:
        return None
    if _connection_attempted:
        return _client

    _connection_attempted = True
    try:
        client = redis.from_url(REDIS_URL, socket_connect_timeout=1.5, socket_timeout=1.5)
        client.ping()
        _client = client
        logger.info("Connected to Redis cache at %s", REDIS_URL)
    except Exception as e:
        logger.warning("Redis cache unavailable (%s) — running uncached.", e)
        _client = None
    return _client


def cache_get(key: str) -> Optional[dict]:
    client = _get_client()
    if client is None:
        return None
    try:
        raw = client.get(key)
        return json.loads(raw) if raw else None
    except Exception as e:
        logger.warning("Redis GET failed for %s: %s", key, e)
        return None


def cache_set(key: str, value: dict, ttl_seconds: int) -> None:
    client = _get_client()
    if client is None:
        return
    try:
        client.setex(key, ttl_seconds, json.dumps(value, default=str))
    except Exception as e:
        logger.warning("Redis SET failed for %s: %s", key, e)
