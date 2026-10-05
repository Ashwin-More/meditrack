// js/views/register.js
import { api } from "../api.js";
import { el, wordmark, startCooldown } from "../ui.js";

export function renderRegister(root) {
  let errorMsg = "";
  let successMsg = "";
  let role = "DOCTOR";

  function draw() {
    root.innerHTML = "";

    const usernameInput = el("input", {
      type: "text", placeholder: "e.g. dr.rahul", autocomplete: "username", maxlength: "50",
      pattern: "[A-Za-z][A-Za-z0-9._\\-]{2,49}",
      title: "3-50 characters, starting with a letter. Letters, numbers, dot, underscore or hyphen only.",
    });
    const emailInput = el("input", { type: "email", placeholder: "jsmith@hospital.com" });
    const passwordInput = el("input", { type: "password", placeholder: "8+ characters, a letter and a number" });
    const firstNameInput = el("input", { type: "text", placeholder: "Jane" });
    const lastNameInput = el("input", { type: "text", placeholder: "Smith" });
    const phoneInput = el("input", { type: "text", placeholder: "+1 555 0100" });

    const roleSelect = el("select", {
      onchange: (e) => { role = e.target.value; draw(); },
    }, [
      el("option", { value: "DOCTOR", selected: role === "DOCTOR" ? "selected" : null }, ["Doctor"]),
      el("option", { value: "NURSE", selected: role === "NURSE" ? "selected" : null }, ["Nurse"]),
    ]);
    roleSelect.value = role;

    const specializationInput = el("input", { type: "text", placeholder: "e.g. Cardiology" });
    const qualificationInput = el("input", { type: "text", placeholder: "e.g. MBBS, MD" });
    const licenseInput = el("input", { type: "text", placeholder: "License number" });
    const nurseQualInput = el("input", { type: "text", placeholder: "e.g. B.Sc Nursing" });

    const roleSpecificFields = role === "DOCTOR"
      ? el("div", {}, [
          el("div", { class: "field-row" }, [
            el("div", { class: "field" }, [el("label", {}, ["Specialization"]), specializationInput]),
            el("div", { class: "field" }, [el("label", {}, ["Qualification"]), qualificationInput]),
          ]),
          el("div", { class: "field" }, [el("label", {}, ["License number"]), licenseInput]),
        ])
      : el("div", { class: "field" }, [el("label", {}, ["Qualification"]), nurseQualInput]);

    const submitBtn = el("button", { class: "btn btn-primary btn-full", type: "submit" }, ["Create account"]);

    const form = el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorMsg = ""; successMsg = "";
        submitBtn.disabled = true;
        submitBtn.textContent = "Submitting…";
        try {
          const res = await api.register({
            username: usernameInput.value, // sent exactly as typed - never modified
            email: emailInput.value.trim(),
            password: passwordInput.value,
            firstName: firstNameInput.value.trim(),
            lastName: lastNameInput.value.trim(),
            phone: phoneInput.value.trim(),
            role,
            specialization: specializationInput.value.trim(),
            qualification: qualificationInput.value.trim(),
            licenseNumber: licenseInput.value.trim(),
            nurseQualification: nurseQualInput.value.trim(),
          });
          successMsg = "Registration successful. We sent a 6-digit OTP to your email.";
          const identifier = emailInput.value.trim();
          startCooldown("verify"); // the server just sent a code; resend unlocks after the cooldown
          draw();
          setTimeout(() => { window.location.hash = `#/verify-otp?identifier=${encodeURIComponent(identifier)}`; }, 900);
        } catch (err) {
          errorMsg = err.details && err.details.length ? err.details.join(" · ") : (err.message || "Registration failed");
          draw();
        } finally {
          submitBtn.disabled = false;
          submitBtn.textContent = "Create account";
        }
      },
    }, [
      el("div", { class: "field" }, [el("label", {}, ["I am a…"]), roleSelect]),
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["First name"]), firstNameInput]),
        el("div", { class: "field" }, [el("label", {}, ["Last name"]), lastNameInput]),
      ]),
      el("div", { class: "field" }, [
        el("label", {}, ["Username"]), usernameInput,
        el("small", { class: "text-muted" }, ["Choose your own login name (3-50 characters, starts with a letter)."]),
      ]),
      el("div", { class: "field" }, [el("label", {}, ["Email"]), emailInput]),
      el("div", { class: "field" }, [el("label", {}, ["Phone (also usable to log in)"]), phoneInput]),
      el("div", { class: "field" }, [el("label", {}, ["Password"]), passwordInput]),
      roleSpecificFields,
      submitBtn,
    ]);

    const card = el("div", { class: "auth-card wide" }, [
      el("div", { class: "auth-logo" }, [el("span", { class: "brand-mark" }, ["M"]), wordmark()]),
      el("h1", { class: "auth-title" }, ["Create your account"]),
      el("p", { class: "auth-subtitle" }, ["Doctor and Nurse accounts need a verified email and admin approval before you can log in."]),
      errorMsg ? el("div", { class: "form-error" }, [errorMsg]) : null,
      successMsg ? el("div", { class: "form-success" }, [successMsg]) : null,
      form,
      el("div", { class: "auth-switch" }, [
        "Already have an account? ",
        el("a", { href: "#/login" }, ["Log in"]),
      ]),
    ]);

    root.appendChild(el("div", { class: "auth-screen" }, [card]));
  }

  draw();
}
