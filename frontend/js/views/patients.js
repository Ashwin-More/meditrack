// js/views/patients.js
import { api, auth } from "../api.js";
import { el, toast, formatDate, badge } from "../ui.js";
import { navigate } from "../router.js";

export async function renderPatients(root, query = {}) {
  root.innerHTML = "";

  let showCreateForm = query.new === "1";
  let searchTerm = "";
  let page = 0;
  const pageSize = 10;

  const header = el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["Patients"]),
      el("p", { class: "page-subtitle" }, [
        auth.getUser().role === "ADMIN"
          ? "Search every record, register patients, and see who each patient is assigned to."
          : "Showing patients assigned to you. Patients you register are assigned to you automatically.",
      ]),
    ]),
    el("button", {
      class: "btn btn-primary",
      onclick: () => { showCreateForm = !showCreateForm; redrawForm(); },
    }, [showCreateForm ? "Close form" : "+ Register patient"]),
  ]);
  root.appendChild(header);

  const formContainer = el("div");
  root.appendChild(formContainer);

  const searchBar = el("div", { class: "card" }, [
    el("div", { class: "field", style: "margin-bottom:0;" }, [
      el("label", {}, ["Search by name, code, or phone"]),
      el("input", {
        type: "text",
        placeholder: "e.g. Sharma, PAT-2026-001, 98765…",
        oninput: (e) => { searchTerm = e.target.value; page = 0; loadList(); },
      }),
    ]),
  ]);
  root.appendChild(searchBar);

  const listContainer = el("div", { class: "card" });
  root.appendChild(listContainer);

  function redrawForm() {
    formContainer.innerHTML = "";
    if (!showCreateForm) return;
    formContainer.appendChild(buildCreateForm(async () => {
      showCreateForm = false;
      redrawForm();
      page = 0;
      await loadList();
    }));
  }

  async function loadList() {
    listContainer.innerHTML = "";
    listContainer.appendChild(el("p", { class: "empty-state" }, ["Loading…"]));
    try {
      const result = await api.searchPatients(searchTerm, page, pageSize);
      renderList(result);
    } catch (err) {
      listContainer.innerHTML = "";
      listContainer.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load patients"]));
    }
  }

  function renderList(pageResult) {
    listContainer.innerHTML = "";
    const items = pageResult.content || [];
    if (items.length === 0) {
      listContainer.appendChild(el("p", { class: "empty-state" }, ["No patients found."]));
      return;
    }

    const table = el("table", {}, [
      el("thead", {}, [el("tr", {}, [
        el("th", {}, ["Code"]), el("th", {}, ["Name"]), el("th", {}, ["Age / Gender"]),
        el("th", {}, ["Phone"]), el("th", {}, ["Care team"]), el("th", {}, ["Registered"]),
      ])]),
      el("tbody", {}, items.map((p) => el("tr", {
        class: "row-clickable",
        onclick: () => navigate(`/patients/${p.patientId}`),
      }, [
        el("td", {}, [p.patientCode]),
        el("td", {}, [p.fullName]),
        el("td", {}, [`${p.age ?? "—"} / ${p.gender || "—"}`]),
        el("td", {}, [p.phone || "—"]),
        el("td", {}, [
          p.assignedDoctorName || p.assignedNurseName
            ? el("div", { class: "muted-text" }, [
                p.assignedDoctorName ? `Dr. ${p.assignedDoctorName}` : "no doctor",
                ` · ${p.assignedNurseName ? "Nurse " + p.assignedNurseName : "no nurse"}`,
              ])
            : badge("Unassigned", "warning"),
        ]),
        el("td", {}, [formatDate(p.createdAt)]),
      ]))),
    ]);
    listContainer.appendChild(el("div", { class: "table-wrap" }, [table]));

    const totalPages = pageResult.totalPages || 1;
    if (totalPages > 1) {
      const pager = el("div", { class: "flex-between", style: "margin-top:14px;" }, [
        el("button", {
          class: "btn btn-secondary btn-small",
          disabled: page === 0 ? "disabled" : null,
          onclick: () => { page = Math.max(0, page - 1); loadList(); },
        }, ["← Previous"]),
        el("span", { style: "font-size:12.5px;color:var(--text-muted);" }, [`Page ${page + 1} of ${totalPages}`]),
        el("button", {
          class: "btn btn-secondary btn-small",
          disabled: page + 1 >= totalPages ? "disabled" : null,
          onclick: () => { page = Math.min(totalPages - 1, page + 1); loadList(); },
        }, ["Next →"]),
      ]);
      listContainer.appendChild(pager);
    }
  }

  redrawForm();
  await loadList();
}

