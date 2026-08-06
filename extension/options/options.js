// options/options.js
import { getSettings, saveSettings } from "../lib/api.js";

async function load() {
  const settings = await getSettings();
  document.getElementById("api-base").value = settings.apiBase;
  document.getElementById("auto-block").checked = settings.autoBlock;
  document.getElementById("block-threshold").value = settings.blockThreshold;
}

document.getElementById("save-btn").addEventListener("click", async () => {
  const apiBase = document.getElementById("api-base").value.trim().replace(/\/$/, "");
  const autoBlock = document.getElementById("auto-block").checked;
  const blockThreshold = document.getElementById("block-threshold").value;

  await saveSettings({ apiBase: apiBase || "http://localhost:8000", autoBlock, blockThreshold });

  const note = document.getElementById("saved-note");
  const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1)(:|\/|$)/.test(apiBase);
  if (apiBase.startsWith("http://") && !isLocal) {
    note.textContent = "Saved — but this backend URL uses plain http:// for a non-local host. Use https:// in production so tokens and scanned content aren't sent in the clear.";
    note.style.color = "#d97706";
  } else {
    note.textContent = "Saved ✓";
    note.style.color = "";
  }
  note.classList.remove("hidden");
  setTimeout(() => note.classList.add("hidden"), isLocal || apiBase.startsWith("https://") ? 2000 : 6000);
});

load();
