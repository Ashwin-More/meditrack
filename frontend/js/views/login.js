// js/views/login.js
import { api, auth } from "../api.js";
import { el, wordmark, toast, otpFormatError, OTP_MESSAGES, startCooldown, bindCooldown } from "../ui.js";

export function renderLogin(root, onLoggedIn) {
  let portal = null; // null | "SUPER_ADMIN" | "STAFF"
  let mode = "password"; // "password" | "otp-request" | "otp-verify"
  let errorMsg = "";
  let infoMsg = "";
  let identifierValue = "";

  function drawPortalPicker() {
    root.innerHTML = "";
    const card = el("div", { class: "auth-card" }, [
      el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
      el("h1", { class: "auth-title" }, ["Welcome to MediTrack"]),
      el("p", { class: "auth-subtitle" }, ["Choose which portal you're signing in to."]),
      el("div", { class: "portal-select" }, [
        el("button", {
          type: "button", class: "portal-option",
          onclick: () => { portal = "STAFF"; errorMsg = ""; infoMsg = ""; draw(); },
        }, [
          el("span", { class: "portal-icon" }, ["🩺"]),
          el("span", { class: "portal-text" }, [
            el("strong", {}, ["Staff Login"]),
            el("span", {}, ["Admin, Doctor & Nurse — day-to-day hospital operations"]),
          ]),
        ]),
        el("button", {
          type: "button", class: "portal-option",
          onclick: () => { portal = "SUPER_ADMIN"; errorMsg = ""; infoMsg = ""; draw(); },
        }, [
          el("span", { class: "portal-icon" }, ["🏛"]),
          el("span", { class: "portal-text" }, [
            el("strong", {}, ["Super Admin Login"]),
            el("span", {}, ["Institute-level — manages Admin accounts & system oversight"]),
          ]),
        ]),
      ]),
      el("div", { class: "auth-switch" }, [
        "New doctor or nurse? ",
        el("a", { href: "#/register" }, ["Create an account"]),
      ]),
    ]);
    root.appendChild(el("div", { class: "auth-screen" }, [card]));
  }

  function draw() {
    if (!portal) { drawPortalPicker(); return; }

    root.innerHTML = "";
    const isSuperAdminPortal = portal === "SUPER_ADMIN";
    const needsVerification = errorMsg.toLowerCase().includes("verify your account");

    const portalHeader = el("div", { class: "auth-row", style: "justify-content:space-between;margin-bottom:4px;" }, [
      el("span", { class: isSuperAdminPortal ? "portal-badge" : "muted-text" }, [
        isSuperAdminPortal ? "🏛 SUPER ADMIN" : "Staff login (Admin / Doctor / Nurse)",
      ]),
      el("a", { href: "#", onclick: (e) => { e.preventDefault(); portal = null; errorMsg = ""; infoMsg = ""; draw(); } }, ["Switch portal"]),
    ]);

    const tabs = el("div", { class: "auth-row", style: "gap:8px;margin-bottom:4px;" }, [
      el("button", {
        type: "button",
        class: `btn btn-small ${mode === "password" ? "btn-primary" : "btn-ghost"}`,
        onclick: () => { mode = "password"; errorMsg = ""; infoMsg = ""; draw(); },
      }, ["Password"]),
      el("button", {
        type: "button",
        class: `btn btn-small ${mode !== "password" ? "btn-primary" : "btn-ghost"}`,
        onclick: () => { mode = "otp-request"; errorMsg = ""; infoMsg = ""; draw(); },
      }, ["One-time code"]),
    ]);

    let formBlock;
    let resendBox = null;

    if (mode === "password") {
      const identifierInput = el("input", { type: "text", id: "login-username", placeholder: "Username, email, or phone", autocomplete: "username", value: identifierValue });
      const passwordInput = el("input", { type: "password", id: "login-password", placeholder: "••••••••", autocomplete: "current-password" });
      const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Log in"]);

      formBlock = el("form", {
        onsubmit: async (e) => {
          e.preventDefault();
          errorMsg = ""; infoMsg = "";
          identifierValue = identifierInput.value.trim();
          submitBtn.disabled = true;
          submitBtn.textContent = "Logging in…";
          try {
            const response = await api.login(identifierValue, passwordInput.value, portal);
            auth.setSession(response);
            onLoggedIn();
          } catch (err) {
            errorMsg = err.message || "Login failed";
            draw();
          }
        },
      }, [
        el("div", { class: "field" }, [el("label", {}, ["Username, email, or phone"]), identifierInput]),
        el("div", { class: "field" }, [el("label", {}, ["Password"]), passwordInput]),
        el("div", { class: "auth-row" }, [el("a", { href: "#/forgot-password" }, ["Forgot password?"])]),
        submitBtn,
      ]);

      if (needsVerification) {
        const otpInput = el("input", { type: "text", inputmode: "numeric", maxlength: "6", placeholder: "123456", autocomplete: "one-time-code" });
        const idInput = el("input", { type: "text", placeholder: "Username, email, or phone", value: identifierValue });
        const verifyBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["Verify"]);
        const resendBtn = el("button", { class: "btn btn-ghost btn-small", type: "button" }, ["Resend OTP"]);
        resendBox = el("form", {
          class: "alert-info",
          onsubmit: async (e) => {
            e.preventDefault();
            if (!idInput.value.trim()) return;
            const formatError = otpFormatError(otpInput.value);
            if (formatError) { toast(formatError, "error"); return; }
            verifyBtn.disabled = true;
            try {
              const res = await api.verifyOtp(idInput.value.trim(), otpInput.value.trim());
              infoMsg = res.message; errorMsg = "";
              draw();
            } catch (err) {
              toast(err.message || OTP_MESSAGES.invalid, "error");
              verifyBtn.disabled = false;
            }
          },
        }, [
          el("div", { style: "margin-bottom:8px;" }, ["Your account isn't verified yet. Enter the 6-digit OTP we sent to your email."]),
          el("div", { class: "field", style: "margin-bottom:8px;" }, [idInput]),
          el("div", { class: "field", style: "margin-bottom:8px;" }, [otpInput]),
          el("div", { class: "auth-row", style: "gap:8px;" }, [
            verifyBtn,
            resendBtn,
          ]),
        ]);
        resendBtn.addEventListener("click", async () => {
          if (!idInput.value.trim()) { toast("Enter your username, email, or phone first", "error"); return; }
          resendBtn.disabled = true;
          try {
            await api.resendOtp(idInput.value.trim());
            startCooldown("login-verify");
            toast(OTP_MESSAGES.resent, "success");
          } catch (err) {
            toast(err.message || "Could not send a new OTP", "error");
          } finally {
            bindCooldown(resendBtn, "login-verify", "Resend OTP");
          }
        });
        bindCooldown(resendBtn, "login-verify", "Resend OTP");
      }
    } else if (mode === "otp-request") {
      const identifierInput = el("input", { type: "text", placeholder: "Username, email, or phone", value: identifierValue });
      const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Send code"]);
      formBlock = el("form", {
        onsubmit: async (e) => {
          e.preventDefault();
          if (!identifierInput.value.trim()) return;
          identifierValue = identifierInput.value.trim();
          submitBtn.disabled = true;
          try {
            const res = await api.requestLoginOtp(identifierValue, portal);
            infoMsg = res.message; errorMsg = "";
            startCooldown("login-otp");
            mode = "otp-verify";
            draw();
          } catch (err) {
            errorMsg = err.message || "Could not send a code";
            draw();
          }
        },
      }, [
        el("p", { class: "text-muted", style: "margin-top:0;" }, ["We'll email you a 6-digit OTP — no password needed."]),
        el("div", { class: "field" }, [el("label", {}, ["Username, email, or phone"]), identifierInput]),
        submitBtn,
      ]);
    } else { // otp-verify
      const otpInput = el("input", { type: "text", inputmode: "numeric", maxlength: "6", placeholder: "123456", autofocus: "true", autocomplete: "one-time-code" });
      const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Verify & log in"]);
      const resendBtn = el("button", { class: "btn btn-ghost btn-small", type: "button" }, ["Resend OTP"]);
      formBlock = el("form", {
        onsubmit: async (e) => {
          e.preventDefault();
          errorMsg = "";
          const formatError = otpFormatError(otpInput.value);
          if (formatError) { errorMsg = formatError; draw(); return; }
          submitBtn.disabled = true;
          submitBtn.textContent = "Verifying…";
          try {
            const response = await api.verifyLoginOtp(identifierValue, otpInput.value.trim(), portal);
            auth.setSession(response);
            onLoggedIn();
          } catch (err) {
            errorMsg = err.message || OTP_MESSAGES.invalid;
            draw();
          }
        },
      }, [
        el("p", { class: "text-muted", style: "margin-top:0;" }, [`Code sent to ${identifierValue}.`]),
        el("div", { class: "field" }, [el("label", {}, ["6-digit OTP"]), otpInput]),
        submitBtn,
        el("div", { class: "auth-row" }, [resendBtn, el("a", { href: "#", onclick: (e) => { e.preventDefault(); mode = "otp-request"; draw(); } }, ["Use a different account"])]),
      ]);
      resendBtn.addEventListener("click", async () => {
        resendBtn.disabled = true;
        try {
          await api.requestLoginOtp(identifierValue, portal);
          startCooldown("login-otp");
          toast(OTP_MESSAGES.resent, "success");
        } catch (err) {
          toast(err.message || "Could not send a new OTP", "error");
        } finally {
          bindCooldown(resendBtn, "login-otp", "Resend OTP");
        }
      });
      bindCooldown(resendBtn, "login-otp", "Resend OTP");
    }

    const demoBox = isSuperAdminPortal
      ? el("div", { class: "demo-box" }, [
          el("div", {}, [el("strong", {}, ["Demo Super Admin login"]), ":"]),
          el("div", {}, [el("code", {}, ["superadmin"]), " / ", el("code", {}, ["SuperAdmin@123"])]),
          el("div", { class: "text-muted", style: "margin-top:4px;" }, ["The One-time code tab also works here (the code is emailed to the account)."]),
        ])
      : el("div", { class: "demo-box" }, [
          el("div", {}, [el("strong", {}, ["Demo logins"]), " (seeded on first boot, verified):"]),
          el("div", {}, [el("code", {}, ["admin"]), " / ", el("code", {}, ["Password@123"])]),
          el("div", {}, [el("code", {}, ["drrahul"]), " / ", el("code", {}, ["Password@123"])]),
          el("div", {}, [el("code", {}, ["nursepriya"]), " / ", el("code", {}, ["Password@123"])]),
          el("div", { class: "text-muted", style: "margin-top:4px;" }, ["Also works with their email or phone number. The One-time code tab emails a code to the account."]),
        ]);

    const card = el("div", { class: `auth-card ${isSuperAdminPortal ? "portal-super-admin" : ""}` }, [
      el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
      el("h1", { class: "auth-title" }, [isSuperAdminPortal ? "Institute Sign In" : "Welcome back"]),
      el("p", { class: "auth-subtitle" }, [
        isSuperAdminPortal ? "Super Admin access — manages Admin accounts and system-wide oversight." : "Sign in to your Patient Management & EHR account.",
      ]),
      portalHeader,
      tabs,
      errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
      infoMsg ? el("div", { class: "form-success" }, [infoMsg]) : null,
      resendBox,
      formBlock,
      el("div", { class: "auth-switch" }, [
        "New doctor or nurse? ",
        el("a", { href: "#/register" }, ["Create an account"]),
      ]),
      demoBox,
    ]);

    root.appendChild(el("div", { class: "auth-screen" }, [card]));
  }

  draw();
}
