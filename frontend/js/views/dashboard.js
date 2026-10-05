// js/views/dashboard.js
import { api, auth } from "../api.js";
import { el, badge, statusBadgeKind, formatDateTime, localDateKey } from "../ui.js";

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good Morning" : h < 17 ? "Good Afternoon" : "Good Evening";
}

export async function renderDashboard(root) {
  root.innerHTML = "";
  const user = auth.getUser();
  const first = (user.fullName || user.username).split(" ")[0];

  root.appendChild(el("div", { class: "hero" }, [
    el("h1", {}, [`${greeting()}, ${first}`]),
    el("p", {}, [`Here's what's happening today · signed in as ${user.role.toLowerCase()}`]),
  ]));

  const statsRow = el("div", { class: "grid-3" });
  root.appendChild(statsRow);

  const main = el("div", { class: "grid-main" });
  root.appendChild(main);
  const chartCard = el("div", { class: "card" }, [el("p", { class: "empty-state" }, ["Loading…"])]);
  main.appendChild(chartCard);
  main.appendChild(el("div", { class: "card" }, [
    el("h3", { class: "card-title" }, ["Quick actions"]),
    el("p", { class: "card-subtitle" }, ["Common tasks for your role."]),
    el("div", { style: "display:flex;flex-direction:column;gap:10px;" }, quickActions(user.role)),
  ]));

  const upcomingCard = el("div", { class: "card" });
  root.appendChild(upcomingCard);

  const isAdmin = user.role === "ADMIN";
  const [patients, appts, doctors, nurses, pending] = await Promise.all([
    api.searchPatients("", 0, 1).catch(() => null),
    api.getAppointments().catch(() => []),
    isAdmin ? api.getDoctors().catch(() => null) : null,
    isAdmin ? api.getNurses().catch(() => null) : null,
    isAdmin ? api.getPendingUsers().catch(() => null) : null,
  ]);

  const todayKey = localDateKey(new Date());
  const scheduled = appts.filter((a) => a.status === "SCHEDULED");
  const todays = scheduled.filter((a) => localDateKey(a.scheduledAt) === todayKey);
  const upcoming = scheduled.filter((a) => new Date(a.scheduledAt).getTime() >= Date.now()).slice(0, 6);

  if (isAdmin) {
    stat(statsRow, patients ? patients.totalElements : "—", "Patients", "violet");
    stat(statsRow, doctors && nurses ? doctors.length + nurses.length : "—", "Doctors & Nurses", "blue");
    stat(statsRow, pending ? pending.length : "—", "Pending approvals", "amber");
    if (pending && pending.length > 0) {
      root.insertBefore(el("div", { class: "alert-warning" }, [
        el("strong", {}, ["Pending approvals"]),
        `${pending.length} account(s) are waiting for review. `,
        el("a", { href: "#/admin/users" }, ["Go to Users & Approvals →"]),
      ]), statsRow);
    }
  } else {
    stat(statsRow, patients ? patients.totalElements : "—", "My patients", "violet");
    stat(statsRow, todays.length, "Appointments today", "blue");
    stat(statsRow, scheduled.length, "Scheduled in total", "cyan");
  }

  // ---- 7-day appointment chart ----
  chartCard.innerHTML = "";
  chartCard.appendChild(el("h3", { class: "card-title" }, ["Appointments — next 7 days"]));
  chartCard.appendChild(el("p", { class: "card-subtitle" }, ["Scheduled appointments per day."]));
  const days = [];
  for (let i = 0; i < 7; i++) { const d = new Date(); d.setDate(d.getDate() + i); days.push(d); }
  const counts = days.map((d) => scheduled.filter((a) => localDateKey(a.scheduledAt) === localDateKey(d)).length);
  const max = Math.max(1, ...counts);
  chartCard.appendChild(el("div", { class: "bars" }, days.map((d, i) => el("div", { class: "bar-col" }, [
    el("div", { class: "bar-num" }, [String(counts[i])]),
    el("div", { class: `bar${i === 0 ? " today" : ""}`, style: `height:${Math.round((counts[i] / max) * 120) + 4}px;` }),
    el("div", { class: "bar-label" }, [i === 0 ? "Today" : d.toLocaleDateString(undefined, { weekday: "short" })]),
  ]))));

  // ---- upcoming list ----
  upcomingCard.appendChild(el("h3", { class: "card-title" }, ["Upcoming appointments"]));
  if (upcoming.length === 0) {
    upcomingCard.appendChild(el("p", { class: "card-subtitle" }, ["Nothing scheduled."]));
  } else {
    upcomingCard.appendChild(el("ul", { class: "item-list" }, upcoming.map((a) => el("li", { class: "flex-between" }, [
      el("div", {}, [
        el("a", { href: `#/patients/${a.patientId}` }, [a.patientName]),
        el("div", { class: "muted-text" }, [`${formatDateTime(a.scheduledAt)} · Dr. ${a.doctorName}${a.reason ? " · " + a.reason : ""}`]),
      ]),
      badge(a.status, statusBadgeKind(a.status)),
    ]))));
  }
}

function stat(container, value, label, dot) {
  container.appendChild(el("div", { class: "stat-card" }, [
    el("span", { class: `stat-dot ${dot}` }),
    el("div", { class: "stat-value" }, [String(value)]),
    el("div", { class: "stat-label" }, [label]),
  ]));
}

function quickActions(role) {
  const a = [el("a", { class: "btn btn-primary", href: "#/patients?new=1" }, ["+ Add patient"])];
  if (role === "ADMIN" || role === "DOCTOR") a.push(el("a", { class: "btn btn-cta", href: "#/appointments?new=1" }, ["+ Book appointment"]));
  a.push(el("a", { class: "btn btn-secondary", href: "#/patients" }, [role === "ADMIN" ? "All patients" : "My patients"]));
  a.push(el("a", { class: "btn btn-secondary", href: "#/appointments" }, ["Appointments"]));
  if (role === "ADMIN") {
    a.push(el("a", { class: "btn btn-secondary", href: "#/admin/users" }, ["Manage users"]));
    a.push(el("a", { class: "btn btn-secondary", href: "#/admin/audit-logs" }, ["Audit logs"]));
  }
  return a;
}
