// js/views/patientDetail.js
import { api, auth } from "../api.js";
import { el, toast, badge, statusBadgeKind, formatDate, formatDateTime, showFilePreview } from "../ui.js";
import { navigate } from "../router.js";
import { exportPatientReportPdf } from "../patientPdf.js";

export async function renderPatientDetail(root, patientId) {
  root.innerHTML = "";
  const user = auth.getUser();
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";
  const isNurse = user.role === "NURSE";
  const canAct = true;                                   // allergies: all three roles (care information)
  const canClinical = isAdmin || user.role === "DOCTOR";  // conditions + visits: admin / doctor only
  const canRecordVitals = user.role === "DOCTOR" || user.role === "NURSE";

  let patient, visits, doctors = [], nurses = [], vitals = [], appointments = [], documents = [];
  try {
    [patient, visits] = await Promise.all([
      api.getPatient(patientId),
      api.getVisitsForPatient(patientId),
    ]);
    if (user.role !== "NURSE") { /* doctors list needed to create a visit */ }
    doctors = await api.getDoctors().catch(() => []);
    [vitals, appointments, documents] = await Promise.all([
      api.getVitalsForPatient(patientId).catch(() => []),
      api.getAppointmentsForPatient(patientId).catch(() => []),
      api.getPatientDocuments(patientId).catch(() => []),
    ]);
    if (isAdmin) nurses = await api.getNurses().catch(() => []);
  } catch (err) {
    root.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load patient"]));
    return;
  }

  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, [patient.fullName]),
      el("p", { class: "page-subtitle" }, [
        `${patient.patientCode} · ${patient.age ?? "—"} yrs · ${patient.gender || "—"} · ${patient.bloodGroup || "no blood group on file"}`,
      ]),
    ]),
    el("div", { style: "display:flex; gap:8px;" }, [
      el("button", {
        class: "btn btn-secondary btn-small",
        onclick: async (e) => {
          const btn = e.currentTarget;
          btn.disabled = true;
          const original = btn.textContent;
          btn.textContent = "Preparing PDF…";
          try {
            await exportPatientReportPdf(patientId);
            toast("Patient report exported", "success");
          } catch (err) {
            toast(err.message || "Could not export patient report", "error");
          } finally {
            btn.disabled = false;
            btn.textContent = original;
          }
        },
      }, ["Export PDF"]),
      el("a", { class: "btn btn-secondary btn-small", href: "#/patients" }, ["← Back to patients"]),
    ]),
  ]));

  const grid = el("div", { class: "grid-2" });
  root.appendChild(grid);

  // ---- Left column: contact info, allergies, conditions ----
  const leftCol = el("div");
  grid.appendChild(leftCol);

  leftCol.appendChild(el("div", { class: "card" }, [
    el("h3", { class: "card-title" }, ["Contact"]),
    el("ul", { class: "item-list" }, [
      el("li", {}, [`Phone: ${patient.phone || "—"}`]),
      el("li", {}, [`Email: ${patient.email || "—"}`]),
      el("li", {}, [`Address: ${patient.address || "—"}`]),
      el("li", {}, [`Emergency: ${patient.emergencyContactName || "—"} (${patient.emergencyContactPhone || "—"})`]),
    ]),
  ]));

  // Care team (+ admin can reassign doctor & nurse; nurse can reassign just the doctor)
  const careCard = el("div", { class: "card" });
  leftCol.appendChild(careCard);
  renderCareTeam(careCard, patient, isAdmin, isNurse, doctors, nurses, patientId);

  const allergiesCard = el("div", { class: "card" });
  leftCol.appendChild(allergiesCard);
  renderAllergies(allergiesCard, patient, canAct, patientId);

  const conditionsCard = el("div", { class: "card" });
  leftCol.appendChild(conditionsCard);
  renderConditions(conditionsCard, patient, canClinical, patientId);

  const documentsCard = el("div", { class: "card" });
  leftCol.appendChild(documentsCard);
  renderDocuments(documentsCard, documents, patientId);

  // ---- Right column: visits ----
  const rightCol = el("div");
  grid.appendChild(rightCol);

  const vitalsCard = el("div", { class: "card" });
  rightCol.appendChild(vitalsCard);
  renderVitals(vitalsCard, vitals, canRecordVitals, patientId);

  const apptCard = el("div", { class: "card" });
  rightCol.appendChild(apptCard);
  renderPatientAppointments(apptCard, appointments, isAdmin || user.role === "DOCTOR", patientId);

  const visitsCard = el("div", { class: "card" });
  rightCol.appendChild(visitsCard);
  renderVisits(visitsCard, visits, patientId, doctors, canClinical, user, patient);
}

