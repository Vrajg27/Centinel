"""
Phase 2: ThreatIntelProvider abstraction.

Every provider in threat_intel.py collapsed a lot of genuinely different
outcomes into the same `None` — "no API key configured", "network timeout",
"provider is down", "we don't have data on this indicator", and "bad
response we couldn't parse" all looked identical to a caller. That's the
exact thing SECURITY_FINDINGS.md and the target architecture call out:
provider failure must never be treated the same as "clean" — and if it's
also indistinguishable from "we don't have data," a caller can't even try
to make that distinction correctly.

This module defines the normalized six-state contract and one
`ThreatIntelProvider` per external service. Each provider re-implements its
HTTP call against the *same* endpoints, with the *same* parsing, that
threat_intel.py already used (verified line-for-line against the previous
implementation) — the only change is that outcomes are now classified into
a `ProviderResult` instead of collapsed to `dict | None`.

threat_intel.py's existing public functions (virustotal_url_report,
google_safe_browsing_check, etc.) now delegate to the providers defined
here and translate the result back into the old dict-or-None shape, so
every engine module that already calls e.g. `threat_intel.hibp_check(email)`
keeps working completely unchanged — this file is what those functions are
now implemented in terms of, not a parallel/competing code path.

State semantics:
    CONFIRMED    Provider actively found something notable (a detection, a
                 hit, a breach, a rated grade) about this exact indicator.
    CLEAN        Provider checked this exact indicator and affirmatively
                 found nothing notable (not the same as "we don't know" —
                 see UNKNOWN).
    UNKNOWN      Provider is reachable and configured, but has no record for
                 this exact indicator (e.g. VirusTotal has never analyzed
                 this URL) — genuinely different from CLEAN.
    UNAVAILABLE  Provider not configured (no API key / feature flag off),
                 unreachable, timed out, or rate-limited — a transient or
                 configuration gap, not a statement about the indicator.
    ERROR        Provider responded but the response was malformed/
                 unparseable, or a non-transient error (e.g. bad API key
                 rejected with 401/403).
    EXPIRED      Reserved for the Phase 6 caching layer, to distinguish a
                 stale cached result being served past its TTL from a fresh
                 one. Not produced anywhere in this file yet — there is no
                 cache-aware caller here to set it, and no code path claims
                 otherwise.
"""
import base64
import logging
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional

from .. import config
from . import http_client as _http

logger = logging.getLogger("centinel.providers")


class ProviderState(str, Enum):
    CONFIRMED = "CONFIRMED"
    CLEAN = "CLEAN"
    UNKNOWN = "UNKNOWN"
    UNAVAILABLE = "UNAVAILABLE"
    ERROR = "ERROR"
    EXPIRED = "EXPIRED"


@dataclass
class ProviderResult:
    state: ProviderState
    data: Optional[dict] = None
    provider: str = ""
    version: str = "1.0.0"
    fetched_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))
    cache_hit: bool = False


class ThreatIntelProvider:
    """Base class. Subclasses implement `lookup()` and set `name`/`version`."""
    name: str = "unknown"
    version: str = "1.0.0"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:  # pragma: no cover - interface
        raise NotImplementedError

    def _result(self, state: ProviderState, data: Optional[dict] = None) -> ProviderResult:
        return ProviderResult(state=state, data=data, provider=self.name, version=self.version)


