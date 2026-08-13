# Nelsen LMS

## Primary store

- Prefer **Postgres** when `DATABASE_URL` is set.
- Else **SQLite via sql.js** (WASM) at `data/lms.sqlite` — avoids native `better-sqlite3` (segfaults on VPS Node 20).
- Startup logs: `[lms-db] primary=postgres|sqlite`.
- Dual-write to Firebase RTDB still runs.

## Env

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | Optional Postgres connection string |
| `UPLOAD_DIR` | Disk root for lesson media (default `./uploads`) |
| `LMS_DATA_DIR` | SQLite + data dir (default `./data`) |
| `PUBLIC_BASE_URL` | Public origin for play URLs |
| `MEDIA_SIGNING_SECRET` | HMAC for enrolled playback tokens |

Media storage vars are listed with placeholders in `.env.example`.

## Media storage

Bytes live in object storage, metadata lives in `media_assets`. Blobs are never
stored in the DB.

`MEDIA_STORAGE_DRIVER` selects the driver:

- `local` (default) — disk under `UPLOAD_DIR`. Presigned URLs are HMAC-signed
  and point back at `GET|PUT /lms/media/blob`.
- `r2` — S3-compatible presigned URLs via `@aws-sdk/client-s3`. Targets
  Cloudflare R2; the same code path serves AWS S3, Backblaze B2 and MinIO by
  changing `MEDIA_S3_ENDPOINT` / `MEDIA_S3_REGION`.

Object keys are tenant-first so one prefix can be granted, listed, lifecycled or
purged per school:

```
schools/{schoolId}/tracks/{trackId}/lessons/{lessonId}/{mediaId}.{ext}
schools/{schoolId}/tracks/{trackId}/modules/{moduleId}/{mediaId}.{ext}
schools/{schoolId}/tracks/{trackId}/{mediaId}.{ext}
schools/{schoolId}/branding/{mediaId}.{ext}
schools/{schoolId}/misc/{mediaId}.{ext}
```

Upload is a three-step direct-to-storage flow so large video never transits this
API:

1. `POST /lms/media/upload-url` — Mentor/SchoolAdmin/SuperAdmin only, scoped to
   the caller's school. Inserts a `pending` row, returns a presigned PUT.
2. Client `PUT`s the bytes at the returned URL.
3. `POST /lms/media/:mediaId/finalize` — HEADs the object, records the real size
   and content type, flips the row to `ready`, attaches it to its lesson.

Playback is `GET /lms/media/:mediaId/url`, which re-runs the enrollment/role
check and returns a fresh presigned GET plus `expiresAt`.

### R2 bucket setup (manual)

The bucket must have **no public access**. Direct browser uploads need a CORS
policy on the bucket allowing `PUT` from the web origin:

```json
[
  {
    "AllowedOrigins": ["https://nelsensavannah.com", "http://localhost:3000"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["content-type"],
    "ExposeHeaders": ["etag", "content-length", "content-range", "accept-ranges"],
    "MaxAgeSeconds": 3600
  }
]
```

Without `GET`/`HEAD` plus the range-related `ExposeHeaders`, `<video>` seeking
against a presigned URL fails in the browser.

### Security boundary

A presigned URL expires, which kills a link copied out of devtools. It does not
stop an authorized learner from downloading the file inside the window. Real
protection needs signed cookies over short HLS segments, or DRM.

### Orphan reaper

`startMediaReaper()` runs on boot and deletes `pending` rows past
`MEDIA_PENDING_TTL_SECONDS` (with their objects) plus stale `uploads/_tmp`
files. `POST /lms/admin/media/reap` triggers it by hand.

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

- ~~Strip public `/uploads` or gate all media via signed `/play` only~~ — done;
  set `MEDIA_PUBLIC_UPLOADS=1` to restore the old open mount
- HLS packaging + per-segment signing (stronger than a single presigned MP4 URL)
- Postgres on VPS + migration from SQLite
- Deploy + smoke on `api.tommlyjumah.dev`
- Quiz question bank CRUD (beyond score via progress)
- Certificate PDF generation / verify page
