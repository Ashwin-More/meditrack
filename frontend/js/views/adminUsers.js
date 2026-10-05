// js/views/adminUsers.js
import { api, auth } from "../api.js";
import { el, toast, badge, statusBadgeKind, formatDateTime } from "../ui.js";

const ADMIN_TIER = ["ADMIN", "SUPER_ADMIN"];

export async function renderAdminUsers(root) {
  const viewer = auth.getUser();
  const viewerIsSuperAdmin = viewer.role === "SUPER_ADMIN";

  root.innerHTML = "";
  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["User management"]),
      el("p", { class: "page-subtitle" }, [
        viewerIsSuperAdmin
          ? "Approve new sign-ups, manage every account (including Admins), or create one directly."
          : "Approve new sign-ups, manage accounts, or create one directly. Admin-level accounts are managed by a Super Admin.",
      ]),
    ]),
  ]));

  const createCard = el("div", { class: "card" });
  root.appendChild(createCard);
  buildCreateUserForm(createCard, () => loadAll(), viewerIsSuperAdmin);

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
      renderPending(pendingCard, pending, loadAll, viewerIsSuperAdmin);
      renderAll(allCard, all, loadAll, viewerIsSuperAdmin);
    } catch (err) {
      pendingCard.innerHTML = "";
      pendingCard.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load users"]));
      allCard.innerHTML = "";
    }
  }

  await loadAll();
}

/** Non-super-admin viewers can't act on Admin-tier rows — shown as a muted note instead of buttons. */
function lockedNote() {
  return el("span", { class: "muted-text" }, ["Managed by Super Admin"]);
}

function renderPending(container, users, reload, viewerIsSuperAdmin) {
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
    el("tbody", {}, users.map((u) => {
      const isLocked = ADMIN_TIER.includes(u.role) && !viewerIsSuperAdmin;
      return el("tr", {}, [
        el("td", {}, [u.fullName]),
        el("td", {}, [u.username]),
        el("td", {}, [badge(u.role === "SUPER_ADMIN" ? "Super Admin" : u.role, "neutral")]),
        el("td", {}, [u.emailVerified ? badge("Verified", "success") : badge("Unverified", "warning")]),
        el("td", {}, [formatDateTime(u.createdAt)]),
        el("td", { class: "flex-gap" }, isLocked ? [lockedNote()] : [
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
      ]);
    })),
  ]));
}

function renderAll(container, users, reload, viewerIsSuperAdmin) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, [`All users (${users.length})`]));

  container.appendChild(el("table", {}, [
    el("thead", {}, [el("tr", {}, [
      el("th", {}, ["Name"]), el("th", {}, ["Username"]), el("th", {}, ["Role"]), el("th", {}, ["Status"]), el("th", {}, ["Email"]), el("th", {}, ["Last login"]), el("th", {}, ["Actions"]),
    ])]),
    el("tbody", {}, users.map((u) => {
      const isLocked = ADMIN_TIER.includes(u.role) && !viewerIsSuperAdmin;
      const actions = isLocked ? [lockedNote()] : [
        !u.emailVerified
          ? el("button", {
              class: "btn btn-secondary btn-small",
              onclick: async () => {
                try { await api.resendUserVerification(u.userId); toast("Verification code sent", "success"); }
                catch (err) { toast(err.message || "Failed", "error"); }
              },
            }, ["Resend code"])
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
      ];
      return el("tr", {}, [
        el("td", {}, [u.fullName]),
        el("td", {}, [u.username]),
        el("td", {}, [badge(u.role === "SUPER_ADMIN" ? "Super Admin" : u.role, "neutral")]),
        el("td", {}, [badge(u.status, statusBadgeKind(u.status))]),
        el("td", {}, [u.emailVerified ? badge("Verified", "success") : badge("Unverified", "warning")]),
        el("td", {}, [formatDateTime(u.lastLogin)]),
        el("td", { class: "flex-gap" }, actions),
      ]);
    })),
  ]));
}

function buildCreateUserForm(container, onCreated, viewerIsSuperAdmin) {
  container.innerHTML = "";
  let role = "DOCTOR";

  function draw() {
    container.innerHTML = "";

    const username = el("input", { type: "text", placeholder: "e.g. dr.rahul", maxlength: "50" });
    const email = el("input", { type: "email", placeholder: "jsmith@hospital.com" });
    const password = el("input", { type: "password", placeholder: "8+ chars, letter + number" });
    const firstName = el("input", { type: "text", placeholder: "Jane" });
    const lastName = el("input", { type: "text", placeholder: "Smith" });
    const phone = el("input", { type: "text", placeholder: "+1 555 0100" });

    // Only a Super Admin may create another Admin-tier account (closes the old privilege-escalation gap).
    const roleOptions = viewerIsSuperAdmin
      ? [["SUPER_ADMIN", "Super Admin"], ["ADMIN", "Admin"], ["DOCTOR", "Doctor"], ["NURSE", "Nurse"]]
      : [["DOCTOR", "Doctor"], ["NURSE", "Nurse"]];
    if (!roleOptions.some(([value]) => value === role)) role = roleOptions[0][0];

    const roleSelect = el("select", { onchange: (e) => { role = e.target.value; draw(); } },
      roleOptions.map(([value, label]) => el("option", { value, selected: role === value ? "selected" : null }, [label])));
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
    const submitBtn = el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["Create user & send verification code"]);

    container.appendChild(el("h3", { class: "card-title" }, ["Create a user directly"]));
    container.appendChild(el("p", { class: "card-subtitle" }, [
      viewerIsSuperAdmin
        ? "Skips the approval queue. Admin-tier accounts you create here are active immediately once verified."
        : "Skips the approval queue, but the new user must still enter the 6-digit code we send them before they can log in.",
    ]));
    container.appendChild(el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorSlot.innerHTML = "";
        submitBtn.disabled = true;
        try {
          const created = await api.createUser({
            username: role === "ADMIN" ? undefined : username.value, // Admin accounts get a generated one (admin001...); everyone else chooses, sent exactly as typed email: email.value.trim(), password: password.value,
            firstName: firstName.value.trim(), lastName: lastName.value.trim(), phone: phone.value.trim(),
            role, specialization: specialization.value.trim(), qualification: qualification.value.trim(),
            licenseNumber: license.value.trim(),
          });
          toast(`User created (${created && created.username ? created.username : "username generated"}) — verification code sent`, "success");
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
        role !== "ADMIN" ? el("div", { class: "field" }, [el("label", {}, ["Username"]), username]) : null,
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