function renderAllergies(container, patient, canAct, patientId) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Allergies"]));

  if (patient.allergies.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No known allergies on file."]));
  } else {
    container.appendChild(el("ul", { class: "item-list" }, patient.allergies.map((a) =>
      el("li", {}, [
        el("strong", {}, [a.allergyName]),
        a.severity ? badge(a.severity, statusBadgeKind(a.severity)) : null,
        a.reaction ? ` — ${a.reaction}` : "",
      ])
    )));
  }

  if (!canAct) return;

  const nameInput = el("input", { type: "text", placeholder: "e.g. Penicillin" });
  const reactionInput = el("input", { type: "text", placeholder: "e.g. Rash" });
  const severitySelect = el("select", {}, [
    el("option", { value: "MILD" }, ["Mild"]),
    el("option", { value: "MODERATE" }, ["Moderate"]),
    el("option", { value: "SEVERE" }, ["Severe"]),
  ]);
  const addBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["+ Add allergy"]);

  container.appendChild(el("form", {
    class: "field-row", style: "margin-top:14px;align-items:end;",
    onsubmit: async (e) => {
      e.preventDefault();
      if (!nameInput.value.trim()) return;
      try {
        await api.addAllergy(patientId, {
          allergyName: nameInput.value.trim(),
          reaction: reactionInput.value.trim() || null,
          severity: severitySelect.value,
        });
        toast("Allergy added", "success");
        const refreshed = await api.getPatient(patientId);
        renderAllergies(container, refreshed, canAct, patientId);
      } catch (err) {
        toast(err.message || "Failed to add allergy", "error");
      }
    },
  }, [
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Allergy"]), nameInput]),
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Reaction"]), reactionInput]),
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Severity"]), severitySelect]),
    addBtn,
  ]));
}

const RECORD_TYPES = [
  ["CONDITION", "Condition / disease"],
  ["SURGERY", "Previous surgery"],
  ["HOSPITALIZATION", "Previous hospitalization"],
  ["NOTE", "Important medical note"],
];
const RECORD_TYPE_HEADINGS = {
  CONDITION: "Conditions",
  SURGERY: "Previous surgeries",
  HOSPITALIZATION: "Previous hospitalizations",
  NOTE: "Important medical notes",
};
const STATUS_LABELS = { ONGOING: "Ongoing", CHRONIC: "Chronic", RESOLVED: "Recovered" };

