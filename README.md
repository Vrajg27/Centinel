# 🛡️ Centinel

> AI-Powered Cybersecurity Platform for Intelligent Threat Detection

Centinel is a comprehensive cybersecurity platform designed to help users identify and analyze digital threats through an AI-powered scanning engine. The project consists of a **FastAPI backend**, an **Android application**, and a **Browser Extension**, providing a unified experience for detecting phishing attempts, malicious websites, suspicious emails, weak passwords, unsafe files, QR code attacks, SSL issues, SMS scams, and data breaches.

---

## ✨ Features

### 🔍 Security Scanners

- 🌐 URL Scanner
- 🖥️ Website Scanner
  - Redirect chain analysis
  - JavaScript inspection
  - Login form detection
- 📧 Email Scanner
  - Raw email analysis
  - `.eml` file support
- 📨 Email Header Analyzer
- 📱 SMS Scam Detection
- 🔳 QR Code Scanner
- 📄 File Scanner
- 🔑 Password Strength Analyzer
- 🔒 SSL Certificate Checker
- 📊 Data Breach Detection

---

## 🤖 AI-Powered Analysis

Centinel uses an offline heuristic AI engine to analyze threats without relying on third-party APIs.

The detection engine performs:

- Threat scoring
- Risk classification
- Pattern matching
- Rule-based phishing detection
- Suspicious content identification
- Security recommendations

The architecture is designed so external threat intelligence providers (VirusTotal, Google Safe Browsing, AbuseIPDB, HaveIBeenPwned, SSL Labs, etc.) can be integrated later without modifying existing endpoints.

---

# 🏗 Project Structure

```
Centinel/
│
├── backend/          # FastAPI Backend
├── android/          # Android Application
├── extension/        # Browser Extension
└── README.md
```

---

# 🚀 Technology Stack

## Backend

- FastAPI
- Python
- SQLAlchemy
- JWT Authentication
- RBAC Authorization
- Offline Heuristic AI Engine

## Android

- Kotlin
- Jetpack Compose
- MVVM Architecture

## Browser Extension

- Manifest V3
- JavaScript (ES Modules)

---

# 📱 Applications

## Backend

Provides:

- Authentication
- Scan APIs
- AI Threat Detection
- Report Generation
- History
- Analytics
- Notifications
- Admin APIs

---

## Android App

Features include:

- User Login
- Dashboard
- Security Scanning
- Threat History
- PDF Report Downloads
- Analytics Dashboard
- Security Tips

---

## Browser Extension

The extension provides:

- URL Scanning
- Website Analysis
- Redirect Monitoring
- Shared Login
- Scan History Synchronization

---

# 🔐 Security Features

- JWT Authentication
- Role-Based Access Control (RBAC)
- Password Reset
- Rate Limiting
- HTTPS Enforcement
- Encrypted Scan Storage
- WHOIS Domain Age Analysis

---

# 📊 Analytics

Centinel includes:

- Threat Distribution Charts
- Scan History
- Risk Statistics
- Downloadable PDF Reports

---

# 📦 Installation

## Backend

```bash
cd backend

pip install -r requirements.txt

uvicorn app.main:app --reload
```

Open:

```
http://localhost:8000/docs
```

---

## Android

1. Open `android/` in Android Studio.
2. Sync Gradle.
3. Configure `API_BASE_URL` if necessary.
4. Run on an emulator or physical device.

---

## Browser Extension

1. Open Chrome/Edge/Brave.
2. Navigate to:

```
chrome://extensions
```

3. Enable **Developer Mode**.
4. Click **Load Unpacked**.
5. Select the `extension` folder.

---

# 📈 Current Status

### ✅ Implemented

- Backend API
- Authentication
- AI Threat Engine
- Website Scanner
- URL Scanner
- Email Scanner
- Email Header Analysis
- Password Analysis
- SSL Checker
- QR Scanner
- SMS Scanner
- File Scanner
- Data Breach Checker
- History
- Analytics
- PDF Reports
- Browser Extension
- Android Client
- Admin APIs
- RBAC
- Encryption at Rest
- Rate Limiting

---

# 🔮 Planned Improvements

- VirusTotal Integration
- Google Safe Browsing
- AbuseIPDB
- HaveIBeenPwned
- SSL Labs Integration
- Firebase Push Notifications
- Background Scan Jobs
- Redis Caching
- Admin Dashboard UI

---

# 📄 License

This project is intended for educational, research, and cybersecurity learning purposes.

---

# 👨‍💻 Author

Developed as an AI-powered cybersecurity platform demonstrating modern application development across backend, mobile, and browser technologies.

---

## ⭐ Support

If you find this project useful, consider giving it a **⭐ Star** on GitHub.
