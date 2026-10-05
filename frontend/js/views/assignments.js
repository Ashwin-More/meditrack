// js/views/assignments.js
// "Manage Assignments" — ADMIN, SUPER_ADMIN and NURSE can all assign the doctor
// AND nurse responsible for ANY patient in the hospital from here (option A:
// a nurse has the same power as an admin for this one action, not just for
// patients already in her own care).
import { api } from "../api.js";
import { el, toast } from "../ui.js";

export async function renderAssignments(root) {
  root.innerHTML = "";

  let searchTerm = "";
  let page = 0;
  const pageSize = 10;
  let doctors = [];
  let nurses = [];

  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["Manage Assignments"]),
      el("p", { class: "page-subtitle" }, [
        "Assign the doctor and nurse responsible for any patient in the hospital.",
      ]),
    ]),
  ]));

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

  const listContainer = el("div", { style: "margin-top:16px;" });
  root.appendChild(listContainer);

  function rowForm(patient) {
    const doctorSel = el("select", {}, [
      el("option", { value: "" }, ["— No doctor —"]),
      ...doctors.map((d) => el("option", { value: d.doctorId }, [`Dr. ${d.fullName}`])),
    ]);
    const nurseSel = el("select", {}, [
      el("option", { value: "" }, ["— No nurse —"]),
      ...nurses.map((n) => el("option", { value: n.nurseId }, [n.fullName])),
    ]);
    doctorSel.value = patient.assignedDoctorId ? String(patient.assignedDoctorId) : "";
    nurseSel.value = patient.assignedNurseId ? String(patient.assignedNurseId) : "";
    const saveBtn = el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["Save"]);

    return el("form", {
      class: "field-row",
      style: "align-items:flex-end;margin-top:10px;",
      onsubmit: async (e) => {
        e.preventDefault();
        saveBtn.disabled = true;
        try {
          await api.assignPatient(patient.patientId,
            doctorSel.value ? Number(doctorSel.value) : null,
            nurseSel.value ? Number(nurseSel.value) : null);
          toast(`Assignment saved for ${patient.fullName}`, "success");
        } catch (err) {
          toast(err.message || "Failed to save assignment", "error");
        } finally {
          saveBtn.disabled = false;
        }
      },
    }, [
      el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Doctor"]), doctorSel]),
      el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Nurse"]), nurseSel]),
      saveBtn,
    ]);
  }

  async function loadList() {
    listContainer.innerHTML = "";
    listContainer.appendChild(el("p", { class: "empty-state" }, ["Loading patients…"]));
    try {
      const result = await api.searchPatientsForAssignment(searchTerm, page, pageSize);
      const items = result.content || [];
      listContainer.innerHTML = "";

      if (items.length === 0) {
        listContainer.appendChild(el("p", { class: "empty-state" }, ["No patients found."]));
        return;
      }

      items.forEach((patient) => {
        listContainer.appendChild(el("div", { class: "card" }, [
          el("div", { class: "flex-between" }, [
            el("div", {}, [
              el("h3", { class: "card-title", style: "margin-bottom:2px;" }, [patient.fullName]),
              el("p", { class: "muted-text" }, [
                `${patient.patientCode} · ${patient.age ?? "—"} yrs · ${patient.gender || "—"}`,
              ]),
            ]),
            el("div", { class: "muted-text", style: "text-align:right;" }, [
              el("div", {}, [`Doctor: ${patient.assignedDoctorName ? "Dr. " + patient.assignedDoctorName : "Unassigned"}`]),
              el("div", {}, [`Nurse: ${patient.assignedNurseName || "Unassigned"}`]),
            ]),
          ]),
          rowForm(patient),
        ]));
      });

      if (result.totalPages > 1) {
        listContainer.appendChild(el("div", { class: "pagination" }, [
          el("button", {
            class: "btn btn-ghost btn-small", disabled: page === 0,
            onclick: () => { page -= 1; loadList(); },
          }, ["← Previous"]),
          el("span", { class: "muted-text" }, [`Page ${page + 1} of ${result.totalPages}`]),
          el("button", {
            class: "btn btn-ghost btn-small", disabled: page >= result.totalPages - 1,
            onclick: () => { page += 1; loadList(); },
          }, ["Next →"]),
        ]));
      }
    } catch (err) {
      listContainer.innerHTML = "";
      listContainer.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load patients"]));
    }
  }

  [doctors, nurses] = await Promise.all([
    api.getDoctors().catch(() => []),
    api.getNurses().catch(() => []),
  ]);
  await loadList();
}
