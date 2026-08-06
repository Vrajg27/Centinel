// warning/warning.js
const params = new URLSearchParams(window.location.search);
const url = params.get("url") || "";
const score = params.get("score") || "?";
const level = params.get("level") || "High";
const explanation = params.get("explanation") || "This site was flagged as potentially dangerous.";
const indicators = JSON.parse(params.get("indicators") || "[]");
const recommendations = JSON.parse(params.get("recommendations") || "[]");
const hopCount = parseInt(params.get("hopCount") || "0", 10);
const tabId = params.get("tabId");

const LEVEL_COLORS = {
  Medium: "#d97706", High: "#ea580c", Critical: "#dc2626",
};
const LEVEL_ICONS = { Medium: "⚠️", High: "🛑", Critical: "☠️" };

document.getElementById("level-icon").textContent = LEVEL_ICONS[level] || "⚠️";
document.getElementById("title").textContent =
  level === "Critical" ? "Dangerous site blocked" : "This site was blocked";
document.getElementById("url-display").textContent = url;
document.getElementById("score-display").textContent = `Risk Score: ${score}/100`;

const badge = document.getElementById("level-badge");
badge.textContent = level;
badge.style.background = LEVEL_COLORS[level] || "#ea580c";

document.getElementById("explanation").textContent = explanation;

if (hopCount > 0) {
  const note = document.createElement("p");
  note.style.color = "#ea580c";
  note.style.fontWeight = "600";
  note.textContent = `This page also redirected ${hopCount} time(s) before landing here.`;
  document.getElementById("explanation").insertAdjacentElement("afterend", note);
}

const indicatorsList = document.getElementById("indicators-list");
if (indicators.length) {
  indicators.forEach((i) => {
    const li = document.createElement("li");
    li.textContent = i;
    indicatorsList.appendChild(li);
  });
} else {
  document.getElementById("indicators-section").style.display = "none";
}

const recList = document.getElementById("recommendations-list");
if (recommendations.length) {
  recommendations.forEach((r) => {
    const li = document.createElement("li");
    li.textContent = r;
    recList.appendChild(li);
  });
} else {
  document.getElementById("recommendations-section").style.display = "none";
}

document.getElementById("go-back-btn").addEventListener("click", () => {
  // Prefer closing back to wherever the user came from; fall back to a safe page.
  if (window.history.length > 1) {
    window.history.back();
  } else {
    window.location.href = "https://www.google.com";
  }
});

document.getElementById("proceed-btn").addEventListener("click", () => {
  chrome.runtime.sendMessage({ type: "PROCEED_ANYWAY", url, tabId }, () => {
    // background.js navigates this tab to the real URL and remembers the
    // choice for the rest of this browser session so it won't re-block it.
  });
});