class VirusTotalUrlProvider(ThreatIntelProvider):
    name = "virustotal_url"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.VIRUSTOTAL_API_KEY:
            return self._result(ProviderState.UNAVAILABLE)
        url_id = base64.urlsafe_b64encode(indicator.encode()).decode().strip("=")
        resp = _http.get(
            f"https://www.virustotal.com/api/v3/urls/{url_id}",
            headers={"x-apikey": config.VIRUSTOTAL_API_KEY},
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code == 404:
            return self._result(ProviderState.UNKNOWN)  # VT has never analyzed this URL
        if resp.status_code in (401, 403):
            return self._result(ProviderState.ERROR)
        if resp.status_code == 429:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            attrs = resp.json()["data"]["attributes"]
            stats = attrs.get("last_analysis_stats", {})
            data = {
                "malicious": stats.get("malicious", 0),
                "suspicious": stats.get("suspicious", 0),
                "harmless": stats.get("harmless", 0),
                "undetected": stats.get("undetected", 0),
                "reputation": attrs.get("reputation", 0),
                "total_engines": sum(stats.values()) or 1,
            }
        except (KeyError, ValueError, TypeError) as e:
            logger.info("Could not parse VirusTotal URL response: %s", e)
            return self._result(ProviderState.ERROR)
        state = ProviderState.CONFIRMED if (data["malicious"] or data["suspicious"]) else ProviderState.CLEAN
        return self._result(state, data)


class VirusTotalFileProvider(ThreatIntelProvider):
    name = "virustotal_file"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.VIRUSTOTAL_API_KEY:
            return self._result(ProviderState.UNAVAILABLE)
        resp = _http.get(
            f"https://www.virustotal.com/api/v3/files/{indicator}",
            headers={"x-apikey": config.VIRUSTOTAL_API_KEY},
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code == 404:
            return self._result(ProviderState.UNKNOWN)
        if resp.status_code in (401, 403):
            return self._result(ProviderState.ERROR)
        if resp.status_code == 429:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            attrs = resp.json()["data"]["attributes"]
            stats = attrs.get("last_analysis_stats", {})
            data = {
                "malicious": stats.get("malicious", 0),
                "suspicious": stats.get("suspicious", 0),
                "harmless": stats.get("harmless", 0),
                "undetected": stats.get("undetected", 0),
                "total_engines": sum(stats.values()) or 1,
                "type_description": attrs.get("type_description"),
            }
        except (KeyError, ValueError, TypeError) as e:
            logger.info("Could not parse VirusTotal file response: %s", e)
            return self._result(ProviderState.ERROR)
        state = ProviderState.CONFIRMED if (data["malicious"] or data["suspicious"]) else ProviderState.CLEAN
        return self._result(state, data)


class GoogleSafeBrowsingProvider(ThreatIntelProvider):
    name = "google_safe_browsing"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.GOOGLE_SAFE_BROWSING_API_KEY:
            return self._result(ProviderState.UNAVAILABLE)
        resp = _http.post(
            f"https://safebrowsing.googleapis.com/v4/threatMatches:find?key={config.GOOGLE_SAFE_BROWSING_API_KEY}",
            json_body={
                "client": {"clientId": "centinel", "clientVersion": "1.1.0"},
                "threatInfo": {
                    "threatTypes": ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"],
                    "platformTypes": ["ANY_PLATFORM"],
                    "threatEntryTypes": ["URL"],
                    "threatEntries": [{"url": indicator}],
                },
            },
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code in (401, 403):
            return self._result(ProviderState.ERROR)
        if resp.status_code == 429:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            matches = resp.json().get("matches", [])
        except (ValueError, TypeError) as e:
            logger.info("Could not parse Google Safe Browsing response: %s", e)
            return self._result(ProviderState.ERROR)
        if not matches:
            return self._result(ProviderState.CLEAN)
        threat_types = sorted({m.get("threatType", "UNKNOWN") for m in matches})
        return self._result(ProviderState.CONFIRMED, {"threat_types": threat_types})


class AbuseIPDBProvider(ThreatIntelProvider):
    name = "abuseipdb"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.ABUSEIPDB_API_KEY:
            return self._result(ProviderState.UNAVAILABLE)
        resp = _http.get(
            "https://api.abuseipdb.com/api/v2/check",
            headers={"Key": config.ABUSEIPDB_API_KEY, "Accept": "application/json"},
            params={"ipAddress": indicator, "maxAgeInDays": 90},
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code in (401, 403):
            return self._result(ProviderState.ERROR)
        if resp.status_code == 429:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            d = resp.json()["data"]
            data = {
                "abuse_confidence_score": d.get("abuseConfidenceScore", 0),
                "total_reports": d.get("totalReports", 0),
                "country_code": d.get("countryCode"),
                "isp": d.get("isp"),
                "domain": d.get("domain"),
                "usage_type": d.get("usageType"),
            }
        except (KeyError, ValueError, TypeError) as e:
            logger.info("Could not parse AbuseIPDB response: %s", e)
            return self._result(ProviderState.ERROR)
        state = ProviderState.CONFIRMED if data["abuse_confidence_score"] else ProviderState.CLEAN
        return self._result(state, data)


class IPGeolocationProvider(ThreatIntelProvider):
    """Enrichment only — not a threat verdict source. Included in the
    abstraction for consistent health-check/observability treatment
    (Phase 15), not because a geolocation result implies maliciousness."""
    name = "ip_geolocation"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.ENABLE_IP_GEOLOCATION:
            return self._result(ProviderState.UNAVAILABLE)
        resp = _http.get(
            f"http://ip-api.com/json/{indicator}",
            params={"fields": "status,country,regionName,city,isp,org,as,lat,lon,query"},
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            d = resp.json()
        except (ValueError, TypeError) as e:
            logger.info("Could not parse ip-api.com response: %s", e)
            return self._result(ProviderState.ERROR)
        if d.get("status") != "success":
            return self._result(ProviderState.UNKNOWN)
        data = {
            "country": d.get("country"),
            "region": d.get("regionName"),
            "city": d.get("city"),
            "isp": d.get("isp"),
            "org": d.get("org"),
            "asn": d.get("as"),
            "latitude": d.get("lat"),
            "longitude": d.get("lon"),
        }
        return self._result(ProviderState.CONFIRMED, data)


class HIBPProvider(ThreatIntelProvider):
    name = "hibp"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.HIBP_API_KEY:
            return self._result(ProviderState.UNAVAILABLE)
        resp = _http.get(
            f"https://haveibeenpwned.com/api/v3/breachedaccount/{indicator}",
            headers={"hibp-api-key": config.HIBP_API_KEY, "User-Agent": "Centinel-Breach-Checker"},
            params={"truncateResponse": "false"},
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code == 404:
            return self._result(ProviderState.CLEAN, {"breaches": []})
        if resp.status_code in (401, 403):
            return self._result(ProviderState.ERROR)
        if resp.status_code == 429:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            breaches = resp.json()
            data = {
                "breaches": [
                    {"name": b.get("Name"), "title": b.get("Title"), "breach_date": b.get("BreachDate"),
                     "data_classes": b.get("DataClasses", [])}
                    for b in breaches
                ]
            }
        except (ValueError, TypeError) as e:
            logger.info("Could not parse HIBP response: %s", e)
            return self._result(ProviderState.ERROR)
        return self._result(ProviderState.CONFIRMED, data)


class SSLLabsProvider(ThreatIntelProvider):
    """Grade/assessment data, not a malicious/clean verdict — the calling
    engine (ssl_analysis.py) turns the grade into its own severity."""
    name = "ssl_labs"

    def lookup(self, indicator: str, **kwargs: Any) -> ProviderResult:
        if not config.ENABLE_SSL_LABS:
            return self._result(ProviderState.UNAVAILABLE)
        resp = _http.get(
            "https://api.ssllabs.com/api/v3/analyze",
            params={"host": indicator, "fromCache": "on", "maxAge": "24"},
        )
        if resp is None:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code == 429:
            return self._result(ProviderState.UNAVAILABLE)
        if resp.status_code != 200:
            return self._result(ProviderState.ERROR)
        try:
            d = resp.json()
        except (ValueError, TypeError) as e:
            logger.info("Could not parse SSL Labs response: %s", e)
            return self._result(ProviderState.ERROR)
        if d.get("status") != "READY":
            return self._result(ProviderState.UNKNOWN)  # no cached assessment available
        endpoints = d.get("endpoints", [])
        grade = endpoints[0].get("grade") if endpoints else None
        if not grade:
            return self._result(ProviderState.UNKNOWN)
        return self._result(ProviderState.CONFIRMED, {"grade": grade})


# Registry of every provider instance, keyed by name — used for future
# health-check/observability endpoints (Phase 15) and for tests that need
# to iterate "every known provider" without hardcoding the list twice.
REGISTRY: dict[str, ThreatIntelProvider] = {
    p.name: p for p in (
        VirusTotalUrlProvider(),
        VirusTotalFileProvider(),
        GoogleSafeBrowsingProvider(),
        AbuseIPDBProvider(),
        IPGeolocationProvider(),
        HIBPProvider(),
        SSLLabsProvider(),
    )
}
