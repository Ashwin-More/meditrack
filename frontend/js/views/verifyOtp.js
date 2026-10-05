// js/views/verifyOtp.js
// Replaces the old clickable-link email verification: the user types the
// 6-digit code MediTrack emailed/texted them.
import { api } from "../api.js";
import { el, wordmark, toast, otpFormatError, OTP_MESSAGES, startCooldown, bindCooldown } from "../ui.js";

export function renderVerifyOtp(root, _onLoggedIn, query = {}) {
  query = { ...query };
  let errorMsg = "";
  let successMsg = "";

  function draw() {
    root.innerHTML = "";

    const identifierInput = el("input", { type: "text", placeholder: "Username, email, or phone", value: query.identifier || "" });
    const otpInput = el("input", { type: "text", inputmode: "numeric", maxlength: "6", placeholder: "123456", autocomplete: "one-time-code" });
    const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Verify account"]);
    const resendBtn = el("button", { class: "btn btn-ghost btn-small", type: "button" }, ["Resend OTP"]);

    const form = el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorMsg = ""; successMsg = "";
        if (!identifierInput.value.trim()) return;
        const formatError = otpFormatError(otpInput.value);
        if (formatError) { errorMsg = formatError; draw(); return; }
        submitBtn.disabled = true;
        submitBtn.textContent = "Verifying…";
        try {
          const res = await api.verifyOtp(identifierInput.value.trim(), otpInput.value.trim());
          successMsg = res.message;
          draw();
        } catch (err) {
          errorMsg = err.message || OTP_MESSAGES.invalid;
          draw();
        } finally {
          submitBtn.disabled = false;
          submitBtn.textContent = "Verify account";
        }
      },
    }, [
      el("div", { class: "field" }, [el("label", {}, ["Username, email, or phone"]), identifierInput]),
      el("div", { class: "field" }, [el("label", {}, ["6-digit OTP"]), otpInput]),
      submitBtn,
      el("div", { class: "auth-row" }, [resendBtn]),
    ]);

    resendBtn.addEventListener("click", async () => {
      if (!identifierInput.value.trim()) { toast("Enter your username, email, or phone first", "error"); return; }
      resendBtn.disabled = true;
      try {
        await api.resendOtp(identifierInput.value.trim());
        startCooldown("verify");
        toast(OTP_MESSAGES.resent, "success"); // same wording whether or not the account exists
      } catch (err) {
        toast(err.message || "Could not send a new OTP", "error");
      } finally {
        bindCooldown(resendBtn, "verify", "Resend OTP");
      }
    });
    bindCooldown(resendBtn, "verify", "Resend OTP");

    const card = el("div", { class: "auth-card" }, [
      el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
      el("h1", { class: "auth-title" }, ["Verify your account"]),
      el("p", { class: "auth-subtitle" }, ["Enter the 6-digit OTP we sent to your email. Your account then waits for admin approval before you can log in."]),
      errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
      successMsg ? el("div", { class: "form-success" }, [successMsg]) : null,
      form,
      el("div", { class: "auth-switch" }, [
        el("a", { href: "#/login" }, ["Back to log in"]),
      ]),
    ]);

    root.appendChild(el("div", { class: "auth-screen" }, [card]));
  }

  draw();
}
