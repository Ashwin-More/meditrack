// js/router.js
// Minimal hash-based router. Routes are registered as [regex, handler] pairs;
// handler receives an object of named captures.

const routes = [];

export function route(pattern, handler) {
  // pattern like "/patients/:id" -> regex with named groups
  const paramNames = [];
  const regexStr = pattern
    .replace(/\/:([^/]+)/g, (_, name) => {
      paramNames.push(name);
      return "/([^/]+)";
    });
  const regex = new RegExp(`^${regexStr}$`);
  routes.push({ regex, paramNames, handler });
}

export function navigate(path) {
  window.location.hash = `#${path}`;
}

export function startRouter(notFoundHandler) {
  const dispatch = () => {
    const hash = window.location.hash.replace(/^#/, "") || "/dashboard";
    const path = hash.split("?")[0];
    const queryStr = hash.includes("?") ? hash.split("?")[1] : "";
    const query = Object.fromEntries(new URLSearchParams(queryStr));

    for (const r of routes) {
      const match = r.regex.exec(path);
      if (match) {
        const params = {};
        r.paramNames.forEach((name, i) => (params[name] = decodeURIComponent(match[i + 1])));
        r.handler(params, query);
        highlightNav(path);
        return;
      }
    }
    notFoundHandler ? notFoundHandler() : navigate("/dashboard");
  };

  window.addEventListener("hashchange", dispatch);
  dispatch();
}

function highlightNav(path) {
  document.querySelectorAll("#main-nav a").forEach((a) => {
    const target = a.getAttribute("href").replace(/^#/, "");
    a.classList.toggle("active", path === target || (target !== "/dashboard" && path.startsWith(target)));
  });
}
