# Nelsen LMS — agent handoff prompt

Copy everything below the line into another agent.

---

## Mission

Build the **Nelsen LMS** by extending the existing Express API (no new microservice). Android app + website both consume the same `/lms/*` endpoints. Commit after every milestone.

**Start at M0.** Do not skip ahead.

## Repos & hosts

| Piece | Path |
|--------|------|
| API (extend this) | `/home/tommlyjumah/web101/nisisi-africa-webhook` |
| Deployed on | `server-remote` → `/home/server/WebHooks/Nisisi-Africa/` · PM2 `nisisi-africa` · `/health` OK |
| Website | `/home/tommlyjumah/web101/nelsen-savanna` |
| Android | `/home/tommlyjumah/StudioProjects/NisisiAfrica` |
| Roadmap source | `nelsen-savanna/src/data/lms-roadmap.js` |
| Full request/response DTOs | `nelsen-savanna/src/data/lms-api-contract.js` |
| Track/module seed data | `LMS_TRACKS` / `LMS_MODULES` in `lms-roadmap.js` |

Existing API mounts (keep): `/notifications`, `/chat`, `/didit`.

Auth already works: `src/middleware/auth.js` → `admin.auth().verifyIdToken` on `Authorization: Bearer <Firebase ID token>`.  
Android: `GoogleAuthHelper.kt` → FirebaseAuth → same ID token.  
Website: add Google login with the **same Firebase project** (`firebase-service-account.json` already on API).

Roles (mirror Android RTDB `roles/{uid}`): `Mentee` | `Mentor` | `Admin`.

## Architecture rules

1. **One API** — add `/lms` router inside `nisisi-africa-webhook` (`app.js`).
2. **Dual store** — primary DB on server + Firebase RTDB mirror.
   - Write: primary first, then mirror metadata to RTDB.
   - Read: prefer primary; on failure fall back to RTDB; set `source: "postgres"|"sqlite"|"rtdb"` on responses.
   - Prefer **Postgres** if `DATABASE_URL` is available; if Postgres auth is blocked on the VPS, use **SQLite file** at `data/lms.sqlite` on the server as primary for M0 (still dual-write RTDB). Document which one you used.
3. **Uploads on our server** (user decision) — store files under e.g. `uploads/` on the API host; serve via `/uploads` or signed paths. Metadata only in DB + RTDB. Do **not** put video bytes in RTDB or Firebase Storage as CDN.
4. **Progress math** (server-side) — from `lms-roadmap.js`: open 10% + content 40% + quiz 20% + assignment 30%; missing quiz/assignment weight rolls into content. Pass threshold **80%**.
5. **Android UI compat** — `GET /lms/tracks` must include `CourseItem` fields: `courseId`, `tutorId`, `courseImageUrl`, `tutorAvatarUrl`, `tutorName`, `courseTitle`, `duration`, `lessons`, `courseLink`, `isLiked`, plus LMS extras (`trackId`, `does`, `trackPercent`, `enrolled`, `programSlug`).
6. **Commit after every milestone** with a clear message. Do not force-push.

## Milestones

### M0 — API ground + dual DB + upload dir + `/lms/me` (15%)
- `/lms` router wired in `app.js`
- DB schema: users_mirror, roles, tracks, modules, lessons, enrollments, progress, submissions, certificates, media_assets
- Dual-write helper (DB → RTDB `lms/*` + `roles/{uid}`)
- `GET /lms/me` → uid, email, displayName, userRole, capabilities
- Create `uploads/` + `data/` on server; env: `UPLOAD_DIR`, `PUBLIC_BASE_URL`, optional `DATABASE_URL`
- Site: Google login page that calls `/lms/me` with Bearer token
- Accept: Android + web same token works on `/lms/me`; failover read works

### M1 — Catalog API (15%)
- `GET /lms/tracks`, `/lms/tracks/:id`, `/lms/modules/:id`, `/lms/lessons/:id`
- Seed from `LMS_TRACKS` / `LMS_MODULES`
- Wire site `/learning` + Android courses list to API
- Accept: identical catalog JSON both clients

### M2 — Enroll + progress % (20%)
- `POST/GET/DELETE /lms/enrollments…`, `PATCH/GET /lms/progress…`
- Server computes lesson/module/track %
- Resume Android ↔ web
- Accept: start on web, continue on Android same %

### M3 — Server uploads + lesson media (15%)
- `POST /lms/media/upload` (multipart to **our server** `uploads/`) — replace/adjust old “presigned Cloudflare” plan
- `GET /lms/media/:id` status; lesson GET returns playable URL for enrolled users
- Optional later: ffmpeg HLS on server or keep progressive MP4 for v1
- Players: web hls.js or `<video>`; Android ExoPlayer
- Accept: file lands on VPS disk; metadata in DB+RTDB; only enrolled get URL

### M4 — Assignments + mentor marking (15%)
- submissions CRUD + mentor queue + mark → updates assignmentPct / track %
- Accept: mark on Android updates web %

### M5 — Admin CMS, quizzes, certificates (20%)
- `/lms/admin/*` CRUD, role promote, stats
- Quizzes + certs when track % ≥ 80 and required assignments passed

## Endpoint checklist (implement in order)

```
GET    /lms/me
GET    /lms/tracks
GET    /lms/tracks/:trackId
GET    /lms/modules/:moduleId
GET    /lms/lessons/:lessonId
POST   /lms/enrollments
GET    /lms/enrollments/me
DELETE /lms/enrollments/:trackId
PATCH  /lms/progress/:lessonId
GET    /lms/progress/me
POST   /lms/submissions
GET    /lms/submissions/me
GET    /lms/submissions/queue
PATCH  /lms/submissions/:id/mark
GET    /lms/certificates/me
POST   /lms/tracks/:trackId/like
POST   /lms/media/upload          ← server disk (multipart)
GET    /lms/media/:mediaId
POST   /lms/admin/tracks
PUT    /lms/admin/tracks/:trackId
POST   /lms/admin/modules
POST   /lms/admin/lessons
PATCH  /lms/admin/users/:uid/role
GET    /lms/admin/stats
GET    /lms/admin/mentees/:mentorId/progress
```

Full JSON shapes: read `nelsen-savanna/src/data/lms-api-contract.js`.

## Related site context (already done)

- Events aligned with Android `Event` (`date` ms, `startTime`, `endTime`, `location`, `mode`) — `nelsen-savanna/src/data/events.ts`
- `/blogs` renamed to `/media`
- Learning page reads `LMS_TRACKS` from roadmap (switch to API in M1)
- Programs: Sela, Trailblazers, Scripture Safari, Go for it Codelab

## SSH / deploy

- Host alias: `server-remote` (user `server`)
- Deploy: GitHub Action on webhook `master` → rsync to `/home/server/WebHooks/Nisisi-Africa/` → `pm2 restart nisisi-africa`
- Do not commit secrets (`.env`, `firebase-service-account.json`)

## First command for the agent

1. Open `/home/tommlyjumah/web101/nisisi-africa-webhook`
2. Implement **M0 only**
3. Commit: `feat(lms): M0 ground — /lms router, DB schema, /lms/me, server uploads dir`
4. Stop and report acceptance checklist before M1