// Medical History: conditions, previous surgeries, hospitalizations and important notes.
function renderConditions(container, patient, canAct, patientId) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Medical history"]));

  const entries = patient.conditions || [];
  if (entries.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No medical history on file."]));
  } else {
    RECORD_TYPES.forEach(([type]) => {
      const group = entries.filter((c) => (c.recordType || "CONDITION") === type);
      if (group.length === 0) return;
      container.appendChild(el("p", { class: "section-title", style: "margin:12px 0 4px 0;" }, [RECORD_TYPE_HEADINGS[type]]));
      container.appendChild(el("ul", { class: "item-list" }, group.map((c) =>
        el("li", {}, [
          el("strong", {}, [c.conditionName]),
          " ",
          badge(STATUS_LABELS[c.status] || c.status, statusBadgeKind(c.status)),
          c.diagnosedDate ? ` — ${formatDate(c.diagnosedDate)}` : "",
          c.notes ? el("div", { class: "muted-text" }, [c.notes]) : null,
        ])
      )));
    });
  }

  if (!canAct) return;

  const typeSelect = el("select", {}, RECORD_TYPES.map(([value, label]) => el("option", { value }, [label])));
  const nameInput = el("input", { type: "text", placeholder: "e.g. Type 2 Diabetes / Appendectomy" });
  const dateInput = el("input", { type: "date" });
  const statusSelect = el("select", {}, [
    el("option", { value: "ONGOING" }, ["Ongoing"]),
    el("option", { value: "CHRONIC" }, ["Chronic"]),
    el("option", { value: "RESOLVED" }, ["Recovered"]),
  ]);
  const notesInput = el("input", { type: "text", placeholder: "Notes (optional)" });
  const addBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["+ Add to history"]);

  container.appendChild(el("hr", { class: "divider" }));
  container.appendChild(el("form", {
    style: "margin-top:8px;",
    onsubmit: async (e) => {
      e.preventDefault();
      if (!nameInput.value.trim()) return;
      addBtn.disabled = true;
      try {
        await api.addCondition(patientId, {
          recordType: typeSelect.value,
          conditionName: nameInput.value.trim(),
          diagnosedDate: dateInput.value || null,
          status: statusSelect.value,
          notes: notesInput.value.trim() || null,
        });
        toast("Added to medical history", "success");
        const fresh = await api.getPatient(patientId);
        renderConditions(container, fresh, canAct, patientId);
      } catch (err) {
        toast(err.message || "Failed to add", "error");
        addBtn.disabled = false;
      }
    },
  }, [
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Type"]), typeSelect]),
      el("div", { class: "field" }, [el("label", {}, ["Name"]), nameInput]),
    ]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Date (diagnosed / performed)"]), dateInput]),
      el("div", { class: "field" }, [el("label", {}, ["Status"]), statusSelect]),
    ]),
    el("div", { class: "field" }, [el("label", {}, ["Notes"]), notesInput]),
    addBtn,
  ]));
}

function renderVisits(container, visits, patientId, doctors, canAct, user, patient) {
  container.innerHTML = "";
  container.appendChild(el("div", { class: "flex-between" }, [
    el("h3", { class: "card-title" }, ["Visit history"]),
  ]));

  if (visits.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No visits recorded yet."]));
  } else {
    container.appendChild(el("ul", { class: "item-list" }, visits.map((v) =>
      el("li", { class: "flex-between" }, [
        el("div", {}, [
          el("div", {}, [
            el("span", { class: "pill-link", onclick: () => navigate(`/visits/${v.visitId}`) }, [
              `${formatDateTime(v.visitDate)} — Dr. ${v.doctorName}`,
            ]),
          ]),
          el("div", { style: "color:var(--text-muted);font-size:12.5px;margin-top:2px;" }, [v.chiefComplaint || "No chief complaint noted"]),
        ]),
        badge(v.status, statusBadgeKind(v.status)),
      ])
    )));
  }

  if (!canAct) return;

  container.appendChild(el("hr", { class: "divider" }));
  container.appendChild(buildNewVisitForm(patientId, doctors, user, patient));
}

