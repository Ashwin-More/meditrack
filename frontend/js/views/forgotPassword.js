// js/views/forgotPassword.js
// Two-step, OTP-based flow (replaces the old emailed reset link):
//   1) enter identifier -> we send a 6-digit code
//   2) enter code + new password -> done
import { api } from "../api.js";
import { el, wordmark, toast, otpFormatError, OTP_MESSAGES, startCooldown, bindCooldown } from "../ui.js";

export function renderForgotPassword(root, _onLoggedIn, query = {}) {
  let step = query.identifier ? "reset" : "request";
  let identifierValue = query.identifier || "";
  let errorMsg = "";
  let successMsg = "";

  function draw() {
    root.innerHTML = "";
    let form;

    if (step === "request") {
      const identifierInput = el("input", { type: "text", placeholder: "Username, email, or phone", value: identifierValue });
      const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Send reset code"]);
      form = el("form", {
        onsubmit: async (e) => {
          e.preventDefault();
          if (!identifierInput.value.trim()) return;
          identifierValue = identifierInput.value.trim();
          submitBtn.disabled = true;
          try {
            const res = await api.forgotPassword(identifierValue);
            successMsg = res.message; errorMsg = "";
            startCooldown("forgot");
            step = "reset";
            draw();
          } catch (err) {
            errorMsg = err.message || "Could not send a reset code";
            draw();
          } finally {
            submitBtn.disabled = false;
          }
        },
      }, [
        el("div", { class: "field" }, [el("label", {}, ["Username, email, or phone"]), identifierInput]),
        submitBtn,
      ]);
    } else {
      const otpInput = el("input", { type: "text", inputmode: "numeric", maxlength: "6", placeholder: "123456", autocomplete: "one-time-code" });
      const newPasswordInput = el("input", { type: "password", placeholder: "8+ characters, a letter and a number" });
      const confirmInput = el("input", { type: "password", placeholder: "Repeat new password" });
      const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Reset password"]);
      const resendBtn = el("button", { class: "btn btn-ghost btn-small", type: "button" }, ["Resend OTP"]);

      form = el("form", {
        onsubmit: async (e) => {
          e.preventDefault();
          errorMsg = "";
          const formatError = otpFormatError(otpInput.value);
          if (formatError) { errorMsg = formatError; draw(); return; }
          if (newPasswordInput.value !== confirmInput.value) {
            errorMsg = "Passwords do not match";
            draw();
            return;
          }
          submitBtn.disabled = true;
          submitBtn.textContent = "Resetting…";
          try {
            await api.resetPassword(identifierValue, otpInput.value.trim(), newPasswordInput.value);
            successMsg = "Password updated. You can log in with your new password now.";
            step = "done";
            draw();
          } catch (err) {
            errorMsg = err.details && err.details.length ? err.details.join(" · ") : (err.message || "Could not reset your password");
            draw();
          } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = "Reset password";
          }
        },
      }, [
        el("p", { class: "text-muted", style: "margin-top:0;" }, [`Code sent to ${identifierValue}.`]),
        el("div", { class: "field" }, [el("label", {}, ["6-digit OTP"]), otpInput]),
        el("div", { class: "field" }, [el("label", {}, ["New password"]), newPasswordInput]),
        el("div", { class: "field" }, [el("label", {}, ["Confirm new password"]), confirmInput]),
        submitBtn,
        el("div", { class: "auth-row" }, [
          resendBtn,
          el("a", { href: "#", onclick: (e) => { e.preventDefault(); step = "request"; errorMsg = ""; successMsg = ""; draw(); } }, ["Use a different account"]),
        ]),
      ]);
      resendBtn.addEventListener("click", async () => {
        resendBtn.disabled = true;
        try {
          await api.forgotPassword(identifierValue);
          startCooldown("forgot");
          toast(OTP_MESSAGES.resent, "success");
        } catch (err) {
          toast(err.message || "Could not send a new OTP", "error");
        } finally {
          bindCooldown(resendBtn, "forgot", "Resend OTP");
        }
      });
      bindCooldown(resendBtn, "forgot", "Resend OTP");
    }

    const card = el("div", { class: "auth-card" }, [
      el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
      el("h1", { class: "auth-title" }, ["Reset your password"]),
      el("p", { class: "auth-subtitle" }, [
        step === "request"
          ? "We'll send a 6-digit code to your email or phone."
          : "Enter the code and choose a new password.",
      ]),
      errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
      successMsg && step !== "done" ? el("div", { class: "form-success" }, [successMsg]) : null,
      step === "done" ? el("div", { class: "form-success" }, [successMsg]) : form,
      el("div", { class: "auth-switch" }, [
        el("a", { href: "#/login" }, ["Back to log in"]),
      ]),
    ]);

    root.appendChild(el("div", { class: "auth-screen" }, [card]));
  }

  draw();
}
