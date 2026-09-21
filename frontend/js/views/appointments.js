// js/views/appointments.js
import { api, auth } from "../api.js";
import { el, toast, badge, statusBadgeKind, formatDateTime } from "../ui.js";
import { navigate } from "../router.js";

export async function renderAppointments(root, query = {}) {
  root.innerHTML = "";
  const user = auth.getUser();
  const canBook = user.role === "ADMIN" || user.role === "DOCTOR";
  let showForm = (!!query.patient || query.new === "1") && canBook;
  let filter = "UPCOMING";

  const subtitle = {
    ADMIN: "Every appointment in the system.",
    DOCTOR: "Your schedule.",
    NURSE: "Appointments for patients assigned to you (read-only).",
  }[user.role];

  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["Appointments"]),
      el("p", { class: "page-subtitle" }, [subtitle]),
    ]),
    canBook
      ? el("button", { class: "btn btn-cta", onclick: () => { showForm = !showForm; drawForm(); } },
          [showForm ? "Close form" : "+ Book appointment"])
      : null,
  ]));

  const formBox = el("div");
  root.appendChild(formBox);

  const filterSelect = el("select", { onchange: (e) => { filter = e.target.value; drawList(); } }, [
    el("option", { value: "UPCOMING" }, ["Upcoming (scheduled)"]),
    el("option", { value: "PAST" }, ["Past / closed"]),
    el("option", { value: "ALL" }, ["All"]),
  ]);
  root.appendChild(el("div", { class: "card" }, [
    el("div", { class: "field", style: "margin-bottom:0;max-width:260px;" }, [el("label", {}, ["Show"]), filterSelect]),
  ]));

  const listCard = el("div", { class: "card" });
  root.appendChild(listCard);

  let appointments = [];

  async function load() {
    listCard.innerHTML = "";
    listCard.appendChild(el("p", { class: "empty-state" }, ["Loading…"]));
    try {
      appointments = await api.getAppointments();
      drawList();
    } catch (err) {
      listCard.innerHTML = "";
      listCard.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load appointments"]));
    }
  }

  function drawList() {
    listCard.innerHTML = "";
    const now = Date.now();
    let rows = appointments.slice();
    if (filter === "UPCOMING") rows = rows.filter((a) => a.status === "SCHEDULED" && new Date(a.scheduledAt).getTime() >= now - 3600_000);
    else if (filter === "PAST") rows = rows.filter((a) => a.status !== "SCHEDULED" || new Date(a.scheduledAt).getTime() < now - 3600_000).reverse();

    if (rows.length === 0) {
      listCard.appendChild(el("p", { class: "empty-state" }, ["No appointments to show."]));
      return;
    }

    listCard.appendChild(el("div", { class: "table-wrap" }, [el("table", {}, [
      el("thead", {}, [el("tr", {}, [
        el("th", {}, ["When"]), el("th", {}, ["Patient"]), el("th", {}, ["Doctor"]),
        el("th", {}, ["Reason"]), el("th", {}, ["Status"]), canBook ? el("th", {}, ["Actions"]) : null,
      ])]),
      el("tbody", {}, rows.map((a) => el("tr", {}, [
        el("td", {}, [formatDateTime(a.scheduledAt)]),
        el("td", {}, [el("a", { href: `#/patients/${a.patientId}` }, [`${a.patientName}`]), el("div", { class: "muted-text" }, [a.patientCode])]),
        el("td", {}, [`Dr. ${a.doctorName}`]),
        el("td", {}, [a.reason || "—"]),
        el("td", {}, [badge(a.status, statusBadgeKind(a.status))]),
        canBook
          ? el("td", { class: "flex-gap" }, a.status === "SCHEDULED" ? [
              el("button", { class: "btn btn-secondary btn-small", onclick: () => setStatus(a, "COMPLETED") }, ["Complete"]),
              el("button", { class: "btn btn-danger btn-small", onclick: () => setStatus(a, "CANCELLED") }, ["Cancel"]),
            ] : [])
          : null,
      ]))),
    ])]));
  }

  async function setStatus(a, status) {
    try {
      await api.updateAppointmentStatus(a.appointmentId, status);
      toast(`Appointment ${status.toLowerCase()}`, "success");
      await load();
    } catch (err) { toast(err.message || "Update failed", "error"); }
  }

  async function drawForm() {
    formBox.innerHTML = "";
    if (!showForm) return;

    const card = el("div", { class: "card" }, [el("p", { class: "empty-state" }, ["Loading form…"])]);
    formBox.appendChild(card);

    let patients = [], doctors = [];
    try {
      const page = await api.searchPatients("", 0, 100);
      patients = page.content || [];
      if (user.role === "ADMIN") doctors = await api.getDoctors();
    } catch (err) {
      card.innerHTML = "";
      card.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load form data"]));
      return;
    }

    const patientSelect = el("select", {}, [
      el("option", { value: "" }, ["Select patient…"]),
      ...patients.map((p) => el("option", { value: p.patientId }, [`${p.fullName} (${p.patientCode})`])),
    ]);
    if (query.patient) patientSelect.value = String(query.patient);

    const doctorSelect = el("select", {}, [
      el("option", { value: "" }, ["Select doctor…"]),
      ...doctors.map((d) => el("option", { value: d.doctorId }, [`Dr. ${d.fullName} (${d.specialization || "General"})`])),
    ]);
    // Convenience for admins: preselect the patient's assigned doctor.
    patientSelect.addEventListener("change", () => {
      const p = patients.find((x) => String(x.patientId) === patientSelect.value);
      if (p && p.assignedDoctorId && user.role === "ADMIN") doctorSelect.value = String(p.assignedDoctorId);
    });

    const when = el("input", { type: "datetime-local" });
    const reason = el("input", { type: "text", placeholder: "e.g. Follow-up for blood pressure" });
    const errorSlot = el("div");
    const submit = el("button", { class: "btn btn-cta", type: "submit" }, ["Book appointment"]);

    card.innerHTML = "";
    card.appendChild(el("h3", { class: "card-title" }, ["Book an appointment"]));
    card.appendChild(el("p", { class: "card-subtitle" }, [
      user.role === "DOCTOR" ? "Only patients assigned to you are listed. The appointment is booked with you." : "Choose a patient and a doctor.",
    ]));
    card.appendChild(el("form", {
      onsubmit: async (e) => {
        e.preventDefault();
        errorSlot.innerHTML = "";
        if (!patientSelect.value || !when.value) { errorSlot.appendChild(el("div", { class: "form-error" }, ["Patient and date/time are required"])); return; }
        if (user.role === "ADMIN" && !doctorSelect.value) { errorSlot.appendChild(el("div", { class: "form-error" }, ["Please select a doctor"])); return; }
        submit.disabled = true;
        try {
          await api.createAppointment({
            patientId: Number(patientSelect.value),
            doctorId: doctorSelect.value ? Number(doctorSelect.value) : null,
            scheduledAt: when.value,
            reason: reason.value.trim() || null,
          });
          toast("Appointment booked", "success");
          showForm = false;
          if (query.patient) { navigate(`/patients/${query.patient}`); return; }
          drawForm();
          await load();
        } catch (err) {
          errorSlot.appendChild(el("div", { class: "form-error" }, [err.details && err.details.length ? err.details.join(" · ") : (err.message || "Failed to book")]));
          submit.disabled = false;
        }
      },
    }, [
      errorSlot,
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["Patient"]), patientSelect]),
        user.role === "ADMIN" ? el("div", { class: "field" }, [el("label", {}, ["Doctor"]), doctorSelect]) : el("div"),
      ]),
      el("div", { class: "field-row" }, [
        el("div", { class: "field" }, [el("label", {}, ["Date & time"]), when]),
        el("div", { class: "field" }, [el("label", {}, ["Reason"]), reason]),
      ]),
      submit,
    ]));
  }

  await drawForm();
  await load();
}
