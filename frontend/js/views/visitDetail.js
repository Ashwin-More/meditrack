// js/views/visitDetail.js
import { api, auth } from "../api.js";
import { el, toast, badge, statusBadgeKind, formatDateTime, formatDate } from "../ui.js";
import { navigate } from "../router.js";

export async function renderVisitDetail(root, visitId) {
  root.innerHTML = "";
  const user = auth.getUser();
  const isDoctor = user.role === "DOCTOR";
  const isAdmin = user.role === "ADMIN";

  let visit, prescriptions, tests;
  try {
    visit = await api.getVisit(visitId);
    const [allPrescriptions, allTests] = await Promise.all([
      api.getPrescriptionsForPatient(visit.patientId),
      api.getTestsForPatient(visit.patientId),
    ]);
    prescriptions = allPrescriptions.filter((p) => p.visitId === visit.visitId);
    tests = allTests.filter((t) => t.visitId === visit.visitId);
  } catch (err) {
    root.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load visit"]));
    return;
  }

  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, [`Visit — ${formatDateTime(visit.visitDate)}`]),
      el("p", { class: "page-subtitle" }, [
        `${visit.patientName} · Dr. ${visit.doctorName} · `,
        badge(visit.visitType || "OPD", "neutral"),
        " ",
        badge(visit.status, statusBadgeKind(visit.status)),
      ]),
    ]),
    el("div", { class: "flex-gap" }, [
      el("a", { class: "btn btn-secondary btn-small", href: `#/patients/${visit.patientId}` }, ["← Back to patient"]),
      (isDoctor || isAdmin) && visit.status === "OPEN"
        ? el("button", {
            class: "btn btn-secondary btn-small",
            onclick: async () => {
              try { await api.closeVisit(visitId); toast("Visit closed", "success"); renderVisitDetail(root, visitId); }
              catch (err) { toast(err.message || "Failed to close visit", "error"); }
            },
          }, ["Close visit"])
        : null,
    ]),
  ]));

  root.appendChild(el("div", { class: "card" }, [
    el("h3", { class: "card-title" }, ["Notes"]),
    el("p", {}, [el("strong", {}, ["Chief complaint: "]), visit.chiefComplaint || "—"]),
    el("p", {}, [el("strong", {}, ["Symptoms: "]), visit.symptoms || "—"]),
    el("p", {}, [el("strong", {}, ["Doctor's notes: "]), visit.doctorNotes || "—"]),
    visit.followUpDate ? el("p", {}, [el("strong", {}, ["Follow-up date: "]), formatDate(visit.followUpDate)]) : null,
  ]));

  // ---- Diagnoses ----
  const diagCard = el("div", { class: "card" });
  root.appendChild(diagCard);
  renderDiagnoses(diagCard, visit, isDoctor);

  // ---- Prescriptions ----
  const rxCard = el("div", { class: "card" });
  root.appendChild(rxCard);
  renderPrescriptions(rxCard, visit, prescriptions, isDoctor);

  // ---- Medical tests ----
  const testsCard = el("div", { class: "card" });
  root.appendChild(testsCard);
  renderTests(testsCard, visit, tests, isDoctor, isAdmin, user);
}