function buildNewVisitForm(patientId, doctors, user, patient) {
  const doctorSelect = el("select", {}, [
    el("option", { value: "" }, ["Select doctor…"]),
    ...doctors.map((d) => el("option", { value: d.doctorId }, [`Dr. ${d.fullName} (${d.specialization || "General"})`])),
  ]);
  // Prefill the assigned doctor; a doctor can only record visits as themselves, so lock it for them.
  if (patient && patient.assignedDoctorId) doctorSelect.value = String(patient.assignedDoctorId);
  if (user.role === "DOCTOR") doctorSelect.disabled = true;
  const visitType = el("select", {}, [
    el("option", { value: "OPD" }, ["OPD"]),
    el("option", { value: "FOLLOW_UP" }, ["Follow-up"]),
    el("option", { value: "EMERGENCY" }, ["Emergency"]),
  ]);
  const complaint = el("input", { type: "text", placeholder: "e.g. Persistent cough" });
  const symptoms = el("textarea", { rows: "2", placeholder: "Symptoms described by the patient" });
  const submitBtn = el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["+ Record new visit"]);

  return el("form", {
    onsubmit: async (e) => {
      e.preventDefault();
      if (!doctorSelect.value) { toast("Please select a doctor", "error"); return; }
      submitBtn.disabled = true;
      try {
        const visit = await api.createVisit({
          patientId: Number(patientId),
          doctorId: Number(doctorSelect.value),
          visitType: visitType.value,
          chiefComplaint: complaint.value.trim() || null,
          symptoms: symptoms.value.trim() || null,
        });
        toast("Visit recorded", "success");
        navigate(`/visits/${visit.visitId}`);
      } catch (err) {
        toast(err.message || "Failed to create visit", "error");
      } finally {
        submitBtn.disabled = false;
      }
    },
  }, [
    el("p", { class: "section-title" }, ["Record a new visit"]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Doctor"]), doctorSelect]),
      el("div", { class: "field" }, [el("label", {}, ["Visit type"]), visitType]),
    ]),
    el("div", { class: "field" }, [el("label", {}, ["Chief complaint"]), complaint]),
    el("div", { class: "field" }, [el("label", {}, ["Symptoms"]), symptoms]),
    submitBtn,
  ]);
}


// ---------------------------------------------------------------------
// Care team + assignment
//   ADMIN / SUPER_ADMIN can reassign both doctor and nurse for any patient here.
//   NURSE has the same full assignment power, but exercises it from the dedicated
//   "Manage Assignments" page (any patient in the hospital, not just her own) —
//   this patient-detail page is read-only for her, since she can only open it
//   for patients already assigned to her anyway.
// ---------------------------------------------------------------------
function renderCareTeam(container, patient, isAdmin, isNurse, doctors, nurses, patientId) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Care team"]));
  container.appendChild(el("ul", { class: "item-list" }, [
    el("li", {}, [`Doctor: ${patient.assignedDoctorName ? "Dr. " + patient.assignedDoctorName : "Unassigned"}`]),
    el("li", {}, [`Nurse: ${patient.assignedNurseName || "Unassigned"}`]),
  ]));

  if (isAdmin) {
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

    container.appendChild(el("form", {
      style: "margin-top:14px;",
      onsubmit: async (e) => {
        e.preventDefault();
        try {
          const updated = await api.assignPatient(patientId,
            doctorSel.value ? Number(doctorSel.value) : null,
            nurseSel.value ? Number(nurseSel.value) : null);
          toast("Assignment saved", "success");
          renderCareTeam(container, updated, isAdmin, isNurse, doctors, nurses, patientId);
        } catch (err) { toast(err.message || "Failed to assign", "error"); }
      },
    }, [
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["Assign doctor"]), doctorSel]),
        el("div", { class: "field" }, [el("label", {}, ["Assign nurse"]), nurseSel]),
      ]),
      el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["Save assignment"]),
    ]));
    return;
  }

  if (isNurse) {
    container.appendChild(el("p", { class: "card-subtitle", style: "margin-top:10px;" }, [
      "You have full assignment power (same as an admin) from the ",
      el("a", { href: "#/assignments" }, ["Manage Assignments"]),
      " page — for any patient in the hospital, not just this one.",
    ]));
  }
}

// ---------------------------------------------------------------------
// Medical Documents / Attachments (previous records, discharge summaries,
// insurance paperwork, etc.) — separate from Medical Tests & Reports.
// ---------------------------------------------------------------------
const DOCUMENT_TYPES = [
  ["PREVIOUS_RECORD", "Previous Medical Record"],
  ["LAB_REPORT", "Lab Report"],
  ["XRAY_SCAN", "X-Ray / Scan"],
  ["PRESCRIPTION_DOC", "Prescription"],
  ["DISCHARGE_SUMMARY", "Discharge Summary"],
  ["INSURANCE", "Insurance Document"],
  ["OTHER", "Other"],
];
const DOCUMENT_TYPE_LABELS = Object.fromEntries(DOCUMENT_TYPES);

