// content/content-script.js
// Runs on every http/https page. Two jobs:
//  1. Detect login/password forms — extra-important context for phishing.
//  2. Show a lightweight in-page banner if this page was flagged risky
//     (a fallback for when auto-block is switched off in Settings, or the
//     threshold is set above this page's level).

function hasLoginForm() {
  return document.querySelector('input[type="password"]') !== null;
}

function buildBanner(result, loginFormPresent) {
  if (document.getElementById("centinel-banner")) return;

  const banner = document.createElement("div");
  banner.id = "centinel-banner";
  banner.className = `centinel-banner centinel-${result.threat_level.toLowerCase()}`;

  const loginNote = loginFormPresent
    ? " This page also contains a login form — be extra cautious entering credentials."
    : "";

  banner.innerHTML = `
    <div class="centinel-banner-content">
      <img src="${chrome.runtime.getURL("icons/icon32.png")}" class="centinel-icon" alt="" />
      <div class="centinel-text">
        <strong>Centinel: ${result.threat_level} risk (${result.risk_score}/100)</strong>
        <span>${result.explanation}${loginNote}</span>
      </div>
      <button id="centinel-dismiss" class="centinel-dismiss">Dismiss</button>
    </div>
  `;
  document.documentElement.appendChild(banner);
  document.getElementById("centinel-dismiss").addEventListener("click", () => banner.remove());
}

function checkPageRisk() {
  chrome.runtime.sendMessage({ type: "GET_TAB_RESULT" }, (response) => {
    if (chrome.runtime.lastError) return; // extension context gone / reloaded, ignore
    const result = response?.result;
    if (!result) return;
    if (result.threat_level === "Medium" || result.threat_level === "High" || result.threat_level === "Critical") {
      buildBanner(result, hasLoginForm());
    }
  });
}

// The scan happens asynchronously in the background right after navigation,
// so give it a moment, then check. Also re-check if a form appears later
// (e.g. a login modal injected by client-side JS).
setTimeout(checkPageRisk, 800);
setTimeout(checkPageRisk, 2500);

const observer = new MutationObserver(() => {
  if (hasLoginForm()) {
    checkPageRisk();
    observer.disconnect();
  }
});
observer.observe(document.documentElement, { childList: true, subtree: true });
setTimeout(() => observer.disconnect(), 15000); // stop watching after 15s to save resources
