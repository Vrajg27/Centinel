"""
Real threat-intelligence provider clients.

Phase 2 update: every function below now delegates to a `ThreatIntelProvider`
in providers.py (the new common interface with a normalized six-state
result: CONFIRMED/CLEAN/UNKNOWN/UNAVAILABLE/ERROR/EXPIRED — see that
module's docstring). This file's public functions are kept, with their
exact original signatures and return contract, purely so every existing
caller (url_analysis.py, file_analysis.py, header_analysis.py,
breach_check.py, ssl_analysis.py) keeps working completely unchanged:

  - Returns a plain dict on a successful, meaningful lookup
    (provider state CONFIRMED, or CLEAN where the caller already
    distinguished "checked and clean" from "no data" — currently only
    hibp_check does this, matching the pre-Phase-2 behavior exactly).
  - Returns None for every other state (UNKNOWN, UNAVAILABLE, ERROR,
    EXPIRED) — never raises.

New code should call the providers in providers.py directly (via
`providers.REGISTRY` or the provider classes) to get the full
ProviderResult with its state, rather than adding new callers of the
dict-or-None functions here. This file is deliberately being kept as a
thin backward-compatible shim, not extended further.

openrouter_analyze() is intentionally NOT part of the ThreatIntelProvider
abstraction — it's the AI Security Copilot's client, a different kind of
integration with its own AIProvider abstraction planned for Phase 11 (see
ARCHITECTURE_TARGET.md and SECURITY_FINDINGS.md F-06). Its implementation
is unchanged in this phase.
"""
import logging

from .. import config
from . import http_client as _http
from . import providers
from .providers import ProviderState

logger = logging.getLogger("centinel.threat_intel")


def _dict_or_none(result: "providers.ProviderResult") -> dict | None:
    """Old-contract adapter: CONFIRMED/CLEAN -> the data dict, everything
    else -> None. Matches this file's pre-Phase-2 behavior exactly."""
    if result.state in (ProviderState.CONFIRMED, ProviderState.CLEAN):
        return result.data
    return None


# ---------------------------------------------------------------------------
# VirusTotal (API v3) — URL reputation and file-hash reputation
# ---------------------------------------------------------------------------

def virustotal_url_report(url: str) -> dict:
    """See providers.VirusTotalUrlProvider for the real implementation and
    the CONFIRMED/UNKNOWN/UNAVAILABLE/ERROR distinctions it now makes.
    This wrapper collapses those back to dict-or-None for existing callers."""
    return _dict_or_none(providers.REGISTRY["virustotal_url"].lookup(url))


def virustotal_file_report(sha256_hash: str) -> dict:
    """See providers.VirusTotalFileProvider."""
    return _dict_or_none(providers.REGISTRY["virustotal_file"].lookup(sha256_hash))


# ---------------------------------------------------------------------------
# Google Safe Browsing (v4)
# ---------------------------------------------------------------------------

def google_safe_browsing_check(url: str) -> dict:
    """See providers.GoogleSafeBrowsingProvider."""
    return _dict_or_none(providers.REGISTRY["google_safe_browsing"].lookup(url))


# ---------------------------------------------------------------------------
# AbuseIPDB
# ---------------------------------------------------------------------------

def abuseipdb_check(ip: str) -> dict:
    """See providers.AbuseIPDBProvider."""
    return _dict_or_none(providers.REGISTRY["abuseipdb"].lookup(ip))


# ---------------------------------------------------------------------------
# IP Geolocation — ip-api.com's free tier needs no API key
# ---------------------------------------------------------------------------

def ip_geolocation(ip: str) -> dict:
    """See providers.IPGeolocationProvider. Enrichment only, not a threat
    signal on its own."""
    return _dict_or_none(providers.REGISTRY["ip_geolocation"].lookup(ip))


# ---------------------------------------------------------------------------
# Have I Been Pwned (v3)
# ---------------------------------------------------------------------------

def hibp_check(email: str) -> dict:
    """See providers.HIBPProvider. Note this is the one existing function
    that already distinguished confirmed-clean ({"breaches": []}) from
    unknown/unavailable (None) before Phase 2 — that distinction is
    preserved exactly, now sourced from ProviderState.CLEAN vs everything
    else instead of an inline status-code check."""
    return _dict_or_none(providers.REGISTRY["hibp"].lookup(email))


# ---------------------------------------------------------------------------
# SSL Labs — cache-only (a fresh assessment can take 1-2+ minutes)
# ---------------------------------------------------------------------------

def ssl_labs_grade(hostname: str) -> dict:
    """See providers.SSLLabsProvider."""
    return _dict_or_none(providers.REGISTRY["ssl_labs"].lookup(hostname))


# ---------------------------------------------------------------------------
# OpenRouter — AI-powered intelligent threat analysis
# ---------------------------------------------------------------------------
# Not migrated to the ThreatIntelProvider abstraction (see module docstring)
# — this is the AI Security Copilot's client and is explicitly Phase 11
# scope, including closing SECURITY_FINDINGS.md F-06 (raw untrusted content
# in the prompt, LLM risk_score used as a direct signal weight). Both of
# those problems are UNCHANGED in this phase — do not treat this function's
# continued presence here as though F-06 has been addressed; it hasn't.

def openrouter_analyze(content_type: str, content: str) -> dict:
    """
    Uses OpenRouter to perform intelligent behavioral and contextual
    analysis on suspicious items. This can detect subtle social engineering,
    psychological lures, and novel obfuscation techniques using a variety of
    LLMs (defaults to Google Gemini Flash for speed and cost).
    """
    if not config.OPENROUTER_API_KEY:
        return None

    prompt = (
        f"Analyze this {content_type} for cybersecurity threats (phishing, malware, scams). "
        f"Content: \"{content}\" "
        "Return your assessment in EXACT JSON format with these keys: "
        "\"risk_score\" (0-100), \"reason\" (short explanation), \"threat_category\" (one word)."
    )

    resp = _http.post(
        "https://openrouter.ai/api/v1/chat/completions",
        headers={
            "Authorization": f"Bearer {config.OPENROUTER_API_KEY}",
            "HTTP-Referer": "https://centinel-cyber.com",  # Required by OpenRouter
            "X-Title": "Centinel Cybersecurity",
            "Content-Type": "application/json"
        },
        json_body={
            "model": "google/gemini-2.0-flash-001",  # High performance / low cost model
            "messages": [
                {"role": "system", "content": "You are a cybersecurity intelligence expert specializing in threat detection."},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0
        }
    )

    if resp is None or resp.status_code != 200:
        if resp is not None:
            logger.info("OpenRouter API error: %s %s", resp.status_code, resp.text)
        return None

    try:
        raw_text = resp.json()["choices"][0]["message"]["content"]
        # Strip potential markdown formatting if model returned it
        if "```json" in raw_text:
            raw_text = raw_text.split("```json")[1].split("```")[0].strip()

        import json
        data = json.loads(raw_text)
        return {
            "risk_score": int(data.get("risk_score", 0)),
            "reason": data.get("reason", "AI detected potential anomalies"),
            "category": data.get("threat_category", "ai_analysis")
        }
    except (KeyError, ValueError, TypeError, json.JSONDecodeError) as e:
        logger.info("Could not parse AI response: %s", e)
        return None
