// js/views/verifyEmail.js — landing page for the link in the verification email
import { api } from "../api.js";
import { el, wordmark } from "../ui.js";

export async function renderVerifyEmail(root, _onLoggedIn, query = {}) {
  function paint(icon, title, message, kind) {
    root.innerHTML = "";
    root.appendChild(el("div", { class: "auth-screen" }, [
      el("div", { class: "auth-card" }, [
        el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
        el("div", { class: "auth-status" }, [
          el("div", { class: "big" }, [icon]),
          el("h1", { class: "auth-title" }, [title]),
          el("div", { class: kind === "error" ? "form-error" : "form-success", style: "text-align:left;" }, [message]),
        ]),
        el("div", { class: "auth-switch" }, [el("a", { href: "#/login" }, ["Go to login"])]),
      ]),
    ]));
  }

  if (!query.token) {
    paint("⚠️", "Link incomplete", "This verification link is missing its token. Open the link from your email again.", "error");
    return;
  }

  paint("⏳", "Verifying…", "Checking your link, one moment.", "success");
  try {
    const res = await api.verifyEmail(query.token);
    paint("✅", "Email verified", res.message, "success");
  } catch (err) {
    paint("❌", "Could not verify", err.message || "This link is invalid or expired.", "error");
  }
}
