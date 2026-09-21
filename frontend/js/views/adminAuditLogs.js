// js/views/adminAuditLogs.js
import { api } from "../api.js";
import { el, badge, formatDateTime } from "../ui.js";

const ACTION_BADGE = {
  LOGIN: "success", LOGOUT: "muted", LOGIN_FAILED: "error",
  CREATE: "neutral", UPDATE: "warning", DELETE: "error",
  APPROVE: "success", REJECT: "error", DOWNLOAD: "muted",
};

export async function renderAdminAuditLogs(root) {
  root.innerHTML = "";
  let page = 0;
  const size = 20;

  root.appendChild(el("div", { class: "page-header" }, [
    el("div", {}, [
      el("h1", { class: "page-title" }, ["Audit logs"]),
      el("p", { class: "page-subtitle" }, ["Every login, approval, and record change is logged here."]),
    ]),
  ]));

  const card = el("div", { class: "card" });
  root.appendChild(card);

  async function load() {
    card.innerHTML = "";
    card.appendChild(el("p", { class: "empty-state" }, ["Loading…"]));
    try {
      const result = await api.getAuditLogs(page, size);
      render(result);
    } catch (err) {
      card.innerHTML = "";
      card.appendChild(el("p", { class: "empty-state" }, [err.message || "Failed to load audit logs"]));
    }
  }

  function render(pageResult) {
    card.innerHTML = "";
    const items = pageResult.content || [];
    if (items.length === 0) {
      card.appendChild(el("p", { class: "empty-state" }, ["No audit entries yet."]));
      return;
    }

    card.appendChild(el("table", {}, [
      el("thead", {}, [el("tr", {}, [
        el("th", {}, ["When"]), el("th", {}, ["User"]), el("th", {}, ["Action"]),
        el("th", {}, ["Table"]), el("th", {}, ["Description"]), el("th", {}, ["IP"]),
      ])]),
      el("tbody", {}, items.map((l) => el("tr", {}, [
        el("td", {}, [formatDateTime(l.createdAt)]),
        el("td", {}, [l.username || "SYSTEM"]),
        el("td", {}, [badge(l.action || "—", ACTION_BADGE[l.action] || "muted")]),
        el("td", {}, [l.tableName || "—"]),
        el("td", {}, [l.description || "—"]),
        el("td", {}, [l.ipAddress || "—"]),
      ]))),
    ]));

    const totalPages = pageResult.totalPages || 1;
    if (totalPages > 1) {
      card.appendChild(el("div", { class: "flex-between", style: "margin-top:14px;" }, [
        el("button", {
          class: "btn btn-secondary btn-small",
          disabled: page === 0 ? "disabled" : null,
          onclick: () => { page = Math.max(0, page - 1); load(); },
        }, ["← Previous"]),
        el("span", { style: "font-size:12.5px;color:var(--text-muted);" }, [`Page ${page + 1} of ${totalPages}`]),
        el("button", {
          class: "btn btn-secondary btn-small",
          disabled: page + 1 >= totalPages ? "disabled" : null,
          onclick: () => { page = Math.min(totalPages - 1, page + 1); load(); },
        }, ["Next →"]),
      ]));
    }
  }

  await load();
}
