"""
Unit tests for app/engine/providers.py — the ThreatIntelProvider
abstraction introduced in Phase 2. All HTTP calls are mocked so these run
offline and deterministically; provider failure-vs-clean-vs-unknown is
exactly the distinction SECURITY_FINDINGS.md flags as previously missing,
so it's covered explicitly here rather than only exercised incidentally by
integration tests.
"""
from unittest.mock import patch

from app.engine import providers
from app.engine.providers import ProviderState


class _FakeResponse:
    def __init__(self, status_code, json_data=None):
        self.status_code = status_code
        self._json_data = json_data or {}

    def json(self):
        return self._json_data


def test_virustotal_unavailable_when_no_api_key(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "")
    result = providers.VirusTotalUrlProvider().lookup("http://example.com")
    assert result.state == ProviderState.UNAVAILABLE
    assert result.data is None


def test_virustotal_unknown_on_404(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "fake-key")
    with patch.object(providers._http, "get", return_value=_FakeResponse(404)):
        result = providers.VirusTotalUrlProvider().lookup("http://never-seen.example")
    assert result.state == ProviderState.UNKNOWN


def test_virustotal_unavailable_on_network_failure(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "fake-key")
    with patch.object(providers._http, "get", return_value=None):
        result = providers.VirusTotalUrlProvider().lookup("http://example.com")
    assert result.state == ProviderState.UNAVAILABLE, (
        "A network/timeout failure must never be reported the same as a "
        "clean result (SECURITY_FINDINGS.md: provider failure != clean)."
    )


def test_virustotal_error_on_bad_key(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "fake-key")
    with patch.object(providers._http, "get", return_value=_FakeResponse(401)):
        result = providers.VirusTotalUrlProvider().lookup("http://example.com")
    assert result.state == ProviderState.ERROR


def test_virustotal_confirmed_when_engines_flag_malicious(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "fake-key")
    payload = {
        "data": {"attributes": {"last_analysis_stats": {"malicious": 5, "suspicious": 1, "harmless": 60, "undetected": 4}, "reputation": -10}}
    }
    with patch.object(providers._http, "get", return_value=_FakeResponse(200, payload)):
        result = providers.VirusTotalUrlProvider().lookup("http://bad.example")
    assert result.state == ProviderState.CONFIRMED
    assert result.data["malicious"] == 5


def test_virustotal_clean_when_zero_detections(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "fake-key")
    payload = {
        "data": {"attributes": {"last_analysis_stats": {"malicious": 0, "suspicious": 0, "harmless": 70, "undetected": 0}, "reputation": 5}}
    }
    with patch.object(providers._http, "get", return_value=_FakeResponse(200, payload)):
        result = providers.VirusTotalUrlProvider().lookup("http://good.example")
    assert result.state == ProviderState.CLEAN


def test_virustotal_error_on_unparseable_response(monkeypatch):
    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "fake-key")
    with patch.object(providers._http, "get", return_value=_FakeResponse(200, {"unexpected": "shape"})):
        result = providers.VirusTotalUrlProvider().lookup("http://example.com")
    assert result.state == ProviderState.ERROR


def test_hibp_clean_distinct_from_unavailable(monkeypatch):
    """The specific distinction the pre-Phase-2 code already got right for
    HIBP: a confirmed 404 ('no breaches') must not be conflated with 'we
    couldn't check.'"""
    monkeypatch.setattr(providers.config, "HIBP_API_KEY", "fake-key")
    with patch.object(providers._http, "get", return_value=_FakeResponse(404)):
        clean = providers.HIBPProvider().lookup("nobody@example.com")
    with patch.object(providers._http, "get", return_value=None):
        unavailable = providers.HIBPProvider().lookup("nobody@example.com")
    assert clean.state == ProviderState.CLEAN
    assert clean.data == {"breaches": []}
    assert unavailable.state == ProviderState.UNAVAILABLE
    assert clean.state != unavailable.state


def test_google_safe_browsing_confirmed_on_match(monkeypatch):
    monkeypatch.setattr(providers.config, "GOOGLE_SAFE_BROWSING_API_KEY", "fake-key")
    payload = {"matches": [{"threatType": "SOCIAL_ENGINEERING"}]}
    with patch.object(providers._http, "post", return_value=_FakeResponse(200, payload)):
        result = providers.GoogleSafeBrowsingProvider().lookup("http://phish.example")
    assert result.state == ProviderState.CONFIRMED
    assert result.data["threat_types"] == ["SOCIAL_ENGINEERING"]


def test_backward_compatible_wrapper_matches_old_contract(monkeypatch):
    """threat_intel.py's public functions must still return dict-or-None,
    not a ProviderResult, so every existing engine caller keeps working."""
    from app.engine import threat_intel

    monkeypatch.setattr(providers.config, "VIRUSTOTAL_API_KEY", "")
    assert threat_intel.virustotal_url_report("http://example.com") is None

    monkeypatch.setattr(providers.config, "HIBP_API_KEY", "fake-key")
    with patch.object(providers._http, "get", return_value=_FakeResponse(404)):
        result = threat_intel.hibp_check("nobody@example.com")
    assert result == {"breaches": []}
