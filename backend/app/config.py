import os

# In production, load these from environment variables / a secrets manager.
# A random default is fine for local development only.
SECRET_KEY = os.environ.get("CENTINEL_SECRET_KEY", "dev-secret-change-me-in-prod")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30
REFRESH_TOKEN_EXPIRE_DAYS = 14
RESET_TOKEN_EXPIRE_MINUTES = 15

# Enables convenience-only development behavior: password-reset links get
# printed to the server console and included in the /forgot-password
# response body (as `dev_reset_url`) so the full reset flow can be tested
# with no email service wired up. Defaults on, matching every other
# setting in this file being zero-config-friendly for local dev — but this
# one MUST be set to false (CENTINEL_DEBUG=false) before deploying
# anywhere reachable by untrusted users: leaking a valid password-reset
# link in an API response is a full account-takeover primitive.
DEBUG = os.environ.get("CENTINEL_DEBUG", "true").lower() != "false"

if not DEBUG and SECRET_KEY == "dev-secret-change-me-in-prod":
    # CENTINEL_DEBUG=false is this project's explicit "this is a real
    # deployment" signal (see above). Refusing to start with the
    # well-known placeholder JWT signing secret in that case — rather
    # than just logging a warning — matters because this specific secret
    # is public (it's sitting in this file, in every checkout of this
    # codebase): anyone who's seen it can forge a valid access token for
    # any user, including an admin, which is a full authentication bypass,
    # not a "weak" configuration. Local dev is unaffected: DEBUG defaults
    # to true, so this branch is simply never reached with zero config.
    raise RuntimeError(
        "CENTINEL_DEBUG=false but CENTINEL_SECRET_KEY was never set — refusing to start. "
        "The default JWT signing secret is public (it's in this source file), so leaving it "
        "in place here would let anyone forge valid access tokens for any account, including "
        "admins. Set CENTINEL_SECRET_KEY to a real random value, e.g. `openssl rand -hex 32`."
    )

# Slots for real threat-intel providers. Leave blank to run in
# offline/heuristic-only mode (default for this local build).
VIRUSTOTAL_API_KEY = os.environ.get("VIRUSTOTAL_API_KEY", "")
GOOGLE_SAFE_BROWSING_API_KEY = os.environ.get("GOOGLE_SAFE_BROWSING_API_KEY", "")
ABUSEIPDB_API_KEY = os.environ.get("ABUSEIPDB_API_KEY", "")
HIBP_API_KEY = os.environ.get("HIBP_API_KEY", "")
# SECURITY (Phase 0.5, 2026): a prior build of this file shipped a live
# OpenRouter key hardcoded as this default. That key has been treated as
# compromised and must be rotated/revoked at https://openrouter.ai/keys —
# do not reuse it. This default is now empty, matching every other
# provider key in this file (VIRUSTOTAL_API_KEY, GOOGLE_SAFE_BROWSING_API_KEY,
# etc.) — the AI Security Copilot simply stays disabled until a real key is
# supplied via the OPENROUTER_API_KEY environment variable.
OPENROUTER_API_KEY = os.environ.get("OPENROUTER_API_KEY", "")

# Every real threat-intel HTTP call uses this as its timeout, so one slow/
# unresponsive provider can only delay a scan by this much, not hang it —
# each call is independently wrapped in try/except and simply skips adding
# a signal on any failure (timeout, bad key, provider outage, etc).
THREAT_INTEL_TIMEOUT_SECONDS = int(os.environ.get("CENTINEL_THREAT_INTEL_TIMEOUT", "5"))

# IP geolocation via ip-api.com's free tier needs no API key at all, so
# it's on by default whenever ENABLE_WHOIS_LOOKUP-style network calls are
# acceptable in your environment. Flip off if your network blocks it.
ENABLE_IP_GEOLOCATION = os.environ.get("CENTINEL_ENABLE_GEOLOCATION", "true").lower() != "false"

# SSL Labs grades are cache-only here (fromCache=on) rather than triggering
# a fresh assessment, since a fresh SSL Labs scan can take 1-2+ minutes —
# far too slow for a synchronous scan endpoint. If SSL Labs has no cached
# result for a host, this signal is simply skipped for that scan.
ENABLE_SSL_LABS = os.environ.get("CENTINEL_ENABLE_SSL_LABS", "true").lower() != "false"

# Push notifications (Firebase Cloud Messaging) — off unless both a service
# account credentials file is provided AND the `firebase-admin` package is
# installed. Without either, in-app notifications (GET /notifications)
# still work exactly as before; this only adds actual device push on top.
FIREBASE_CREDENTIALS_PATH = os.environ.get("CENTINEL_FIREBASE_CREDENTIALS_PATH", "")

# Comma-separated emails that are automatically granted admin on
# registration — the bootstrap mechanism for creating your first admin
# account, since there's no other admin yet to promote one. Once you have
# an admin account, promote further users via POST /admin/users/{id}/promote
# instead of adding more emails here.
ADMIN_EMAILS = {
    e.strip().lower()
    for e in os.environ.get("CENTINEL_ADMIN_EMAILS", "").split(",")
    if e.strip()
}

# Domain-age (WHOIS) lookups add ~1-4s of network latency per URL scan and
# require outbound access to WHOIS servers (port 43), which some corporate
# networks/sandboxes block. Flip to false to skip that signal entirely.
ENABLE_WHOIS_LOOKUP = os.environ.get("CENTINEL_ENABLE_WHOIS", "true").lower() != "false"
WHOIS_TIMEOUT_SECONDS = 4
