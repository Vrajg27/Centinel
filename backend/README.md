# Centinel Backend (FastAPI)

Runs 100% locally with zero config. No cloud account, no API keys required
to get started — every scan module ships with a working offline heuristic/
rule-based engine. Real threat-intel providers (VirusTotal, Google Safe
Browsing, AbuseIPDB, HaveIBeenPwned, SSL Labs) are fully implemented and
layer on top the moment you add their API keys to `app/config.py` — see
"Real threat intelligence" further down. Without any keys, every scan runs
on offline heuristics alone with no behavior change or errors.

Optional infrastructure (PostgreSQL, Redis caching, S3 file storage, Celery
background tasks) is documented further down too — none of it is required
for local dev; every default is SQLite + no cache + no background tasks.

## 1. Setup

```bash
cd backend
python3 -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env            # optional — only needed if you're changing any defaults
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
| `POST /scan/website` | Live fetch + redirect-chain following, hidden iframes/obfuscated JS, credential-exfiltration login forms |
| `POST /scan/email` | Phishing email content analysis, display-name spoofing, embedded link scoring, SPF/DKIM/DMARC parsing |
| `POST /scan/email/upload` | Same detection logic, from an uploaded raw `.eml` file instead of pasted text |
| `POST /scan/header` | Raw header parsing: sender IP extraction, relay chain length, auth failures, geolocation placeholder |
| `POST /scan/sms` | Smishing detection: banking fraud, OTP theft, fake delivery, prize scams, loan scams |
| `POST /scan/qr` | Decoded QR text run through the URL engine |
| `POST /scan/password` | Entropy + dictionary + pattern based password strength (plaintext never stored) |
| `POST /scan/ssl` | Live TLS handshake + certificate expiry/hostname-mismatch checks |
| `POST /scan/file` | Hash generation, dangerous extensions, double-extension tricks, macro/zip inspection |
| `POST /scan/breach` | Offline HIBP-style breach lookup (k-anonymity style hashing) |
| `POST /scan/url/async`, `POST /scan/website/async`, `GET /scan/tasks/{id}` | Optional Celery-backed background variants — see "Celery background tasks" below |
| `GET /history`, `DELETE /history/{id}` | Scan history with search/filter |
| `GET /history/export?format=csv\|json` | Full (unpaginated) export of your filtered scan history |
| `GET /analytics` | Daily/weekly/monthly counts, risk distribution |
| `GET /report/{id}` | Downloadable PDF report per scan |
| `GET /notifications`, `POST /notification/read` | High/Critical scans auto-generate notifications |
| `POST /auth/forgot-password`, `POST /auth/reset-password` | Password reset (see note below) |
| `/admin/*` | RBAC-gated system-wide views — see "Admin / RBAC" below |

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

## PostgreSQL

Defaults to a local SQLite file (`centinel.db`) — zero setup. Swap in
PostgreSQL (or any other SQLAlchemy-supported DB) with one env var, no code
changes, since every model/query in this codebase uses plain SQLAlchemy
types with no SQLite-specific syntax:

```bash
createdb centinel   # once, using whatever Postgres tooling you have
export CENTINEL_DATABASE_URL="postgresql://user:password@localhost:5432/centinel"
uvicorn app.main:app --reload
```

Tables are still auto-created by `Base.metadata.create_all()` on startup,
same as with SQLite — there's no separate migration step for a fresh
database. (If you need real schema migrations for an *existing* production
database as the models evolve, add Alembic — this build doesn't include it,
since `create_all()` is additive-only and won't alter existing tables.)

## Redis cache

Repeated scans of the same URL/website/hostname reuse the cached *analysis*
result (WHOIS lookup, live page fetch, TLS handshake) instead of redoing
that network work — each request still creates its own scan-history row
for the requesting user, only the underlying analysis is shared.

```bash
export CENTINEL_REDIS_URL="redis://localhost:6379/0"   # default if unset
```

Cached for 10 minutes (`CACHE_TTL_SECONDS` in `app/scan_helpers.py`) — long
enough to matter, short enough that a domain flipping from safe to
compromised shows up again reasonably quickly. If Redis isn't
installed/reachable, every scan just runs uncached — nothing errors or
degrades in accuracy, it's purely a speed optimization. Disable outright
with `CENTINEL_ENABLE_CACHE=false`.

## File storage (S3 or local disk)

Off by default (`CENTINEL_PERSIST_FILES=false`) — uploaded files are
analyzed entirely in-memory in `POST /scan/file` and discarded. Turn it on
to persist scanned files for audit/retention:

```bash
export CENTINEL_PERSIST_FILES=true
# Local disk (default backend once persistence is on) — saved under backend/uploads/, nothing else to configure.

# Or S3 instead:
export CENTINEL_S3_BUCKET="your-bucket-name"
# plus standard AWS credentials (env vars, an IAM role, or ~/.aws/credentials — boto3 picks these up automatically)
```

The response from `POST /scan/file` includes a `storage_reference` field
(a local path or `s3://bucket/key`, or `null` if persistence is off/failed).
Filenames are sanitized against path traversal before being used as a
storage key. Firebase Storage isn't implemented (would need the
firebase-admin SDK + a service account) but would slot in the same way —
see the comments in `app/storage.py`.

## Celery background tasks

Opt-in async processing for the two slowest scan types — URL scanning (the
WHOIS lookup can take a few seconds) and Website scanning (a live page
fetch + redirect chain). Every other scan type stays synchronous only;
they're fast enough that blocking the request is fine.

**Nothing requires this to be running.** `POST /scan/url` and
`POST /scan/website` still work exactly as before, synchronously, with zero
setup. Celery only adds three *additional* endpoints:

| Route | What it does |
|---|---|
| `POST /scan/url/async` | Queues a URL scan, returns `{"task_id": ..., "status": "queued"}` immediately |
| `POST /scan/website/async` | Same, for the Website Scanner |
| `GET /scan/tasks/{task_id}` | Poll for the result: `pending` → `running` → `completed` (with the full result, same shape as the synchronous endpoint) or `failed` |

If the `celery` package isn't installed, these three routes return `503`
with a clear message rather than failing to start up — the rest of the API
is unaffected either way.

**Running it:**

```bash
# Terminal 1 — make sure Redis is running (also used as the cache above)
redis-server

# Terminal 2 — the worker that actually executes queued scans
cd backend
celery -A app.celery_app worker --loglevel=info

# Terminal 3 — the API server, as usual
uvicorn app.main:app --reload
```

```bash
# Try it
TOKEN="<your access token>"
curl -X POST http://localhost:8000/scan/website/async -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" -d '{"url":"https://example.com"}'
# -> {"task_id": "abc-123", "status": "queued"}

curl http://localhost:8000/scan/tasks/abc-123 -H "Authorization: Bearer $TOKEN"
# -> {"task_id": "abc-123", "status": "completed", "result": {...same shape as /scan/website...}}
```

Task ownership is tracked (via the same Redis instance, 1-hour TTL) so one
user can't poll another user's `task_id` — `GET /scan/tasks/{id}` returns
`403` if the task belongs to someone else. If that ownership record is
missing (TTL expired, or the cache itself is unavailable), polling is
allowed rather than blocked — a deliberate fail-open tradeoff so a Redis
hiccup can't permanently strand a legitimate result behind a 403.

## Real threat intelligence

All five providers from the original spec are implemented in
`app/engine/threat_intel.py` — one module, one consistent pattern (see its
docstring): every function takes a value, returns a plain dict on a
meaningful result or `None` on anything else (no key configured,
unreachable, bad response, parse failure), and never raises. Every call
uses `config.THREAT_INTEL_TIMEOUT_SECONDS` (5s default) so one slow
provider can only delay a scan by a bounded amount — a scan is never made
to hang on a third-party API.

I verified every provider's response-parsing logic against realistic mocked
API responses (VirusTotal malicious/clean, Google Safe Browsing
matched/empty, AbuseIPDB, HIBP breached/404-clean, SSL Labs ready/in-
progress, ip-api.com) — see the test transcripts earlier in this
conversation. I obviously couldn't hit the real, live endpoints from this
sandbox (no API keys, and some of these also just take real network
access I don't have here), so treat the *request shapes* as correct against
each provider's documented API, but the actual live behavior as something
worth confirming once you've got real keys in hand.

| Provider | Where | What it adds | Needs a key? |
|---|---|---|---|
| **VirusTotal** | `/scan/url`, `/scan/file` | Reputation from 70+ AV engines, by existing report only (no submit+poll — see the module docstring for why) | `VIRUSTOTAL_API_KEY` |
| **Google Safe Browsing** | `/scan/url` | The same malware/phishing blocklist Chrome itself warns on | `GOOGLE_SAFE_BROWSING_API_KEY` |
| **AbuseIPDB** | `/scan/header` | Abuse confidence score (0-100) + report count for the sender IP | `ABUSEIPDB_API_KEY` |
| **IP Geolocation** | `/scan/header` | Real country/region/city/ISP/ASN for the sender IP, replacing the old offline placeholder | None — [ip-api.com](https://ip-api.com)'s free tier |
| **Have I Been Pwned** | `/scan/breach` | Real breach lookup, replacing the offline demo dataset | `HIBP_API_KEY` — see note below |
| **SSL Labs** | `/scan/ssl` | TLS configuration letter grade (A+ through F) | None, but cache-only (see below) |

**Without any keys configured**, every one of these functions returns
`None` immediately and the corresponding scan module runs exactly as it did
in Phases 1-4 — pure offline heuristics, no behavior change, no errors.

**HIBP note:** as of this codebase's knowledge cutoff, HaveIBeenPwned's
breach-checking API had moved behind a paid subscription (it was free for
years before that) — verify current pricing/access at
https://haveibeenpwned.com/API/Key. The request/response handling is
written against their documented v3 API shape regardless of what the
current access model is.

**SSL Labs note:** a *fresh* assessment can take 1-2+ minutes, far too slow
for a synchronous scan endpoint, so this only uses `fromCache=on` — if SSL
Labs has no cached result for a host yet, the signal is just skipped for
that scan (not delayed, not retried). No API key needed either way.

**Adding another provider** follows the same pattern: write a function in
`threat_intel.py` matching the `None`-or-dict contract above, call it from
the relevant `app/engine/*.py` module, and append a `Signal` if it comes
back with something worth flagging. Nothing about scoring, explanation
generation, storage, caching, or the API response shape needs to change —
that's the entire point of the `Signal` abstraction in `risk_engine.py`.

Every one of these calls automatically benefits from the Phase 4 Redis
cache (10 min TTL) — a repeat scan of the same URL/hostname within that
window reuses the full cached assessment (including whatever VT/GSB/SSL
Labs contributed) rather than re-querying every provider.

## Push notifications (Firebase Cloud Messaging)

Every High/Critical scan already creates an in-app notification (`GET
/notifications`) with zero setup — that's been true since Phase 1. This
adds actual device push on top, following the same off-by-default,
graceful-degradation pattern as everything else optional in this backend.

**Requires two things together:**
1. The `firebase-admin` package installed (already in `requirements.txt`).
2. `CENTINEL_FIREBASE_CREDENTIALS_PATH` pointing at a Firebase **service
   account** JSON file — from your Firebase project's console: Project
   Settings → Service Accounts → Generate new private key. (This is a
   different file from the Android app's `google-services.json` — that one
   configures the *client*, this one authenticates the *server* to send
   pushes. Keep it secret; treat it like any other credential.)

```bash
export CENTINEL_FIREBASE_CREDENTIALS_PATH=/path/to/serviceAccountKey.json
```

Without both, `app/push.py`'s `send_push()` silently no-ops — in-app
notifications are completely unaffected either way.

| Route | What it does |
|---|---|
| `POST /notifications/register-device` | Registers (or refreshes) an FCM token for the current user — the Android app calls this right after login |
| `POST /notifications/unregister-device` | Called on logout, so a signed-out device stops receiving pushes for that account |

When a scan comes back High/Critical, `scan_helpers.persist_scan()` looks
up every device token registered to that user and sends each one a push via
`messaging.send()` (one call per token, not a batch API — see the
docstring in `push.py` for why: it's the single-message API that's stayed
stable across `firebase-admin` SDK versions, where the batch APIs have
churned). Any token Firebase reports as no-longer-valid (app uninstalled,
etc.) gets pruned from the `device_tokens` table automatically, so it
doesn't keep failing silently on every future alert.

I verified the token-registration upsert logic, the send/prune control
flow, and all three response paths (successful send, invalid token,
transient network error) using a mocked `firebase_admin` module — see the
test transcripts earlier in this conversation. I don't have a real Firebase
project or the `firebase-admin` package available in the sandbox this was
built in, so — same caveat as the rest of "Real threat intelligence" above
— the request/response handling is written against Firebase's documented
API shape, worth confirming once you've got real credentials in hand.
