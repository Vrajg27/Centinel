# Centinel Backend (FastAPI)

Runs 100% locally. No cloud account, no API keys required to get started —
every scan module ships with a working offline heuristic/rule-based engine.
Slots exist in `app/config.py` to plug in real VirusTotal / Google Safe
Browsing / AbuseIPDB / HaveIBeenPwned / SSL Labs API keys later without
changing any endpoint code.

## 1. Setup

```bash
cd backend
python3 -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## 2. Run

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- API root: http://localhost:8000
- Interactive docs (Swagger UI): http://localhost:8000/docs
- SQLite database file `centinel.db` is created automatically in this folder.

## 3. Try it

```bash
# Register
curl -X POST http://localhost:8000/auth/register -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"SuperSecret123!","full_name":"You"}'

# Login (grab the access_token from the response)
curl -X POST http://localhost:8000/auth/login -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"SuperSecret123!"}'

# Scan a URL (replace TOKEN)
curl -X POST http://localhost:8000/scan/url -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"url":"http://paypa1-secure-login.verify-account.xyz"}'
```

## Modules implemented

| Endpoint | What it does |
|---|---|
| `POST /scan/url` | Phishing/URL heuristics: shorteners, IP hosts, brand look-alikes, punycode, suspicious TLDs, urgency words, `@` tricks |
| `POST /scan/email` | Phishing email content analysis, display-name spoofing, embedded link scoring, SPF/DKIM/DMARC parsing |
| `POST /scan/header` | Raw header parsing: sender IP extraction, relay chain length, auth failures, geolocation placeholder |
| `POST /scan/sms` | Smishing detection: banking fraud, OTP theft, fake delivery, prize scams, loan scams |
| `POST /scan/qr` | Decoded QR text run through the URL engine |
| `POST /scan/password` | Entropy + dictionary + pattern based password strength (plaintext never stored) |
| `POST /scan/ssl` | Live TLS handshake + certificate expiry/hostname-mismatch checks |
| `POST /scan/file` | Hash generation, dangerous extensions, double-extension tricks, macro/zip inspection |
| `POST /scan/breach` | Offline HIBP-style breach lookup (k-anonymity style hashing) |
| `GET /history`, `DELETE /history/{id}` | Scan history with search/filter |
| `GET /history/export?format=csv\|json` | Full (unpaginated) export of your filtered scan history |
| `GET /analytics` | Daily/weekly/monthly counts, risk distribution |
| `GET /report/{id}` | Downloadable PDF report per scan |
| `GET /notifications`, `POST /notification/read` | High/Critical scans auto-generate notifications |
| `POST /auth/forgot-password`, `POST /auth/reset-password` | Password reset (see note below) |

## Rate limiting

Every route is now rate-limited (via `slowapi`), with tighter limits on the
endpoints most worth protecting:

| Route(s) | Limit |
|---|---|
| `/auth/register`, `/auth/forgot-password`, `/auth/reset-password` | 5/minute |
| `/auth/login` | 10/minute |
| `/scan/ssl` (live TLS handshake per call) | 15/minute |
| `/scan/file` | 20/minute |
| Other `/scan/*` | 30/minute |
| Everything else (history, analytics, notifications, `/auth/refresh`) | 120/minute (app default) |

Limits are keyed by client IP. Tune them in `app/limiter.py` (the default) or
per-route via the `@limiter.limit(...)` decorator in each router.

## Password reset (dev-mode behavior)

`POST /auth/forgot-password` issues a short-lived (15 min) JWT reset token.
**There's no email service wired up in this local build**, so if the account
exists, the reset link is:
1. printed to the server console, and
2. included in the JSON response as `dev_reset_url` for convenience.

Both of those are dev-only conveniences — **remove them and wire a real
mail provider (SendGrid, SES, etc.) before deploying anywhere reachable by
untrusted users**, since returning the reset link in the API response
defeats the purpose of emailing it in the first place.

```bash
curl -X POST http://localhost:8000/auth/forgot-password -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}'
# -> {"detail": "...", "dev_reset_url": "centinel://reset-password?token=..."}

curl -X POST http://localhost:8000/auth/reset-password -H "Content-Type: application/json" \
  -d '{"token":"<the token from dev_reset_url>","new_password":"NewSecret123!"}'
```

## Domain age (WHOIS) in the URL scanner

`POST /scan/url` now also checks how long ago the domain was registered
(newly-registered domains are one of the strongest phishing signals).
This needs outbound access to WHOIS servers (port 43) and adds ~1-4s of
latency per scan; it fails silently (no signal added, not an error) if the
lookup times out, is blocked by your network, or the registrar's response
can't be parsed. Disable it entirely with:

```bash
export CENTINEL_ENABLE_WHOIS=false
```

## Admin / RBAC

`is_admin` on the User model now actually gates something. All `/admin/*`
routes require it (`get_current_admin` in `app/auth.py`), returning 401 if
you're not logged in and 403 if you're logged in but not an admin.

| Route | What it does |
|---|---|
| `GET /admin/users` | Every registered user + their scan count |
| `POST /admin/users/{id}/promote`, `/demote` | Grant/revoke admin (can't demote/delete yourself) |
| `DELETE /admin/users/{id}` | Deletes a user and cascades their scans/notifications |
| `GET /admin/scans` | Every scan across every user, filterable by type/level/email |
| `GET /admin/stats` | System-wide totals and distributions |
| `GET /admin/flagged-ips` | Sender IPs from Header Analyzer scans flagged High/Critical, with counts |
| `GET /admin/blocked-domains` | URL/QR targets flagged High/Critical, with counts |
| `GET /admin/system-logs` | Every High/Critical scan across all users, newest first — a proxy for a system log since there's no separate audit-log table in this build |

**Bootstrapping your first admin** (there's a chicken-and-egg problem —
promoting a user requires an admin, but you don't have one yet):

```bash
# Option A: set this before that user registers
export CENTINEL_ADMIN_EMAILS=you@example.com

# Option B: promote an existing user directly via the database
cd backend && python scripts/make_admin.py you@example.com
```

## Encryption at rest

`Scan.input_payload` (the raw scanned content — email bodies, SMS text,
headers, filenames) is now encrypted with Fernet (AES-128-CBC + HMAC) before
it touches the database, via `app/crypto.py`. It was never exposed through
the API anyway (not part of the `ScanResult` response), so this is purely a
database-file-theft mitigation.

- Set `CENTINEL_ENCRYPTION_KEY` (output of `Fernet.generate_key()`) in any
  real deployment.
- If unset, a key is auto-generated and cached in `backend/.encryption_key`
  on first run so local dev works with zero setup — **back that file up (or
  set the env var) if you need encrypted rows to survive past a fresh
  checkout**, since losing the key makes existing encrypted data permanently
  unreadable. It's gitignored by default.

`User.email`/`full_name` are intentionally left unencrypted since login
looks users up by email — encrypting a field you need to query on
either breaks lookups or requires deterministic encryption, which weakens
the guarantee it's meant to provide. Password hashing (bcrypt, one-way) was
already handled separately before this phase.

## HTTPS enforcement

Off by default so local dev over `http://localhost:8000` keeps working with
no config. Once this is deployed behind a real domain with a TLS
certificate, turn it on so any stray `http://` request gets redirected:

```bash
export CENTINEL_FORCE_HTTPS=true
```

The Android app was also tightened to match: instead of allowing cleartext
traffic app-wide, `network_security_config.xml` now only permits it for
`10.0.2.2`/`localhost`/`127.0.0.1` (the emulator dev hosts). Any other host
— including a real production `API_BASE_URL` — must be `https://` or the
OS blocks the request outright.

## Website Scanner (distinct from URL Scanner)

`POST /scan/website` actually fetches the page and follows its redirect
chain, unlike `/scan/url` which only ever inspects the URL string. It
checks:

- Redirect hop count and whether the chain crosses domains
- The original and final URLs independently, reusing the URL Scanner's
  brand-impersonation/shortener/TLD heuristics on both
- Hidden iframes, meta-refresh redirects, and a few obfuscated-JavaScript
  patterns (`eval()`, `atob()`, JS-based redirects, right-click disabling)
- **Login forms that submit to a different domain than the page itself** —
  the strongest single signal this module adds, and a very reliable
  indicator of credential harvesting

It includes a basic SSRF guard (rejects private/loopback/link-local/reserved
IP targets before fetching) so a scan request can't be used to probe your
own internal infrastructure through this server. Rate-limited at 15/min
since each call does a live fetch, tighter than `/scan/url`'s 30/min.

Requires `httpx` and `beautifulsoup4` (now in `requirements.txt`); if
either is missing, this degrades gracefully — you still get the
URL-string-based signals, just without the fetch/JS/form analysis.

## .eml file upload

`POST /scan/email/upload` (multipart) is the file-upload counterpart to
`POST /scan/email` (pasted text). It parses a real `.eml` file's
subject/sender/body — preferring the plain-text part over HTML, skipping
attachments — via `email_analysis.parse_eml()` (stdlib `email` module, no
new dependency), then runs it through the exact same detection logic. Since
a real `.eml` has real headers, the SPF/DKIM/DMARC check in
`analyze_email()` actually gets exercised here, unlike with pasted body text.

## Going to production / adding real threat intel

Every analysis module in `app/engine/` produces a list of `Signal(name, description, weight, category)`
objects that get combined by `app/engine/risk_engine.py`. To add a real provider:

1. Add the API key to `app/config.py` (already has slots for VirusTotal, Google Safe Browsing, AbuseIPDB, HIBP).
2. In the relevant module (e.g. `url_analysis.py`), make the API call and append a new `Signal` based on the result.
3. No changes needed anywhere else — scoring, explanation generation, storage, and the API response shape all stay the same.

Swap SQLite for Postgres by changing `SQLALCHEMY_DATABASE_URL` in `app/database.py`.
