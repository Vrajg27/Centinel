"""
Sends push notifications via Firebase Cloud Messaging when a High/Critical
scan is detected, to every device a user has registered — the "browser
extension blocks website / data breach found / malware detected" push
notifications from the original spec.

Off by default and fully optional, following this project's established
pattern for every other real external integration (see threat_intel.py):
requires BOTH the `firebase-admin` package installed AND
CENTINEL_FIREBASE_CREDENTIALS_PATH pointing at a real Firebase service
account JSON file. Without either, send_push() silently no-ops and returns
an empty result — in-app notifications (GET /notifications, already fully
functional since Phase 1) are completely unaffected either way, since they
don't depend on Firebase at all.

Uses messaging.send() in a loop (one call per device token) rather than a
batch/multicast call — the single-message API has been stable across
firebase-admin SDK versions for years, where the batch APIs have churned
(send_multicast was deprecated in favor of send_each_for_multicast in
newer SDK versions). Looping is marginally less efficient for a user with
many devices, but it means this code doesn't depend on which exact SDK
version happens to be installed, and it lets a single expired/invalid
token get identified and pruned without failing the rest of the batch.
"""
import logging

from . import config

logger = logging.getLogger("centinel.push")

try:
    import firebase_admin
    from firebase_admin import credentials, messaging
except ImportError:  # pragma: no cover - optional dependency
    firebase_admin = None
    credentials = None
    messaging = None

_app = None
_init_attempted = False


def _get_app():
    global _app, _init_attempted
    if firebase_admin is None or not config.FIREBASE_CREDENTIALS_PATH:
        return None
    if _init_attempted:
        return _app

    _init_attempted = True
    try:
        cred = credentials.Certificate(config.FIREBASE_CREDENTIALS_PATH)
        _app = firebase_admin.initialize_app(cred)
        logger.info("Firebase Admin initialized — push notifications are active.")
    except Exception as e:
        logger.warning(
            "Could not initialize Firebase Admin (%s) — push notifications disabled. "
            "In-app notifications (GET /notifications) still work normally.", e,
        )
        _app = None
    return _app


def is_configured() -> bool:
    return _get_app() is not None


def send_push(tokens: list, title: str, body: str, data: dict = None) -> dict:
    """
    Sends a push notification to each of the given FCM device tokens.
    Never raises — this is always called after the triggering scan/
    notification has already been persisted, so a push failure here should
    never surface as an error to the API caller.

    Returns {"sent": [...], "invalid": [...]}: `sent` is how many tokens
    the message was successfully handed off for delivery to; `invalid`
    lists tokens that Firebase reports as unregistered/invalid, so the
    caller can prune them from the DeviceToken table (a token becomes
    invalid whenever the app is uninstalled, among other reasons).
    """
    app = _get_app()
    result = {"sent": [], "invalid": []}
    if app is None or not tokens:
        return result

    for token in tokens:
        try:
            message = messaging.Message(
                notification=messaging.Notification(title=title, body=body),
                data={k: str(v) for k, v in (data or {}).items()},
                token=token,
            )
            messaging.send(message, app=app)
            result["sent"].append(token)
        except messaging.UnregisteredError:
            result["invalid"].append(token)
        except Exception as e:
            # Any other failure (network, quota, malformed message) — skip
            # this token but don't treat it as permanently invalid.
            logger.warning("Push send failed for a device token: %s", e)

    return result
