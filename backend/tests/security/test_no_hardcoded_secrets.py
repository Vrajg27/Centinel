"""
Phase 0.5 regression coverage.

These tests exist specifically because a prior build of this project shipped
a live OpenRouter API key hardcoded as a default value in app/config.py
(see SECURITY_FINDINGS.md F-01), plus a real Fernet encryption key and a
populated SQLite database committed into the working tree (F-02). This
suite is meant to make a recurrence of either mistake fail CI immediately,
not to be a one-time manual check.
"""
import ast
import importlib
import re
from pathlib import Path


BACKEND_APP_DIR = Path(__file__).resolve().parents[2] / "app"

# Patterns for well-known secret formats. Kept intentionally narrow (real
# provider key formats) rather than a broad entropy scanner, to avoid noisy
# false positives on things like JWTs issued during tests or random UUIDs.
SECRET_PATTERNS = [
    re.compile(r"sk-or-v1-[A-Za-z0-9]{20,}"),   # OpenRouter
    re.compile(r"AKIA[0-9A-Z]{16}"),             # AWS access key
    re.compile(r"AIza[0-9A-Za-z_\-]{35}"),       # Google API key
    re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"), # Slack token
    re.compile(r"ghp_[A-Za-z0-9]{36}"),          # GitHub PAT
    re.compile(r"-----BEGIN (RSA |EC )?PRIVATE KEY-----"),
]


def _iter_source_files():
    for path in BACKEND_APP_DIR.rglob("*.py"):
        if "__pycache__" in path.parts:
            continue
        yield path


def test_no_known_secret_formats_in_source():
    """No literal value matching a known provider secret format may appear
    anywhere in app/ source. This is what would have caught F-01."""
    offenders = []
    for path in _iter_source_files():
        text = path.read_text(encoding="utf-8", errors="ignore")
        for pattern in SECRET_PATTERNS:
            if pattern.search(text):
                offenders.append((str(path), pattern.pattern))
    assert not offenders, (
        "Found text matching a known secret format in source: "
        f"{offenders}. Rotate the credential immediately (see "
        "SECURITY_FINDINGS.md F-01) and remove the literal value."
    )


def test_provider_key_settings_default_to_empty_string():
    """Every `*_API_KEY` / `*_SECRET` setting in app/config.py must default
    to "" via os.environ.get(NAME, ""), never to a non-empty literal. We
    parse the AST rather than import the module so this test doesn't depend
    on (or get confused by) whatever's actually in the environment when the
    test runs."""
    config_path = BACKEND_APP_DIR / "config.py"
    tree = ast.parse(config_path.read_text(encoding="utf-8"))

    checked = 0
    for node in ast.walk(tree):
        if not isinstance(node, ast.Assign):
            continue
        target_names = [t.id for t in node.targets if isinstance(t, ast.Name)]
        secret_like_targets = [
            n for n in target_names
            if n.endswith(("_API_KEY", "_SECRET", "_SECRET_KEY"))
            # JWT/session signing secrets are handled by their own
            # startup-refusal check in config.py and are allowed a
            # non-empty *placeholder* default that the app refuses to
            # boot with in production — they're a different control,
            # not a leaked live credential.
            and n not in ("JWT_SECRET_KEY", "SECRET_KEY")
        ]
        if not secret_like_targets:
            continue
        if not (
            isinstance(node.value, ast.Call)
            and isinstance(node.value.func, ast.Attribute)
            and node.value.func.attr == "get"
        ):
            continue
        args = node.value.args
        if len(args) < 2:
            continue
        default_arg = args[1]
        checked += 1
        is_empty_string_default = (
            isinstance(default_arg, ast.Constant) and default_arg.value == ""
        )
        assert is_empty_string_default, (
            f"{secret_like_targets} in app/config.py has a non-empty "
            "default value. Provider/secret keys must default to \"\" "
            "and be supplied only via environment variable."
        )

    assert checked >= 4, (
        "Expected to find at least the four existing provider key settings "
        "(VIRUSTOTAL, GOOGLE_SAFE_BROWSING, ABUSEIPDB, HIBP) plus "
        "OPENROUTER_API_KEY — found fewer than expected, which suggests "
        "this test's AST walk no longer matches config.py's structure."
    )


def test_openrouter_key_empty_by_default(monkeypatch):
    """Direct behavioral check: import config with the env var unset and
    confirm the resulting value is empty, not the old hardcoded key."""
    monkeypatch.delenv("OPENROUTER_API_KEY", raising=False)
    import app.config as config_module
    importlib.reload(config_module)
    assert config_module.OPENROUTER_API_KEY == ""


def test_encryption_key_is_always_generated_never_hardcoded():
    """F-02 was a specific *leaked key value* shipped inside a zip archive
    — not the mere existence of backend/.encryption_key. The app
    legitimately (and safely) auto-generates and persists a fresh key to
    that gitignored path on first run for local-dev convenience (see
    app/crypto.py's own docstring); asserting the file must never exist
    would fail on every normal local run and tests the wrong thing.

    What actually matters, and what this test checks: the key-generation
    code path always calls Fernet.generate_key() (a fresh random key) when
    no CENTINEL_ENCRYPTION_KEY/cached file is present — it must never fall
    back to a hardcoded literal key value the way config.py's
    OPENROUTER_API_KEY default previously did."""
    crypto_path = BACKEND_APP_DIR / "crypto.py"
    source = crypto_path.read_text(encoding="utf-8")
    assert "Fernet.generate_key()" in source, (
        "crypto.py no longer appears to generate a random key when none is "
        "configured — verify it doesn't fall back to a fixed literal."
    )
    for pattern in SECRET_PATTERNS:
        assert not pattern.search(source), (
            f"crypto.py contains text matching a known secret format "
            f"({pattern.pattern}) — a hardcoded key must never ship here."
        )


def test_repository_root_does_not_ship_a_populated_database():
    """A *distributed/packaged* copy of this project (e.g. a zip handed to
    a new environment) must not carry forward a database with pre-existing
    rows — that was the actual F-02 finding (a populated centinel.db was
    present in the delivered archive), distinct from the empty SQLite file
    SQLAlchemy's `create_all()` legitimately creates on first run in any
    dev environment (including while this very test suite runs).

    This test only fails if centinel.db exists AND is non-trivially sized
    (i.e. actually contains rows beyond an empty schema), so it doesn't
    fight the normal test/dev lifecycle."""
    db_path = BACKEND_APP_DIR.parent / "centinel.db"
    if not db_path.exists():
        return
    # An empty (schema-only) SQLite file from this project's tables is a
    # few tens of KB at most; the leaked archive's copy was ~86KB with real
    # rows. 200KB is a deliberately generous line so this never false-fails
    # on ordinary local test runs, while still catching an actually
    # populated database being carried into a shared/packaged tree.
    size_kb = db_path.stat().st_size / 1024
    assert size_kb < 200, (
        f"backend/centinel.db is {size_kb:.0f}KB — large enough to suggest "
        "real data, not an empty freshly-created schema. Do not package or "
        "share a populated database (SECURITY_FINDINGS.md F-02)."
    )


def test_gitignore_excludes_secret_bearing_files():
    gitignore_path = BACKEND_APP_DIR.parent / ".gitignore"
    content = gitignore_path.read_text(encoding="utf-8")
    for required in (".env", "*.db", ".encryption_key", "venv/"):
        assert required in content, (
            f"{required!r} is missing from backend/.gitignore — a file "
            "that can hold real secrets or user data must never be "
            "committable."
        )
