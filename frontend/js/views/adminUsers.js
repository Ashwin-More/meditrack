// js/views/adminUsers.js
import { api } from "../api.js";
import { el, toast, badge, statusBadgeKind, formatDateTime } from "../ui.js";

export async function renderAdminUsers(root) {
  root.innerHTML = "";
  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["User management"]),
      el("p", { class: "page-subtitle" }, ["Approve new sign-ups, manage accounts, or create one directly."]),
    ]),
  ]));

  const createCard = el("div", { class: "card" });
  root.appendChild(createCard);
  buildCreateUserForm(createCard, () => loadAll());

  const pendingCard = el("div", { class: "card" });
  root.appendChild(pendingCard);

  const allCard = el("div", { class: "card" });
  root.appendChild(allCard);

  async function loadAll() {
    pendingCard.innerHTML = "";
    allCard.innerHTML = "";
    pendingCard.appendChild(el("p", { class: "empty-state" }, ["Loading…"]));
    allCard.appendChild(el("p", { class: "empty-state" }, ["Loading…"]));

    try {
      const [pending, all] = await Promise.all([api.getPendingUsers(), api.getAllUsers()]);
      renderPending(pendingCard, pending, loadAll);
      renderAll(allCard, all, loadAll);
    } catch (err) {
      pendingCard.innerHTML = "";
      pendingCard.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load users"]));
      allCard.innerHTML = "";
    }
  }

  await loadAll();
}

function renderPending(container, users, reload) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, [`Pending approvals (${users.length})`]));

  if (users.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["Nothing waiting on review."]));
    return;
  }

  container.appendChild(el("table", {}, [
    el("thead", {}, [el("tr", {}, [
      el("th", {}, ["Name"]), el("th", {}, ["Username"]), el("th", {}, ["Role"]), el("th", {}, ["Email"]), el("th", {}, ["Registered"]), el("th", {}, ["Actions"]),
    ])]),
    el("tbody", {}, users.map((u) => el("tr", {}, [
      el("td", {}, [u.fullName]),
      el("td", {}, [u.username]),
      el("td", {}, [badge(u.role, "neutral")]),
      el("td", {}, [u.emailVerified ? badge("Verified", "success") : badge("Unverified", "warning")]),
      el("td", {}, [formatDateTime(u.createdAt)]),
      el("td", { class: "flex-gap" }, [
        el("button", {
          class: "btn btn-primary btn-small",
          title: u.emailVerified ? "" : "The user must verify their email first",
          disabled: u.emailVerified ? null : "disabled",
          onclick: async () => {
            try { await api.approveUser(u.userId); toast("Approved", "success"); reload(); }
            catch (err) { toast(err.message || "Failed", "error"); }
          },
        }, ["Approve"]),
        el("button", {
          class: "btn btn-danger btn-small",
          onclick: async () => {
            try { await api.rejectUser(u.userId); toast("Rejected", "success"); reload(); }
            catch (err) { toast(err.message || "Failed", "error"); }
          },
        }, ["Reject"]),
      ]),
    ]))),
  ]));
}

function renderAll(container, users, reload) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, [`All users (${users.length})`]));

  container.appendChild(el("table", {}, [
    el("thead", {}, [el("tr", {}, [
      el("th", {}, ["Name"]), el("th", {}, ["Username"]), el("th", {}, ["Role"]), el("th", {}, ["Status"]), el("th", {}, ["Email"]), el("th", {}, ["Last login"]), el("th", {}, ["Actions"]),
    ])]),
    el("tbody", {}, users.map((u) => el("tr", {}, [
      el("td", {}, [u.fullName]),
      el("td", {}, [u.username]),
      el("td", {}, [badge(u.role, "neutral")]),
      el("td", {}, [badge(u.status, statusBadgeKind(u.status))]),
      el("td", {}, [u.emailVerified ? badge("Verified", "success") : badge("Unverified", "warning")]),
      el("td", {}, [formatDateTime(u.lastLogin)]),
      el("td", { class: "flex-gap" }, [
        !u.emailVerified
          ? el("button", {
              class: "btn btn-secondary btn-small",
              onclick: async () => {
                try { await api.resendUserVerification(u.userId); toast("Verification email sent", "success"); }
                catch (err) { toast(err.message || "Failed", "error"); }
              },
            }, ["Resend link"])
          : null,
        u.status === "ACTIVE"
          ? el("button", {
              class: "btn btn-danger btn-small",
              onclick: async () => {
                try { await api.disableUser(u.userId); toast("Disabled", "success"); reload(); }
                catch (err) { toast(err.message || "Failed", "error"); }
              },
            }, ["Disable"])
          : el("button", {
              class: "btn btn-secondary btn-small",
              onclick: async () => {
                try { await api.enableUser(u.userId); toast("Enabled", "success"); reload(); }
                catch (err) { toast(err.message || "Failed", "error"); }
              },
            }, ["Enable"]),
      ]),
    ]))),
  ]));
}

