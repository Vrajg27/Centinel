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

    scans = relationship("Scan", back_populates="owner")
    notifications = relationship("Notification", back_populates="owner")


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
