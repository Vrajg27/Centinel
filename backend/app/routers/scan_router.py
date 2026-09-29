import json
from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile, File as FastFile
from sqlalchemy.orm import Session

from .. import models, schemas, auth, crypto, redis_client, storage
from ..database import get_db
from ..limiter import limiter
from ..engine import (
    url_analysis, email_analysis, sms_analysis, password_analysis,
    ssl_analysis, header_analysis, file_analysis, breach_check, website_analysis,
)
from ..scan_helpers import (
    persist_scan as _persist,
    scan_to_out as _to_out,
    assessment_to_dict as _assessment_to_dict,
    dict_to_assessment as _dict_to_assessment,
    CACHE_TTL_SECONDS,
)

# Background task processing (Celery) is entirely optional infrastructure —
# every endpoint above works synchronously with zero setup. This import is
# guarded so a server without the `celery` package installed still starts
# up fine; only the /scan/*/async and /scan/tasks/{id} endpoints below
# become unavailable (503) in that case.
try:
    from celery.result import AsyncResult
    from .. import tasks as celery_tasks
    from ..celery_app import celery_app
    CELERY_AVAILABLE = True
except ImportError:
    CELERY_AVAILABLE = False

router = APIRouter(prefix="/scan", tags=["Scanning"])


@router.post("/url", response_model=schemas.ScanResult)
@limiter.limit("30/minute")
def scan_url(request: Request, payload: schemas.UrlScanRequest, db: Session = Depends(get_db),
             user: models.User = Depends(auth.get_current_user)):
    cache_key = f"scan:url:{payload.url.strip().lower()}"
    cached = redis_client.cache_get(cache_key)
    if cached:
        assessment = _dict_to_assessment(cached)
    else:
        assessment = url_analysis.analyze_url(payload.url)
        redis_client.cache_set(cache_key, _assessment_to_dict(assessment), CACHE_TTL_SECONDS)
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
    cache_key = f"scan:website:{payload.url.strip().lower()}"
    cached = redis_client.cache_get(cache_key)
    if cached:
        assessment = _dict_to_assessment(cached["assessment"])
        extracted = cached["extracted"]
    else:
        assessment, extracted = website_analysis.analyze_website(payload.url)
        redis_client.cache_set(
            cache_key, {"assessment": _assessment_to_dict(assessment), "extracted": extracted}, CACHE_TTL_SECONDS
        )
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
    cache_key = f"scan:ssl:{payload.hostname.strip().lower()}"
    cached = redis_client.cache_get(cache_key)
    if cached:
        assessment = _dict_to_assessment(cached["assessment"])
        cert_info = cached["cert_info"]
    else:
        assessment, cert_info = ssl_analysis.analyze_ssl(payload.hostname)
        redis_client.cache_set(
            cache_key, {"assessment": _assessment_to_dict(assessment), "cert_info": cert_info}, CACHE_TTL_SECONDS
        )
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
    # Off by default (CENTINEL_PERSIST_FILES=false) — the file is analyzed
    # in-memory above and `data` simply falls out of scope unsaved. When
    # enabled, persistence happens after analysis and never affects the
    # scan result either way; see app/storage.py.
    result["storage_reference"] = storage.store_file(file.filename, data, scan.id)
    return result


# ---- Background (Celery) variants ----
# Opt-in async processing for the two slowest scan types: URL scanning (the
# WHOIS lookup can take several seconds) and Website scanning (a live page
# fetch + redirect following). Everything else stays fast enough that
# blocking the request is fine, so there's no async variant for them.


def _require_celery():
    if not CELERY_AVAILABLE:
        raise HTTPException(
            status_code=503,
            detail="Background task processing isn't available on this server (the 'celery' "
                   "package isn't installed). Use the synchronous endpoint instead, or see "
                   "backend/README.md for how to enable it.",
        )


def _enqueue(task, user_id: str, url: str) -> dict:
    try:
        async_result = task.delay(user_id, url)
    except Exception as e:
        raise HTTPException(
            status_code=503,
            detail=f"Could not queue the scan — is the Celery broker (Redis) reachable? ({e})",
        )
    # Records who owns this task so GET /scan/tasks/{id} can't be used by
    # one user to read another user's scan result. This intentionally
    # fails OPEN (allows polling) if the ownership record is missing —
    # e.g. its TTL expired, or the cache backend hiccuped — rather than
    # blocking a legitimate poll; it only fails CLOSED (403) when there IS
    # a record and it names someone else.
    redis_client.cache_set(f"task_owner:{async_result.id}", {"user_id": user_id}, 3600)
    return {"task_id": async_result.id, "status": "queued"}


@router.post("/url/async")
@limiter.limit("30/minute")
def scan_url_async(request: Request, payload: schemas.UrlScanRequest,
                    user: models.User = Depends(auth.get_current_user)):
    _require_celery()
    return _enqueue(celery_tasks.scan_url_task, user.id, payload.url)


@router.post("/website/async")
@limiter.limit("15/minute")
def scan_website_async(request: Request, payload: schemas.WebsiteScanRequest,
                        user: models.User = Depends(auth.get_current_user)):
    _require_celery()
    return _enqueue(celery_tasks.scan_website_task, user.id, payload.url)


@router.get("/tasks/{task_id}")
@limiter.limit("120/minute")
def get_task_status(request: Request, task_id: str, user: models.User = Depends(auth.get_current_user)):
    _require_celery()

    owner = redis_client.cache_get(f"task_owner:{task_id}")
    if owner is not None and owner.get("user_id") != user.id:
        raise HTTPException(status_code=403, detail="This task does not belong to you.")

    result = AsyncResult(task_id, app=celery_app)
    if result.state == "PENDING":
        return {"task_id": task_id, "status": "pending"}
    if result.state == "STARTED":
        return {"task_id": task_id, "status": "running"}
    if result.state == "SUCCESS":
        return {"task_id": task_id, "status": "completed", "result": result.result}
    if result.state == "FAILURE":
        return {"task_id": task_id, "status": "failed", "error": str(result.result)}
    return {"task_id": task_id, "status": result.state.lower()}
