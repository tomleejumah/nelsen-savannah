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

## Endpoints (M0–M1)

- `GET /lms/me` — Bearer Firebase ID token → profile + role + capabilities
- `GET /lms/health` — primary + RTDB health (no auth)
- `GET /lms/tracks` — TrackCardDto[] (CourseItem-compatible)
- `GET /lms/tracks/:trackId` — track + modules[]
- `GET /lms/modules/:moduleId` — module + lessons[]
- `GET /lms/lessons/:lessonId` — lesson detail
- `POST /lms/enrollments` — enroll `{ trackId, platform? }`
- `GET /lms/enrollments/me` — my enrollments + %
- `DELETE /lms/enrollments/:trackId` — unenroll
- `PATCH /lms/progress/:lessonId` — upsert progress → lesson/module/track %
- `GET /lms/progress/me` — resume map `byLessonId` / `byTrackId`
- `POST /lms/media/upload` — multipart `file` (+ optional `lessonId`); Mentor/Admin; disk under `UPLOAD_DIR`
- `GET /lms/media/:mediaId` — status metadata
- `GET /lms/media/:mediaId/play?uid=&token=` — signed play (enrolled only)
- Lesson `GET` returns `playbackUrl` only when enrolled
- `GET /uploads/*` — raw static (prefer signed `/play` for learners)


Catalog seed: `src/data/lmsSeed.json` (from `LMS_TRACKS` / `LMS_MODULES`) on empty DB.
