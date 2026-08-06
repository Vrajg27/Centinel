from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from .. import models, schemas, auth
from ..database import get_db

router = APIRouter(tags=["Notifications"])


@router.get("/notifications", response_model=list[schemas.NotificationOut])
def get_notifications(db: Session = Depends(get_db), user: models.User = Depends(auth.get_current_user)):
    items = (
        db.query(models.Notification)
        .filter(models.Notification.user_id == user.id)
        .order_by(models.Notification.created_at.desc())
        .all()
    )
    return items


@router.post("/notification/read")
def mark_read(notification_id: str, db: Session = Depends(get_db),
              user: models.User = Depends(auth.get_current_user)):
    n = db.query(models.Notification).filter(
        models.Notification.id == notification_id, models.Notification.user_id == user.id
    ).first()
    if not n:
        raise HTTPException(status_code=404, detail="Notification not found")
    n.is_read = True
    db.commit()
    return {"detail": "Marked as read"}
