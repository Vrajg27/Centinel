import re
import socket
import datetime as dt
from urllib.parse import urlparse

try:
    import tldextract
except ImportError:  # pragma: no cover - optional dependency
    tldextract = None

try:
    import whois as whois_lib
except ImportError:  # pragma: no cover - optional dependency
    whois_lib = None

from .risk_engine import Signal, combine_signals, RiskAssessment
from .. import config

SHORTENERS = {
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
    "buff.ly", "shorte.st", "rebrand.ly", "cutt.ly",
}

BRAND_KEYWORDS = [
    "paypal", "bankofamerica", "chase", "wellsfargo", "amazon", "apple",
    "microsoft", "google", "netflix", "facebook", "instagram", "hdfcbank",
    "icicibank", "sbi", "irs", "dhl", "fedex", "coinbase",
]

SUSPICIOUS_TLDS = {".zip", ".mov", ".xyz", ".top", ".tk", ".gq", ".ml", ".cf", ".work", ".click"}

URGENT_WORDS = ["verify", "urgent", "suspend", "locked", "confirm-account", "update-payment", "security-alert"]


def _looks_like_ip(host: str) -> bool:
    return bool(re.fullmatch(r"(\d{1,3}\.){3}\d{1,3}", host or ""))


def _levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i] + [0] * len(b)
        for j, cb in enumerate(b, 1):
            cur[j] = min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb))
        prev = cur
    return prev[-1]


def _domain_registration_age_days(registrable_domain: str):
    """
    Returns how many days ago the domain was registered, or None if the
    lookup is disabled, unavailable, times out, or the registrar's response
    can't be parsed (many WHOIS/RDAP responses are inconsistently formatted
    or redacted for privacy — that's treated as "unknown", not "safe").
    """
    if not config.ENABLE_WHOIS_LOOKUP or whois_lib is None:
        return None

    old_timeout = socket.getdefaulttimeout()
    try:
        socket.setdefaulttimeout(config.WHOIS_TIMEOUT_SECONDS)
        record = whois_lib.whois(registrable_domain)
        creation = record.creation_date
        if isinstance(creation, list):
            creation = creation[0] if creation else None
        if not isinstance(creation, dt.datetime):
            return None
        if creation.tzinfo is not None:
            creation = creation.replace(tzinfo=None)
        age = (dt.datetime.utcnow() - creation).days
        return age if age >= 0 else None
    except Exception:
        # WHOIS servers are rate-limited/unreliable and formats vary wildly
        # by TLD — any failure here just means "no domain-age signal", not
        # an error worth surfacing to the user.
        return None
    finally:
        socket.setdefaulttimeout(old_timeout)


def analyze_url(raw_url: str) -> RiskAssessment:
    signals = []
    url = raw_url.strip()
    if not re.match(r"^https?://", url, re.I):
        url = "http://" + url

    parsed = urlparse(url)
    host = (parsed.hostname or "").lower()
    path = parsed.path or ""
    scheme = parsed.scheme

    # 1. Not using HTTPS
    if scheme != "https":
        signals.append(Signal(
            "no_https", "the site does not use HTTPS encryption", 12, "technical"
        ))

    # 2. URL shortener (destination unknown, common phishing vector)
    if host in SHORTENERS:
        signals.append(Signal(
            "shortened_url",
            f"it uses the URL shortening service '{host}', hiding the real destination",
            15, "reputation",
        ))

    # 3. Raw IP address instead of domain name
    if _looks_like_ip(host):
        signals.append(Signal(
            "ip_address_host",
            "it uses a raw IP address instead of a domain name, which is unusual for legitimate sites",
            25, "technical",
        ))

    # 4. Suspicious TLD
    for tld in SUSPICIOUS_TLDS:
        if host.endswith(tld):
            signals.append(Signal(
                "suspicious_tld",
                f"it uses the top-level domain '{tld}', which is frequently abused for scams",
                15, "reputation",
            ))
            break

    # 5. Brand name impersonation (brand keyword present but not the real domain)
    for brand in BRAND_KEYWORDS:
        if brand in host and not host.endswith(f"{brand}.com"):
            dist = _levenshtein(host.split(".")[0], brand)
            if dist <= 4:
                signals.append(Signal(
                    "brand_impersonation",
                    f"the domain closely imitates the well-known brand '{brand}' without being its official domain",
                    35, "reputation",
                ))
                break

    # 6. Excessive subdomains / hyphens (typo-squatting pattern)
    if host.count("-") >= 2:
        signals.append(Signal(
            "many_hyphens",
            "the domain contains multiple hyphens, a pattern common in look-alike phishing domains",
            10, "reputation",
        ))
    if host.count(".") >= 4:
        signals.append(Signal(
            "many_subdomains",
            "the domain has an unusually deep subdomain structure often used to disguise the real host",
            12, "technical",
        ))

    # 7. Urgent/action words in path or query (typical phishing lure)
    lowered = (path + "?" + (parsed.query or "")).lower()
    hit_words = [w for w in URGENT_WORDS if w.replace("-", "") in lowered.replace("-", "")]
    if hit_words:
        signals.append(Signal(
            "urgent_language_in_url",
            f"the URL path contains urgency-related terms ({', '.join(hit_words)}) typical of phishing lures",
            15, "content",
        ))

    # 8. @ symbol trick (browser treats everything before @ as userinfo)
    if "@" in url.split("://", 1)[-1]:
        signals.append(Signal(
            "at_symbol_trick",
            "the URL uses an '@' symbol to disguise the real destination host",
            30, "technical",
        ))

    # 9. Punycode / IDN homograph attack
    if "xn--" in host:
        signals.append(Signal(
            "punycode_domain",
            "the domain uses punycode encoding, which can be used to visually spoof a trusted domain",
            30, "technical",
        ))

    # 10. Domain age via WHOIS — newly registered domains are one of the
    # single strongest phishing indicators (attackers rarely reuse aged
    # domains). Skipped automatically if disabled/unreachable/unparseable.
    if not _looks_like_ip(host) and host:
        if tldextract is not None:
            ext = tldextract.extract(host)
            registrable_domain = f"{ext.domain}.{ext.suffix}" if ext.domain and ext.suffix else host
        else:
            # Naive fallback (wrong for compound TLDs like .co.uk, but good
            # enough as a WHOIS lookup target when tldextract isn't installed).
            parts = host.split(".")
            registrable_domain = ".".join(parts[-2:]) if len(parts) >= 2 else host
        age_days = _domain_registration_age_days(registrable_domain)
        if age_days is not None:
            if age_days < 7:
                signals.append(Signal(
                    "newly_registered_domain",
                    f"the domain was registered only {age_days} day(s) ago, a very strong phishing indicator",
                    30, "reputation",
                ))
            elif age_days < 30:
                signals.append(Signal(
                    "recently_registered_domain",
                    f"the domain was registered {age_days} days ago (under 30 days old), commonly seen in phishing campaigns",
                    18, "reputation",
                ))

    # 11. No suspicious signals baseline note
    assessment = combine_signals(
        signals,
        base_confidence=0.62,
        subject_label=f"the URL '{raw_url}'",
    )
    return assessment
