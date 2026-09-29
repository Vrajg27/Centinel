import re
from .risk_engine import Signal, combine_signals, RiskAssessment
from .url_analysis import analyze_url
from . import threat_intel

URL_RE = re.compile(r"https?://[^\s]+|(?:www\.)[^\s]+")

BANKING_FRAUD = ["account blocked", "kyc", "re-kyc", "update your kyc", "account suspended",
                 "verify your bank", "debit card blocked", "credit card blocked"]
OTP_THEFT = ["otp", "one time password", "do not share your otp", "share otp", "confirm otp"]
DELIVERY_SCAM = ["your parcel", "delivery failed", "package held", "customs fee", "reschedule delivery",
                 "courier could not deliver"]
PRIZE_SCAM = ["you have won", "lottery", "claim your prize", "congratulations you"]
LOAN_SCAM = ["loan approved", "instant loan", "pre-approved loan"]


def analyze_sms(message: str, sender: str = None) -> RiskAssessment:
    text = message.lower()
    signals = []

    def check(keywords, name, desc, weight, category="content"):
        hits = [k for k in keywords if k in text]
        if hits:
            signals.append(Signal(name, desc.format(hits=", ".join(hits[:2])), weight, category))

    check(BANKING_FRAUD, "banking_fraud_language",
          "it mimics a bank/KYC alert ({hits}), a very common banking fraud pattern", 28)
    check(OTP_THEFT, "otp_theft_language",
          "it references an OTP / one-time password ({hits}), a classic OTP-theft scam pattern", 32)
    check(DELIVERY_SCAM, "fake_delivery_language",
          "it impersonates a delivery/courier notice ({hits}), a common smishing pattern", 20)
    check(PRIZE_SCAM, "prize_scam_language",
          "it promises a prize or lottery win ({hits}), a classic scam lure", 25)
    check(LOAN_SCAM, "loan_scam_language",
          "it advertises an instant/pre-approved loan ({hits}), frequently used in fraud", 18)

    urls = URL_RE.findall(text)
    if urls:
        cleaned = [u if u.startswith("http") else "http://" + u for u in urls]
        worst = max((analyze_url(u) for u in cleaned), key=lambda a: a.risk_score, default=None)
        weight = 10 + (worst.risk_score * 0.3 if worst else 0)
        signals.append(Signal(
            "embedded_link",
            f"it contains a link ({urls[0]}) which independently scores "
            f"{worst.risk_score if worst else '?'}/100 risk",
            weight, "reputation",
        ))

    if sender and re.fullmatch(r"[A-Za-z]{2}-?\w{4,8}", sender.strip()) is None and re.match(r"^\+?\d{6,}$", sender.strip() or ""):
        signals.append(Signal(
            "unregistered_sender_number",
            "the message came from a long numeric sender ID rather than a registered short/alphanumeric "
            "sender ID, common for spoofed SMS",
            10, "reputation",
        ))

    if re.search(r"\b(free|urgent|winner|act now|limited)\b", text) and text.count("!") >= 1:
        signals.append(Signal(
            "spam_tone",
            "the tone and word choice match typical bulk-spam/scam messaging",
            5, "content",
        ))

    # AI Intelligent Detection
    ai = threat_intel.openrouter_analyze("SMS", message)
    if ai:
        signals.append(Signal(
            "ai_threat_detection",
            f"OpenRouter AI identified threats: {ai['reason']}",
            ai["risk_score"], ai["category"]
        ))

    return combine_signals(signals, base_confidence=0.58, subject_label="this SMS message")