function documentIcon(fileType) {
  return (fileType || "").toLowerCase() === "pdf" ? "📄" : "🖼";
}

function renderDocuments(container, documents, patientId) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Medical Documents"]));
  container.appendChild(el("p", { class: "card-subtitle" }, ["Previous records, discharge summaries, insurance paperwork, and other attachments."]));

  if (documents.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No documents uploaded yet."]));
  } else {
    container.appendChild(el("ul", { class: "item-list" }, documents.map((d) =>
      el("li", { class: "flex-between" }, [
        el("div", {}, [
          el("div", {}, [`${documentIcon(d.fileType)} ${d.documentName}`]),
          el("div", { class: "muted-text" }, [
            `${DOCUMENT_TYPE_LABELS[d.documentType] || d.documentType} · ${formatDate(d.uploadedAt)}` +
            (d.uploadedByName ? ` · ${d.uploadedByName}` : ""),
          ]),
        ]),
        el("div", { style: "display:flex;gap:8px;flex-shrink:0;" }, [
          el("button", {
            class: "btn btn-ghost btn-small", type: "button",
            onclick: async () => {
              try { showFilePreview(await api.viewPatientDocument(d.documentId), d.documentName || d.fileName); }
              catch (err) { toast(err.message || "Failed to open document", "error"); }
            },
          }, ["View"]),
          el("button", {
            class: "btn btn-ghost btn-small", type: "button",
            onclick: async () => {
              try { await api.downloadPatientDocument(d.documentId, d.downloadFileName || d.fileName); }
              catch (err) { toast(err.message || "Failed to download document", "error"); }
            },
          }, ["Download"]),
        ]),
      ])
    )));
  }

  const nameInput = el("input", { type: "text", placeholder: "e.g. Previous Hospital Record" });
  const typeSelect = el("select", {}, DOCUMENT_TYPES.map(([value, label]) => el("option", { value }, [label])));
  const fileInput = el("input", { type: "file", accept: ".pdf,.jpg,.jpeg,.png" });
  const descInput = el("input", { type: "text", placeholder: "Optional description" });
  const uploadBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["+ Upload document"]);
  const errorSlot = el("div");

  container.appendChild(el("hr", { class: "divider" }));
  container.appendChild(el("form", {
    style: "margin-top:8px;",
    onsubmit: async (e) => {
      e.preventDefault();
      errorSlot.innerHTML = "";
      if (!fileInput.files || fileInput.files.length === 0) {
        errorSlot.appendChild(el("div", { class: "form-error" }, ["Choose a PDF, JPG, or PNG file first"]));
        return;
      }
      uploadBtn.disabled = true;
      try {
        await api.uploadPatientDocument(patientId, {
          documentName: nameInput.value.trim(),
          documentType: typeSelect.value,
          file: fileInput.files[0],
          description: descInput.value.trim() || null,
        });
        toast("Document uploaded", "success");
        renderDocuments(container, await api.getPatientDocuments(patientId), patientId);
      } catch (err) {
        errorSlot.appendChild(el("div", { class: "form-error" }, [err.message || "Failed to upload document"]));
        uploadBtn.disabled = false;
      }
    },
  }, [
    el("p", { class: "section-title", style: "margin-top:0;" }, ["Upload a document"]),
    errorSlot,
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Document name"]), nameInput]),
      el("div", { class: "field" }, [el("label", {}, ["Type"]), typeSelect]),
    ]),
    el("div", { class: "field" }, [el("label", {}, ["File (PDF, JPG, or PNG)"]), fileInput]),
    el("div", { class: "field" }, [el("label", {}, ["Description"]), descInput]),
    uploadBtn,
  ]));
}

