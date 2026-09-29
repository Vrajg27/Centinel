"""
Phase 2: standardized error envelope.

Every error response — validation failures, auth failures, rate limits,
not-found, and unhandled exceptions alike — now has the same shape:

    {"error": {"code": "...", "message": "...", "request_id": "..."}}

Route handlers don't need to change how they raise errors: `raise
HTTPException(status_code=404, detail="Scan not found")` still works exactly
as before and gets wrapped into this envelope automatically, with `code`
derived from the status code. A route can opt into a specific machine-
readable code by raising `HTTPException(status_code=404, detail={"code":
"SCAN_NOT_FOUND", "message": "Scan was not found."})` instead — both forms
are supported so this is an additive change, not a required rewrite of
every existing `raise HTTPException(...)` call site.

Unhandled exceptions are logged with a full traceback server-side and never
expose exception text, a stack trace, or any internal detail to the client
— the client only ever sees a generic "internal error" message plus the
request_id needed to correlate with server logs.
"""
import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from slowapi.errors import RateLimitExceeded
from starlette.exceptions import HTTPException as StarletteHTTPException

from .request_context import get_request_id

logger = logging.getLogger("centinel.errors")

_STATUS_CODE_DEFAULTS = {
    400: "BAD_REQUEST",
    401: "UNAUTHORIZED",
    403: "FORBIDDEN",
    404: "NOT_FOUND",
    405: "METHOD_NOT_ALLOWED",
    409: "CONFLICT",
    413: "PAYLOAD_TOO_LARGE",
    422: "VALIDATION_ERROR",
    429: "RATE_LIMITED",
    500: "INTERNAL_ERROR",
    503: "SERVICE_UNAVAILABLE",
}


def _code_for_status(status_code: int) -> str:
    return _STATUS_CODE_DEFAULTS.get(status_code, f"HTTP_{status_code}")


def build_error_envelope(code: str, message: str, request_id: str | None = None) -> dict:
    return {
        "error": {
            "code": code,
            "message": message,
            "request_id": request_id if request_id is not None else get_request_id(),
        }
    }


async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    detail = exc.detail
    if isinstance(detail, dict) and "code" in detail and "message" in detail:
        code, message = detail["code"], detail["message"]
    else:
        code = _code_for_status(exc.status_code)
        message = detail if isinstance(detail, str) else str(detail)
    return JSONResponse(
        status_code=exc.status_code,
        content=build_error_envelope(code, message),
        headers=getattr(exc, "headers", None) or {},
    )


async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    envelope = build_error_envelope(
        "VALIDATION_ERROR",
        "The request did not pass validation.",
    )
    # Field-level detail is genuinely useful for API consumers building
    # against this and contains no server-internal information (just which
    # of the client's own fields were malformed), so it's safe to include.
    envelope["error"]["details"] = exc.errors()
    return JSONResponse(status_code=422, content=envelope)


async def rate_limit_exception_handler(request: Request, exc: RateLimitExceeded) -> JSONResponse:
    return JSONResponse(
        status_code=429,
        content=build_error_envelope(
            "RATE_LIMITED",
            f"Rate limit exceeded: {exc.detail}",
        ),
    )


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    # Full detail goes to the server log only, tagged with the request ID so
    # it can be correlated with what the client sees.
    logger.exception(
        "Unhandled exception while processing %s %s [request_id=%s]",
        request.method,
        request.url.path,
        get_request_id(),
    )
    return JSONResponse(
        status_code=500,
        content=build_error_envelope(
            "INTERNAL_ERROR",
            "An unexpected error occurred. If this persists, report the "
            "request_id below.",
        ),
    )


def install_error_handlers(app: FastAPI) -> None:
    app.add_exception_handler(StarletteHTTPException, http_exception_handler)
    app.add_exception_handler(RequestValidationError, validation_exception_handler)
    app.add_exception_handler(RateLimitExceeded, rate_limit_exception_handler)
    app.add_exception_handler(Exception, unhandled_exception_handler)
