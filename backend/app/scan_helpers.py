"""
Shared helpers for persisting/serializing scan results — used by both the
synchronous endpoints in routers/scan_router.py and the background Celery
tasks in tasks.py. Pulled out into their own module (rather than living in
scan_router.py, as they did through Phase 3) specifically so tasks.py can
import them without creating a circular import back into the router.
"""
import json

from . import models, schemas, crypto, push
from .engine.risk_engine import RiskAssessment

# How long a cached analysis result is reused for (see redis_client.py).
# Short enough that a domain going from safe to compromised (or vice versa)
# is reflected reasonably quickly, long enough to actually cut down on
# repeated WHOIS lookups / live page fetches / TLS handshakes for the same
# target.
CACHE_TTL_SECONDS = 600


def assessment_to_dict(a: RiskAssessment) -> dict:
    return {
        "risk_score": a.risk_score,
        "threat_level": a.threat_level,
        "confidence": a.confidence,
        "detected_threats": a.detected_threats,
        "indicators": a.indicators,
        "recommendations": a.recommendations,
        "explanation": a.explanation,
    }


def dict_to_assessment(d: dict) -> RiskAssessment:
    return RiskAssessment(**d)


def persist_scan(db, user_id, scan_type, target_summary, payload, assessment) -> models.Scan:
    scan = models.Scan(
        user_id=user_id,
        scan_type=scan_type,
        target_summary=target_summary,
        input_payload=crypto.encrypt_text(json.dumps(payload)[:4000]),
        risk_score=assessment.risk_score,
        threat_level=assessment.threat_level,
        confidence=assessment.confidence,
        detected_threats=json.dumps(assessment.detected_threats),
        indicators=json.dumps(assessment.indicators),
        recommendations=json.dumps(assessment.recommendations),
        explanation=assessment.explanation,
    )
    db.add(scan)

    if assessment.threat_level in ("High", "Critical"):
        db.add(models.Notification(
            user_id=user_id,
            title=f"{assessment.threat_level} risk {scan_type} threat detected",
            message=assessment.explanation[:200],
            severity="critical" if assessment.threat_level == "Critical" else "high",
        ))
        _push_to_user_devices(db, user_id, scan_type, assessment)

    db.commit()
    db.refresh(scan)
    return scan


def _push_to_user_devices(db, user_id: str, scan_type: str, assessment: RiskAssessment) -> None:
    """
    Sends an actual device push for this High/Critical scan, if the user
    has any registered devices AND Firebase is configured (see push.py —
    silently no-ops otherwise, so this is always safe to call). Any
    device tokens Firebase reports as no-longer-valid get pruned from the
    DeviceToken table right away, so they don't keep failing on every
    future High/Critical scan.
    """
    tokens = [t.fcm_token for t in db.query(models.DeviceToken).filter(models.DeviceToken.user_id == user_id).all()]
    if not tokens:
        return

    result = push.send_push(
        tokens,
        title=f"Centinel — {assessment.threat_level} risk detected",
        body=assessment.explanation[:150],
        data={"scan_type": scan_type, "threat_level": assessment.threat_level, "risk_score": assessment.risk_score},
    )

    if result["invalid"]:
        db.query(models.DeviceToken).filter(models.DeviceToken.fcm_token.in_(result["invalid"])).delete(
            synchronize_session=False
        )


def scan_to_out(scan: models.Scan) -> schemas.ScanResult:
    return schemas.ScanResult(
        id=scan.id,
        scan_type=scan.scan_type,
        target_summary=scan.target_summary,
        risk_score=scan.risk_score,
        threat_level=scan.threat_level,
        confidence=scan.confidence,
        detected_threats=json.loads(scan.detected_threats),
        indicators=json.loads(scan.indicators),
        recommendations=json.loads(scan.recommendations),
        explanation=scan.explanation,
        created_at=scan.created_at,
    )