function buildCreateUserForm(container, onCreated) {
  container.innerHTML = "";
  let role = "DOCTOR";

  function draw() {
    container.innerHTML = "";

    const username = el("input", { type: "text", placeholder: "jsmith" });
    const email = el("input", { type: "email", placeholder: "jsmith@hospital.com" });
    const password = el("input", { type: "password", placeholder: "8+ chars, letter + number" });
    const firstName = el("input", { type: "text", placeholder: "Jane" });
    const lastName = el("input", { type: "text", placeholder: "Smith" });
    const phone = el("input", { type: "text", placeholder: "+1 555 0100" });
    const roleSelect = el("select", { onchange: (e) => { role = e.target.value; draw(); } }, [
      el("option", { value: "ADMIN", selected: role === "ADMIN" ? "selected" : null }, ["Admin"]),
      el("option", { value: "DOCTOR", selected: role === "DOCTOR" ? "selected" : null }, ["Doctor"]),
      el("option", { value: "NURSE", selected: role === "NURSE" ? "selected" : null }, ["Nurse"]),
    ]);
    roleSelect.value = role;

    const specialization = el("input", { type: "text", placeholder: "e.g. Cardiology" });
    const qualification = el("input", { type: "text", placeholder: "e.g. MBBS, MD" });
    const license = el("input", { type: "text", placeholder: "License number" });

    const roleFields = role === "DOCTOR"
      ? el("div", { class: "field-row" }, [
          el("div", { class: "field" }, [el("label", {}, ["Specialization"]), specialization]),
          el("div", { class: "field" }, [el("label", {}, ["Qualification"]), qualification]),
        ])
      : role === "NURSE"
        ? el("div", { class: "field" }, [el("label", {}, ["Qualification"]), qualification])
        : null;

    const errorSlot = el("div");
    const submitBtn = el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["Create user & send verification email"]);

    container.appendChild(el("h3", { class: "card-title" }, ["Create a user directly"]));
    container.appendChild(el("p", { class: "card-subtitle" }, ["Skips the approval queue, but the new user must still click the verification link we email them before they can log in."]));
    container.appendChild(el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorSlot.innerHTML = "";
        submitBtn.disabled = true;
        try {
          await api.createUser({
            username: username.value.trim(), email: email.value.trim(), password: password.value,
            firstName: firstName.value.trim(), lastName: lastName.value.trim(), phone: phone.value.trim(),
            role, specialization: specialization.value.trim(), qualification: qualification.value.trim(),
            licenseNumber: license.value.trim(),
          });
          toast("User created — verification email sent", "success");
          onCreated();
          draw();
        } catch (err) {
          errorSlot.appendChild(el("div", { class: "form-error" }, [err.details ? err.details.join(" · ") : (err.message || "Failed to create user")]));
        } finally {
          submitBtn.disabled = false;
        }
      },
    }, [
      errorSlot,
      el("div", { class: "field" }, [el("label", {}, ["Role"]), roleSelect]),
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["First name"]), firstName]),
        el("div", { class: "field" }, [el("label", {}, ["Last name"]), lastName]),
      ]),
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["Username"]), username]),
        el("div", { class: "field" }, [el("label", {}, ["Email"]), email]),
      ]),
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["Phone"]), phone]),
        el("div", { class: "field" }, [el("label", {}, ["Password"]), password]),
      ]),
      roleFields,
      submitBtn,
    ]));
  }

  draw();
}
