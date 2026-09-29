import datetime as dt
from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from .. import models, schemas, auth
from ..database import get_db
from ..limiter import limiter

router = APIRouter(tags=["Notifications"])


@router.get("/notifications", response_model=list[schemas.NotificationOut])
@limiter.limit("60/minute")
def get_notifications(request: Request, db: Session = Depends(get_db), user: models.User = Depends(auth.get_current_user)):
    items = (
        db.query(models.Notification)
        .filter(models.Notification.user_id == user.id)
        .order_by(models.Notification.created_at.desc())
        .all()
    )
    return items


@router.post("/notification/read")
@limiter.limit("60/minute")
def mark_read(request: Request, notification_id: str, db: Session = Depends(get_db),
              user: models.User = Depends(auth.get_current_user)):
    n = db.query(models.Notification).filter(
        models.Notification.id == notification_id, models.Notification.user_id == user.id
    ).first()
    if not n:
        raise HTTPException(status_code=404, detail="Notification not found")
    n.is_read = True
    db.commit()
    return {"detail": "Marked as read"}


@router.post("/notifications/register-device")
@limiter.limit("20/minute")
def register_device(request: Request, payload: schemas.RegisterDeviceRequest, db: Session = Depends(get_db),
                     user: models.User = Depends(auth.get_current_user)):
    """
    Registers (or refreshes) an FCM device token for the current user, so
    High/Critical scans can push a notification to it — see app/push.py.
    Safe to call every time the app starts or the token rotates (FCM tokens
    do rotate periodically); this upserts rather than erroring on a repeat
    registration of the same token, and re-associates it with whichever
    user is currently logged in if it was previously registered to someone
    else (e.g. a shared/reset device).
    """
    existing = db.query(models.DeviceToken).filter(models.DeviceToken.fcm_token == payload.fcm_token).first()
    if existing:
        existing.user_id = user.id
        existing.platform = payload.platform
        existing.last_used_at = dt.datetime.utcnow()
    else:
        db.add(models.DeviceToken(
            user_id=user.id, fcm_token=payload.fcm_token, platform=payload.platform,
        ))
    db.commit()
    return {"detail": "Device registered for push notifications."}


@router.post("/notifications/unregister-device")
@limiter.limit("20/minute")
def unregister_device(request: Request, payload: schemas.UnregisterDeviceRequest, db: Session = Depends(get_db),
                       user: models.User = Depends(auth.get_current_user)):
    """Called on logout so a signed-out device stops receiving pushes for
    the account that just logged out of it."""
    db.query(models.DeviceToken).filter(
        models.DeviceToken.fcm_token == payload.fcm_token, models.DeviceToken.user_id == user.id,
    ).delete()
    db.commit()
    return {"detail": "Device unregistered."}
