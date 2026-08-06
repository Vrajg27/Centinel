"""
Website Scanner — distinct from the URL Scanner (url_analysis.py): where the
URL Scanner only inspects the URL string itself, this actually fetches the
page and follows its redirect chain to look at behavior — how many hops it
takes, whether it crosses domains along the way, and what the landing page's
HTML/JS actually does (hidden iframes, obfuscated JS, meta-refresh bouncing,
and — the big one — login forms that submit credentials to a different
domain than the page itself).
"""
import re
import ipaddress
import socket
from urllib.parse import urlparse, urljoin

try:
    import httpx
except ImportError:  # pragma: no cover - optional dependency
    httpx = None

try:
    from bs4 import BeautifulSoup
except ImportError:  # pragma: no cover - optional dependency
    BeautifulSoup = None

from .risk_engine import Signal, combine_signals, RiskAssessment
from . import url_analysis

MAX_REDIRECTS = 10
FETCH_TIMEOUT_SECONDS = 6.0
USER_AGENT = "Mozilla/5.0 (compatible; CentinelWebsiteScanner/1.0; +https://github.com/)"

SUSPICIOUS_JS_PATTERNS = [
    (re.compile(r"\beval\s*\("), "eval_usage",
     "the page's JavaScript uses eval(), a common obfuscation/injection technique", 15),
    (re.compile(r"\b(unescape|atob)\s*\("), "encoded_payload",
     "the page decodes base64/escaped strings at runtime, often used to hide malicious payloads from static scanners", 15),
    (re.compile(r"(window\.location(\.href)?|top\.location)\s*="), "js_redirect",
     "the page redirects via JavaScript rather than a standard HTTP redirect, which can evade some security scanners", 10),
    (re.compile(r"oncontextmenu\s*=\s*[\"']?\s*return\s*false", re.I), "right_click_disabled",
     "the page disables right-click, a pattern sometimes used to hinder inspection of phishing pages", 6),
    (re.compile(r"document\.write\s*\("), "document_write",
     "the page uses document.write() to inject content, an older technique often paired with malicious ad/redirect chains", 6),
]


def _is_safe_fetch_target(host: str) -> bool:
    """
    Basic SSRF guard: refuses to fetch private/loopback/link-local/reserved
    addresses so a scan request can't be used to probe internal
    infrastructure (e.g. http://169.254.169.254/, http://localhost:22/)
    through this server. Returns True if the host resolves to a public
    address, or if it can't be resolved at all (in which case the fetch
    itself will simply fail naturally rather than being blocked here).
    """
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        try:
            resolved = socket.gethostbyname(host)
            ip = ipaddress.ip_address(resolved)
        except Exception:
            return True
    return not (ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast)


def _follow_redirects(start_url: str):
    """
    Manually follows redirects one hop at a time (rather than letting the
    HTTP client auto-follow) so we can record the full chain and re-check
    the SSRF guard on every hop — a redirect could point somewhere unsafe
    even if the original URL didn't.
    """
    chain = [start_url]
    current = start_url
    html_text = ""
    fetch_error = None

    if httpx is None:
        return chain, start_url, "", "The 'httpx' package isn't installed, so the page couldn't be fetched — redirect/JS/form analysis was skipped. Domain/URL-based checks below still apply."

    try:
        with httpx.Client(follow_redirects=False, timeout=FETCH_TIMEOUT_SECONDS,
                           headers={"User-Agent": USER_AGENT}) as client:
            for _ in range(MAX_REDIRECTS):
                host = urlparse(current).hostname
                if not host or not _is_safe_fetch_target(host):
                    fetch_error = f"Refused to fetch '{current}' — it resolves to a private/internal address."
                    break

                resp = client.get(current)
                if resp.status_code in (301, 302, 303, 307, 308) and "location" in resp.headers:
                    next_url = urljoin(current, resp.headers["location"])
                    chain.append(next_url)
                    current = next_url
                    continue

                content_type = resp.headers.get("content-type", "")
                if "text/html" in content_type:
                    html_text = resp.text[:500_000]  # cap to keep parsing fast on huge pages
                break
            else:
                fetch_error = f"Stopped after {MAX_REDIRECTS} redirects without reaching a final page."
    except Exception as e:
        fetch_error = f"Could not fetch the page: {e}"

    return chain, current, html_text, fetch_error


