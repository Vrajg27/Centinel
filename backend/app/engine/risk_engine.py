"""
Core risk-scoring primitives shared by every scan module.

This runs fully offline: no external API keys required. Each analysis
module (url_analysis.py, email_analysis.py, ...) produces a list of
`Signal` objects; this file combines them into a final weighted
Risk Score (0-100), a Threat Level, and a Confidence Score, exactly
like the spec's "Risk Scoring Engine" section describes.

To plug in real threat-intel providers later (VirusTotal, Google Safe
Browsing, AbuseIPDB, PhishTank, HaveIBeenPwned, SSL Labs), add a
Signal in the relevant analysis module using the same `Signal` shape -
the scoring/explanation logic does not need to change.
"""
from dataclasses import dataclass, field
from typing import List


@dataclass
class Signal:
    """One piece of evidence discovered during analysis."""
    name: str                 # short id, e.g. "recently_registered_domain"
    description: str          # human readable, used in the explanation
    weight: float              # contribution to risk score, 0-100 scale
    category: str = "general"  # e.g. reputation, content, technical, behavioral


@dataclass
class RiskAssessment:
    risk_score: int
    threat_level: str
    confidence: float
    detected_threats: List[str] = field(default_factory=list)
    indicators: List[str] = field(default_factory=list)
    recommendations: List[str] = field(default_factory=list)
    explanation: str = ""


THREAT_LEVELS = [
    (0, 15, "Safe"),
    (15, 35, "Low"),
    (35, 60, "Medium"),
    (60, 85, "High"),
    (85, 101, "Critical"),
]


def score_to_level(score: int) -> str:
    for lo, hi, label in THREAT_LEVELS:
        if lo <= score < hi:
            return label
    return "Critical"


def combine_signals(
    signals: List[Signal],
    base_confidence: float = 0.6,
    recommendations: List[str] = None,
    subject_label: str = "this item",
) -> RiskAssessment:
    """
    Weighted combination of signals into a final score.
    Uses a soft-saturating sum so many small signals don't blow past 100
    while a couple of severe signals can still push into Critical.
    """
    positive_weight_sum = sum(max(s.weight, 0) for s in signals)
    # Soft saturation: score approaches 100 asymptotically as evidence piles up.
    risk_score = int(round(100 * (1 - pow(2.71828, -positive_weight_sum / 55.0))))
    risk_score = max(0, min(100, risk_score))

    threat_level = score_to_level(risk_score)

    # Confidence rises with number/strength of signals found (more evidence
    # either way = more confident), capped at 0.97.
    evidence_strength = min(len(signals) * 0.07, 0.35)
    confidence = round(min(base_confidence + evidence_strength, 0.97), 2)

    detected_threats = sorted(
        {s.category for s in signals if s.weight > 0}
    )
    indicators = [s.description for s in signals]

    explanation = _build_explanation(subject_label, risk_score, threat_level, signals)

    return RiskAssessment(
        risk_score=risk_score,
        threat_level=threat_level,
        confidence=confidence,
        detected_threats=detected_threats or ["none"],
        indicators=indicators or ["No suspicious indicators found."],
        recommendations=recommendations or _default_recommendations(threat_level),
        explanation=explanation,
    )


def _default_recommendations(threat_level: str) -> List[str]:
    if threat_level in ("Critical", "High"):
        return [
            "Do not proceed or interact with this item.",
            "Do not enter credentials, OTPs, or payment details.",
            "Report/delete the item and block the sender or domain if possible.",
        ]
    if threat_level == "Medium":
        return [
            "Proceed with caution and verify the source through a separate channel.",
            "Avoid entering sensitive information until verified.",
        ]
    if threat_level == "Low":
        return [
            "Generally low risk, but stay alert for follow-up requests for sensitive info.",
        ]
    return ["No action needed. This appears safe based on available signals."]


def _build_explanation(subject_label, risk_score, threat_level, signals: List[Signal]) -> str:
    if not signals:
        return (
            f"{subject_label.capitalize()} was analyzed and no suspicious indicators "
            f"were found. Risk Score: {risk_score}/100 ({threat_level})."
        )

    # Use the top 3 highest-weight signals as the "why"
    top = sorted(signals, key=lambda s: s.weight, reverse=True)[:3]
    reasons = "; ".join(s.description for s in top)

    verdict = {
        "Critical": "is very likely dangerous",
        "High": "shows strong signs of being dangerous",
        "Medium": "shows some suspicious signs and should be treated with caution",
        "Low": "shows minor irregularities but is probably low risk",
        "Safe": "appears safe",
    }[threat_level]

    return (
        f"{subject_label.capitalize()} {verdict} because {reasons}. "
        f"Risk Score: {risk_score}/100 ({threat_level})."
    )
