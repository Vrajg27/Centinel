from slowapi import Limiter
from slowapi.util import get_remote_address

# Shared across main.py (registers the exception handler + middleware) and
# every router (applies @limiter.limit(...) to individual endpoints).
# Default covers any route that doesn't set its own explicit limit.
limiter = Limiter(key_func=get_remote_address, default_limits=["120/minute"])
