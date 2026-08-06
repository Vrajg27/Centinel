// popup/popup.js
import { login, register, logout, isLoggedIn, getHistory } from "../lib/api.js";

const LEVEL_COLORS = {
  Safe: "#16a34a", Low: "#65a30d", Medium: "#d97706", High: "#ea580c", Critical: "#dc2626",
};

const authView = document.getElementById("auth-view");
const mainView = document.getElementById("main-view");

async function init() {
  if (await isLoggedIn()) {
    showMain();
  } else {
    showAuth();
  }
}

function showAuth() {
  authView.classList.remove("hidden");
  mainView.classList.add("hidden");
}

async function showMain() {
  authView.classList.add("hidden");
  mainView.classList.remove("hidden");
  await loadCurrentTabCard();
  await loadRecentHistory();
}

// ---- Auth tabs ----
document.getElementById("tab-login").addEventListener("click", () => {
  document.getElementById("tab-login").classList.add("active");
  document.getElementById("tab-register").classList.remove("active");
  document.getElementById("login-form").classList.remove("hidden");
  document.getElementById("register-form").classList.add("hidden");
});
document.getElementById("tab-register").addEventListener("click", () => {
  document.getElementById("tab-register").classList.add("active");
  document.getElementById("tab-login").classList.remove("active");
  document.getElementById("register-form").classList.remove("hidden");
  document.getElementById("login-form").classList.add("hidden");
});

document.getElementById("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const email = document.getElementById("login-email").value.trim();
  const password = document.getElementById("login-password").value;
  const errorEl = document.getElementById("login-error");
  errorEl.textContent = "";
  try {
    await login(email, password);
    await showMain();
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

document.getElementById("register-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const fullName = document.getElementById("register-name").value.trim();
  const email = document.getElementById("register-email").value.trim();
  const password = document.getElementById("register-password").value;
  const errorEl = document.getElementById("register-error");
  errorEl.textContent = "";
  try {
    await register(email, password, fullName);
    await login(email, password);
    await showMain();
  } catch (err) {
    errorEl.textContent = err.message;
  }
});

document.getElementById("logout-btn").addEventListener("click", async () => {
  await logout();
  showAuth();
});

document.getElementById("settings-btn").addEventListener("click", () => {
  chrome.runtime.openOptionsPage();
});

// ---- Current tab status ----
async function loadCurrentTabCard() {
  const container = document.getElementById("current-tab-card");
  container.innerHTML = `<p class="muted">Checking current tab…</p>`;

  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.url || !/^https?:\/\//i.test(tab.url)) {
    container.innerHTML = `<p class="muted">This tab isn't a scannable web page.</p>`;
    return;
  }

  chrome.runtime.sendMessage({ type: "GET_TAB_RESULT", tabId: tab.id }, async (response) => {
    const result = response?.result;
    if (result && result.url === tab.url) {
      renderCard(container, result);
    } else {
      container.innerHTML = `<p class="muted">Not scanned yet — scanning now…</p>`;
      chrome.runtime.sendMessage({ type: "RESCAN_ACTIVE_TAB" }, (res) => {
        if (res?.ok) {
          renderCard(container, { url: tab.url, ...res.result });
        } else {
          container.innerHTML = `<p class="muted">${res?.error || "Could not scan this tab."}</p>`;
        }
      });
    }
  });
}

function renderCard(container, result) {
  const redirectNote = result.hopCount > 0
    ? `<div class="row" style="margin-top:6px;"><span class="muted">Redirects</span><span>${result.hopCount} hop${result.hopCount === 1 ? "" : "s"} before landing here</span></div>`
    : "";
  container.innerHTML = `
    <div class="row">
      <strong>${new URL(result.url).hostname}</strong>
      <span class="badge" style="background:${LEVEL_COLORS[result.threat_level] || "#64748b"}">${result.threat_level}</span>
    </div>
    <div class="row" style="margin-top:6px;">
      <span class="muted">Risk Score</span>
      <span>${result.risk_score}/100</span>
    </div>
    ${redirectNote}
    <div class="explanation">${result.explanation}</div>
  `;
}

// ---- Manual scan ----
document.getElementById("manual-scan-btn").addEventListener("click", async () => {
  const input = document.getElementById("manual-url");
  const url = input.value.trim();
  if (!url) return;
  const resultBox = document.getElementById("manual-result");
  resultBox.innerHTML = `<p class="muted">Scanning…</p>`;

  chrome.runtime.sendMessage({ type: "MANUAL_SCAN", url }, (res) => {
    if (res?.ok) {
      const el = document.createElement("div");
      el.className = "card";
      resultBox.innerHTML = "";
      resultBox.appendChild(el);
      renderCard(el, { url, ...res.result });
    } else {
      resultBox.innerHTML = `<p class="muted">${res?.error || "Scan failed."}</p>`;
    }
  });
});

// ---- Recent history ----
async function loadRecentHistory() {
  const list = document.getElementById("recent-list");
  list.innerHTML = `<p class="muted">Loading…</p>`;
  try {
    const history = await getHistory(8);
    if (!history.length) {
      list.innerHTML = `<p class="muted">No scans yet.</p>`;
      return;
    }
    list.innerHTML = "";
    history.forEach((item) => {
      const row = document.createElement("div");
      row.className = "history-item";
      row.innerHTML = `
        <span class="target">${item.target_summary}</span>
        <span class="level" style="background:${LEVEL_COLORS[item.threat_level] || "#64748b"}">${item.threat_level}</span>
      `;
      list.appendChild(row);
    });
  } catch (e) {
    list.innerHTML = `<p class="muted">${e.message}</p>`;
  }
}

init();
