from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy.orm import Session

from .. import models, schemas, auth, config
from ..database import get_db
from ..limiter import limiter

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/register", response_model=schemas.UserOut, status_code=201)
@limiter.limit("5/minute")
def register(request: Request, payload: schemas.UserRegister, db: Session = Depends(get_db)):
    existing = db.query(models.User).filter(models.User.email == payload.email).first()
    if existing:
        raise HTTPException(status_code=400, detail="Email already registered")
    user = models.User(
        email=payload.email,
        full_name=payload.full_name,
        hashed_password=auth.hash_password(payload.password),
        is_admin=payload.email.strip().lower() in config.ADMIN_EMAILS,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


@router.post("/login", response_model=schemas.TokenPair)
@limiter.limit("10/minute")
def login(request: Request, payload: schemas.UserLogin, db: Session = Depends(get_db)):
    user = db.query(models.User).filter(models.User.email == payload.email).first()
    if not user or not auth.verify_password(payload.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    return schemas.TokenPair(
        access_token=auth.create_access_token(user.id),
        refresh_token=auth.create_refresh_token(user.id),
    )


@router.post("/refresh", response_model=schemas.TokenPair)
@limiter.limit("30/minute")
def refresh(request: Request, payload: schemas.RefreshRequest, db: Session = Depends(get_db)):
    data = auth.decode_token(payload.refresh_token)
    if data.get("type") != "refresh":
        raise HTTPException(status_code=401, detail="Invalid refresh token")
    user = db.query(models.User).filter(models.User.id == data.get("sub")).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return schemas.TokenPair(
        access_token=auth.create_access_token(user.id),
        refresh_token=auth.create_refresh_token(user.id),
    )


@router.post("/logout")
def logout(current_user: models.User = Depends(auth.get_current_user)):
    # Stateless JWTs: logout is handled client-side by discarding tokens.
    # For true server-side revocation, maintain a token blocklist keyed by jti.
    return {"detail": "Logged out"}


@router.get("/me", response_model=schemas.UserOut)
def me(current_user: models.User = Depends(auth.get_current_user)):
    return current_user


@router.post("/forgot-password")
@limiter.limit("5/minute")
def forgot_password(request: Request, payload: schemas.ForgotPasswordRequest, db: Session = Depends(get_db)):
    """
    Always returns the same generic message whether or not the email exists,
    to avoid leaking which emails are registered (user enumeration).

    NOTE — local/dev behavior: there's no email service wired up in this
    build, so if the account exists, the reset link is printed to the
    server console instead of emailed. Wire a real mail provider (e.g.
    SendGrid/SES) in production and remove the console-print + the
    `dev_reset_url` field from the response before deploying.
    """
    user = db.query(models.User).filter(models.User.email == payload.email).first()
    reset_url = None
    if user:
        token = auth.create_reset_token(user.id)
        reset_url = f"centinel://reset-password?token={token}"
        print(f"[Centinel] Password reset requested for {user.email}: {reset_url}")

    response = {"detail": "If that email is registered, a password reset link has been sent."}
    if reset_url:
        # Dev convenience only — see note above.
        response["dev_reset_url"] = reset_url
    return response


@router.post("/reset-password")
@limiter.limit("5/minute")
def reset_password(request: Request, payload: schemas.ResetPasswordRequest, db: Session = Depends(get_db)):
    user_id = auth.decode_reset_token(payload.token)
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=400, detail="Invalid or expired reset link")

    user.hashed_password = auth.hash_password(payload.new_password)
    db.commit()
    return {"detail": "Password has been reset. You can now log in with your new password."}
