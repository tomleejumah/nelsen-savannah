# nelsen-savannah

Monorepo for the Nelsen Savannah LMS — same API for web + Android. Schools get their own wing; Nelsen keeps a digital school (`nelsen-digital`).

## Layout

| Path | Description |
|------|-------------|
| `web/` | SPA (TanStack Router / Vite) |
| `api/` | Node LMS API + Firebase auth |
| `android/` | Android app (Nisisi / Nelsen) |

Historical capability map (some early tables are superseded): [`LMS_ROADMAP.md`](./LMS_ROADMAP.md). Current implementation and acceptance checklist: [`NELSEN_LMS_ROADMAP.md`](./NELSEN_LMS_ROADMAP.md). The dated [implementation-to-README benchmark](./IMPLEMENTATION_README_BENCHMARK.md) distinguishes committed code from field-verified behavior.

---

## How the system works

### Auth

1. User signs in with **Firebase** (Google / email).
2. Client sends `Authorization: Bearer <Firebase ID token>` to the API.
3. API verifies the token → `uid` + email.
4. `GET /lms/me` returns profile, global identity **role**, memberships, `activeSchoolId`, separately verified `activeSchoolRole`, shell, and capabilities. Staff permissions within a school are determined by its active membership—not simply the global role string.

### Roles and their scopes

Persist the canonical PascalCase values below. The API's `normalizeRole()` currently accepts some case-insensitive/legacy spellings (`admin`, `school_admin`, etc.), while Android UI role checks use the canonical strings. **Don't rely on capitalization as an authorization control.**

| Role | Who | Web shell | Scope |
|------|-----|-----------|--------|
| `Mentee` | Student (default for new users) | `/learning` | Own enrollments, coursework, certificates |
| `Mentor` | Teacher / tutor | `/teach` | Assign & mark work, mentee progress |
| `SchoolAdmin` | School principal / ops lead | `/school` | **One school only** — roster, mentors, mentees, school catalog, branding, dashboard |
| `SuperAdmin` | Nelsen platform owners | `/admin` | **All schools** — create schools, appoint school admins, promote SuperAdmins, global CMS |
| `Admin` | **Legacy alias only** | `/admin` | Same powers as `SuperAdmin` — **not** the school principal |

**Important:** `Admin` ≠ principal. Principal = `SchoolAdmin`. `Admin` = old name for SuperAdmin.

Hierarchy: `SuperAdmin` → `SchoolAdmin` → `Mentor` → `Mentee`.

Higher shells can open lower ones (e.g. SuperAdmin can use `/school` and `/teach`).

### Where roles are stored (global identity and per-school membership)

| Store | Path / table | Used by |
|-------|----------------|---------|
| Firebase RTDB | `roles/{uid}` → `"SchoolAdmin"` | Android + legacy |
| LMS DB | `roles` (`uid`, `role`) | Global legacy identity and compatibility gates |
| LMS DB | `school_memberships` (`uid`, `school_id`, `role`, `status`) | Authoritative school-specific Mentor / SchoolAdmin permissions; only `active` members qualify |
| LMS DB | `users_mirror.active_school_id` | Selected school; checked alongside active membership |
| Android | UID-specific verified role snapshot from `/lms/me` | Displays staff UI only after verifying both Firebase UID and active-school scope |

Global role records are mirrored between the LMS DB and RTDB for compatibility. **Current legacy behavior:** `GET /lms/me` can reconcile a differing RTDB global role into the DB. That does **not** grant an active membership role in every school. School staff scope is verified independently using the selected school's active membership.

**Do not edit `roles/{uid}` manually to grant production access.** Use authorized administrative role and membership APIs/UI and verify both the global and selected-school roles afterward. Treat legacy RTDB reconciliation and its Firebase security rules as a production security review item.

Appoint SuperAdmin by email from `/admin` (target must have signed in with Firebase at least once).

### Schools / tenancy

