from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import func
from sqlalchemy.orm import Session

from .. import models, auth
from ..database import get_db
from ..limiter import limiter

router = APIRouter(prefix="/admin", tags=["Admin"], dependencies=[Depends(auth.get_current_admin)])


@router.get("/users")
@limiter.limit("30/minute")
def list_users(request: Request, db: Session = Depends(get_db)):
    users = db.query(models.User).order_by(models.User.created_at.desc()).all()
    scan_counts = dict(
        db.query(models.Scan.user_id, func.count(models.Scan.id))
        .group_by(models.Scan.user_id)
        .all()
    )
    return [
        {
            "id": u.id,
            "email": u.email,
            "full_name": u.full_name,
            "is_admin": u.is_admin,
            "created_at": u.created_at,
            "scan_count": scan_counts.get(u.id, 0),
        }
        for u in users
    ]


@router.post("/users/{user_id}/promote")
@limiter.limit("10/minute")
def promote_user(request: Request, user_id: str, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.is_admin = True
    db.commit()
    return {"detail": f"{user.email} is now an admin."}


@router.post("/users/{user_id}/demote")
@limiter.limit("10/minute")
def demote_user(request: Request, user_id: str, db: Session = Depends(get_db),
                 current_admin: models.User = Depends(auth.get_current_admin)):
    if user_id == current_admin.id:
        raise HTTPException(status_code=400, detail="You can't demote your own account.")
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.is_admin = False
    db.commit()
    return {"detail": f"{user.email} is no longer an admin."}


@router.delete("/users/{user_id}")
@limiter.limit("10/minute")
def delete_user(request: Request, user_id: str, db: Session = Depends(get_db),
                 current_admin: models.User = Depends(auth.get_current_admin)):
    if user_id == current_admin.id:
        raise HTTPException(status_code=400, detail="You can't delete your own account.")
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    db.query(models.Scan).filter(models.Scan.user_id == user_id).delete()
    db.query(models.Notification).filter(models.Notification.user_id == user_id).delete()
    db.delete(user)
    db.commit()
    return {"detail": f"{user.email} and their data have been deleted."}


@router.get("/scans")
@limiter.limit("30/minute")
def list_all_scans(
    request: Request,
    scan_type: Optional[str] = None,
    threat_level: Optional[str] = None,
    user_email: Optional[str] = None,
    limit: int = 100,
    offset: int = 0,
    db: Session = Depends(get_db),
):
    q = db.query(models.Scan, models.User.email).join(models.User, models.Scan.user_id == models.User.id)
    if scan_type:
        q = q.filter(models.Scan.scan_type == scan_type)
    if threat_level:
        q = q.filter(models.Scan.threat_level == threat_level)
    if user_email:
        q = q.filter(models.User.email.ilike(f"%{user_email}%"))
    rows = q.order_by(models.Scan.created_at.desc()).offset(offset).limit(limit).all()
    return [
        {
            "id": scan.id,
            "user_email": email,
            "scan_type": scan.scan_type,
            "target_summary": scan.target_summary,
            "risk_score": scan.risk_score,
            "threat_level": scan.threat_level,
            "created_at": scan.created_at,
        }
        for scan, email in rows
    ]


@router.get("/stats")
@limiter.limit("30/minute")
def system_stats(request: Request, db: Session = Depends(get_db)):
    total_users = db.query(func.count(models.User.id)).scalar()
    admin_count = db.query(func.count(models.User.id)).filter(models.User.is_admin == True).scalar()  # noqa: E712
    total_scans = db.query(func.count(models.Scan.id)).scalar()

    by_type = dict(db.query(models.Scan.scan_type, func.count(models.Scan.id)).group_by(models.Scan.scan_type).all())
    by_level = dict(db.query(models.Scan.threat_level, func.count(models.Scan.id)).group_by(models.Scan.threat_level).all())

    return {
        "total_users": total_users,
        "admin_count": admin_count,
        "total_scans": total_scans,
        "scans_by_type": by_type,
        "threat_level_distribution": by_level,
    }


@router.get("/flagged-ips")
@limiter.limit("30/minute")
def flagged_ips(request: Request, db: Session = Depends(get_db)):
    """Sender IPs from Email Header Analyzer scans that came back High/Critical,
    with how many times each has been flagged across all users."""
    rows = (
        db.query(models.Scan.target_summary, func.count(models.Scan.id))
        .filter(models.Scan.scan_type == "header")
        .filter(models.Scan.threat_level.in_(["High", "Critical"]))
        .filter(models.Scan.target_summary != "unknown sender")
        .group_by(models.Scan.target_summary)
        .order_by(func.count(models.Scan.id).desc())
        .all()
    )
    return [{"ip": ip, "flag_count": count} for ip, count in rows]


@router.get("/blocked-domains")
@limiter.limit("30/minute")
def blocked_domains(request: Request, db: Session = Depends(get_db)):
    """URLs/QR targets that came back High/Critical, with flag counts —
    a system-wide view of what's been getting auto-blocked across users."""
    rows = (
        db.query(models.Scan.target_summary, func.count(models.Scan.id))
        .filter(models.Scan.scan_type.in_(["url", "qr"]))
        .filter(models.Scan.threat_level.in_(["High", "Critical"]))
        .group_by(models.Scan.target_summary)
        .order_by(func.count(models.Scan.id).desc())
        .limit(200)
        .all()
    )
    return [{"target": target, "flag_count": count} for target, count in rows]


@router.get("/system-logs")
@limiter.limit("30/minute")
def system_logs(request: Request, limit: int = 100, db: Session = Depends(get_db)):
    """There's no separate audit-log table in this build, so this surfaces
    the most security-relevant events that already exist: every High/Critical
    scan across all users, newest first — a reasonable proxy for a system log
    without adding new infrastructure."""
    rows = (
        db.query(models.Scan, models.User.email)
        .join(models.User, models.Scan.user_id == models.User.id)
        .filter(models.Scan.threat_level.in_(["High", "Critical"]))
        .order_by(models.Scan.created_at.desc())
        .limit(limit)
        .all()
    )
    return [
        {
            "timestamp": scan.created_at,
            "user_email": email,
            "event": f"{scan.threat_level} risk {scan.scan_type} scan",
            "target": scan.target_summary,
            "risk_score": scan.risk_score,
        }
        for scan, email in rows
    ]
