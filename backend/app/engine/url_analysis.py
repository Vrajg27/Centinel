import re
import socket
import datetime as dt
from urllib.parse import urlparse

try:
    import tldextract
except ImportError:  # pragma: no cover
    tldextract = None

try:
    import whois as whois_lib
except ImportError:  # pragma: no cover
    whois_lib = None

from .risk_engine import Signal, combine_signals, RiskAssessment
from . import threat_intel
from .. import config

SHORTENERS = {
    "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
    "buff.ly", "shorte.st", "rebrand.ly", "cutt.ly", "bc.vc",
    "v.gd", "qr.ae", "adf.ly", "dub.sh", "rb.gy",
}

# Trusted official domains mapped to their brand keywords
TRUSTED_BRAND_DOMAINS = {
    "paypal": ["paypal.com", "paypal.me"],
    "bankofamerica": ["bankofamerica.com", "bofa.com"],
    "chase": ["chase.com"],
    "wellsfargo": ["wellsfargo.com"],
    "amazon": ["amazon.com", "amazon.co.uk", "amazon.de", "amazon.in", "aws.amazon.com"],
    "apple": ["apple.com", "icloud.com"],
    "microsoft": ["microsoft.com", "live.com", "outlook.com", "office.com"],
    "google": ["google.com", "youtube.com", "gmail.com"],
    "netflix": ["netflix.com"],
    "facebook": ["facebook.com", "fb.com"],
    "instagram": ["instagram.com"],
    "hdfcbank": ["hdfcbank.com"],
    "icicibank": ["icicibank.com"],
    "sbi": ["sbi.co.in", "onlinesbi.sbi"],
    "dhl": ["dhl.com"],
    "fedex": ["fedex.com"],
    "coinbase": ["coinbase.com"],
    "binance": ["binance.com"],
    "whatsapp": ["whatsapp.com"],
    "telegram": ["telegram.org", "t.me"],
    "linkedin": ["linkedin.com"],
    "twitter": ["twitter.com", "x.com"],
    "spotify": ["spotify.com"],
    "steam": ["steampowered.com", "steamcommunity.com"],
    "usps": ["usps.com"],
    "citi": ["citibank.com", "citi.com"],
    "hsbc": ["hsbc.com"],
}

SUSPICIOUS_TLDS = {
    ".zip", ".mov", ".xyz", ".top", ".tk", ".gq", ".ml", ".cf", ".work",
    ".click", ".club", ".online", ".site", ".vip", ".icu", ".monster",
    ".buzz", ".rest", ".fit", ".cc", ".su", ".pw", ".cn", ".surf", ".space",
    ".gdn", ".kim", ".loan", ".racing", ".bid", ".stream", ".download",
}

URGENT_WORDS = [
    "verify", "urgent", "suspend", "locked", "confirm-account", "update-payment",
    "security-alert", "login", "signin", "auth", "account", "secure", "bank",
    "kyc", "support", "billing", "claim", "bonus", "gift", "prize", "reward",
    "verification", "security", "wallet", "resolution", "revalidate", "passcode",
    "otp", "unusual", "unauthorized", "restore", "credential",
]

