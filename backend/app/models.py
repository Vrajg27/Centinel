import uuid
import datetime as dt

from sqlalchemy import (
    Column, String, Integer, Float, DateTime, Boolean, Text, ForeignKey
)
from sqlalchemy.orm import relationship

from .database import Base


def gen_id():
    return str(uuid.uuid4())


class User(Base):
    __tablename__ = "users"

    id = Column(String, primary_key=True, default=gen_id)
    email = Column(String, unique=True, index=True, nullable=False)
    full_name = Column(String, nullable=True)
    hashed_password = Column(String, nullable=False)
    is_admin = Column(Boolean, default=False)
    created_at = Column(DateTime, default=dt.datetime.utcnow)

    # Password reset (see app/auth.py's create_password_reset_token /
    # consume_password_reset_token). Only a SHA-256 hash of the token is
    # ever stored — never the raw token — so a database compromise alone
    # can't be used to reset anyone's password. A single column pair means
    # requesting a new reset link automatically invalidates any older one
    # (there's only ever one "current" token per user), and reset_token_hash
    # is cleared the moment a token is used, making it single-use.
    reset_token_hash = Column(String, nullable=True, index=True)
    reset_token_expires = Column(DateTime, nullable=True)

    scans = relationship("Scan", back_populates="owner")
    notifications = relationship("Notification", back_populates="owner")
    device_tokens = relationship("DeviceToken", back_populates="owner", cascade="all, delete-orphan")


class Scan(Base):
    __tablename__ = "scans"

    id = Column(String, primary_key=True, default=gen_id)
    user_id = Column(String, ForeignKey("users.id"))
    scan_type = Column(String, index=True)  # url, email, sms, file, ssl, header, password, qr, breach
    target_summary = Column(String)          # short human label, e.g. the URL or masked email
    input_payload = Column(Text)             # Fernet-encrypted JSON string of what was analyzed (see app/crypto.py)
    risk_score = Column(Integer)
    threat_level = Column(String)            # Safe/Low/Medium/High/Critical
    confidence = Column(Float)
    detected_threats = Column(Text)          # JSON list
    indicators = Column(Text)                # JSON list
    recommendations = Column(Text)           # JSON list
    explanation = Column(Text)
    created_at = Column(DateTime, default=dt.datetime.utcnow)

    owner = relationship("User", back_populates="scans")


class Notification(Base):
    __tablename__ = "notifications"

    id = Column(String, primary_key=True, default=gen_id)
    user_id = Column(String, ForeignKey("users.id"))
    title = Column(String)
    message = Column(String)
    severity = Column(String, default="info")
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=dt.datetime.utcnow)

    owner = relationship("User", back_populates="notifications")


class DeviceToken(Base):
    """
    An FCM registration token for one of a user's devices (Android app
    installs, mainly). A user can have several — one per device/reinstall —
    which is why this is its own table with a many-to-one relationship to
    User, rather than a single column on User.
    """
    __tablename__ = "device_tokens"

    id = Column(String, primary_key=True, default=gen_id)
    user_id = Column(String, ForeignKey("users.id"))
    fcm_token = Column(String, unique=True, index=True, nullable=False)
    platform = Column(String, default="android")
    created_at = Column(DateTime, default=dt.datetime.utcnow)
    last_used_at = Column(DateTime, default=dt.datetime.utcnow)

    owner = relationship("User", back_populates="device_tokens")
