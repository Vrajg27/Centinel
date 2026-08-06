// background.js — MV3 service worker
import { getSettings, isLoggedIn, scanUrl } from "./lib/api.js";

const DANGEROUS_DOWNLOAD_EXT = [
  ".exe", ".scr", ".bat", ".cmd", ".vbs", ".js", ".jar", ".msi", ".apk", ".ps1",
];

const LEVEL_RANK = { Safe: 0, Low: 1, Medium: 2, High: 3, Critical: 4 };
const BADGE_COLORS = {
  Safe: "#16a34a", Low: "#65a30d", Medium: "#d97706", High: "#ea580c", Critical: "#dc2626",
};

// ---- Session-scoped state (survives service worker restarts via chrome.storage.session) ----
async function getWhitelist() {
  const { proceedWhitelist } = await chrome.storage.session.get("proceedWhitelist");
  return new Set(proceedWhitelist || []);
}
async function addToWhitelist(url) {
  const set = await getWhitelist();
  set.add(url);
  await chrome.storage.session.set({ proceedWhitelist: Array.from(set) });
}

async function setTabResult(tabId, result) {
  const { tabResults } = await chrome.storage.session.get("tabResults");
  const map = tabResults || {};
  map[tabId] = result;
  await chrome.storage.session.set({ tabResults: map });
}
async function getTabResult(tabId) {
  const { tabResults } = await chrome.storage.session.get("tabResults");
  return (tabResults || {})[tabId] || null;
}

// Redirect chains are tracked per-tab across the onBeforeNavigate ->
// onBeforeRedirect -> onCommitted sequence, so the popup/banner can show
// "this page redirected N times" alongside the scan result for the URL it
// finally landed on.
async function resetRedirectChain(tabId, startUrl) {
  const { redirectChains } = await chrome.storage.session.get("redirectChains");
  const map = redirectChains || {};
  map[tabId] = [startUrl];
  await chrome.storage.session.set({ redirectChains: map });
}
async function appendRedirectHop(tabId, nextUrl) {
  const { redirectChains } = await chrome.storage.session.get("redirectChains");
  const map = redirectChains || {};
  const chain = map[tabId] || [];
  chain.push(nextUrl);
  map[tabId] = chain;
  await chrome.storage.session.set({ redirectChains: map });
}
async function getRedirectChain(tabId) {
  const { redirectChains } = await chrome.storage.session.get("redirectChains");
  return (redirectChains || {})[tabId] || [];
}

function setBadge(tabId, level) {
  const short = { Safe: "OK", Low: "LOW", Medium: "MED", High: "HIGH", Critical: "!!!" }[level] || "";
  chrome.action.setBadgeText({ tabId, text: short });
  chrome.action.setBadgeBackgroundColor({ tabId, color: BADGE_COLORS[level] || "#64748b" });
}

// ---- Redirect chain tracking ----
// A fresh top-level navigation starts a new chain; every redirect along the
// way gets appended to it before onCommitted finally fires for wherever it
// landed.
chrome.webNavigation.onBeforeNavigate.addListener((details) => {
  if (details.frameId !== 0) return;
  resetRedirectChain(details.tabId, details.url);
});

chrome.webNavigation.onBeforeRedirect.addListener((details) => {
  if (details.frameId !== 0) return;
  appendRedirectHop(details.tabId, details.redirectUrl);
});