function renderDiagnoses(container, visit, isDoctor) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Diagnoses"]));

  if (visit.diagnoses.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No diagnoses recorded for this visit."]));
  } else {
    container.appendChild(el("ul", { class: "item-list" }, visit.diagnoses.map((d) =>
      el("li", {}, [
        el("strong", {}, [d.diagnosisName]),
        d.severity ? badge(d.severity, statusBadgeKind(d.severity)) : null,
        el("div", { style: "color:var(--text-muted);font-size:12.5px;margin-top:2px;" }, [d.description || ""]),
      ])
    )));
  }

  if (!isDoctor) return;

  const nameInput = el("input", { type: "text", placeholder: "e.g. Acute bronchitis" });
  const description = el("textarea", { rows: "2", placeholder: "Clinical description" });
  const severity = el("select", {}, [
    el("option", { value: "" }, ["Severity (optional)"]),
    el("option", { value: "MILD" }, ["Mild"]),
    el("option", { value: "MODERATE" }, ["Moderate"]),
    el("option", { value: "SEVERE" }, ["Severe"]),
  ]);
  const submitBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["+ Add diagnosis"]);

  container.appendChild(el("form", {
    style: "margin-top:14px;",
    onsubmit: async (e) => {
      e.preventDefault();
      if (!nameInput.value.trim()) return;
      submitBtn.disabled = true;
      try {
        await api.addDiagnosis(visit.visitId, {
          diagnosisName: nameInput.value.trim(),
          description: description.value.trim() || null,
          severity: severity.value || null,
        });
        toast("Diagnosis added", "success");
        const refreshed = await api.getVisit(visit.visitId);
        renderDiagnoses(container, refreshed, isDoctor);
      } catch (err) {
        toast(err.message || "Failed to add diagnosis", "error");
      } finally {
        submitBtn.disabled = false;
      }
    },
  }, [
    el("div", { class: "field" }, [el("label", {}, ["Diagnosis"]), nameInput]),
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Description"]), description]),
      el("div", { class: "field" }, [el("label", {}, ["Severity"]), severity]),
    ]),
    submitBtn,
  ]));
}

function renderPrescriptions(container, visit, prescriptions, isDoctor) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Prescriptions"]));

  if (prescriptions.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No prescriptions written for this visit."]));
  } else {
    prescriptions.forEach((rx) => {
      if (rx.allergyWarnings && rx.allergyWarnings.length > 0) {
        container.appendChild(el("div", { class: "alert-warning" }, [
          el("strong", {}, ["⚠ Allergy warning"]),
          ...rx.allergyWarnings.map((w) => el("div", {}, [w])),
        ]));
      }
      container.appendChild(el("div", { style: "margin-bottom:14px;" }, [
        el("div", { style: "font-size:12.5px;color:var(--text-muted);margin-bottom:6px;" }, [formatDateTime(rx.prescriptionDate)]),
        el("table", {}, [
          el("thead", {}, [el("tr", {}, [
            el("th", {}, ["Medicine"]), el("th", {}, ["Dosage"]), el("th", {}, ["Frequency"]),
            el("th", {}, ["Duration"]), el("th", {}, ["Route"]),
          ])]),
          el("tbody", {}, rx.items.map((i) => el("tr", {}, [
            el("td", {}, [i.medicineName]), el("td", {}, [i.dosage || "—"]),
            el("td", {}, [i.frequency || "—"]), el("td", {}, [i.duration || "—"]), el("td", {}, [i.route || "—"]),
          ]))),
        ]),
        rx.instructions ? el("p", { style: "font-size:13px;color:var(--text-muted);margin-top:6px;" }, [`Instructions: ${rx.instructions}`]) : null,
      ]));
    });
  }

  if (!isDoctor) return;
  container.appendChild(el("hr", { class: "divider" }));
  container.appendChild(buildPrescriptionForm(visit));
}

