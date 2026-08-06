// lib/api.js
// Shared client for talking to the Centinel backend (the same FastAPI
// backend the Android app uses — logging in here uses the same account,
// so scan history is synchronized between the extension and the app).

const DEFAULT_API_BASE = "http://localhost:8000";

export async function getSettings() {
  const { apiBase, autoBlock, blockThreshold } = await chrome.storage.local.get([
    "apiBase", "autoBlock", "blockThreshold",
  ]);
  return {
    apiBase: apiBase || DEFAULT_API_BASE,
    autoBlock: autoBlock !== undefined ? autoBlock : true,
    blockThreshold: blockThreshold || "High", // block on High or Critical by default
  };
}

export async function saveSettings(partial) {
  await chrome.storage.local.set(partial);
}

async function getTokens() {
  const { accessToken, refreshToken } = await chrome.storage.local.get(["accessToken", "refreshToken"]);
  return { accessToken, refreshToken };
}

async function saveTokens(accessToken, refreshToken) {
  await chrome.storage.local.set({ accessToken, refreshToken });
}

export async function clearSession() {
  await chrome.storage.local.remove(["accessToken", "refreshToken", "userEmail"]);
}

export async function isLoggedIn() {
  const { accessToken } = await getTokens();
  return !!accessToken;
}

async function apiFetch(path, options = {}, retry = true) {
  const { apiBase } = await getSettings();
  const { accessToken } = await getTokens();

  const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;

  let resp;
  try {
    resp = await fetch(`${apiBase}${path}`, { ...options, headers });
  } catch (e) {
    throw new Error(`Could not reach the Centinel backend at ${apiBase}. Is it running?`);
  }

  if (resp.status === 401 && retry) {
    const refreshed = await tryRefresh();
    if (refreshed) return apiFetch(path, options, false);
  }

  if (!resp.ok) {
    let detail = `Request failed (${resp.status})`;
    try {
      const body = await resp.json();
      if (body.detail) detail = body.detail;
    } catch (_) {}
    throw new Error(detail);
  }

  if (resp.status === 204) return null;
  return resp.json();
}

async function tryRefresh() {
  const { refreshToken } = await getTokens();
  if (!refreshToken) return false;
  try {
    const { apiBase } = await getSettings();
    const resp = await fetch(`${apiBase}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refresh_token: refreshToken }),
    });
    if (!resp.ok) return false;
    const data = await resp.json();
    await saveTokens(data.access_token, data.refresh_token);
    return true;
  } catch (_) {
    return false;
  }
}

// ---- Auth ----
export async function register(email, password, fullName) {
  return apiFetch("/auth/register", {
    method: "POST",
    body: JSON.stringify({ email, password, full_name: fullName || null }),
  });
}

export async function login(email, password) {
  const data = await apiFetch("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  });
  await saveTokens(data.access_token, data.refresh_token);
  await chrome.storage.local.set({ userEmail: email });
  return data;
}

export async function logout() {
  try { await apiFetch("/auth/logout", { method: "POST" }); } catch (_) {}
  await clearSession();
}

// ---- Scans ----
export async function scanUrl(url) {
  return apiFetch("/scan/url", { method: "POST", body: JSON.stringify({ url }) });
}

export async function scanSms(message, sender) {
  return apiFetch("/scan/sms", { method: "POST", body: JSON.stringify({ message, sender: sender || null }) });
}

export async function getHistory(limit = 10) {
  return apiFetch(`/history?limit=${limit}`, { method: "GET" });
}

export async function getNotifications() {
  return apiFetch("/notifications", { method: "GET" });
}

export async function getAnalytics() {
  return apiFetch("/analytics", { method: "GET" });
}