// ---------------------------------------------------------------------
// Vitals
// ---------------------------------------------------------------------
// Indicative colouring only (not a diagnosis): ok / warn / danger.
const VITAL_RULES = {
  temperatureC: (v) => (v >= 39 || v < 35 ? "danger" : v >= 37.6 || v < 36.1 ? "warn" : "ok"),
  pulseBpm: (v) => (v > 120 || v < 50 ? "danger" : v > 100 || v < 60 ? "warn" : "ok"),
  respiratoryRate: (v) => (v > 24 || v < 10 ? "danger" : v > 20 || v < 12 ? "warn" : "ok"),
  spo2: (v) => (v < 90 ? "danger" : v < 95 ? "warn" : "ok"),
};

function bpLevel(sys, dia) {
  if (sys == null || dia == null) return "none";
  if (sys >= 180 || dia >= 120 || sys < 80) return "danger";
  if (sys >= 140 || dia >= 90 || sys < 90) return "warn";
  return "ok";
}

function tile(label, value, unit, level) {
  const has = value !== null && value !== undefined && value !== "";
  return el("div", { class: `vital-tile ${has ? level : "none"}` }, [
    el("div", { class: "v-label" }, [label]),
    el("div", { class: "v-value" }, [has ? String(value) : "—", has && unit ? el("span", { class: "v-unit" }, [unit]) : null]),
  ]);
}

