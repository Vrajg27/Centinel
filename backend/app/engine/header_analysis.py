import re
from .risk_engine import Signal, combine_signals, RiskAssessment

IP_RE = re.compile(r"\b(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})\b")

PRIVATE_RANGES = [
    re.compile(r"^10\."),
    re.compile(r"^192\.168\."),
    re.compile(r"^172\.(1[6-9]|2\d|3[0-1])\."),
    re.compile(r"^127\."),
]


def _is_private(ip: str) -> bool:
    return any(p.match(ip) for p in PRIVATE_RANGES)


def analyze_headers(raw_headers: str) -> dict:
    """Returns (RiskAssessment, extracted_info dict) for the header analyzer."""
    signals = []
    lines = raw_headers.splitlines()

    received_lines = [l for l in lines if l.lower().startswith("received:")]
    all_ips = list(dict.fromkeys(IP_RE.findall(raw_headers)))
    public_ips = [ip for ip in all_ips if not _is_private(ip)]

    sender_ip = public_ips[0] if public_ips else None

    if not sender_ip:
        signals.append(Signal(
            "hidden_sender_ip",
            "no public sender IP could be found in the header chain — it may be hidden behind a "
            "relay, VPN, or the headers were stripped/forged",
            22, "technical",
        ))

    if len(received_lines) >= 6:
        signals.append(Signal(
            "long_relay_chain",
            f"the email passed through an unusually long relay chain ({len(received_lines)} hops)",
            10, "technical",
        ))

    low = raw_headers.lower()
    fail_pattern = re.compile(r"(spf|dkim|dmarc)\s*[:=]\s*(fail|softfail|none)")
    seen_mechs = set()
    for mech, result in fail_pattern.findall(low):
        if mech not in seen_mechs:
            seen_mechs.add(mech)
            signals.append(Signal(
                f"{mech}_failure",
                f"{mech.upper()} authentication did not pass ({result}), indicating the sending server may not be authorized",
                20, "technical",
            ))

    if "x-mailer: unknown" in low or re.search(r"from:.*<.*@.*\.(ru|cn|tk|ml)>", low):
        signals.append(Signal(
            "suspicious_origin_pattern",
            "header patterns match those commonly seen in spoofed or bulk-spam origin servers",
            12, "reputation",
        ))

    assessment = combine_signals(signals, base_confidence=0.55, subject_label="this email's headers")

    # Mock geolocation/reputation lookup (offline placeholder — swap for a real
    # IP geolocation + AbuseIPDB call by filling in config.ABUSEIPDB_API_KEY).
    geo = None
    if sender_ip:
        geo = {
            "ip": sender_ip,
            "country": "Unknown (offline mode)",
            "region": "Unknown (offline mode)",
            "city": "Unknown (offline mode)",
            "isp": "Unknown (offline mode)",
            "asn": "Unknown (offline mode)",
            "latitude": None,
            "longitude": None,
            "note": "Live geolocation requires an IP Geolocation / AbuseIPDB API key (see config.py).",
        }

    extracted = {
        "sender_ip": sender_ip,
        "relay_ips": public_ips,
        "received_hops": len(received_lines),
        "geo": geo,
    }
    return assessment, extracted