function buildCreateForm(onCreated) {
  const firstName = el("input", { type: "text", placeholder: "Jane" });
  const lastName = el("input", { type: "text", placeholder: "Doe" });
  const dob = el("input", { type: "date" });
  const gender = el("select", {}, [
    el("option", { value: "" }, ["Select…"]),
    el("option", { value: "MALE" }, ["Male"]),
    el("option", { value: "FEMALE" }, ["Female"]),
    el("option", { value: "OTHER" }, ["Other"]),
  ]);
  const bloodGroup = el("input", { type: "text", placeholder: "e.g. O+" });
  const phone = el("input", { type: "text", placeholder: "+1 555 0100" });
  const email = el("input", { type: "email", placeholder: "jane@example.com" });
  const address = el("input", { type: "text", placeholder: "123 Main St" });
  const ecName = el("input", { type: "text", placeholder: "Emergency contact name" });
  const ecPhone = el("input", { type: "text", placeholder: "Emergency contact phone" });

  let errorMsg = "";
  const errorSlot = el("div");

  const submitBtn = el("button", { class: "btn btn-primary", type: "submit" }, ["Register patient"]);

  const form = el("form", {
    onsubmit: async (e) => {
      e.preventDefault();
      errorSlot.innerHTML = "";
      submitBtn.disabled = true;
      try {
        const patient = await api.createPatient({
          firstName: firstName.value.trim(),
          lastName: lastName.value.trim(),
          dateOfBirth: dob.value || null,
          gender: gender.value || null,
          bloodGroup: bloodGroup.value.trim() || null,
          phone: phone.value.trim() || null,
          email: email.value.trim() || null,
          address: address.value.trim() || null,
          emergencyContactName: ecName.value.trim() || null,
          emergencyContactPhone: ecPhone.value.trim() || null,
        });
        toast(`Patient ${patient.patientCode} registered`, "success");
        onCreated();
      } catch (err) {
        errorSlot.innerHTML = "";
        errorSlot.appendChild(el("div", { class: "form-error" }, [err.message || "Failed to create patient"]));
      } finally {
        submitBtn.disabled = false;
      }
    },
  }, [
    errorSlot,
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["First name *"]), firstName]),
      el("div", { class: "field" }, [el("label", {}, ["Last name *"]), lastName]),
    ]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Date of birth"]), dob]),
      el("div", { class: "field" }, [el("label", {}, ["Gender"]), gender]),
    ]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Blood group"]), bloodGroup]),
      el("div", { class: "field" }, [el("label", {}, ["Phone"]), phone]),
    ]),
    el("div", { class: "field" }, [el("label", {}, ["Email"]), email]),
    el("div", { class: "field" }, [el("label", {}, ["Address"]), address]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Emergency contact name"]), ecName]),
      el("div", { class: "field" }, [el("label", {}, ["Emergency contact phone"]), ecPhone]),
    ]),
    submitBtn,
  ]);

  return el("div", { class: "card" }, [
    el("h3", { class: "card-title" }, ["Register new patient"]),
    el("p", { class: "card-subtitle" }, ["A patient code (e.g. PAT-2026-001) is generated automatically."]),
    form,
  ]);
}
