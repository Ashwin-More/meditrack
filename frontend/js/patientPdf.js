// js/patientPdf.js
// PDF exports for patients:
//   exportPatientReportPdf(patientId)  - the complete record of ONE patient
//   exportAllPatientsPdf(searchTerm)   - a register of ALL patients the signed-in user may see
import { api, auth } from "./api.js";
import { PdfDoc } from "./pdf.js";
import { localDateKey } from "./ui.js";

const D = { day: "2-digit", month: "short", year: "numeric" };
const fmtDate = (v) => {
  if (!v) return "-";
  const d = new Date(v);
  return isNaN(d.getTime()) ? String(v) : d.toLocaleDateString("en-GB", D);
};
const fmtDateTime = (v) => {
  if (!v) return "-";
  const d = new Date(v);
  return isNaN(d.getTime()) ? String(v)
    : `${d.toLocaleDateString("en-GB", D)} ${d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false })}`;
};
const nice = (v) => (v ? String(v).replace(/_/g, " ").toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) : "-");
const dash = (v) => (v === null || v === undefined || v === "" ? "-" : v);
const safeName = (s) => String(s || "report").trim().replace(/[\\/:*?"<>|\s]+/g, "_").replace(/^_+|_+$/g, "") || "report";
const stamp = () => localDateKey(new Date());

function generatedBy() {
  const u = auth.getUser();
  return u ? `${u.fullName} (${String(u.role).replace("_", " ")})` : "MediTrack user";
}
function footer(label) {
  return `MediTrack | ${label} | Confidential medical record | Generated ${fmtDateTime(new Date())} by ${generatedBy()}`;
}

/** Builds (but does not save) the full single-patient report. */
export function buildPatientReport(data) {
  const { patient: p, visits = [], vitals = [], appointments = [], prescriptions = [], tests = [], documents = [] } = data;
  const doc = new PdfDoc({ title: `Patient report - ${p.patientCode}`, footerText: footer(p.patientCode) });

  doc.banner(p.fullName, `${p.patientCode}  |  ${p.age ?? "-"} yrs  |  ${nice(p.gender)}  |  Blood group ${dash(p.bloodGroup)}`);

  doc.section("Patient details");
  doc.keyValues([
    ["Date of birth", fmtDate(p.dateOfBirth)], ["Status", nice(p.status)],
    ["Phone", p.phone], ["Email", p.email],
    ["Address", p.address], ["Registered", fmtDate(p.createdAt)],
    ["Emergency contact", p.emergencyContactName], ["Emergency phone", p.emergencyContactPhone],
    ["Assigned doctor", p.assignedDoctorName ? `Dr. ${p.assignedDoctorName}` : "Unassigned"],
    ["Assigned nurse", p.assignedNurseName ? `Nurse ${p.assignedNurseName}` : "Unassigned"],
  ]);

  doc.section("Allergies");
  doc.table(["Allergy", "Severity", "Reaction", "Notes"],
    (p.allergies || []).map((a) => [a.allergyName, nice(a.severity), a.reaction, a.notes]), [0.28, 0.14, 0.28, 0.3]);

  doc.section("Medical history");
  doc.table(["Type", "Name", "Date", "Status", "Notes"],
    (p.conditions || []).map((c) => [nice(c.recordType), c.conditionName, fmtDate(c.diagnosedDate), nice(c.status), c.notes]),
    [0.15, 0.27, 0.14, 0.13, 0.31]);

  doc.section("Vitals");
  doc.table(["Recorded", "Temp C", "Pulse", "BP", "Resp", "SpO2", "Weight kg", "By"],
    vitals.map((v) => [fmtDateTime(v.recordedAt), v.temperatureC, v.pulseBpm,
      v.systolicBp != null && v.diastolicBp != null ? `${v.systolicBp}/${v.diastolicBp}` : "-",
      v.respiratoryRate, v.spo2 != null ? `${v.spo2}%` : "-", v.weightKg, v.recordedByName]),
    [0.2, 0.09, 0.08, 0.1, 0.08, 0.08, 0.11, 0.26]);

  doc.section("Appointments");
  doc.table(["When", "Doctor", "Reason", "Status"],
    appointments.map((a) => [fmtDateTime(a.scheduledAt), a.doctorName ? `Dr. ${a.doctorName}` : "-", a.reason, nice(a.status)]),
    [0.22, 0.24, 0.38, 0.16]);

  doc.section("Visits and diagnoses");
  if (visits.length === 0) doc.table(["Visit"], [], [1]);
  visits.forEach((v) => {
    doc.paragraph(`${fmtDateTime(v.visitDate)}  -  ${nice(v.visitType)}  -  ${nice(v.status)}  -  Dr. ${dash(v.doctorName)}`, { bold: true, size: 10 });
    doc.keyValues([
      ["Chief complaint", v.chiefComplaint], ["Symptoms", v.symptoms],
      ["Doctor's notes", v.doctorNotes], ["Follow-up", fmtDate(v.followUpDate)],
    ]);
    if ((v.diagnoses || []).length) {
      doc.table(["Diagnosis", "Severity", "Date", "Details"],
        v.diagnoses.map((d) => [d.diagnosisName, nice(d.severity), fmtDate(d.diagnosedDate), [d.description, d.notes].filter(Boolean).join(" - ")]),
        [0.28, 0.13, 0.14, 0.45]);
    }
  });

  doc.section("Prescriptions");
  if (prescriptions.length === 0) doc.table(["Medicine"], [], [1]);
  prescriptions.forEach((rx) => {
    doc.paragraph(`${fmtDateTime(rx.prescriptionDate)}  -  Dr. ${dash(rx.doctorName)}${rx.instructions ? "  -  " + rx.instructions : ""}`, { bold: true, size: 10 });
    doc.table(["Medicine", "Dosage", "Frequency", "Duration", "Route", "Instructions"],
      (rx.items || []).map((i) => [i.medicineName, i.dosage, i.frequency, i.duration, i.route, i.instructions]),
      [0.24, 0.12, 0.14, 0.12, 0.1, 0.28]);
  });

  doc.section("Medical tests and results");
  doc.table(["Date", "Test", "Result", "Normal range", "Status", "Remarks / reports"],
    tests.map((t) => [fmtDate(t.testDate), t.testName, t.result, t.normalRange, nice(t.status),
      [t.remarks, (t.reports || []).length ? `Reports: ${t.reports.map((r) => r.fileName).join(", ")}` : ""].filter(Boolean).join(" | ")]),
    [0.13, 0.2, 0.17, 0.14, 0.11, 0.25]);

  doc.section("Attached documents");
  doc.table(["Uploaded", "Document", "Type", "File", "Uploaded by"],
    documents.map((d) => [fmtDate(d.uploadedAt), d.documentName, nice(d.documentType), d.fileName, d.uploadedByName]),
    [0.15, 0.27, 0.16, 0.24, 0.18]);

  return doc;
}

/** Loads everything for one patient and downloads the PDF. Sections a role can't read are left empty. */
export async function exportPatientReportPdf(patientId) {
  const [patient, visits] = await Promise.all([api.getPatient(patientId), api.getVisitsForPatient(patientId).catch(() => [])]);
  const [vitals, appointments, prescriptions, tests, documents] = await Promise.all([
    api.getVitalsForPatient(patientId).catch(() => []),
    api.getAppointmentsForPatient(patientId).catch(() => []),
    api.getPrescriptionsForPatient(patientId).catch(() => []),
    api.getTestsForPatient(patientId).catch(() => []),
    api.getPatientDocuments(patientId).catch(() => []),
  ]);
  const doc = buildPatientReport({ patient, visits, vitals, appointments, prescriptions, tests, documents });
  doc.save(`${safeName(patient.fullName || patient.patientCode)}_${stamp()}.pdf`); // e.g. Rahul_Sharma_2026-09-29.pdf
}

/** Builds the register of all patients (summary table). */
export function buildAllPatientsReport(patients, searchTerm = "") {
  const doc = new PdfDoc({ title: "MediTrack patient register", footerText: footer("Patient register") });
  doc.banner("Patient register", `${patients.length} patient${patients.length === 1 ? "" : "s"}${searchTerm ? `  |  filter: "${searchTerm}"` : "  |  all patients"}`);
  doc.table(["Code", "Name", "Age / Sex", "Blood", "Phone", "Doctor", "Nurse", "Registered"],
    patients.map((p) => [p.patientCode, p.fullName, `${p.age ?? "-"} / ${nice(p.gender)}`, p.bloodGroup, p.phone,
      p.assignedDoctorName ? `Dr. ${p.assignedDoctorName}` : "Unassigned", p.assignedNurseName, fmtDate(p.createdAt)]),
    [0.13, 0.2, 0.1, 0.07, 0.13, 0.16, 0.12, 0.09]);
  return doc;
}

/** Fetches every page of patients (respecting the search box) and downloads the PDF. */
export async function exportAllPatientsPdf(searchTerm = "") {
  const all = [];
  for (let page = 0; page < 200; page++) {
    const res = await api.searchPatients(searchTerm, page, 100);
    all.push(...(res.content || []));
    if (page + 1 >= (res.totalPages || 1)) break;
  }
  const now = new Date();
  buildAllPatientsReport(all, searchTerm).save(`All_Patients_${now.toLocaleString("en-US", { month: "long" })}_${now.getFullYear()}.pdf`); // e.g. All_Patients_September_2026.pdf
  return all.length;
}
