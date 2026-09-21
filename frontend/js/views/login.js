// js/views/login.js
import { api, auth } from "../api.js";
import { el, wordmark, toast } from "../ui.js";

export function renderLogin(root, onLoggedIn) {
  let errorMsg = "";
  let resendDone = "";

  function draw() {
    root.innerHTML = "";

    const needsVerification = errorMsg.toLowerCase().includes("verify your email");

    const usernameInput = el("input", { type: "text", id: "login-username", placeholder: "e.g. drrahul", autocomplete: "username" });
    const passwordInput = el("input", { type: "password", id: "login-password", placeholder: "••••••••", autocomplete: "current-password" });
    const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Log in"]);

    const form = el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorMsg = ""; resendDone = "";
        submitBtn.disabled = true;
        submitBtn.textContent = "Logging in…";
        try {
          const response = await api.login(usernameInput.value.trim(), passwordInput.value);
          auth.setSession(response);
          onLoggedIn();
        } catch (err) {
          errorMsg = err.message || "Login failed";
          draw();
        }
      },
    }, [
      el("div", { class: "field" }, [el("label", {}, ["Username"]), usernameInput]),
      el("div", { class: "field" }, [el("label", {}, ["Password"]), passwordInput]),
      el("div", { class: "auth-row" }, [el("a", { href: "#/forgot-password" }, ["Forgot password?"])]),
      submitBtn,
    ]);

    // Shown only when the server says the email is not verified yet.
    let resendBox = null;
    if (needsVerification) {
      const emailInput = el("input", { type: "email", placeholder: "Your registered email" });
      const resendBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["Send new verification link"]);
      resendBox = el("form", {
        class: "alert-info",
        onsubmit: async (e) => {
          e.preventDefault();
          if (!emailInput.value.trim()) return;
          resendBtn.disabled = true;
          try {
            const res = await api.resendVerification(emailInput.value.trim());
            resendDone = res.message;
            draw();
          } catch (err) {
            toast(err.message || "Could not send the link", "error");
            resendBtn.disabled = false;
          }
        },
      }, [
        el("div", { style: "margin-bottom:8px;" }, ["Didn't get the email? Enter your address and we'll send another link."]),
        el("div", { class: "field", style: "margin-bottom:8px;" }, [emailInput]),
        resendBtn,
      ]);
    }

    const card = el("div", { class: "auth-card" }, [
      el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
      el("h1", { class: "auth-title" }, ["Welcome back"]),
      el("p", { class: "auth-subtitle" }, ["Sign in to your Patient Management & EHR account."]),
      errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
      resendDone ? el("div", { class: "form-success" }, [resendDone]) : null,
      resendBox,
      form,
      el("div", { class: "auth-switch" }, [
        "New doctor or nurse? ",
        el("a", { href: "#/register" }, ["Create an account"]),
      ]),
      el("div", { class: "demo-box" }, [
        el("div", {}, [el("strong", {}, ["Demo logins"]), " (seeded on first boot, email pre-verified):"]),
        el("div", {}, [el("code", {}, ["admin"]), " / ", el("code", {}, ["Password@123"])]),
        el("div", {}, [el("code", {}, ["drrahul"]), " / ", el("code", {}, ["Password@123"])]),
        el("div", {}, [el("code", {}, ["nursepriya"]), " / ", el("code", {}, ["Password@123"])]),
      ]),
    ]);

    root.appendChild(el("div", { class: "auth-screen" }, [card]));
  }

  draw();
}
