import datetime as dt
import hashlib
import secrets
from typing import Optional

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import jwt, JWTError
from passlib.context import CryptContext
from sqlalchemy.orm import Session

from . import models, config
from .database import get_db

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def _create_token(data: dict, expires_delta: dt.timedelta) -> str:
    to_encode = data.copy()
    expire = dt.datetime.utcnow() + expires_delta
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, config.SECRET_KEY, algorithm=config.ALGORITHM)


def create_access_token(user_id: str) -> str:
    return _create_token(
        {"sub": user_id, "type": "access"},
        dt.timedelta(minutes=config.ACCESS_TOKEN_EXPIRE_MINUTES),
    )


def create_refresh_token(user_id: str) -> str:
    return _create_token(
        {"sub": user_id, "type": "refresh"},
        dt.timedelta(days=config.REFRESH_TOKEN_EXPIRE_DAYS),
    )


def _hash_reset_token(raw_token: str) -> str:
    return hashlib.sha256(raw_token.encode()).hexdigest()


def issue_password_reset_token(user: models.User) -> str:
    """
    Issues a new password-reset token for this user and returns the raw
    (unhashed) token to send to them — this is the ONLY place the raw
    value ever exists; only its SHA-256 hash is persisted, so a database
    read alone can never be used to reset an account's password.

    Overwrites whatever reset_token_hash/reset_token_expires this user
    already had, so requesting a new reset link automatically invalidates
    any previous one — there's only ever one live token per user. Doesn't
    commit; the caller is expected to commit (see auth_router.forgot_password).
    """
    raw_token = secrets.token_urlsafe(32)
    user.reset_token_hash = _hash_reset_token(raw_token)
    user.reset_token_expires = dt.datetime.utcnow() + dt.timedelta(minutes=config.RESET_TOKEN_EXPIRE_MINUTES)
    return raw_token


def consume_password_reset_token(db: Session, raw_token: str) -> Optional[models.User]:
    """
    Validates a reset token and, if valid, atomically consumes it (clears
    reset_token_hash/reset_token_expires so it can never be used again —
    this is what makes it single-use) and returns the matching user.

    Returns None for an unknown, expired, or already-used token, without
    distinguishing between those cases — callers should surface a single
    generic "Invalid or expired reset link" error either way, both to
    avoid leaking account existence and because a used-up token and an
    expired one aren't meaningfully different to whoever's holding it.

    Doesn't commit; the caller commits the token consumption together with
    the password change itself (see auth_router.reset_password) so the two
    happen atomically — a token can never be marked used without the
    password actually having been changed, or vice versa.
    """
    token_hash = _hash_reset_token(raw_token)
    user = db.query(models.User).filter(models.User.reset_token_hash == token_hash).first()
    if not user or not user.reset_token_expires or user.reset_token_expires < dt.datetime.utcnow():
        return None
    user.reset_token_hash = None
    user.reset_token_expires = None
    return user


def decode_token(token: str) -> dict:
    try:
        return jwt.decode(token, config.SECRET_KEY, algorithms=[config.ALGORITHM])
    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
        )


def get_current_user(
    token: str = Depends(oauth2_scheme), db: Session = Depends(get_db)
) -> models.User:
    payload = decode_token(token)
    if payload.get("type") != "access":
        raise HTTPException(status_code=401, detail="Invalid token type")
    user_id: Optional[str] = payload.get("sub")
    user = db.query(models.User).filter(models.User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    return user


def get_current_admin(current_user: models.User = Depends(get_current_user)) -> models.User:
    """Gate for admin-only routes. Depends on get_current_user first, so an
    unauthenticated request correctly gets 401 rather than 403 — 403 would
    leak that the route exists and requires elevated privileges."""
    if not current_user.is_admin:
        raise HTTPException(status_code=403, detail="Admin privileges required")
    return current_user
