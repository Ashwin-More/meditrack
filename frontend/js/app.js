// js/app.js
import { api, auth } from "./api.js";
import { route, startRouter, navigate } from "./router.js";
import { el, clear, localDateKey } from "./ui.js";
import { initThemeToggle, applyTheme, getTheme } from "./theme.js";
import { renderLogin } from "./views/login.js";
import { renderRegister } from "./views/register.js";
import { renderDashboard } from "./views/dashboard.js";
import { renderPatients } from "./views/patients.js";
import { renderPatientDetail } from "./views/patientDetail.js";
import { renderVisitDetail } from "./views/visitDetail.js";
import { renderAdminUsers } from "./views/adminUsers.js";
import { renderAdminAuditLogs } from "./views/adminAuditLogs.js";
import { renderDirectory } from "./views/directory.js";
import { renderAppointments } from "./views/appointments.js";
import { renderProfile } from "./views/profile.js";
import { renderAssignments } from "./views/assignments.js";
import { renderVerifyOtp } from "./views/verifyOtp.js";
import { renderForgotPassword } from "./views/forgotPassword.js";

const authRoot = document.getElementById("auth-root");
const appShell = document.getElementById("app-shell");
const viewRoot = document.getElementById("view-root");

applyTheme(getTheme());
initThemeToggle(document.getElementById("theme-toggle-slot"));
const authThemeSlot = document.getElementById("auth-theme-slot");
initThemeToggle(authThemeSlot);

function showAuthScreen(renderFn, query = {}) {
  appShell.classList.add("hidden");
  authThemeSlot.classList.remove("hidden");
  authRoot.classList.remove("hidden");
  renderFn(authRoot, () => { showApp(); navigate("/dashboard"); }, query);
}

function showApp() {
  authRoot.classList.add("hidden");
  authThemeSlot.classList.add("hidden");
  authRoot.innerHTML = "";
  appShell.classList.remove("hidden");
  paintUserChip();
  applyRoleVisibility();
  paintTopbar();
}

let lastTopbarRefresh = 0;

/** Top bar: today's date, role pill, and a bell with a best-effort count (never blocks the page). */
async function paintTopbar(force = false) {
  const user = auth.getUser();
  if (!user) return;
  document.getElementById("topbar-date").textContent =
    new Date().toLocaleDateString(undefined, { weekday: "long", year: "numeric", month: "long", day: "numeric" });
  document.getElementById("topbar-role").textContent = user.role === "SUPER_ADMIN" ? "SUPER ADMIN" : user.role;

  if (!force && Date.now() - lastTopbarRefresh < 30000) return;
  lastTopbarRefresh = Date.now();

  const bell = document.getElementById("bell");
  const count = document.getElementById("bell-count");
  let n = 0;
  try {
    if (user.role === "ADMIN" || user.role === "SUPER_ADMIN") {
      bell.setAttribute("href", "#/admin/users");
      bell.title = "Accounts waiting for approval";
      n = (await api.getPendingUsers()).length;
    } else {
      bell.setAttribute("href", "#/appointments");
      bell.title = "Appointments today";
      const today = localDateKey(new Date());
      n = (await api.getAppointments()).filter((a) => a.status === "SCHEDULED" && localDateKey(a.scheduledAt) === today).length;
    }
  } catch { n = 0; }
  count.textContent = String(n);
  count.classList.toggle("hidden", n === 0);
}

function paintUserChip() {
  const user = auth.getUser();
  if (!user) return;
  document.getElementById("user-avatar").textContent = (user.fullName || "?").charAt(0).toUpperCase();
  document.getElementById("user-name").textContent = user.fullName;
  document.getElementById("user-role").textContent = user.role === "SUPER_ADMIN" ? "🏛 Super Admin" : user.role;
}

function applyRoleVisibility() {
  const user = auth.getUser();
  const isAdmin = user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN");
  const canAssign = user && (user.role === "ADMIN" || user.role === "SUPER_ADMIN" || user.role === "NURSE");
  document.querySelectorAll(".role-admin").forEach((elm) => {
    elm.style.display = isAdmin ? "" : "none";
  });
  document.querySelectorAll(".role-assign").forEach((elm) => {
    elm.style.display = canAssign ? "" : "none";
  });
}

document.getElementById("logout-btn").addEventListener("click", async () => {
  try { await api.logout(); } catch (e) { /* best-effort */ }
  auth.clearSession();
  lastTopbarRefresh = 0;
  window.location.hash = "#/login";
  showAuthScreen(renderLogin);
});

function requireAuth(handler) {
  return (params, query) => {
    if (!auth.isLoggedIn()) {
      showAuthScreen(renderLogin);
      return;
    }
    showApp();
    clear(viewRoot);
    handler(params, query);
  };
}

// ---- routes ----
route("/login", () => showAuthScreen(renderLogin));
route("/register", () => showAuthScreen(renderRegister));
route("/verify-otp", (params, query) => showAuthScreen(renderVerifyOtp, query));
route("/forgot-password", (params, query) => showAuthScreen(renderForgotPassword, query));

route("/dashboard", requireAuth(() => renderDashboard(viewRoot)));
route("/patients", requireAuth((params, query) => renderPatients(viewRoot, query)));
route("/patients/:id", requireAuth((params) => renderPatientDetail(viewRoot, params.id)));
route("/visits/:id", requireAuth((params) => renderVisitDetail(viewRoot, params.id)));
route("/appointments", requireAuth((params, query) => renderAppointments(viewRoot, query)));
route("/profile", requireAuth(() => renderProfile(viewRoot)));
route("/directory", requireAuth(() => renderDirectory(viewRoot)));
route("/assignments", requireAuth(() => renderAssignments(viewRoot)));
route("/admin/users", requireAuth(() => renderAdminUsers(viewRoot)));
route("/admin/audit-logs", requireAuth(() => renderAdminAuditLogs(viewRoot)));

// ---- boot ----
if (auth.isLoggedIn()) {
  showApp();
} else {
  showAuthScreen(renderLogin);
}

startRouter(() => navigate("/dashboard"));
