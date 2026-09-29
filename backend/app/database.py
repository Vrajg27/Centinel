"""
Database configuration. Defaults to SQLite so local dev needs zero setup.

Swap to PostgreSQL by setting CENTINEL_DATABASE_URL — no other code changes
needed, since every model/query in this codebase uses plain SQLAlchemy
types and ORM queries with no SQLite-specific syntax:

    export CENTINEL_DATABASE_URL="postgresql://user:password@localhost:5432/centinel"

then create the database once (e.g. `createdb centinel`) before starting
the app — tables are still auto-created by `Base.metadata.create_all()` in
main.py, same as with SQLite.
"""
import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

SQLALCHEMY_DATABASE_URL = os.environ.get("CENTINEL_DATABASE_URL", "sqlite:///./centinel.db")

if SQLALCHEMY_DATABASE_URL.startswith("sqlite"):
    # Needed because SQLite only allows a connection to be used by the
    # thread that created it, by default — FastAPI's dependency injection
    # can hand a session to a different thread than the one that opened it.
    engine = create_engine(SQLALCHEMY_DATABASE_URL, connect_args={"check_same_thread": False})
else:
    # PostgreSQL (or any other real server-based DB): pool_pre_ping reaps
    # dead/stale connections (e.g. after the DB restarts or an idle
    # connection gets dropped) instead of surfacing them as a query error.
    engine = create_engine(SQLALCHEMY_DATABASE_URL, pool_pre_ping=True)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def run_lightweight_migrations() -> None:
    """
    This project has no Alembic/migration framework — `Base.metadata.create_all()`
    (called right after this in main.py) only creates tables that don't exist
    yet, so it silently does nothing for columns added to an *existing*
    table. That's fine for brand-new installs, but anyone upgrading from a
    build before the password-reset rework would be left with a `users`
    table missing `reset_token_hash`/`reset_token_expires`, and every
    forgot-password/reset-password call would fail with a DB error.
    A single small, additive, idempotent ALTER TABLE (guarded by an
    information-schema/pragma check, so it's a no-op on fresh installs and
    on every subsequent restart) is enough here — a full migration
    framework would be over-engineering for one nullable column pair.
    """
    from sqlalchemy import inspect, text

    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return  # fresh install — create_all() will create the table with these columns already present

    existing_columns = {col["name"] for col in inspector.get_columns("users")}
    datetime_type = "TIMESTAMP" if engine.dialect.name != "sqlite" else "DATETIME"
    with engine.begin() as conn:
        if "reset_token_hash" not in existing_columns:
            conn.execute(text("ALTER TABLE users ADD COLUMN reset_token_hash VARCHAR"))
        if "reset_token_expires" not in existing_columns:
            conn.execute(text(f"ALTER TABLE users ADD COLUMN reset_token_expires {datetime_type}"))


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
