import hashlib
from .risk_engine import Signal, combine_signals

# In production, call the real Have I Been Pwned API using
# config.HIBP_API_KEY (https://haveibeenpwned.com/API/v3). Running fully
# offline here, we use a small local demo dataset keyed by SHA-1(email)
# so no real email is ever transmitted anywhere, exactly like HIBP's
# k-anonymity model for password checks.
_DEMO_BREACHED_HASHES = {
    hashlib.sha1(b"demo@example.com").hexdigest(): ["ExampleCorp 2019 Breach", "DataLeakX 2021 Breach"],
    hashlib.sha1(b"test@test.com").hexdigest(): ["BigSocialNet 2020 Breach"],
}


def check_breach(email: str):
    email_norm = email.strip().lower()
    h = hashlib.sha1(email_norm.encode()).hexdigest()
    breaches = _DEMO_BREACHED_HASHES.get(h, [])

    signals = []
    if breaches:
        signals.append(Signal(
            "found_in_breaches",
            f"this email address appears in {len(breaches)} known data breach(es): {', '.join(breaches)}",
            min(20 * len(breaches), 70), "reputation",
        ))

    assessment = combine_signals(
        signals, base_confidence=0.5,
        subject_label="this email address",
        recommendations=(
            [
                "Change the password for every account using this email immediately.",
                "Enable two-factor authentication where available.",
                "Use a unique password per site via a password manager.",
            ] if breaches else
            ["No known breaches found in this offline demo dataset. Still enable 2FA and use unique passwords."]
        ),
    )
    return assessment, {"email_checked": email_norm, "breaches_found": breaches,
                         "note": "Running in offline demo mode. Configure HIBP_API_KEY in config.py for live results."}
