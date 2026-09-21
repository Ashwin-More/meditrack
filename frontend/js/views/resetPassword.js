// js/views/resetPassword.js — landing page for the link in the password-reset email
import { api } from "../api.js";
import { el, wordmark } from "../ui.js";

export function renderResetPassword(root, _onLoggedIn, query = {}) {
  let errorMsg = "";
  let done = false;

  function draw() {
    root.innerHTML = "";

    let body;
    if (!query.token) {
      body = [el("div", { class: "form-error" }, ["This reset link is missing its token. Open the link from your email again, or request a new one."]),
              el("div", { class: "auth-switch" }, [el("a", { href: "#/forgot-password" }, ["Request a new link"])])];
    } else if (done) {
      body = [el("div", { class: "form-success" }, ["Password updated. You can now log in with your new password."]),
              el("a", { class: "btn btn-primary btn-full", href: "#/login" }, ["Go to login"])];
    } else {
      const pw = el("input", { type: "password", placeholder: "At least 8 characters, with a letter and a number", autocomplete: "new-password" });
      const pw2 = el("input", { type: "password", placeholder: "Repeat the new password", autocomplete: "new-password" });
      const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Update password"]);
      body = [
        errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
        el("form", {
          onsubmit: async (e) => {
            e.preventDefault();
            errorMsg = "";
            if (pw.value !== pw2.value) { errorMsg = "The two passwords do not match"; draw(); return; }
            submitBtn.disabled = true;
            submitBtn.textContent = "Updating…";
            try {
              await api.resetPassword(query.token, pw.value);
              done = true;
            } catch (err) {
              errorMsg = err.details && err.details.length ? err.details.join(" · ") : (err.message || "Could not reset password");
            }
            draw();
          },
        }, [
          el("div", { class: "field" }, [el("label", {}, ["New password"]), pw]),
          el("div", { class: "field" }, [el("label", {}, ["Confirm password"]), pw2]),
          submitBtn,
        ]),
        el("div", { class: "auth-switch" }, [el("a", { href: "#/login" }, ["← Back to login"])]),
      ];
    }

    root.appendChild(el("div", { class: "auth-screen" }, [
      el("div", { class: "auth-card" }, [
        el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
        el("h1", { class: "auth-title" }, ["Choose a new password"]),
        el("p", { class: "auth-subtitle" }, ["Use at least 8 characters with a letter and a number."]),
        ...body,
      ]),
    ]));
  }

  draw();
}
