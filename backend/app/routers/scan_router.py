import json
from fastapi import APIRouter, Depends, Request, UploadFile, File as FastFile
from sqlalchemy.orm import Session

from .. import models, schemas, auth, crypto
from ..database import get_db
from ..limiter import limiter
from ..engine import (
    url_analysis, email_analysis, sms_analysis, password_analysis,
    ssl_analysis, header_analysis, file_analysis, breach_check, website_analysis,
)

router = APIRouter(prefix="/scan", tags=["Scanning"])


def _persist(db, user_id, scan_type, target_summary, payload, assessment) -> models.Scan:
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

    db.commit()
    db.refresh(scan)
    return scan


def _to_out(scan: models.Scan) -> schemas.ScanResult:
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


@router.post("/url", response_model=schemas.ScanResult)
@limiter.limit("30/minute")
def scan_url(request: Request, payload: schemas.UrlScanRequest, db: Session = Depends(get_db),
             user: models.User = Depends(auth.get_current_user)):
    assessment = url_analysis.analyze_url(payload.url)
    scan = _persist(db, user.id, "url", payload.url, {"url": payload.url}, assessment)
    return _to_out(scan)


@router.post("/website")
@limiter.limit("15/minute")
def scan_website(request: Request, payload: schemas.WebsiteScanRequest, db: Session = Depends(get_db),
                  user: models.User = Depends(auth.get_current_user)):
    """
    Unlike /scan/url (which only inspects the URL string), this actually
    fetches the page: follows its redirect chain, and inspects the landing
    page's HTML/JS for hidden iframes, obfuscated scripts, meta-refresh
    bouncing, and login forms that submit credentials to a different domain
    than the page itself. Rate-limited tighter than /scan/url since each
    call does a live network fetch (with an SSRF guard against internal
    addresses) rather than pure string analysis.
    """
    assessment, extracted = website_analysis.analyze_website(payload.url)
    scan = _persist(db, user.id, "website", payload.url, {"url": payload.url}, assessment)
    result = _to_out(scan).model_dump()
    result["extracted"] = extracted
    return result


@router.post("/email", response_model=schemas.ScanResult)
@limiter.limit("30/minute")
def scan_email(request: Request, payload: schemas.EmailScanRequest, db: Session = Depends(get_db),
                user: models.User = Depends(auth.get_current_user)):
    assessment = email_analysis.analyze_email(
        subject=payload.subject, sender=payload.sender, body=payload.body, raw_email=payload.raw_email
    )
    summary = payload.subject or (payload.sender or "email")[:60]
    scan = _persist(db, user.id, "email", summary, payload.model_dump(), assessment)
    return _to_out(scan)


@router.post("/email/upload", response_model=schemas.ScanResult)
@limiter.limit("20/minute")
async def scan_email_file(request: Request, file: UploadFile = FastFile(...), db: Session = Depends(get_db),
                           user: models.User = Depends(auth.get_current_user)):
    """
    Accepts a raw .eml file upload as an alternative to pasting email text
    into POST /scan/email. Parses subject/sender/body out of the file (see
    email_analysis.parse_eml) and runs it through the exact same detection
    logic — including a headers-based SPF/DKIM/DMARC check, since a real
    .eml file (unlike pasted body text) actually has real headers.
    """
    raw_bytes = await file.read()
    parsed = email_analysis.parse_eml(raw_bytes)
    assessment = email_analysis.analyze_email(**parsed)
    summary = parsed["subject"] or (parsed["sender"] or file.filename or "email")[:60]
    scan = _persist(db, user.id, "email", summary,
                     {"filename": file.filename, "subject": parsed["subject"], "sender": parsed["sender"]},
                     assessment)
    return _to_out(scan)


@router.post("/header")
@limiter.limit("30/minute")
def scan_header(request: Request, payload: schemas.HeaderScanRequest, db: Session = Depends(get_db),
                 user: models.User = Depends(auth.get_current_user)):
    assessment, extracted = header_analysis.analyze_headers(payload.raw_headers)
    scan = _persist(db, user.id, "header", extracted.get("sender_ip") or "unknown sender",
                     {"raw_headers_len": len(payload.raw_headers)}, assessment)
    result = _to_out(scan).model_dump()
    result["extracted"] = extracted
    return result


