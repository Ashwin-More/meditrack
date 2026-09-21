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
    PENDING: "warning", ONGOING: "warning", MODERATE: "warning",
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
