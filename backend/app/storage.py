"""
Optional persistent storage for files uploaded to the File Scanner.

By default, uploaded files are analyzed entirely in-memory and discarded —
nothing is written anywhere. Set CENTINEL_PERSIST_FILES=true to persist
scanned files instead, e.g. for audit/retention purposes:

  - Local disk (default backend once persistence is on): saved under
    backend/uploads/ — zero extra config.
  - AWS S3: also set CENTINEL_S3_BUCKET (plus standard AWS credentials via
    environment variables, an IAM role, or ~/.aws/credentials — boto3
    picks these up automatically) and files go there instead.

Firebase Storage isn't implemented here — it would need the firebase-admin
SDK and a service account — but would slot in the same way: implement a
firebase upload path below and check for it the same way S3_BUCKET is
checked. Whatever the backend, failing to persist a file NEVER fails the
scan itself; storage happens only after analysis has already completed and
its result is on the way back to the caller, purely for optional retention.
"""
import os
import logging
from pathlib import Path
from typing import Optional

logger = logging.getLogger("centinel.storage")

PERSIST_FILES = os.environ.get("CENTINEL_PERSIST_FILES", "false").lower() == "true"
S3_BUCKET = os.environ.get("CENTINEL_S3_BUCKET", "")
LOCAL_UPLOAD_DIR = Path(__file__).resolve().parent.parent / "uploads"

try:
    import boto3
    from botocore.exceptions import BotoCoreError, ClientError
except ImportError:  # pragma: no cover - optional dependency
    boto3 = None
    BotoCoreError = ClientError = Exception

_s3_client = None
_s3_init_attempted = False


def _get_s3_client():
    global _s3_client, _s3_init_attempted
    if boto3 is None:
        return None
    if _s3_init_attempted:
        return _s3_client
    _s3_init_attempted = True
    try:
        _s3_client = boto3.client("s3")
    except Exception as e:
        logger.warning("Could not create S3 client: %s", e)
        _s3_client = None
    return _s3_client


def _safe_filename(filename: str, scan_id: str) -> str:
    # Strips any path components from the original filename (defends
    # against path traversal like "../../etc/passwd") and prefixes with the
    # scan id so repeated uploads of the same filename never collide.
    base = Path(filename or "upload").name
    return f"{scan_id}_{base}"[:200]


def store_file(filename: str, data: bytes, scan_id: str) -> Optional[str]:
    """
    Persists a scanned file if CENTINEL_PERSIST_FILES=true. Returns a
    reference string (a local path, or an s3:// URI) on success, or None if
    persistence is disabled or the write failed for any reason — callers
    should treat None as "not stored" and continue normally either way.
    """
    if not PERSIST_FILES:
        return None

    safe_name = _safe_filename(filename, scan_id)

    if S3_BUCKET:
        client = _get_s3_client()
        if client is not None:
            try:
                client.put_object(Bucket=S3_BUCKET, Key=safe_name, Body=data)
                return f"s3://{S3_BUCKET}/{safe_name}"
            except (BotoCoreError, ClientError) as e:
                logger.warning("S3 upload failed for %s, falling back to local disk: %s", safe_name, e)
        else:
            logger.warning(
                "CENTINEL_S3_BUCKET is set but boto3 isn't installed/couldn't initialize — "
                "falling back to local disk for %s", safe_name,
            )

    try:
        LOCAL_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
        path = LOCAL_UPLOAD_DIR / safe_name
        path.write_bytes(data)
        return str(path)
    except OSError as e:
        logger.warning("Could not persist %s to local disk: %s", safe_name, e)
        return None
