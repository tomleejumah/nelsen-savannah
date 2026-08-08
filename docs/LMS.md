# Nelsen LMS

## Primary store

- Prefer **Postgres** when `DATABASE_URL` is set.
- If Postgres is missing or auth fails (common on the VPS), the API uses **SQLite** at `data/lms.sqlite`.
- Startup logs: `[lms-db] primary=sqlite|postgres`.
- Dual-write to Firebase RTDB still runs.

## Env

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | Optional Postgres connection string |
| `UPLOAD_DIR` | Disk root for lesson media (default `./uploads`) |
| `LMS_DATA_DIR` | SQLite + data dir (default `./data`) |
| `PUBLIC_BASE_URL` | Public origin for play URLs |
| `MEDIA_SIGNING_SECRET` | HMAC for enrolled playback tokens |

## Shipped endpoints (M0–M5)

See route table in `src/routes/lms.js`.

## Deferred TODOs

### M6 — Events API (not built)

Site event cards (e.g. Trailblazers Live Practice Lab: seats left, capacity %, Reserve a seat, mode, fee, location).

Planned (do not implement until scheduled):

- `GET /lms/events` — list (program tag, mode, date ms, start/end, location, fee, seatsLeft, capacityPct)
- `GET /lms/events/:eventId`
- `POST /lms/events/:eventId/reserve` — auth; decrement seats; dual-write
- Align with Android `Event` + site `events.ts`

### M7 — Hardening / parity (not built)

- Strip public `/uploads` or gate all media via signed `/play` only
- Postgres on VPS + migration from SQLite
- Deploy + smoke on `api.tommlyjumah.dev`
- Quiz question bank CRUD (beyond score via progress)
- Certificate PDF generation / verify page