def analyze_website(raw_url: str):
    signals = []
    url = raw_url.strip()
    if not re.match(r"^https?://", url, re.I):
        url = "http://" + url

    chain, final_url, html_text, fetch_error = _follow_redirects(url)
    hop_count = len(chain) - 1

    if fetch_error:
        signals.append(Signal("fetch_issue", fetch_error, 5, "technical"))

    if hop_count >= 5:
        signals.append(Signal(
            "long_redirect_chain",
            f"the page redirected {hop_count} times before reaching its final destination — an unusually long chain often used to evade detection",
            22, "behavioral",
        ))
    elif hop_count >= 2:
        signals.append(Signal(
            "multiple_redirects",
            f"the page redirected {hop_count} times before reaching its final destination",
            8, "behavioral",
        ))

    domains_in_chain = [urlparse(u).hostname for u in chain if urlparse(u).hostname]
    unique_domains = sorted(set(domains_in_chain))
    if len(unique_domains) > 1:
        signals.append(Signal(
            "cross_domain_redirect",
            f"the redirect chain crossed {len(unique_domains)} different domains ({', '.join(unique_domains[:4])}) before landing",
            15, "behavioral",
        ))

    # Reuse the URL Scanner's heuristics on both the original and final URL —
    # a phishing kit sometimes uses an innocuous-looking short link that only
    # reveals its real (suspicious) destination after redirecting.
    initial_assessment = url_analysis.analyze_url(url)
    if initial_assessment.risk_score > 0:
        signals.append(Signal(
            "initial_url_risk",
            f"the original URL independently scores {initial_assessment.risk_score}/100 risk on its own",
            initial_assessment.risk_score * 0.4, "reputation",
        ))
    if final_url != url:
        final_assessment = url_analysis.analyze_url(final_url)
        if final_assessment.risk_score > 0:
            signals.append(Signal(
                "final_url_risk",
                f"the final landing page URL independently scores {final_assessment.risk_score}/100 risk",
                final_assessment.risk_score * 0.5, "reputation",
            ))

    has_login_form = False
    credential_exfil_risk = False
    hidden_iframe_count = 0
    has_meta_refresh = False
    js_findings = []

    if html_text and BeautifulSoup is not None:
        soup = BeautifulSoup(html_text, "html.parser")

        if soup.find("input", {"type": "password"}):
            has_login_form = True
            page_domain = urlparse(final_url).hostname
            for form in soup.find_all("form"):
                if not form.find("input", {"type": "password"}):
                    continue
                action = form.get("action", "")
                if not action:
                    continue
                action_domain = urlparse(urljoin(final_url, action)).hostname
                if action_domain and page_domain and action_domain != page_domain:
                    credential_exfil_risk = True
                    signals.append(Signal(
                        "credential_exfil_form",
                        f"the login form on this page submits to a different domain ('{action_domain}') than "
                        f"the page itself ('{page_domain}') — a strong sign of credential harvesting",
                        35, "technical",
                    ))
                    break

        for iframe in soup.find_all("iframe"):
            style = (iframe.get("style") or "").replace(" ", "").lower()
            dims_hidden = iframe.get("width") in ("0", "1") or iframe.get("height") in ("0", "1")
            if "display:none" in style or "visibility:hidden" in style or dims_hidden:
                hidden_iframe_count += 1
        if hidden_iframe_count:
            signals.append(Signal(
                "hidden_iframe",
                f"the page contains {hidden_iframe_count} hidden iframe(s), sometimes used to load malicious content invisibly",
                18, "technical",
            ))

        if soup.find("meta", attrs={"http-equiv": re.compile("refresh", re.I)}):
            has_meta_refresh = True
            signals.append(Signal(
                "meta_refresh_redirect",
                "the page uses a meta-refresh redirect, sometimes used to bounce visitors through additional hops",
                8, "technical",
            ))

        script_text = " ".join(s.get_text() for s in soup.find_all("script"))
        for pattern, name, desc, weight in SUSPICIOUS_JS_PATTERNS:
            if pattern.search(script_text):
                signals.append(Signal(name, desc, weight, "technical"))
                js_findings.append(name)

    assessment = combine_signals(signals, base_confidence=0.55, subject_label=f"the website '{raw_url}'")

    extracted = {
        "redirect_chain": chain,
        "final_url": final_url,
        "redirect_hop_count": hop_count,
        "login_form_detected": has_login_form,
        "credential_exfil_risk": credential_exfil_risk,
        "hidden_iframe_count": hidden_iframe_count,
        "meta_refresh_redirect": has_meta_refresh,
        "suspicious_js_patterns": js_findings,
        "fetch_error": fetch_error,
    }
    return assessment, extracted
