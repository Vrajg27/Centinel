"""
Phase 2: request ID propagation.

Every inbound request gets a UUID4 request ID — generated fresh, or taken
from an inbound `X-Request-ID` header if a trusted upstream (e.g. a load
balancer) already set one. The ID is:

  * stored on `request.state.request_id` so route handlers can log it,
  * available via `get_request_id()` from anywhere in the call stack for
    this request (used by the error envelope in app/errors.py, including
    from exception handlers that only receive the raw ASGI request), and
  * echoed back on every response (including error responses) as the
    `X-Request-ID` header, so a client can hand it back when reporting
    an issue.
"""
import uuid
from contextvars import ContextVar

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.types import ASGIApp

_request_id_ctx: ContextVar[str] = ContextVar("request_id", default="")


def get_request_id() -> str:
    """Best-effort accessor. Returns "" if called outside a request
    (e.g. at import time, in a background task not carrying the context) —
    callers should treat that as "no request ID available", not an error."""
    return _request_id_ctx.get()


class RequestIDMiddleware(BaseHTTPMiddleware):
    def __init__(self, app: ASGIApp) -> None:
        super().__init__(app)

    async def dispatch(self, request: Request, call_next):
        incoming = request.headers.get("x-request-id")
        request_id = incoming if incoming else str(uuid.uuid4())
        token = _request_id_ctx.set(request_id)
        request.state.request_id = request_id
        try:
            response = await call_next(request)
        finally:
            _request_id_ctx.reset(token)
        response.headers["X-Request-ID"] = request_id
        return response