function buildPrescriptionForm(visit) {
  const itemsContainer = el("div");
  let itemRows = [];

  function addItemRow() {
    const medicine = el("input", { type: "text", placeholder: "Medicine name" });
    const dosage = el("input", { type: "text", placeholder: "e.g. 500 mg" });
    const frequency = el("input", { type: "text", placeholder: "e.g. 2x/day" });
    const duration = el("input", { type: "text", placeholder: "e.g. 5 days" });
    const route = el("input", { type: "text", placeholder: "e.g. Oral" });
    const row = el("div", { class: "field-row", style: "grid-template-columns:2fr 1fr 1fr 1fr 1fr auto;align-items:end;gap:8px;margin-bottom:10px;" }, [
      el("div", {}, [el("label", {}, ["Medicine"]), medicine]),
      el("div", {}, [el("label", {}, ["Dosage"]), dosage]),
      el("div", {}, [el("label", {}, ["Frequency"]), frequency]),
      el("div", {}, [el("label", {}, ["Duration"]), duration]),
      el("div", {}, [el("label", {}, ["Route"]), route]),
      el("button", { type: "button", class: "btn btn-danger btn-small", onclick: () => removeRow(rowRef) }, ["✕"]),
    ]);
    const rowRef = { row, medicine, dosage, frequency, duration, route };
    itemRows.push(rowRef);
    itemsContainer.appendChild(row);
  }

  function removeRow(rowRef) {
    itemRows = itemRows.filter((r) => r !== rowRef);
    rowRef.row.remove();
  }

  addItemRow();

  const instructions = el("textarea", { rows: "2", placeholder: "General instructions (optional)" });
  const submitBtn = el("button", { class: "btn btn-primary btn-small", type: "submit" }, ["Save prescription"]);
  const errorSlot = el("div");

  return el("form", {
    onsubmit: async (e) => {
      e.preventDefault();
      errorSlot.innerHTML = "";
      const items = itemRows
        .filter((r) => r.medicine.value.trim())
        .map((r) => ({
          medicineName: r.medicine.value.trim(),
          dosage: r.dosage.value.trim() || null,
          frequency: r.frequency.value.trim() || null,
          duration: r.duration.value.trim() || null,
          route: r.route.value.trim() || null,
        }));
      if (items.length === 0) {
        errorSlot.appendChild(el("div", { class: "form-error" }, ["Add at least one medicine"]));
        return;
      }
      submitBtn.disabled = true;
      try {
        await api.createPrescription({
          visitId: visit.visitId,
          doctorId: visit.doctorId,
          patientId: visit.patientId,
          instructions: instructions.value.trim() || null,
          items,
        });
        toast("Prescription saved", "success");
        window.location.reload();
      } catch (err) {
        errorSlot.appendChild(el("div", { class: "form-error" }, [err.message || "Failed to save prescription"]));
      } finally {
        submitBtn.disabled = false;
      }
    },
  }, [
    el("p", { class: "section-title" }, ["New prescription"]),
    errorSlot,
    itemsContainer,
    el("button", { type: "button", class: "btn btn-secondary btn-small", style: "margin-bottom:14px;", onclick: addItemRow }, ["+ Add another medicine"]),
    el("div", { class: "field" }, [el("label", {}, ["Instructions"]), instructions]),
    submitBtn,
  ]);
}

function renderTests(container, visit, tests, isDoctor, isAdmin, user) {
  container.innerHTML = "";
  container.appendChild(el("h3", { class: "card-title" }, ["Medical tests"]));

  if (tests.length === 0) {
    container.appendChild(el("p", { class: "card-subtitle" }, ["No tests ordered for this visit."]));
  } else {
    tests.forEach((t) => container.appendChild(buildTestBlock(t, user)));
  }

  if (!isDoctor && !isAdmin) return;
  container.appendChild(el("hr", { class: "divider" }));

  const testName = el("input", { type: "text", placeholder: "e.g. Complete Blood Count" });
  const testType = el("select", {}, [
    el("option", { value: "BLOOD" }, ["Blood"]),
    el("option", { value: "X-RAY" }, ["X-Ray"]),
    el("option", { value: "MRI" }, ["MRI"]),
    el("option", { value: "CT" }, ["CT"]),
    el("option", { value: "URINE" }, ["Urine"]),
    el("option", { value: "OTHER" }, ["Other"]),
  ]);
  const submitBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["+ Order test"]);

  container.appendChild(el("form", {
    class: "field-row", style: "align-items:end;",
    onsubmit: async (e) => {
      e.preventDefault();
      if (!testName.value.trim()) return;
      submitBtn.disabled = true;
      try {
        await api.orderTest({
          visitId: visit.visitId,
          patientId: visit.patientId,
          doctorId: visit.doctorId,
          testName: testName.value.trim(),
          testType: testType.value,
        });
        toast("Test ordered", "success");
        window.location.reload();
      } catch (err) {
        toast(err.message || "Failed to order test", "error");
      } finally {
        submitBtn.disabled = false;
      }
    },
  }, [
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Test name"]), testName]),
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Type"]), testType]),
    submitBtn,
  ]));
}