- Default Nelsen tenant: `nelsen-digital`.
- Each user may belong to multiple schools. `activeSchoolId` selects the one whose membership grants apply; an account may be Mentor at School A and Mentee at School B.
- **SchoolAdmin** can only manage members and catalog for **their** school.
- **SuperAdmin** is not school-locked; they create partner schools and appoint principals (`SchoolAdmin`).
- Adding people: school admin invites **by email** (mentor or mentee). No Firebase UID is needed at invite time. Accepted active memberships have their **own school role**; don't infer staff rights at other schools from a global Mentor title.

### Learning product flow

```
Catalog (tracks) → enroll → modules → lessons
Mentor creates assignment (optional lessonId + model answer)
Student opens assignment in Coursework → answers → submits
Mentor marks from Teach → certificates when track progress qualifies
```

| Shell | Route | Main jobs |
|-------|-------|-----------|
| Student | `/learning` | Catalog, track/lesson, coursework, certificates |
| Mentor | `/teach` | Submission queue, assign work, mentees |
| School | `/school` | People, roster CSV, school CMS, dashboard, payments stub |
| Platform | `/admin` | Schools list, create school, appoint SchoolAdmin / SuperAdmin, global CMS |

Profile: `/profile` (from learning Account / capabilities — not primary nav).

### Assignments & submissions

- Mentors/SchoolAdmins/SuperAdmins can `POST /lms/assignments` with `title`, `prompt`, optional `modelAnswer`, `trackId`, `lessonId`, `assigneeUid`.
- Model answers are **mentor-only** (stripped from student inbox).
- Students submit via `POST /lms/submissions` with `assignmentId` (+ optional `lessonId`); Coursework UI opens the assignment in place (does not bounce to the track page).

---

## Live learning (implemented; field QA outstanding)

- School channels use YouTube OAuth and RTMPS for broadcasts. Android host provides camera, microphone, screen-share controls and bounded socket reconnects. Android/web viewers use the embedded player when a public watch URL is available.
- Audience rules are enforced by the API: `platform` only for SuperAdmin, `school` only for active members of the active school, and `course` for authorized enrolled mentees/assigned mentors or school administrators. A host can see their own session; missing legacy audience scope is **not public**.
- Event discovery: `GET /lms/events/public`; create: `POST /lms/events`; host state: `PATCH /lms/events/:eventId/live-status`.
- Authenticated playback metadata, viewer counts and the latest chat: `GET /lms/events/:eventId/live-state`. Older chat: `GET /lms/events/:eventId/chat?before=<cursor>&limit=50`. Attendance: `POST|GET /lms/events/:eventId/attendance`.
- Android and API builds passing CI is **not** proof of uninterrupted broadcasts. The remaining acceptance test is two real devices, with network handover, PiP, a real channel, FCM delivery and session ending/replay.

## Live URLs

| Surface | URL |
|---------|-----|
| Web | https://nelsen-savannah.co.ke/ |
| API | https://api.nelsen-savannah.co.ke/ |

CI: `.github/workflows/web.yml` (build + rsync deploy), `.github/workflows/api.yml` (API deploy).

---

## Android implementation notes

- Rounded glass bottom navigation is **already fixed**: outer clipped `BlurView` and inner transparent `CardView` in default and `sw600dp` layouts. Only unrelated release device/inset QA remains.
- **Edit Profile already exists** in `EditProfileActivity`: names, bio, profile-photo upload, canonical authenticated LMS save and local profile cache update. Cross-device/device QA remains.
- These are implementation-complete items, not new feature requirements. See `IMPLEMENTATION_README_BENCHMARK.md` for the difference between code-complete and field-verified.

## Quick local pointers

- Web: see `web/README.md` — set `VITE_LMS_API_BASE` (or project equivalent) to the API base.
- API: Firebase Admin credentials + DB engine (SQLite/Postgres) via env; LMS schema/migrations in `api/src/db/lmsDb.js`.
- Android: global legacy identity is separate from UID-verified `activeSchoolRole` fetched from the LMS API. Staff controls fail closed while that role is unverified; see `android/README.md`.

When unsure about a capability, check `LMS_ROADMAP.md` and `api/src/constants/lmsRoles.js`.
