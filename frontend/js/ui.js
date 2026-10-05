// js/ui.js
// Small shared helpers: toast notifications and a terse element builder
// so views don't need a framework to stay readable.

export function toast(message, type = "info", timeoutMs = 3500) {
  const el = document.getElementById("toast");
  el.textContent = message;
  el.className = `toast ${type === "error" ? "error" : type === "success" ? "success" : ""}`;
  el.classList.remove("hidden");
  clearTimeout(toast._t);
  toast._t = setTimeout(() => el.classList.add("hidden"), timeoutMs);
}

/** el("div", {class: "card"}, ["text", el("span", {}, ["nested"])]) */
export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (key === "class") node.className = value;
    else if (key.startsWith("on") && typeof value === "function") node.addEventListener(key.slice(2), value);
    else if (key === "html") node.innerHTML = value;
    else node.setAttribute(key, value);
  }
  (Array.isArray(children) ? children : [children]).forEach((child) => {
    if (child === null || child === undefined) return;
    node.appendChild(typeof child === "string" ? document.createTextNode(child) : child);
  });
  return node;
}

export function badge(text, kind = "muted") {
  return el("span", { class: `badge badge-${kind}` }, [text]);
}

export function statusBadgeKind(status) {
  const map = {
    ACTIVE: "success", COMPLETED: "success", RESOLVED: "success", OPEN: "neutral",
    PENDING: "warning", ONGOING: "warning", CHRONIC: "info", MODERATE: "warning",
    DISABLED: "error", REJECTED: "error", CLOSED: "muted", SEVERE: "error", MILD: "muted",
    SCHEDULED: "info", CANCELLED: "muted",
  };
  return map[status] || "muted";
}

export function formatDate(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return value;
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

export function formatDateTime(value) {
  if (!value) return "—";
  const d = new Date(value);
  if (isNaN(d.getTime())) return value;
  return d.toLocaleString(undefined, { year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function clear(node) {
  while (node.firstChild) node.removeChild(node.firstChild);
}

/** "2026-09-20" style key in the browser's local time zone. */
export function localDateKey(value) {
  const d = value instanceof Date ? value : new Date(value);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function formatTime(value) {
  if (!value) return "—";
  const d = new Date(value);
  return isNaN(d.getTime()) ? value : d.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
}

/** Wordmark: "Medi" in white + "Track" in cyan. */
export function wordmark(cls = "") {
  return el("span", { class: cls }, [el("span", { class: "b-medi" }, ["Medi"]), el("span", { class: "b-track" }, ["Track"])]);
}

export const OTP_MESSAGES = {
  invalid: "Invalid OTP. Please try again.",
  resent: "A new OTP has been sent to your email.",
};

/** Returns an error message if the entered code is empty / not exactly 6 digits, else null. */
export function otpFormatError(value) {
  const v = (value || "").trim();
  if (!v) return "Please enter the OTP.";
  if (!/^\d{6}$/.test(v)) return OTP_MESSAGES.invalid;
  return null;
}

// Resend cooldown (mirrors the server's OTP_RESEND_COOLDOWN_SECONDS). Kept at module level so the
// countdown survives the views re-rendering themselves.
const cooldownUntil = {};
export function startCooldown(key, seconds = 30) { cooldownUntil[key] = Date.now() + seconds * 1000; }

/** Keeps `btn` disabled with a "(Ns)" countdown while the cooldown for `key` is running. */
export function bindCooldown(btn, key, label = "Resend code") {
  let first = true;
  const tick = () => {
    if (!first && !btn.isConnected) return; // view was re-rendered; the new button binds itself
    first = false;
    const left = Math.ceil(((cooldownUntil[key] || 0) - Date.now()) / 1000);
    if (left > 0) { btn.disabled = true; btn.textContent = `${label} (${left}s)`; setTimeout(tick, 1000); }
    else { btn.disabled = false; btn.textContent = label; }
  };
  tick();
}

/** Shows a fetched file (image / PDF blob) in an in-page overlay. Nothing is downloaded. */
export function showFilePreview(blob, title = "Preview") {
  const url = window.URL.createObjectURL(blob);
  const close = () => { overlay.remove(); window.URL.revokeObjectURL(url); document.removeEventListener("keydown", onKey); };
  const onKey = (e) => { if (e.key === "Escape") close(); };
  const isImage = (blob.type || "").startsWith("image/");
  const body = isImage
    ? el("img", { src: url, alt: title, style: "max-width:100%;max-height:100%;object-fit:contain;display:block;margin:auto;" })
    : el("iframe", { src: url, title, style: "width:100%;height:100%;border:0;background:#fff;" });
  const overlay = el("div", {
    style: "position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,0.6);display:flex;align-items:center;justify-content:center;padding:24px;",
    onclick: (e) => { if (e.target === overlay) close(); },
  }, [
    el("div", { style: "background:var(--card);color:var(--text);border:1px solid var(--border-strong);border-radius:12px;width:min(960px,100%);height:min(88vh,100%);display:flex;flex-direction:column;overflow:hidden;" }, [
      el("div", { style: "display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--border);" }, [
        el("strong", {}, [title]),
        el("button", { class: "btn btn-ghost btn-small", type: "button", onclick: close }, ["Close"]),
      ]),
      el("div", { style: "flex:1;min-height:0;overflow:auto;padding:0;display:flex;" }, [body]),
    ]),
  ]);
  document.addEventListener("keydown", onKey);
  document.body.appendChild(overlay);
}
