import os

# In production, load these from environment variables / a secrets manager.
# A random default is fine for local development only.
SECRET_KEY = os.environ.get("CENTINEL_SECRET_KEY", "dev-secret-change-me-in-prod")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30
REFRESH_TOKEN_EXPIRE_DAYS = 14
RESET_TOKEN_EXPIRE_MINUTES = 15

# Slots for real threat-intel providers. Leave blank to run in
# offline/heuristic-only mode (default for this local build).
VIRUSTOTAL_API_KEY = os.environ.get("VIRUSTOTAL_API_KEY", "")
GOOGLE_SAFE_BROWSING_API_KEY = os.environ.get("GOOGLE_SAFE_BROWSING_API_KEY", "")
ABUSEIPDB_API_KEY = os.environ.get("ABUSEIPDB_API_KEY", "")
HIBP_API_KEY = os.environ.get("HIBP_API_KEY", "")

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
