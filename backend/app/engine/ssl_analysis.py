import ssl
import socket
import datetime as dt

from .risk_engine import Signal, combine_signals, RiskAssessment
from . import threat_intel

WEAK_SIG_ALGOS = ("md5", "sha1")

# SSL Labs letter grades that warrant a signal, and how strongly. A+ and A
# are left out deliberately — they add no signal, matching "no news is good
# news" for the rest of this engine's heuristics.
SSL_LABS_GRADE_WEIGHTS = {"B": 10, "C": 20, "D": 30, "E": 35, "F": 45, "T": 40, "M": 40}


def _fetch_cert(hostname: str, port: int = 443, timeout: float = 5.0):
    ctx = ssl.create_default_context()
    with socket.create_connection((hostname, port), timeout=timeout) as sock:
        with ctx.wrap_socket(sock, server_hostname=hostname) as ssock:
            der = ssock.getpeercert(binary_form=False)
            return der


def analyze_ssl(hostname: str):
    hostname = hostname.strip().replace("https://", "").replace("http://", "").split("/")[0]
    signals = []
    cert_info = {"hostname": hostname, "reachable": False}

    try:
        cert = _fetch_cert(hostname)
        cert_info["reachable"] = True
        not_after = cert.get("notAfter")
        not_before = cert.get("notBefore")
        issuer = dict(x[0] for x in cert.get("issuer", []))
        subject = dict(x[0] for x in cert.get("subject", []))

        expire_dt = dt.datetime.strptime(not_after, "%b %d %H:%M:%S %Y %Z") if not_after else None
        days_left = (expire_dt - dt.datetime.utcnow()).days if expire_dt else None

        cert_info.update({
            "issuer": issuer.get("organizationName") or issuer.get("commonName") or "Unknown",
            "subject": subject.get("commonName", hostname),
            "valid_from": not_before,
            "valid_until": not_after,
            "days_until_expiry": days_left,
            "san": [x[1] for x in cert.get("subjectAltName", []) if x[0] == "DNS"],
        })

        if days_left is not None:
            if days_left < 0:
                signals.append(Signal("expired_cert", "the SSL certificate has already expired", 40, "technical"))
            elif days_left < 15:
                signals.append(Signal(
                    "expiring_soon", f"the SSL certificate expires very soon ({days_left} days)", 15, "technical"
                ))

        cn = subject.get("commonName", "")
        sans = cert_info["san"]
        if cn and cn != hostname and hostname not in sans and not any(
            hostname.endswith(s.replace("*", "")) for s in sans
        ):
            signals.append(Signal(
                "hostname_mismatch",
                f"the certificate's common name ('{cn}') does not match the requested hostname",
                35, "technical",
            ))

        org = issuer.get("organizationName", "")
        if not org:
            signals.append(Signal(
                "no_organization_validation",
                "the certificate has no Organization Validation (domain-validated only), lower assurance for sensitive sites",
                6, "technical",
            ))

        # SSL Labs grade — cache-only (see threat_intel.ssl_labs_grade), so
        # this is skipped rather than delayed if there's no cached result
        # for this host yet. A+ / A add no signal; anything else does.
        labs = threat_intel.ssl_labs_grade(hostname)
        if labs:
            cert_info["ssl_labs_grade"] = labs["grade"]
            weight = SSL_LABS_GRADE_WEIGHTS.get(labs["grade"])
            if weight:
                signals.append(Signal(
                    "ssl_labs_weak_grade",
                    f"Qualys SSL Labs grades this server's TLS configuration '{labs['grade']}', "
                    f"indicating a weaker-than-ideal setup (cipher suites, protocol versions, or known "
                    f"vulnerabilities)",
                    weight, "technical",
                ))

    except (socket.timeout, socket.gaierror, ConnectionRefusedError, OSError) as e:
        cert_info["error"] = f"Could not establish a TLS connection: {e}"
        signals.append(Signal(
            "connection_failed",
            "a secure connection to the host could not be established at all, which itself is a red flag "
            "for a supposedly legitimate site",
            20, "technical",
        ))
    except ssl.SSLError as e:
        cert_info["error"] = f"TLS/SSL error: {e}"
        signals.append(Signal(
            "ssl_error",
            f"the TLS handshake failed with an SSL error ({e})",
            30, "technical",
        ))

    assessment = combine_signals(signals, base_confidence=0.65, subject_label=f"the SSL certificate for '{hostname}'")
    return assessment, cert_info
