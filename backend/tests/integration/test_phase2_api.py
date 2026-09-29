"""
Phase 2 regression coverage: /api/v1 versioning, standardized error envelope,
request-ID propagation, and legacy-path backward compatibility during the
client migration window.
"""
import uuid


def _unique_email():
    return f"test-{uuid.uuid4().hex[:10]}@example.com"


def test_versioned_and_legacy_routes_both_exist(client):
    from app.main import app
    paths = {r.path for r in app.routes if hasattr(r, "path")}
    assert "/api/v1/auth/login" in paths
    assert "/auth/login" in paths, (
        "Legacy unprefixed routes must stay mounted until the extension "
        "and Android client are migrated (Phase 12/13)."
    )
    assert "/api/v1/scan/url" in paths
    assert "/scan/url" in paths


def test_every_response_carries_a_request_id_header(client):
    r = client.get("/")
    assert "x-request-id" in {k.lower() for k in r.headers}
    # A malformed request (triggers our error path) must carry one too.
    r2 = client.post("/api/v1/auth/register", json={"not": "valid"})
    assert "x-request-id" in {k.lower() for k in r2.headers}


def test_validation_error_uses_standard_envelope(client):
    r = client.post("/api/v1/auth/register", json={"email": "nope"})
    assert r.status_code == 422
    body = r.json()
    assert set(body.keys()) == {"error"}
    assert body["error"]["code"] == "VALIDATION_ERROR"
    assert "request_id" in body["error"]
    assert body["error"]["request_id"]  # non-empty


def test_not_found_uses_standard_envelope(client):
    email, password = _unique_email(), "CorrectHorseBattery9!"
    client.post("/api/v1/auth/register", json={"email": email, "password": password})
    login = client.post("/api/v1/auth/login", json={"email": email, "password": password})
    token = login.json()["access_token"]

    r = client.get(
        "/api/v1/report/00000000-0000-0000-0000-000000000000",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 404
    body = r.json()
    assert body["error"]["code"] == "NOT_FOUND"
    assert "request_id" in body["error"]


def test_route_specific_error_code_is_preserved(client):
    """Confirms a route that raises HTTPException(detail={"code": ...,
    "message": ...}) keeps its own machine-readable code rather than
    getting overwritten by the generic status-based default."""
    r = client.post("/api/v1/auth/login", json={"email": "nobody@example.com", "password": "wrong-password"})
    assert r.status_code == 401
    body = r.json()
    assert "error" in body
    assert "request_id" in body["error"]


def test_register_login_scan_roundtrip_via_v1(client):
    """End-to-end smoke test through the new /api/v1 surface, exercising
    auth, a real scan endpoint, and (transitively) the provider abstraction
    threat_intel.py now delegates to."""
    email, password = _unique_email(), "CorrectHorseBattery9!"
    r = client.post("/api/v1/auth/register", json={"email": email, "password": password})
    assert r.status_code == 201

    r = client.post("/api/v1/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200
    token = r.json()["access_token"]

    r = client.post(
        "/api/v1/scan/url",
        json={"url": "http://example.com"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert r.status_code == 200
    body = r.json()
    assert "risk_score" in body
    assert "threat_level" in body


def test_legacy_unprefixed_auth_still_works(client):
    """The Android app and browser extension call unprefixed paths today
    and are not updated until Phase 12/13 — this must keep working."""
    email, password = _unique_email(), "CorrectHorseBattery9!"
    r = client.post("/auth/register", json={"email": email, "password": password})
    assert r.status_code == 201
    r = client.post("/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200
