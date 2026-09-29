"""
Bounded-timeout HTTP GET/POST helpers shared by every ThreatIntelProvider
(providers.py) and by the OpenRouter AI client (threat_intel.py).

Split out of threat_intel.py in Phase 2 purely to avoid a circular import
(providers.py needs these helpers; threat_intel.py's public functions now
delegate to providers.py) — the implementation itself is unchanged from
what threat_intel.py had.

Never raises: every third-party HTTP call in this app is best-effort, and a
network hiccup should degrade a single signal, not fail a whole scan.
"""
import logging

from .. import config

logger = logging.getLogger("centinel.http_client")

try:
    import httpx
except ImportError:  # pragma: no cover - optional dependency
    httpx = None

TIMEOUT = config.THREAT_INTEL_TIMEOUT_SECONDS


def get(url: str, headers: dict = None, params: dict = None):
    if httpx is None:
        return None
    try:
        return httpx.get(url, headers=headers or {}, params=params or {}, timeout=TIMEOUT)
    except Exception as e:
        logger.info("GET %s failed: %s", url, e)
        return None


def post(url: str, headers: dict = None, json_body: dict = None):
    if httpx is None:
        return None
    try:
        return httpx.post(url, headers=headers or {}, json=json_body or {}, timeout=TIMEOUT)
    except Exception as e:
        logger.info("POST %s failed: %s", url, e)
        return None
