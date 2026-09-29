from typing import Optional, List
import datetime as dt
from pydantic import BaseModel, EmailStr, Field


# ---------- Auth ----------
class UserRegister(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8)
    full_name: Optional[str] = None


class UserLogin(BaseModel):
    email: EmailStr
    password: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class ResetPasswordRequest(BaseModel):
    token: str
    new_password: str = Field(min_length=8)


class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str = Field(min_length=8)


class UserUpdate(BaseModel):
    full_name: Optional[str] = None


class UserOut(BaseModel):
    id: str
    email: str
    full_name: Optional[str] = None
    is_admin: bool

    class Config:
        from_attributes = True


# ---------- Scan result (shared shape for every scan type) ----------
class ScanResult(BaseModel):
    id: str
    scan_type: str
    target_summary: str
    risk_score: int
    threat_level: str
    confidence: float
    detected_threats: List[str]
    indicators: List[str]
    recommendations: List[str]
    explanation: str
    created_at: dt.datetime

    class Config:
        from_attributes = True


# ---------- Request bodies ----------
class UrlScanRequest(BaseModel):
    url: str


class WebsiteScanRequest(BaseModel):
    url: str


class EmailScanRequest(BaseModel):
    raw_email: Optional[str] = None
    subject: Optional[str] = None
    sender: Optional[str] = None
    body: Optional[str] = None


class HeaderScanRequest(BaseModel):
    raw_headers: str


class SmsScanRequest(BaseModel):
    message: str
    sender: Optional[str] = None


class PasswordScanRequest(BaseModel):
    password: str = Field(min_length=1)


class SslScanRequest(BaseModel):
    hostname: str


class QrScanRequest(BaseModel):
    decoded_text: str


class BreachScanRequest(BaseModel):
    email: EmailStr


class NotificationOut(BaseModel):
    id: str
    title: str
    message: str
    severity: str
    is_read: bool
    created_at: dt.datetime

    class Config:
        from_attributes = True


class RegisterDeviceRequest(BaseModel):
    fcm_token: str
    platform: str = "android"


class UnregisterDeviceRequest(BaseModel):
    fcm_token: str
