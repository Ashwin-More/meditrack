# MediTrack Backend — Spring Boot + Hibernate (JPA) + MySQL

A REST API rebuild of the MediTrack Patient Management / EHR system, matching
the original 14-table schema (roles, users, doctors, nurses, patients,
patient_allergies, patient_conditions, visits, diagnoses, prescriptions,
prescription_items, medical_tests, test_reports, audit_logs) — same
relationships, same RBAC roles (ADMIN / DOCTOR / NURSE), same approval
workflow — implemented with Hibernate as the ORM instead of SQLAlchemy.

## Tech stack

| Layer          | Technology                                   |
|----------------|-----------------------------------------------|
| Language       | Java 17                                        |
| Framework      | Spring Boot 3.3                                |
| ORM            | Hibernate via Spring Data JPA                  |
| Database       | MySQL 8                                        |
| Auth           | Spring Security + JWT (stateless, `jjwt` 0.12) |
| Build          | Maven                                          |
| Validation     | Jakarta Bean Validation                        |

## Folder structure

```
backend/
├── pom.xml
├── docker-compose.yml              # optional local MySQL container
└── src/
    ├── main/
    │   ├── java/com/meditrack/
    │   │   ├── MeditrackBackendApplication.java
    │   │   ├── config/             # SecurityConfig, CorsConfig, OpenApiConfig, DataSeeder
    │   │   ├── entity/             # 15 @Entity classes — one per table
    │   │   ├── repository/         # Spring Data JPA interfaces
    │   │   ├── service/            # interfaces AND their XxxServiceImpl.java together
    │   │   ├── controller/         # @RestController — one per resource
    │   │   ├── dto/                # request + response payloads (named XxxRequest / XxxResponse)
    │   │   ├── security/           # JWT filter, JwtService, UserPrincipal, rate limiter
    │   │   ├── exception/          # custom exceptions + @RestControllerAdvice
    │   │   ├── enums/              # RoleName, UserStatus, VisitType, ...
    │   │   └── util/               # CodeGenerator (PAT-2026-001, DOC-001...)
    │   └── resources/
    │       ├── application.yml
    │       └── db/
    │           ├── schema.sql          # apply by hand if you skip Flyway
    │           └── migration/          # V1/V2/V3 — what Flyway actually runs on boot
    └── test/java/com/meditrack/    # unit tests + context-load smoke test
```

Two folders were deliberately kept flat instead of nested one level deeper:
- **`service/`** holds both the interface (`PatientService.java`) and its
  implementation (`PatientServiceImpl.java`) side by side, instead of a
  separate `service/impl/` subfolder — same code, one less folder to open.
- **`dto/`** holds both request and response classes together, told apart
  by name (`PatientRequest.java` vs `PatientResponse.java`), instead of
  `dto/request/` and `dto/response/` subfolders.

This still mirrors a standard layered Spring Boot architecture: **Controller
→ Service → Repository → Entity**, with DTOs at the boundary so entities
never leak directly into JSON responses — it's just one level shallower to
navigate.

## Setup

### 1. Database

Option A — Docker:
```bash
docker compose up -d
```

Option B — existing MySQL server, letting **Flyway** manage the schema
(recommended — this is now the default):
```bash
mysql -u root -p -e "CREATE DATABASE meditrack CHARACTER SET utf8mb4;"
```
That's it — leave the database empty. On startup, Flyway automatically runs
the migrations under `src/main/resources/db/migration/` in order
(`V1__init_schema.sql`, `V2__seed_roles.sql`, `V3__refresh_tokens.sql`) and
Hibernate runs in `validate` mode against the result, so the schema and the
entities can never silently drift apart.

Option C — apply the schema by hand instead (if you'd rather not use
Flyway): run `../db/meditrack_mysql_schema.sql` (from the project root —
see the top-level README) directly, then set `FLYWAY_ENABLED=false` and
`JPA_DDL_AUTO=update` as environment variables before starting the app.

### 2. Configure environment variables (or edit `application.yml` directly)

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=meditrack
export DB_USER=root
export DB_PASSWORD=root
export JWT_SECRET=replace-with-a-long-random-string
```

### 3. Run

```bash
mvn spring-boot:run
```

The API is served at `http://localhost:8080/api`. On first boot,
`DataSeeder` creates the three roles and three demo accounts if the `users`
table is empty:

| Role   | Username     | Password       |
|--------|--------------|----------------|
| Admin  | `admin`      | `Password@123` |
| Doctor | `drrahul`    | `Password@123` |
| Nurse  | `nursepriya` | `Password@123` |

## Auth flow

- `POST /api/auth/login` → `{ username, password }` → returns an access
  token (`token`, 24h default) and a `refreshToken` (7 days default).
- `POST /api/auth/refresh` → `{ refreshToken }` → returns a new access token
  **and** rotates the refresh token (the old one is invalidated).
- `POST /api/auth/logout` → requires `Authorization: Bearer <token>` →
  revokes all refresh tokens for that user (server-side logout) and writes
  a `LOGOUT` audit log entry.
