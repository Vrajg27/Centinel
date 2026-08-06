import datetime as dt
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


def create_reset_token(user_id: str) -> str:
    return _create_token(
        {"sub": user_id, "type": "reset"},
        dt.timedelta(minutes=config.RESET_TOKEN_EXPIRE_MINUTES),
    )


def decode_reset_token(token: str) -> str:
    """Returns the user_id encoded in a valid, unexpired reset token, or
    raises HTTPException(400) — a 400 (not 401) since this token never
    represents an authenticated session, just a one-time reset grant."""
    try:
        payload = decode_token(token)
    except HTTPException:
        raise HTTPException(status_code=400, detail="Invalid or expired reset link")
    if payload.get("type") != "reset":
        raise HTTPException(status_code=400, detail="Invalid or expired reset link")
    return payload.get("sub")


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