function buildTestBlock(test, user) {
  const canUploadOrUpdate = ["ADMIN", "DOCTOR", "NURSE"].includes(user.role);

  const reportsList = el("ul", { class: "item-list" }, (test.reports || []).map((r) =>
    el("li", { class: "flex-between" }, [
      el("span", {}, [`${r.fileName} (${r.fileType || "?"})`]),
      el("button", {
        class: "btn btn-secondary btn-small",
        onclick: async () => {
          try { await api.downloadReport(r.reportId, r.fileName); }
          catch (err) { toast(err.message || "Download failed", "error"); }
        },
      }, ["Download"]),
    ])
  ));

  const fileInput = el("input", { type: "file", accept: ".pdf,.jpg,.jpeg,.png" });
  const descInput = el("input", { type: "text", placeholder: "Optional note" });
  const uploadBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["Upload report"]);

  const uploadForm = canUploadOrUpdate ? el("form", {
    class: "field-row", style: "align-items:end;margin-top:10px;",
    onsubmit: async (e) => {
      e.preventDefault();
      if (!fileInput.files[0]) { toast("Choose a file first", "error"); return; }
      uploadBtn.disabled = true;
      try {
        await api.uploadReport(test.testId, fileInput.files[0], descInput.value.trim());
        toast("Report uploaded", "success");
        window.location.reload();
      } catch (err) {
        toast(err.message || "Upload failed", "error");
      } finally {
        uploadBtn.disabled = false;
      }
    },
  }, [
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["File (PDF/JPG/PNG)"]), fileInput]),
    el("div", { class: "field", style: "margin-bottom:0;" }, [el("label", {}, ["Note"]), descInput]),
    uploadBtn,
  ]) : null;

  const resultInput = el("textarea", { rows: "2", placeholder: "Enter result text" });
  const statusSelect = el("select", {}, [
    el("option", { value: "PENDING", selected: test.status === "PENDING" ? "selected" : null }, ["Pending"]),
    el("option", { value: "COMPLETED", selected: test.status === "COMPLETED" ? "selected" : null }, ["Completed"]),
  ]);
  const saveResultBtn = el("button", { class: "btn btn-secondary btn-small", type: "submit" }, ["Save result"]);

  const resultForm = canUploadOrUpdate ? el("form", {
    style: "margin-top:10px;",
    onsubmit: async (e) => {
      e.preventDefault();
      saveResultBtn.disabled = true;
      try {
        await api.updateTestResult(test.testId, {
          visitId: test.visitId, patientId: test.patientId, doctorId: test.doctorId,
          testName: test.testName, testType: test.testType,
          result: resultInput.value.trim() || test.result,
          status: statusSelect.value,
        });
        toast("Result saved", "success");
        window.location.reload();
      } catch (err) {
        toast(err.message || "Failed to save result", "error");
      } finally {
        saveResultBtn.disabled = false;
      }
    },
  }, [
    el("div", { class: "field-row" }, [
      el("div", { class: "field" }, [el("label", {}, ["Result"]), resultInput]),
      el("div", { class: "field" }, [el("label", {}, ["Status"]), statusSelect]),
    ]),
    saveResultBtn,
  ]) : null;

  return el("div", { class: "card", style: "background:var(--bg-elev);box-shadow:none;" }, [
    el("div", { class: "flex-between" }, [
      el("strong", {}, [`${test.testName} (${test.testType || "—"})`]),
      badge(test.status, statusBadgeKind(test.status)),
    ]),
    test.result ? el("p", { style: "font-size:13.5px;margin:8px 0;" }, [`Result: ${test.result}`]) : null,
    reportsList.children.length ? reportsList : el("p", { class: "card-subtitle" }, ["No reports uploaded yet."]),
    uploadForm,
    resultForm,
  ]);
}