function renderVitals(container, vitals, canRecord, patientId) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Vitals"]));

  if (vitals.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No vitals recorded yet."]));
  } else {
    const v = vitals[0];
    container.appendChild(el("p", { class: "card-subtitle" }, [
      `Latest — ${formatDateTime(v.recordedAt)} by ${v.recordedByName || "unknown"}${v.recordedByRole ? " (" + v.recordedByRole.toLowerCase() + ")" : ""}`,
    ]));
    container.appendChild(el("div", { class: "vitals-grid" }, [
      tile("Temperature", v.temperatureC, "°C", v.temperatureC != null ? VITAL_RULES.temperatureC(v.temperatureC) : "none"),
      tile("Pulse", v.pulseBpm, "bpm", v.pulseBpm != null ? VITAL_RULES.pulseBpm(v.pulseBpm) : "none"),
      tile("Blood pressure", v.systolicBp != null ? `${v.systolicBp}/${v.diastolicBp}` : null, "mmHg", bpLevel(v.systolicBp, v.diastolicBp)),
      tile("Resp. rate", v.respiratoryRate, "/min", v.respiratoryRate != null ? VITAL_RULES.respiratoryRate(v.respiratoryRate) : "none"),
      tile("SpO₂", v.spo2, "%", v.spo2 != null ? VITAL_RULES.spo2(v.spo2) : "none"),
      tile("Weight", v.weightKg, "kg", "ok"),
    ]));
    if (v.notes) container.appendChild(el("p", { class: "muted-text" }, [`Note: ${v.notes}`]));

    if (vitals.length > 1) {
      container.appendChild(el("p", { class: "section-title" }, ["Earlier readings"]));
      container.appendChild(el("div", { class: "table-wrap" }, [el("table", {}, [
        el("thead", {}, [el("tr", {}, ["When", "Temp", "Pulse", "BP", "SpO₂"].map((h) => el("th", {}, [h])))]),
        el("tbody", {}, vitals.slice(1, 6).map((r) => el("tr", {}, [
          el("td", {}, [formatDateTime(r.recordedAt)]),
          el("td", {}, [r.temperatureC ?? "—"]),
          el("td", {}, [r.pulseBpm ?? "—"]),
          el("td", {}, [r.systolicBp != null ? `${r.systolicBp}/${r.diastolicBp}` : "—"]),
          el("td", {}, [r.spo2 ?? "—"]),
        ]))),
      ])]));
    }
    container.appendChild(el("p", { class: "form-hint" }, ["Colours are an indicative guide only, not a diagnosis."]));
  }

  if (!canRecord) return;

  const f = {
    temperatureC: el("input", { type: "number", step: "0.1", placeholder: "36.8" }),
    pulseBpm: el("input", { type: "number", placeholder: "76" }),
    systolicBp: el("input", { type: "number", placeholder: "120" }),
    diastolicBp: el("input", { type: "number", placeholder: "80" }),
    respiratoryRate: el("input", { type: "number", placeholder: "16" }),
    spo2: el("input", { type: "number", placeholder: "98" }),
    weightKg: el("input", { type: "number", step: "0.1", placeholder: "72.5" }),
    notes: el("input", { type: "text", placeholder: "Optional note" }),
  };
  const errorSlot = el("div");
  const submit = el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["+ Record vitals"]);
  const num = (input) => (input.value === "" ? null : Number(input.value));

  container.appendChild(el("hr", { class: "divider" }));
  container.appendChild(el("form", {
    onsubmit: async (e) => {
      e.preventDefault();
      errorSlot.innerHTML = "";
      submit.disabled = true;
      try {
        await api.recordVitals({
          patientId: Number(patientId),
          temperatureC: num(f.temperatureC), pulseBpm: num(f.pulseBpm),
          systolicBp: num(f.systolicBp), diastolicBp: num(f.diastolicBp),
          respiratoryRate: num(f.respiratoryRate), spo2: num(f.spo2), weightKg: num(f.weightKg),
          notes: f.notes.value.trim() || null,
        });
        toast("Vitals recorded", "success");
        renderVitals(container, await api.getVitalsForPatient(patientId), canRecord, patientId);
      } catch (err) {
        errorSlot.appendChild(el("div", { class: "form-error" }, [err.details && err.details.length ? err.details.join(" · ") : (err.message || "Failed to record vitals")]));
        submit.disabled = false;
      }
    },
  }, [
    el("p", { class: "section-title", style: "margin-top:0;" }, ["Record new vitals"]),
    errorSlot,
    el("div", { class: "field-row-3" }, [
      el("div", { class: "field" }, [el("label", {}, ["Temp °C"]), f.temperatureC]),
      el("div", { class: "field" }, [el("label", {}, ["Pulse bpm"]), f.pulseBpm]),
      el("div", { class: "field" }, [el("label", {}, ["SpO₂ %"]), f.spo2]),
    ]),
    el("div", { class: "field-row-3" }, [
      el("div", { class: "field" }, [el("label", {}, ["Systolic"]), f.systolicBp]),
      el("div", { class: "field" }, [el("label", {}, ["Diastolic"]), f.diastolicBp]),
      el("div", { class: "field" }, [el("label", {}, ["Resp. rate"]), f.respiratoryRate]),
    ]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Weight kg"]), f.weightKg]),
      el("div", { class: "field" }, [el("label", {}, ["Notes"]), f.notes]),
    ]),
    submit,
  ]));
}

// ---------------------------------------------------------------------
// Appointments for this patient
// ---------------------------------------------------------------------
function renderPatientAppointments(container, appointments, canBook, patientId) {
  container.innerHTML = "";
  container.appendChild(el("div", { class: "flex-between" }, [
    el("h3", { class: "card-title" }, ["Appointments"]),
    canBook ? el("a", { class: "btn btn-cta btn-small", href: `#/appointments?patient=${patientId}` }, ["+ Book"]) : null,
  ]));
  if (appointments.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No appointments for this patient."]));
    return;
  }
  container.appendChild(el("ul", { class: "item-list" }, appointments.slice(0, 6).map((a) => el("li", { class: "flex-between" }, [
    el("div", {}, [
      el("div", {}, [formatDateTime(a.scheduledAt)]),
      el("div", { class: "muted-text" }, [`Dr. ${a.doctorName}${a.reason ? " · " + a.reason : ""}`]),
    ]),
    badge(a.status, statusBadgeKind(a.status)),
  ]))));
}
