import csv
import io
import json
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Query
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from .. import models, schemas, auth
from ..database import get_db
from .scan_router import _to_out

router = APIRouter(tags=["History"])


def _query_history(
    db: Session, user_id: str,
    scan_type: Optional[str], threat_level: Optional[str], search: Optional[str],
):
    q = db.query(models.Scan).filter(models.Scan.user_id == user_id)
    if scan_type:
        q = q.filter(models.Scan.scan_type == scan_type)
    if threat_level:
        q = q.filter(models.Scan.threat_level == threat_level)
    if search:
        like = f"%{search}%"
        q = q.filter(
            (models.Scan.target_summary.ilike(like)) | (models.Scan.explanation.ilike(like))
        )
    return q.order_by(models.Scan.created_at.desc())


@router.get("/history", response_model=list[schemas.ScanResult])
def get_history(
    scan_type: Optional[str] = None,
    threat_level: Optional[str] = None,
    search: Optional[str] = Query(None, description="Search target_summary/explanation"),
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db),
    user: models.User = Depends(auth.get_current_user),
):
    q = _query_history(db, user.id, scan_type, threat_level, search)
    scans = q.offset(offset).limit(limit).all()
    return [_to_out(s) for s in scans]


@router.delete("/history/{scan_id}")
def delete_history_item(scan_id: str, db: Session = Depends(get_db),
                         user: models.User = Depends(auth.get_current_user)):
    scan = db.query(models.Scan).filter(
        models.Scan.id == scan_id, models.Scan.user_id == user.id
    ).first()
    if not scan:
        raise HTTPException(status_code=404, detail="Scan not found")
    db.delete(scan)
    db.commit()
    return {"detail": "Deleted"}


@router.get("/history/export")
def export_history(
    format: str = Query("csv", pattern="^(csv|json)$"),
    scan_type: Optional[str] = None,
    threat_level: Optional[str] = None,
    search: Optional[str] = None,
    db: Session = Depends(get_db),
    user: models.User = Depends(auth.get_current_user),
):
    """Exports the same filtered result set as GET /history, in full
    (no pagination), as either CSV or JSON for offline record-keeping."""
    scans = _query_history(db, user.id, scan_type, threat_level, search).all()

    if format == "json":
        payload = json.dumps([_to_out(s).model_dump(mode="json") for s in scans], indent=2, default=str)
        return StreamingResponse(
            io.BytesIO(payload.encode("utf-8")),
            media_type="application/json",
            headers={"Content-Disposition": "attachment; filename=centinel_scan_history.json"},
        )

    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow([
        "id", "scan_type", "target_summary", "risk_score", "threat_level",
        "confidence", "detected_threats", "indicators", "recommendations",
        "explanation", "created_at",
    ])
    for s in scans:
        writer.writerow([
            s.id, s.scan_type, s.target_summary, s.risk_score, s.threat_level,
            s.confidence,
            "; ".join(json.loads(s.detected_threats)),
            "; ".join(json.loads(s.indicators)),
            "; ".join(json.loads(s.recommendations)),
            s.explanation, s.created_at.isoformat(),
        ])

    return StreamingResponse(
        io.BytesIO(buf.getvalue().encode("utf-8")),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=centinel_scan_history.csv"},
    )
