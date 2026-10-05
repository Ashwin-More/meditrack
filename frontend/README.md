# MediTrack Frontend

A dependency-free vanilla HTML/CSS/JS single-page app that talks to the
MediTrack Spring Boot API. No build step, no npm install — open it with any
static file server (or even `file://`, for most of it) and it just runs.

Themed **"Midnight Health-Tech"**: deep navy `#0B1120` background, `#111827` sidebar,
`#172033` cards, electric violet `#7C3AED` as the brand colour, medical cyan `#22D3EE` and blue
`#3B82F6` as accents (~80% navy/neutral, ~15% violet, ~5% cyan/blue). Semantic colours are reserved
for clinical meaning: green `#22C55E` healthy/completed, amber `#F59E0B` pending, red `#EF4444`
critical, sky `#38BDF8` scheduled/info. The violet→blue→cyan gradient is used sparingly (logo mark,
main call-to-action, avatar, chart bars, login glow). All colours are CSS variables at the top of
`css/style.css`.

## Folder structure

```
meditrack-frontend/
├── index.html
├── css/
│   └── style.css        # theme variables + all component styles
└── js/
    ├── api.js            # fetch wrapper: JWT storage, auto-refresh, all endpoints
    ├── ui.js              # el() element builder, toast(), badges, date formatting
    ├── router.js          # minimal hash-based router
    ├── app.js             # entry point — wires auth gating, nav, routes
    └── views/
        ├── login.js
        ├── register.js
        ├── dashboard.js
        ├── patients.js          # search / list / create
        ├── patientDetail.js     # allergies, conditions, visit history, new visit
        ├── visitDetail.js       # diagnoses, prescriptions, medical tests, file upload/download
        ├── adminUsers.js        # approvals, create user, enable/disable
        ├── adminAuditLogs.js
        └── directory.js         # doctors & nurses list
```

## Running it

1. **Start the backend first** (see `../meditrack-backend/README.md`). By
   default it listens on `http://localhost:8080/api`.

2. **Point the frontend at your backend**, if it's not on the default —
   edit the inline `<script>` at the top of `index.html`:
   ```html
   <script>
     window.MEDITRACK_API_BASE = "http://localhost:8080/api";
   </script>
   ```

3. **Serve the frontend** with any static server (it uses ES modules, which
   most browsers block from `file://` for security — a local server avoids
   that):
   ```bash
   cd meditrack-frontend
   python3 -m http.server 5500
   # then open http://localhost:5500
   ```
   or with Node: `npx serve -l 5500 .`, or VS Code's "Live Server" extension.

4. **CORS**: the backend's default allowed origins already include
   `localhost:5500` and `localhost:8000` (common static-server ports) along
   with `3000`/`5173` (common dev-server ports). If you serve the frontend
   from a different port, add it to `CORS_ORIGINS` when starting the
   backend, e.g.:
   ```bash
   export CORS_ORIGINS=http://localhost:5500
   ```

5. Log in with one of the seeded demo accounts (shown right on the login
   screen): `admin` / `drrahul` / `nursepriya`, all with password
   `Password@123`.

## What's implemented

- **Auth**: login, self-registration (Doctor/Nurse, pending approval),
  logout (revokes refresh token server-side), silent token refresh on 401
- **Patients**: search/paginate, register new patient, view detail
  (allergies with severity, existing conditions, full visit history),
  add allergy/condition inline
- **Visits**: record a new visit from a patient's page, close a visit,
  add diagnoses (Doctor only)
- **Prescriptions**: multi-medicine form, shows the backend's allergy
  cross-check as a warning banner when a prescribed medicine matches a
  recorded allergy
- **Medical tests**: order a test, enter/update results, upload a report
  file (PDF/JPG/PNG), download reports (server enforces the nurse
  same-uploader-or-doctor-only rule — the frontend just surfaces whatever
  the API returns)
- **Admin**: pending-approval queue with approve/reject, full user list
  with enable/disable, direct user creation (auto-activated), paginated
  audit log viewer
- **Directory**: doctors & nurses list
- Role-aware navigation (the "User Approvals" / "Audit Logs" nav links only
  render for ADMIN) and role-aware action buttons throughout (e.g. only
  DOCTOR sees the "add diagnosis" / "write prescription" forms)

## Notes

- This is intentionally framework-free (no React/Vue/build tooling) so it
  runs with zero `npm install` — every `.js` file was syntax-checked with
  `node --check` in the sandbox this was built in, but **browser
  integration against your running backend hasn't been exercised** (no
  internet access here to actually boot both servers together). Please
  smoke-test the main flows (login → register patient → record visit →
  write prescription → order test → upload report → admin approve) and
  report anything that doesn't work end-to-end.
- Token storage is `localStorage` (access + refresh token). For a
  production deployment you'd typically move at least the refresh token to
  an `HttpOnly` cookie set by the backend — this SPA keeps things simple
  for a local/demo setup instead.