- `POST /api/auth/register` → self-registration for DOCTOR/NURSE, account
  starts as `PENDING` and cannot log in until an admin calls
  `POST /api/admin/users/{id}/approve`.
- `POST /api/auth/login` and `/auth/register` are rate-limited (5 attempts
  per 60 seconds per IP by default — see `app.rate-limit.login` in
  `application.yml`); exceeding it returns `429 Too Many Requests`.
- Every other endpoint requires `Authorization: Bearer <token>`.
- Role checks are enforced with `@PreAuthorize` on each controller method
  (e.g. only `DOCTOR` can write a prescription or add a diagnosis).

## Key endpoints

| Resource      | Endpoints |
|---------------|-----------|
| Auth          | `POST /auth/login`, `POST /auth/register`, `POST /auth/refresh`, `POST /auth/logout` |
| Admin         | `POST /admin/users`, `GET /admin/users`, `GET /admin/users/pending`, `POST /admin/users/{id}/approve|reject|disable|enable`, `GET /admin/audit-logs` |
| Patients      | `POST /patients`, `PUT /patients/{id}`, `GET /patients/{id}`, `GET /patients?q=`, `POST /patients/{id}/allergies`, `POST /patients/{id}/conditions` |
| Visits        | `POST /visits`, `GET /visits/{id}`, `GET /visits/patient/{id}`, `POST /visits/{id}/close`, `POST /visits/{id}/diagnoses` |
| Prescriptions | `POST /prescriptions`, `GET /prescriptions/{id}`, `GET /prescriptions/patient/{id}` (includes an `allergyWarnings` list, same idea as the Flask app's allergy banner) |
| Medical tests | `POST /medical-tests`, `PUT /medical-tests/{id}/result`, `GET /medical-tests/{id}`, `GET /medical-tests/patient/{id}` |
| Reports       | `POST /reports/tests/{testId}/upload` (multipart, PDF/JPG/PNG only), `GET /reports/{id}/download` |
| Doctors/Nurses| `GET /doctors`, `GET /nurses` |

## API docs (Swagger UI)

Once the app is running, open **http://localhost:8080/api/swagger-ui.html**.
Log in via `POST /auth/login` in the UI, copy the `token` from the response,
click **Authorize**, and paste it in (no need to type `Bearer ` first).

## Tests

```bash
mvn test
```

- `CodeGeneratorTest` — pure logic test for the `PAT-2026-001` / `DOC-001` /
  `NUR-001` code formats.
- `AuthServiceImplTest` — Mockito test covering PENDING/DISABLED/unknown-user
  rejection and the happy-path login/token/refresh-token issuance flow.
- `MedicalTestServiceImplTest` — covers the nurse report-download rule below.
- `LoginRateLimiterTest` — covers the sliding-window limiter in isolation.
- `MeditrackBackendApplicationTests` — full Spring context smoke test.

## Notes / things worth knowing

- **Audit trail**: `AuditLogService` writes a row for every login,
  login-failure, registration, approval/rejection, and user status change —
  same behaviour as the original `audit_service`.
- **Allergy check**: `PrescriptionServiceImpl` cross-checks each prescribed
  medicine name against the patient's recorded allergies and returns any
  matches as `allergyWarnings` in the response, for the frontend to render
  as a warning banner.
- **File uploads**: stored on disk under `app.upload.dir` (defaults to
  `uploads/reports`), only `.pdf/.jpg/.jpeg/.png` accepted, 10 MB max
  (`spring.servlet.multipart.max-file-size`).
- **Nurse report-download restriction**: a NURSE can only download a report
  they uploaded themselves, or one uploaded by a DOCTOR — not a report
  uploaded by another nurse. ADMIN and DOCTOR can download anything. This
  closes the gap flagged as "future work" in the original Flask README.
- **Compiling**: this project was written and reviewed in a sandbox without
  internet access, so `mvn` couldn't actually download dependencies and run
  a build here — package names, entity mappings (`mappedBy`, `@JoinColumn`),
  constructor/field ordering for the new unit tests, and braces were all
  checked by hand and by script, but please run `mvn clean install` yourself
  once before you rely on it, and let me know if anything doesn't compile.

## Still open (lower priority)

Everything from the original scope — including refresh tokens, rate
limiting, and Flyway migrations — is now implemented. What's left is
polish beyond what a submission/demo project typically needs:

- Redis-backed rate limiting instead of in-memory, if you deploy more than
  one instance behind a load balancer (the in-memory limiter is per-instance)
- Refresh-token reuse detection (currently: rotation only, no alerting if a
  revoked token is replayed)
- CI pipeline (GitHub Actions) running `mvn verify` on push

## On the color palette you sent

That palette (Deep Charcoal / Forest Teal / Soft Mint / Ivory, with the
65/20/10/5 usage ratio) is a **frontend** concern — this zip is backend-only,
so there's nothing here to apply it to yet. When you're ready to build (or
regenerate) the UI, hand me this same palette and I'll wire it in as CSS
variables with those ratios respected. If you want, I can also theme
Swagger/OpenAPI docs for this API in the meantime — just say the word.
