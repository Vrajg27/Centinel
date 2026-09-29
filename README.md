# Centinel — Local Build

An AI-powered cybersecurity platform: backend + Android app + browser
extension, matching the original spec's scope with URL, Website (redirect/
JS/login-form analysis), Email (paste or `.eml` upload), Email Header, SMS,
QR, File, Password, SSL, and Data Breach scanning, plus history, analytics,
PDF reports, and notifications.

## What's runnable right now
- **`backend/`** — a real FastAPI service with a working offline heuristic
  AI-scoring engine for every scan type. **I installed its dependencies and
  ran its detection logic in this sandbox to confirm it works correctly**
  (see test output earlier in this conversation). You can have it running
  locally in about a minute — see `backend/README.md`.

## What's code-only (not run here)
- **`android/`** — the full Kotlin/Jetpack Compose app wired to that
  backend. Building and running an Android app needs Android Studio + the
  Android SDK/emulator, which this sandbox doesn't have, so **this code has
  not been compiled or run**. It's structured to open cleanly in Android
  Studio — see `android/README.md` for the two setup steps.
- **`extension/`** — the Manifest V3 browser extension, also wired to the
  same backend and sharing login/history with the Android app. I syntax-
  checked every file (manifest JSON, all JS as ES modules, HTML structure)
  but **this sandbox has no browser to actually load and click through
  it in** — see `extension/README.md` for the two-minute "Load unpacked"
  setup in Chrome/Edge/Brave.

## Recommended order
1. `cd backend && pip install -r requirements.txt && uvicorn app.main:app --reload`
   Confirm it works via `http://localhost:8000/docs`.
2. Open `android/` in Android Studio, let it sync, set `API_BASE_URL` if needed,
   and run on an emulator.
3. Load `extension/` unpacked in Chrome (`chrome://extensions` → Developer
   mode → Load unpacked), log in with the same account as the app.

## Not included in this pass
- An admin dashboard *screen* — the `/admin/*` API endpoints exist and are
  RBAC-gated, but no UI consumes them yet (Android or otherwise).
- Clipboard URL monitoring in the extension (optional in the spec — see
  `extension/README.md` for why it's a deliberate omission, not an oversight).
- Alembic migrations — PostgreSQL is supported (Phase 4), but schema
  changes to an *existing* production database aren't handled beyond the
  additive `create_all()` FastAPI already does on startup.

## Phase 1, 2, 3, 4, 5 & 6 — done

**Phase 1:** rate limiting (actually enforced, not just configured),
password reset, WHOIS domain-age scoring, scan history export.

**Phase 2:** RBAC/admin API endpoints, encryption at rest for scanned
content, scoped HTTPS/cleartext enforcement.

**Phase 3:** Website Scanner (redirect-chain + JS + login-form/credential-
exfiltration analysis, distinct from the URL Scanner), `.eml` file upload
for the Email Scanner, PDF report download in the Android History screen,
real donut/bar charts in Threat Analytics, rotating security tips on the
Dashboard, and redirect-chain monitoring in the browser extension.

**Phase 4:** PostgreSQL support (one env var, no code changes), Redis
caching for repeated URL/website/SSL scans (graceful no-op if unreachable),
optional S3/local-disk file persistence for the File Scanner (off by
default), and Celery background processing for the two slowest scan types
(`/scan/url/async`, `/scan/website/async` + task polling) — entirely
opt-in; every scan still works synchronously with zero setup either way.

**Phase 5:** all five threat-intel providers from the original spec —
VirusTotal (URL + file hash reputation), Google Safe Browsing, AbuseIPDB +
real IP geolocation, HaveIBeenPwned, and SSL Labs grading — fully
implemented in `backend/app/engine/threat_intel.py`, layering real
reputation data on top of the offline heuristics the moment you add API
keys. Zero keys configured = zero behavior change from Phase 4. I verified
every provider's response-parsing logic against realistic mocked API
responses; I couldn't hit the real live endpoints from this sandbox
(no keys, no general internet access here), so that part is worth
confirming once you've got real keys in hand.

**Phase 6:** push notifications end-to-end — device token registration/
unregistration endpoints, Firebase Admin push sending (with automatic
pruning of invalid/expired tokens) on every High/Critical scan, and the
full Android side (local notification display, runtime permission request,
automatic token registration after login / unregistration on logout).
Requires you to bring your own Firebase project either way (both the
Android `google-services.json` and a backend service-account credential) —
see `android/README.md` and `backend/README.md` for the two setup steps.
In-app notifications (`GET /notifications`) have worked with zero setup
since Phase 1 and are completely unaffected by whether Firebase is
configured.

See `backend/README.md`, `android/README.md`, and `extension/README.md`
for the full breakdown of each.
