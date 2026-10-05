// js/views/directory.js
import { api } from "../api.js";
import { el, badge, statusBadgeKind } from "../ui.js";

export async function renderDirectory(root) {
  root.innerHTML = "";
  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["Doctors & Nurses"]),
      el("p", { class: "page-subtitle" }, ["Clinical staff directory."]),
    ]),
  ]));

  const grid = el("div", { class: "grid-2" });
  root.appendChild(grid);

  const doctorsCard = el("div", { class: "card" }, [el("p", { class: "empty-state" }, ["Loading…"])]);
  const nursesCard = el("div", { class: "card" }, [el("p", { class: "empty-state" }, ["Loading…"])]);
  grid.appendChild(doctorsCard);
  grid.appendChild(nursesCard);

  try {
    const doctors = await api.getDoctors();
    doctorsCard.innerHTML = "";
    doctorsCard.appendChild(el("h3", { class: "card-title" }, [`Doctors (${doctors.length})`]));
    doctorsCard.appendChild(el("ul", { class: "item-list" }, doctors.length
      ? doctors.map((d) => el("li", { class: "flex-between" }, [
          el("div", {}, [
            el("strong", {}, [`Dr. ${d.fullName}`]),
            el("div", { style: "color:var(--text-muted);font-size:12.5px;" }, [`${d.doctorCode} · ${d.specialization || "General Medicine"}`]),
          ]),
          badge(d.status, statusBadgeKind(d.status)),
        ]))
      : [el("p", { class: "empty-state" }, ["No doctors on record."])]));
  } catch (err) {
    doctorsCard.innerHTML = "";
    doctorsCard.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load doctors"]));
  }

  try {
    const nurses = await api.getNurses();
    nursesCard.innerHTML = "";
    nursesCard.appendChild(el("h3", { class: "card-title" }, [`Nurses (${nurses.length})`]));
    nursesCard.appendChild(el("ul", { class: "item-list" }, nurses.length
      ? nurses.map((n) => el("li", { class: "flex-between" }, [
          el("div", {}, [
            el("strong", {}, [n.fullName]),
            el("div", { style: "color:var(--text-muted);font-size:12.5px;" }, [`${n.nurseCode} · ${n.qualification || "—"}`]),
          ]),
          badge(n.status, statusBadgeKind(n.status)),
        ]))
      : [el("p", { class: "empty-state" }, ["No nurses on record."])]));
  } catch (err) {
    nursesCard.innerHTML = "";
    nursesCard.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load nurses"]));
  }
}