EXECUTABLE_EXTENSIONS = {
    ".exe", ".scr", ".vbs", ".bat", ".cmd", ".ps1", ".apk", ".iso", ".img", ".msi",
}


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
    scheme = parsed.scheme.lower()

    # 1. Lack of HTTPS Encryption
    if scheme != "https":
        signals.append(Signal(
            "no_https", "Missing HTTPS encryption (data sent in cleartext)", 15, "technical"
        ))

    # 2. URL Shorteners
    if host in SHORTENERS:
        signals.append(Signal(
            "shortened_url",
            f"Uses URL shortening service '{host}' to obscure true destination",
            20, "reputation",
        ))

    # 3. Raw IP Address Host
    if _looks_like_ip(host):
        signals.append(Signal(
            "ip_address_host",
            "Uses a raw IP address instead of a registered domain name",
            30, "technical",
        ))

    # 4. Suspicious Top-Level Domains (TLDs)
    for tld in SUSPICIOUS_TLDS:
        if host.endswith(tld):
            signals.append(Signal(
                "suspicious_tld",
                f"Uses top-level domain '{tld}' frequently associated with scam websites",
                20, "reputation",
            ))
            break

    # 5. Brand Keyword Impersonation & Typosquatting
    host_labels = [p for p in host.split(".") if p]
    for brand, official_domains in TRUSTED_BRAND_DOMAINS.items():
        is_official = any(host == d or host.endswith("." + d) for d in official_domains)
        if not is_official:
            if brand in host:
                signals.append(Signal(
                    "brand_impersonation",
                    f"Domain contains trusted brand keyword '{brand}' without being an official domain",
                    40, "reputation",
                ))
                break
            else:
                # Check labels for typosquatting (e.g., paypa1, arnazon)
                for label in host_labels:
                    dist = _levenshtein(label, brand)
                    if 1 <= dist <= 2 and len(label) >= 4:
                        signals.append(Signal(
                            "typosquatting",
                            f"Host label '{label}' visually mimics trusted brand '{brand}'",
                            35, "reputation",
                        ))
                        break

    # 6. Hyphen & Subdomain Structure Analysis
    hyphen_count = host.count("-")
    if hyphen_count >= 2:
        signals.append(Signal(
            "excessive_hyphens",
            f"Domain contains {hyphen_count} hyphens, a common pattern in phishing lookalikes",
            15, "reputation",
        ))

    subdomain_depth = len(host_labels)
    if subdomain_depth >= 4:
        signals.append(Signal(
            "deep_subdomains",
            f"Unusual subdomain depth ({subdomain_depth} levels) hiding real host origin",
            15, "technical",
        ))

    # 7. Non-standard Network Ports
    if parsed.port and parsed.port not in (80, 443):
        signals.append(Signal(
            "non_standard_port",
            f"Uses non-standard web port {parsed.port}",
            20, "technical",
        ))

    # 8. Phishing Lures & Urgency Keywords in Path or Query
    full_path_query = (path + "?" + (parsed.query or "")).lower()
    hit_words = [w for w in URGENT_WORDS if w.replace("-", "") in full_path_query.replace("-", "")]
    if hit_words:
        signals.append(Signal(
            "urgent_phishing_keywords",
            f"Path contains sensitive/urgent keywords: {', '.join(sorted(set(hit_words))[:4])}",
            20, "content",
        ))

    # 9. `@` Userinfo Trick
    if "@" in url.split("://", 1)[-1]:
        signals.append(Signal(
            "at_symbol_disguise",
            "Uses '@' symbol trick to disguise the real host destination",
            40, "technical",
        ))

    # 10. Punycode IDN Homograph Attack
    if "xn--" in host:
        signals.append(Signal(
            "punycode_domain",
            "Uses Punycode encoding (xn--) which can visually spoof trusted characters",
            35, "technical",
        ))

    # 11. High Numeric Ratio / Random String in Host
    digits_in_host = sum(c.isdigit() for c in host)
    if digits_in_host >= 5 and not _looks_like_ip(host):
        signals.append(Signal(
            "high_digit_count",
            "Host contains unusually high number of numeric digits",
            15, "reputation",
        ))

    # 12. Executable / Archive File Extension in Path
    for ext in EXECUTABLE_EXTENSIONS:
        if path.lower().endswith(ext):
            signals.append(Signal(
                "executable_file_download",
                f"URL directs to an executable file download ({ext})",
                35, "content",
            ))
            break

    # 13. Domain Age via WHOIS
    if not _looks_like_ip(host) and host:
        if tldextract is not None:
            ext = tldextract.extract(host)
            registrable_domain = f"{ext.domain}.{ext.suffix}" if ext.domain and ext.suffix else host
        else:
            parts = host.split(".")
            registrable_domain = ".".join(parts[-2:]) if len(parts) >= 2 else host
        age_days = _domain_registration_age_days(registrable_domain)
        if age_days is not None:
            if age_days < 7:
                signals.append(Signal(
                    "newly_registered_domain",
                    f"Domain registered only {age_days} day(s) ago (extreme risk indicator)",
                    35, "reputation",
                ))
            elif age_days < 30:
                signals.append(Signal(
                    "recently_registered_domain",
                    f"Domain registered recently ({age_days} days ago)",
                    20, "reputation",
                ))

    # 14. External Threat Feeds
    vt = threat_intel.virustotal_url_report(url)
    if vt and (vt["malicious"] > 0 or vt["suspicious"] > 0):
        signals.append(Signal(
            "virustotal_flagged",
            f"VirusTotal vendor flags: {vt['malicious']} malicious, {vt['suspicious']} suspicious",
            min(vt["malicious"] * 8 + vt["suspicious"] * 4, 50), "reputation",
        ))

    gsb = threat_intel.google_safe_browsing_check(url)
    if gsb:
        signals.append(Signal(
            "google_safe_browsing_flagged",
            f"Google Safe Browsing threat detected: {', '.join(t.replace('_', ' ').title() for t in gsb['threat_types'])}",
            50, "reputation",
        ))

    ai = threat_intel.openrouter_analyze("URL", url)
    if ai:
        signals.append(Signal(
            "ai_threat_detection",
            f"AI Threat Engine: {ai['reason']}",
            ai["risk_score"], ai["category"]
        ))

    assessment = combine_signals(
        signals,
        base_confidence=0.65,
        subject_label=f"the URL '{raw_url}'",
    )
    return assessment
