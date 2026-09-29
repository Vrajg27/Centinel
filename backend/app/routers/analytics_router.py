import datetime as dt
from collections import Counter
from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from .. import models, auth
from ..database import get_db
from ..limiter import limiter

router = APIRouter(tags=["Analytics"])


@router.get("/analytics")
@limiter.limit("30/minute")
def get_analytics(request: Request, db: Session = Depends(get_db), user: models.User = Depends(auth.get_current_user)):
    scans = db.query(models.Scan).filter(models.Scan.user_id == user.id).all()

    now = dt.datetime.utcnow()
    daily = [s for s in scans if (now - s.created_at).days < 1]
    weekly = [s for s in scans if (now - s.created_at).days < 7]
    monthly = [s for s in scans if (now - s.created_at).days < 30]

    by_type = Counter(s.scan_type for s in scans)
    by_level = Counter(s.threat_level for s in scans)

    avg_risk = round(sum(s.risk_score for s in scans) / len(scans), 1) if scans else 0

    return {
        "total_scans": len(scans),
        "daily_scans": len(daily),
        "weekly_scans": len(weekly),
        "monthly_scans": len(monthly),
        "average_risk_score": avg_risk,
        "scans_by_type": dict(by_type),
        "threat_level_distribution": dict(by_level),
        "high_risk_count": by_level.get("High", 0) + by_level.get("Critical", 0),
    }
