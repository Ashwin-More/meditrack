// js/views/forgotPassword.js
import { api } from "../api.js";
import { el, wordmark } from "../ui.js";

export function renderForgotPassword(root) {
  let errorMsg = "";
  let successMsg = "";

  function draw() {
    root.innerHTML = "";
    const emailInput = el("input", { type: "email", placeholder: "you@hospital.com", autocomplete: "email" });
    const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Send reset link"]);

    const form = el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorMsg = ""; successMsg = "";
        submitBtn.disabled = true;
        submitBtn.textContent = "Sending…";
        try {
          const res = await api.forgotPassword(emailInput.value.trim());
          successMsg = res.message;
        } catch (err) {
          errorMsg = err.details && err.details.length ? err.details.join(" · ") : (err.message || "Request failed");
        }
        draw();
      },
    }, [
      el("div", { class: "field" }, [el("label", {}, ["Registered email"]), emailInput]),
      submitBtn,
    ]);

    root.appendChild(el("div", { class: "auth-screen" }, [
      el("div", { class: "auth-card" }, [
        el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
        el("h1", { class: "auth-title" }, ["Forgot your password?"]),
        el("p", { class: "auth-subtitle" }, ["Enter your email and we'll send you a link to choose a new one. It expires in 30 minutes."]),
        errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
        successMsg ? el("div", { class: "form-success" }, [successMsg]) : null,
        form,
        el("div", { class: "auth-switch" }, [el("a", { href: "#/login" }, ["← Back to login"])]),
      ]),
    ]));
  }

  draw();
}
