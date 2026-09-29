import hashlib
from .risk_engine import Signal, combine_signals
from . import threat_intel
from .. import config

# Fallback used only when HIBP_API_KEY isn't configured (or the live call
# fails) — a small local demo dataset keyed by SHA-1(email), so no real
# email is ever transmitted anywhere in that mode, echoing HIBP's own
# k-anonymity model for its password-checking endpoint. This is clearly
# labeled as demo data in the response either way (see "mode" below), never
# presented as if it were real breach data.
_DEMO_BREACHED_HASHES = {
    hashlib.sha1(b"demo@example.com").hexdigest(): ["ExampleCorp 2019 Breach", "DataLeakX 2021 Breach"],
    hashlib.sha1(b"test@test.com").hexdigest(): ["BigSocialNet 2020 Breach"],
}


def check_breach(email: str):
    email_norm = email.strip().lower()

    live = threat_intel.hibp_check(email_norm) if config.HIBP_API_KEY else None

    if live is not None:
        # Real HaveIBeenPwned result (either a confirmed empty list from a
        # 404, or actual breach entries from a 200).
        mode = "live"
        breach_names = [b["title"] or b["name"] for b in live["breaches"]]
        breach_details = live["breaches"]
    else:
        # No key configured, or the live call failed for any reason — fall
        # back to the local demo dataset rather than silently reporting
        # "no breaches" (which could read as a false all-clear).
        mode = "offline_demo"
        h = hashlib.sha1(email_norm.encode()).hexdigest()
        breach_names = _DEMO_BREACHED_HASHES.get(h, [])
        breach_details = [{"name": n, "title": n, "breach_date": None, "data_classes": []} for n in breach_names]

    signals = []
    if breach_names:
        signals.append(Signal(
            "found_in_breaches",
            f"this email address appears in {len(breach_names)} known data breach(es): {', '.join(breach_names)}",
            min(20 * len(breach_names), 70), "reputation",
        ))

    assessment = combine_signals(
        signals, base_confidence=0.75 if mode == "live" else 0.5,
        subject_label="this email address",
        recommendations=(
            [
                "Change the password for every account using this email immediately.",
                "Enable two-factor authentication where available.",
                "Use a unique password per site via a password manager.",
            ] if breach_names else
            ["No known breaches found." + ("" if mode == "live" else " (offline demo dataset — configure HIBP_API_KEY for live results.)")]
        ),
    )

    note = (
        "Live result from HaveIBeenPwned." if mode == "live" else
        "Running in offline demo mode (no HIBP_API_KEY configured, or the live lookup failed). "
        "Note HIBP's breach-checking API has required a paid subscription since HIBP moved it "
        "behind a paid tier — verify current access at https://haveibeenpwned.com/API/Key."
    )
    return assessment, {
        "email_checked": email_norm,
        "breaches_found": breach_names,
        "breach_details": breach_details,
        "mode": mode,
        "note": note,
    }