@router.post("/sms", response_model=schemas.ScanResult)
@limiter.limit("30/minute")
def scan_sms(request: Request, payload: schemas.SmsScanRequest, db: Session = Depends(get_db),
             user: models.User = Depends(auth.get_current_user)):
    assessment = sms_analysis.analyze_sms(payload.message, payload.sender)
    scan = _persist(db, user.id, "sms", payload.message[:60], payload.model_dump(), assessment)
    return _to_out(scan)


@router.post("/qr", response_model=schemas.ScanResult)
@limiter.limit("30/minute")
def scan_qr(request: Request, payload: schemas.QrScanRequest, db: Session = Depends(get_db),
            user: models.User = Depends(auth.get_current_user)):
    # QR codes almost always decode to a URL; reuse the URL analysis engine.
    assessment = url_analysis.analyze_url(payload.decoded_text)
    scan = _persist(db, user.id, "qr", payload.decoded_text, payload.model_dump(), assessment)
    return _to_out(scan)


@router.post("/password")
@limiter.limit("30/minute")
def scan_password(request: Request, payload: schemas.PasswordScanRequest,
                   db: Session = Depends(get_db),
                   user: models.User = Depends(auth.get_current_user)):
    result = password_analysis.analyze_password(payload.password)
    # Never store the plaintext password or its derivatives beyond this response.
    scan = models.Scan(
        user_id=user.id,
        scan_type="password",
        target_summary=f"{result['length']}-character password",
        input_payload=crypto.encrypt_text(json.dumps({"length": result["length"]})),
        risk_score=result["risk_score"],
        threat_level=(
            "Safe" if result["strength"] in ("Very Strong", "Strong") else
            "Low" if result["strength"] == "Moderate" else
            "High" if result["strength"] == "Weak" else "Critical"
        ),
        confidence=0.9,
        detected_threats=json.dumps(["weak_password"] if result["risk_score"] > 50 else ["none"]),
        indicators=json.dumps(result["recommendations"]),
        recommendations=json.dumps(result["recommendations"]),
        explanation=result["explanation"],
    )
    db.add(scan)
    db.commit()
    db.refresh(scan)
    out = _to_out(scan).model_dump()
    out["details"] = {k: v for k, v in result.items() if k not in ("recommendations", "explanation", "risk_score")}
    return out


@router.post("/ssl")
@limiter.limit("15/minute")
def scan_ssl(request: Request, payload: schemas.SslScanRequest, db: Session = Depends(get_db),
             user: models.User = Depends(auth.get_current_user)):
    assessment, cert_info = ssl_analysis.analyze_ssl(payload.hostname)
    scan = _persist(db, user.id, "ssl", payload.hostname, {"hostname": payload.hostname}, assessment)
    result = _to_out(scan).model_dump()
    result["certificate"] = cert_info
    return result


@router.post("/breach")
@limiter.limit("30/minute")
def scan_breach(request: Request, payload: schemas.BreachScanRequest, db: Session = Depends(get_db),
                 user: models.User = Depends(auth.get_current_user)):
    assessment, extracted = breach_check.check_breach(payload.email)
    scan = _persist(db, user.id, "breach", payload.email, {"email": payload.email}, assessment)
    result = _to_out(scan).model_dump()
    result["details"] = extracted
    return result


@router.post("/file")
@limiter.limit("20/minute")
async def scan_file(request: Request, file: UploadFile = FastFile(...), db: Session = Depends(get_db),
                     user: models.User = Depends(auth.get_current_user)):
    data = await file.read()
    assessment, info = file_analysis.analyze_file(file.filename, data)
    scan = _persist(db, user.id, "file", file.filename,
                     {"filename": file.filename, "size": len(data)}, assessment)
    result = _to_out(scan).model_dump()
    result["file_info"] = info
    return result
