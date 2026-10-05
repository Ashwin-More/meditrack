// js/views/profile.js — view/edit own details and change password
import { api, auth } from "../api.js";
import { el, toast, badge } from "../ui.js";

export async function renderProfile(root) {
  root.innerHTML = "";
  let me;
  try { me = await api.getMe(); }
  catch (err) { root.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load profile"])); return; }

  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["My profile"]),
      el("p", { class: "page-subtitle" }, [`${me.username} · ${me.email}`]),
    ]),
    el("div", { class: "flex-gap" }, [badge(me.role, "neutral"), me.emailVerified ? badge("Email verified", "success") : badge("Email unverified", "warning")]),
  ]));

  // ---- details ----
  const first = el("input", { type: "text", value: me.firstName || "" });
  const last = el("input", { type: "text", value: me.lastName || "" });
  const phone = el("input", { type: "text", value: me.phone || "" });
  const detailsErr = el("div");
  root.appendChild(el("div", { class: "card" }, [
    el("h3", { class: "card-title" }, ["Personal details"]),
    el("p", { class: "card-subtitle" }, ["Username, email and role can only be changed by an administrator."]),
    el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        detailsErr.innerHTML = "";
        try {
          const updated = await api.updateProfile({ firstName: first.value.trim(), lastName: last.value.trim(), phone: phone.value.trim() });
          const u = auth.getUser();
          localStorage.setItem("meditrack.user", JSON.stringify({ ...u, fullName: updated.fullName }));
          document.getElementById("user-name").textContent = updated.fullName;
          document.getElementById("user-avatar").textContent = updated.fullName.charAt(0).toUpperCase();
          toast("Profile updated", "success");
        } catch (err) {
          detailsErr.appendChild(el("div", { class: "form-error" }, [err.details && err.details.length ? err.details.join(" · ") : (err.message || "Update failed")]));
        }
      },
    }, [
      detailsErr,
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["First name"]), first]),
        el("div", { class: "field" }, [el("label", {}, ["Last name"]), last]),
      ]),
      el("div", { class: "field" }, [el("label", {}, ["Phone"]), phone]),
      el("button", { class: "btn btn-primary", type: "submit" }, ["Save changes"]),
    ]),
  ]));

  // ---- password ----
  const cur = el("input", { type: "password", autocomplete: "current-password" });
  const nw = el("input", { type: "password", autocomplete: "new-password", placeholder: "8+ characters, a letter and a number" });
  const nw2 = el("input", { type: "password", autocomplete: "new-password" });
  const pwErr = el("div");
  root.appendChild(el("div", { class: "card" }, [
    el("h3", { class: "card-title" }, ["Change password"]),
    el("p", { class: "card-subtitle" }, ["Your other signed-in devices will be logged out."]),
    el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        pwErr.innerHTML = "";
        if (nw.value !== nw2.value) { pwErr.appendChild(el("div", { class: "form-error" }, ["The two new passwords do not match"])); return; }
        try {
          await api.changePassword(cur.value, nw.value);
          cur.value = nw.value = nw2.value = "";
          toast("Password changed", "success");
        } catch (err) {
          pwErr.appendChild(el("div", { class: "form-error" }, [err.details && err.details.length ? err.details.join(" · ") : (err.message || "Could not change password")]));
        }
      },
    }, [
      pwErr,
      el("div", { class: "field" }, [el("label", {}, ["Current password"]), cur]),
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["New password"]), nw]),
        el("div", { class: "field" }, [el("label", {}, ["Confirm new password"]), nw2]),
      ]),
      el("button", { class: "btn btn-primary", type: "submit" }, ["Update password"]),
    ]),
  ]));
}
