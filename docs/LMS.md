# Nelsen LMS (M0)

## Primary store

- Prefer **Postgres** when `DATABASE_URL` is set.
- If Postgres is missing or auth fails (common on the VPS), the API uses **SQLite** at `data/lms.sqlite`.
- Startup logs: `[lms-db] primary=sqlite|postgres`.
- M0 local/default: **SQLite**. Dual-write to Firebase RTDB still runs.

## Env

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | Optional Postgres connection string |
| `UPLOAD_DIR` | Disk root for lesson media (default `./uploads`) |
| `LMS_DATA_DIR` | SQLite + data dir (default `./data`) |
| `PUBLIC_BASE_URL` | Public origin for `/uploads/...` URLs |

## Endpoints (M0)

- `GET /lms/me` — Bearer Firebase ID token → profile + role + capabilities
- `GET /lms/health` — primary + RTDB health (no auth)
- `GET /uploads/*` — static files from `UPLOAD_DIR`
