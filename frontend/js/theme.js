// js/theme.js
// Two themes only (no "system" option, per product spec):
//   light (default) — accent #0288D1, background pure white
//   dark             — accent #001832, background pure black
// The very first application happens inline in index.html (before this module
// loads) so there's no flash of the wrong theme on page load.

const STORAGE_KEY = "meditrack.theme";

export function getTheme() {
  return localStorage.getItem(STORAGE_KEY) === "dark" ? "dark" : "light";
}

export function applyTheme(theme) {
  const resolved = theme === "dark" ? "dark" : "light";
  document.documentElement.setAttribute("data-theme", resolved);
  localStorage.setItem(STORAGE_KEY, resolved);
  const meta = document.getElementById("theme-color-meta");
  if (meta) meta.setAttribute("content", resolved === "dark" ? "#001832" : "#0288D1");
  document.querySelectorAll("[data-theme-toggle]").forEach((btn) => {
    btn.innerHTML = resolved === "dark"
      ? '<span class="icon">🌙</span><span>Dark mode</span>'
      : '<span class="icon">☀️</span><span>Light mode</span>';
  });
}

export function toggleTheme() {
  applyTheme(getTheme() === "dark" ? "light" : "dark");
}

export function initThemeToggle(container) {
  if (!container) return;
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "theme-toggle";
  btn.setAttribute("data-theme-toggle", "");
  btn.addEventListener("click", toggleTheme);
  container.appendChild(btn);
  applyTheme(getTheme());
}