// ---- Real-time URL scanning on navigation ----
chrome.webNavigation.onCommitted.addListener(async (details) => {
  if (details.frameId !== 0) return; // top-level frames only
  const url = details.url;
  if (!/^https?:\/\//i.test(url)) return;

  if (!(await isLoggedIn())) {
    chrome.action.setBadgeText({ tabId: details.tabId, text: "" });
    return;
  }

  const settings = await getSettings();
  const whitelist = await getWhitelist();
  const redirectChain = await getRedirectChain(details.tabId);
  const hopCount = Math.max(redirectChain.length - 1, 0);

  try {
    const result = await scanUrl(url);
    await setTabResult(details.tabId, { url, redirectChain, hopCount, ...result });
    setBadge(details.tabId, result.threat_level);

    if (result.threat_level === "Critical") {
      chrome.notifications.create(`centinel-${details.tabId}-${Date.now()}`, {
        type: "basic",
        iconUrl: "icons/icon128.png",
        title: "Centinel — Critical threat blocked",
        message: `${url}\n${result.explanation}`,
        priority: 2,
      });
    } else if (hopCount >= 4) {
      // A long redirect chain is itself worth flagging even when the final
      // URL scores fine on its own — chained redirects are a common way to
      // dodge simple blocklists.
      chrome.notifications.create(`centinel-redirects-${details.tabId}-${Date.now()}`, {
        type: "basic",
        iconUrl: "icons/icon128.png",
        title: "Centinel — Long redirect chain",
        message: `This page redirected ${hopCount} times before loading. That's an unusually long chain, sometimes used to evade detection.`,
        priority: 1,
      });
    }

    const meetsThreshold = LEVEL_RANK[result.threat_level] >= LEVEL_RANK[settings.blockThreshold];
    if (settings.autoBlock && meetsThreshold && !whitelist.has(url)) {
      const warnUrl = chrome.runtime.getURL("warning/warning.html") +
        `?url=${encodeURIComponent(url)}` +
        `&score=${result.risk_score}` +
        `&level=${encodeURIComponent(result.threat_level)}` +
        `&confidence=${result.confidence}` +
        `&explanation=${encodeURIComponent(result.explanation)}` +
        `&indicators=${encodeURIComponent(JSON.stringify(result.indicators || []))}` +
        `&recommendations=${encodeURIComponent(JSON.stringify(result.recommendations || []))}` +
        `&hopCount=${hopCount}` +
        `&tabId=${details.tabId}`;
      chrome.tabs.update(details.tabId, { url: warnUrl });
    }
  } catch (e) {
    // Backend unreachable or scan failed — fail open (don't block) so a down
    // backend never bricks the user's browsing.
    console.warn("Centinel: scan failed", e.message);
  }
});

chrome.tabs.onRemoved.addListener(async (tabId) => {
  const { tabResults } = await chrome.storage.session.get("tabResults");
  if (tabResults && tabResults[tabId]) {
    delete tabResults[tabId];
    await chrome.storage.session.set({ tabResults });
  }
  const { redirectChains } = await chrome.storage.session.get("redirectChains");
  if (redirectChains && redirectChains[tabId]) {
    delete redirectChains[tabId];
    await chrome.storage.session.set({ redirectChains });
  }
});

// ---- Download monitoring (client-side extension heuristic; the real hash/
// malware inspection happens via the File Scanner in the popup/Android app,
// since the Downloads API does not expose file bytes directly) ----
chrome.downloads.onCreated.addListener((item) => {
  const name = (item.filename || item.url || "").toLowerCase();
  const isDangerous = DANGEROUS_DOWNLOAD_EXT.some((ext) => name.endsWith(ext));
  if (isDangerous) {
    chrome.notifications.create(`centinel-dl-${item.id}`, {
      type: "basic",
      iconUrl: "icons/icon128.png",
      title: "Centinel — Potentially unsafe download",
      message: `"${item.filename?.split("/").pop() || item.url}" is a file type commonly used to deliver malware. Scan it with the File Scanner before opening it.`,
      priority: 1,
    });
  }
});

// ---- Messaging bridge for popup / content script / warning page ----
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  (async () => {
    switch (message.type) {
      case "GET_TAB_RESULT": {
        const tabId = message.tabId ?? sender.tab?.id;
        sendResponse({ result: await getTabResult(tabId) });
        break;
      }
      case "PROCEED_ANYWAY": {
        await addToWhitelist(message.url);
        if (message.tabId) chrome.tabs.update(Number(message.tabId), { url: message.url });
        sendResponse({ ok: true });
        break;
      }
      case "MANUAL_SCAN": {
        try {
          const result = await scanUrl(message.url);
          sendResponse({ ok: true, result });
        } catch (e) {
          sendResponse({ ok: false, error: e.message });
        }
        break;
      }
      case "RESCAN_ACTIVE_TAB": {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        if (tab?.url && /^https?:\/\//i.test(tab.url)) {
          try {
            const result = await scanUrl(tab.url);
            await setTabResult(tab.id, { url: tab.url, ...result });
            setBadge(tab.id, result.threat_level);
            sendResponse({ ok: true, result });
          } catch (e) {
            sendResponse({ ok: false, error: e.message });
          }
        } else {
          sendResponse({ ok: false, error: "Current tab is not a scannable http(s) page." });
        }
        break;
      }
      default:
        sendResponse({ ok: false, error: "Unknown message type" });
    }
  })();
  return true; // keep the message channel open for the async response
});
