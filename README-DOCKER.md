# Run MediTrack with Docker

1. Edit `.env`: set `MAIL_USERNAME`, `MAIL_PASSWORD` (Gmail App Password) and `MAIL_FROM`.
   To skip real email, set `MAIL_ENABLED=false` (links then print in the backend logs).
2. `docker compose up --build -d`
3. Frontend: http://localhost:5500  |  API: http://localhost:8080/api  |  MySQL (host): localhost:3307
4. Logs: `docker logs -f meditrack-backend`

Demo logins (password `Password@123`): admin, drrahul, nursepriya.
Reset everything: `docker compose down -v`.
After editing `.env`: `docker compose up -d --force-recreate backend`.
