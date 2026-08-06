# Centinel — Browser Extension (Manifest V3)

Real-time phishing/malware protection in the browser, using the same
backend and the same user account as the Android app — log in here with
the account you registered on the app (or vice versa) and your scan
history is shared, exactly like the spec's "Synchronize with Android app"
requirement.

I wrote and syntax-checked every file in this extension (manifest JSON
validity, all `.js` files parsed as ES modules with Node, HTML structure)
in this sandbox, but **loading and running it requires an actual Chrome/
Edge/Brave browser, which this sandbox doesn't have** — so, like the
Android app, this is reviewed-but-unexecuted code, not something I clicked
through myself.

## What it does
- **Real-time URL scanning** — every top-level page navigation is sent to
  `POST /scan/url` in the background.
- **Redirect chain monitoring** — tracks every hop a page takes via
  `webNavigation.onBeforeRedirect` before it lands, shown in the popup and
  the warning page. Chains of 4+ redirects trigger their own notification
  even if the final page scores fine on its own, since chained redirects
  are a common way to dodge simple blocklists.
- **Blocks phishing/dangerous pages** — if a page's threat level meets your
  configured threshold (default: High or Critical), the tab is redirected
  to a full-page warning with the risk score, explanation, indicators, and
  recommended actions, plus a "Proceed Anyway" override.
- **In-page banner** — a lighter-weight warning banner is injected directly
  into flagged pages (useful when auto-block is off, and doubles as a
  fallback). It also flags when a risky page contains a login form.
- **Toolbar badge** — colored badge (green→red) shows the current tab's
  risk level at a glance.
- **Download monitoring** — flags downloads with dangerous extensions
  (.exe, .apk, .js, .ps1, etc.) via a notification. (The Downloads API
  doesn't expose file bytes, so deep file inspection still goes through the
  File Scanner in the popup/Android app.)
- **Popup** — login/register (JWT, same backend as the app), current tab's
  scan result, a manual "scan any URL" box, and your last 8 scans pulled
  live from `GET /history`.
- **Options page** — backend URL, auto-block on/off, block threshold.

## 1. Start the backend first
See `../backend/README.md`. By default the extension points at
`http://localhost:8000` (change this in the extension's Settings/options
page if your backend runs elsewhere).

## 2. Load the extension
1. Open `chrome://extensions` (or `edge://extensions`, or Brave's equivalent).
2. Turn on **Developer mode** (top-right toggle).
3. Click **Load unpacked** and select the `extension/` folder.
4. Click the Centinel icon in the toolbar, register or log in.

## Notes / limitations
- `chrome.storage.session` is used for the block-whitelist and per-tab scan
  cache so it survives the service worker being killed and restarted (MV3
  service workers aren't long-lived) — this needs **Chrome 102+**.
- If the backend is unreachable, the extension **fails open** (doesn't
  block anything) rather than breaking your browsing — check the toolbar
  badge / popup for a connection error.
- CORS: the backend's `main.py` already allows all origins, which covers
  the extension's `chrome-extension://<id>` origin with no changes needed.
- Clipboard URL monitoring (mentioned as optional in the spec) isn't
  implemented — it requires the sensitive `clipboardRead` permission, which
  most browsers will flag prominently to users during install; add it only
  if you specifically want that feature.
