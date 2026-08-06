"""
Encrypts sensitive free-text fields before they're written to the database
(currently: Scan.input_payload — the raw scanned content, which can contain
email bodies, SMS text, headers, filenames, etc.). Password hashing (bcrypt)
already covers User.hashed_password separately in auth.py.

Key management:
  - Set CENTINEL_ENCRYPTION_KEY (a urlsafe-base64 32-byte key, i.e. the
    output of `Fernet.generate_key()`) as an environment variable in any
    real deployment.
  - If it's not set, a key is generated on first run and cached in
    `.encryption_key` next to this file, so local dev "just works" across
    restarts without extra setup. That file is gitignored — back it up (or
    set the env var instead) if you need encrypted data to survive a fresh
    checkout, since losing the key makes existing encrypted rows permanently
    unreadable.
"""
import os
from pathlib import Path
from cryptography.fernet import Fernet, InvalidToken

_KEY_FILE = Path(__file__).resolve().parent.parent / ".encryption_key"


def _load_or_create_key() -> bytes:
    env_key = os.environ.get("CENTINEL_ENCRYPTION_KEY")
    if env_key:
        return env_key.encode()

    if _KEY_FILE.exists():
        return _KEY_FILE.read_bytes().strip()

    key = Fernet.generate_key()
    try:
        _KEY_FILE.write_bytes(key)
    except OSError:
        # Read-only filesystem etc. — fall back to an in-memory-only key.
        # Encrypted data won't survive a process restart in that case.
        pass
    return key


_fernet = Fernet(_load_or_create_key())


def encrypt_text(plaintext: str) -> str:
    if plaintext is None:
        return None
    return _fernet.encrypt(plaintext.encode("utf-8")).decode("ascii")


def decrypt_text(ciphertext: str) -> str:
    if ciphertext is None:
        return None
    try:
        return _fernet.decrypt(ciphertext.encode("ascii")).decode("utf-8")
    except InvalidToken:
        # Data written before encryption was added, or encrypted with a
        # different key. Surface it rather than crash the caller.
        return "[unreadable: not encrypted with the current key]"
