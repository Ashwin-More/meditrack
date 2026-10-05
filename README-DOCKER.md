# Run MediTrack with Docker

1. Edit `.env`: set `MAIL_USERNAME`, `MAIL_PASSWORD` (Gmail App Password) and `MAIL_FROM`.
   To skip real email, set `MAIL_ENABLED=false` (OTP codes then print in the backend logs).
   Same idea for SMS: `SMS_ENABLED=false` (default) prints phone OTPs to the backend logs
   instead of sending a real text — no SMS provider account is required to run the project.
2. `docker compose up --build -d`
3. Frontend: http://localhost:5500  |  API: http://localhost:8080/api  |  MySQL (host): localhost:3307
4. Logs: `docker logs -f meditrack-backend`

Demo logins (password `Password@123`): admin, drrahul, nursepriya — you can log in with
their username, email, or seeded phone number, and can also use the "One-time code" tab
on the login page for a passwordless sign-in.
Super Admin demo login: `superadmin` / `SuperAdmin@123` (use the "Super Admin Login" portal
option on the login screen).
Reset everything: `docker compose down -v`.
After editing `.env`: `docker compose up -d --force-recreate backend`.

## OTP delivery and security
OTP codes are 6 digits, generated with a secure random source, stored only as a keyed hash, single-use,
and expire after `EMAIL_VERIFY_MINUTES` (10) / `PASSWORD_RESET_MINUTES` (30) / `LOGIN_OTP_MINUTES` (5).
A code is locked after `OTP_MAX_ATTEMPTS` (5) wrong guesses, and a new code can be sent at most once per
`OTP_RESEND_COOLDOWN_SECONDS` (30). Codes are delivered ONLY by email/SMS: they are never returned by the API,
shown on screen or written to the logs, so real email must be configured (`MAIL_ENABLED=true` plus
`MAIL_USERNAME` / `MAIL_PASSWORD` - for Gmail an App Password). Seeded demo accounts only receive codes when
`SEED_EMAIL_BASE` points at a real inbox; they can always sign in with their password.
