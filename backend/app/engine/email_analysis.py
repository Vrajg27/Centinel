import re
import email as email_stdlib
from email.message import Message

from .risk_engine import Signal, combine_signals, RiskAssessment
from .url_analysis import URGENT_WORDS
from . import threat_intel

URGENCY_PHRASES = [
    "act now", "verify your account", "urgent action required", "your account will be suspended",
    "click immediately", "confirm your identity", "unusual activity detected", "limited time",
    "final notice", "payment failed", "update your billing", "you have won", "claim your prize",
]

CREDENTIAL_PHRASES = [
    "enter your password", "confirm your ssn", "provide your otp", "your one time password",
    "banking pin", "card verification", "login to verify",
]

URL_RE = re.compile(r"https?://[^\s\"'<>]+")


def analyze_email(subject: str, sender: str, body: str, raw_email: str = None) -> RiskAssessment:
    text = " ".join(filter(None, [subject or "", body or "", raw_email or ""])).lower()
    signals = []

    urgency_hits = [p for p in URGENCY_PHRASES if p in text]
    if urgency_hits:
        signals.append(Signal(
            "urgency_language",
            f"it uses high-pressure urgency phrases ({', '.join(urgency_hits[:3])})",
            20, "content",
        ))

    cred_hits = [p for p in CREDENTIAL_PHRASES if p in text]
    if cred_hits:
        signals.append(Signal(
            "credential_harvesting_language",
            "it asks the recipient to submit passwords, OTPs, or account credentials directly",
            30, "content",
        ))

    urls = URL_RE.findall(text)
    if urls:
        from .url_analysis import analyze_url
        worst = max((analyze_url(u) for u in urls), key=lambda a: a.risk_score, default=None)
        if worst and worst.risk_score >= 35:
            signals.append(Signal(
                "malicious_embedded_link",
                f"it contains an embedded link that independently scores {worst.risk_score}/100 risk",
                min(worst.risk_score * 0.4, 35), "reputation",
            ))
        elif urls:
            signals.append(Signal(
                "contains_links",
                f"it contains {len(urls)} embedded link(s) requiring caution",
                4, "technical",
            ))

    if sender:
        sender_l = sender.lower()
        display_name_spoof = re.search(r'"?([\w .]+)"?\s*<([^>]+)>', sender)
        if display_name_spoof:
            display_name, addr = display_name_spoof.groups()
            common_brands = ["amazon", "paypal", "bank", "microsoft", "apple", "support", "security"]
            if any(b in display_name.lower() for b in common_brands) and not any(
                b in addr.lower() for b in common_brands
            ):
                signals.append(Signal(
                    "display_name_spoofing",
                    f"the display name ('{display_name.strip()}') suggests a trusted brand but the actual "
                    f"address ('{addr}') does not match",
                    28, "reputation",
                ))
        if re.search(r"@[\w.-]+\.(xyz|top|click|work|tk|gq|ml)$", sender_l):
            signals.append(Signal(
                "suspicious_sender_domain",
                "the sender's email domain uses a top-level domain frequently abused for spam/phishing",
                18, "reputation",
            ))

    if re.search(r"\.(exe|scr|bat|js|vbs|jar|apk)(\s|$|\")", text):
        signals.append(Signal(
            "suspicious_attachment_reference",
            "it references an executable-style attachment (.exe/.scr/.js/.apk etc.), a common malware vector",
            30, "technical",
        ))

    if len(re.findall(r"[!]{2,}", text)) > 0 or text.count("!") >= 5:
        signals.append(Signal(
            "excessive_punctuation",
            "it uses excessive exclamation marks, a stylistic pattern common in scam/marketing spam",
            5, "content",
        ))

    # Simple SPF/DKIM/DMARC mention check when raw headers are embedded in raw_email
    if raw_email:
        low = raw_email.lower()
        fails = sorted({m.upper() for m, r in re.findall(
            r"(spf|dkim|dmarc)\s*[:=]\s*(fail|softfail)", low
        )})
        if fails:
            signals.append(Signal(
                "auth_failures",
                f"email authentication checks failed ({', '.join(fails)}), suggesting the sender may be spoofed",
                25, "technical",
            ))

    # OpenRouter AI Intelligent Detection
    ai = threat_intel.openrouter_analyze("Email", f"Subject: {subject}\nBody: {body}")
    if ai:
        signals.append(Signal(
            "ai_threat_detection",
            f"OpenRouter AI identified risks: {ai['reason']}",
            ai["risk_score"], ai["category"]
        ))

    return combine_signals(
        signals, base_confidence=0.6,
        subject_label="this email",
    )


def _extract_body_text(msg: Message) -> str:
    """
    Walks a (possibly multipart) email message and pulls out the best
    available body text: prefers text/plain parts, falls back to a crude
    HTML-tag strip of text/html parts if no plain-text part exists, and
    skips anything marked as an attachment.
    """
    def decode_part(part: Message) -> str:
        try:
            payload = part.get_payload(decode=True)
            if payload is None:
                return ""
            charset = part.get_content_charset() or "utf-8"
            return payload.decode(charset, errors="replace")
        except Exception:
            return ""

    if not msg.is_multipart():
        return decode_part(msg)

    plain_parts, html_parts = [], []
    for part in msg.walk():
        if part.is_multipart():
            continue
        disposition = str(part.get("Content-Disposition") or "").lower()
        if "attachment" in disposition:
            continue
        content_type = part.get_content_type()
        if content_type == "text/plain":
            plain_parts.append(decode_part(part))
        elif content_type == "text/html":
            html_parts.append(decode_part(part))

    if plain_parts:
        return "\n".join(plain_parts)
    if html_parts:
        # Crude tag strip — good enough for phishing-language detection,
        # not meant to be a real HTML-to-text renderer.
        stripped = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", "\n".join(html_parts), flags=re.I | re.S)
        stripped = re.sub(r"<[^>]+>", " ", stripped)
        return re.sub(r"\s+", " ", stripped).strip()
    return ""


def parse_eml(raw_bytes: bytes) -> dict:
    """
    Parses a raw .eml file into the same subject/sender/body/raw_email shape
    that analyze_email() already expects, so a file upload can reuse the
    exact same detection logic as pasted-text email scanning.
    """
    try:
        raw_text = raw_bytes.decode("utf-8", errors="replace")
    except Exception:
        raw_text = raw_bytes.decode("latin-1", errors="replace")

    msg = email_stdlib.message_from_bytes(raw_bytes)
    return {
        "subject": msg.get("Subject"),
        "sender": msg.get("From"),
        "body": _extract_body_text(msg),
        "raw_email": raw_text,
    }
