import os
import sys
from pathlib import Path

import pytest

# Make sure tests run against a throwaway SQLite DB, never a real one, and
# force debug mode on so the app doesn't refuse to start over the
# production secret-key guard in config.py when tests run without a real
# CENTINEL_JWT_SECRET set.
os.environ.setdefault("CENTINEL_DEBUG", "true")
os.environ.setdefault("CENTINEL_DATABASE_URL", "sqlite:///./test_centinel.db")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture(scope="session", autouse=True)
def _cleanup_test_db():
    yield
    test_db = Path(__file__).resolve().parents[1] / "test_centinel.db"
    if test_db.exists():
        test_db.unlink()


@pytest.fixture()
def client():
    from fastapi.testclient import TestClient
    from app.main import app
    with TestClient(app) as c:
        yield c
