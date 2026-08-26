# nelsen-savannah

Monorepo for the Nelsen Savannah LMS — same API for web + Android. Schools get their own wing; Nelsen keeps a digital school (`nelsen-digital`).

## Layout

| Path | Description |
|------|-------------|
| `web/` | SPA (TanStack Router / Vite) |
| `api/` | Node LMS API + Firebase auth |
| `android/` | Android app (Nisisi / Nelsen) |

Product planning / capability map: [`LMS_ROADMAP.md`](./LMS_ROADMAP.md).

---

## How the system works

### Auth

1. User signs in with **Firebase** (Google / email).
2. Client sends `Authorization: Bearer <Firebase ID token>` to the API.
3. API verifies the token → `uid` + email.
4. `GET /lms/me` returns profile, **role**, school, shell, and capabilities.

### Roles (exact PascalCase — case-sensitive)

Stored as a **string** on the user. Wrong casing (`admin`, `ADMIN`, `schooladmin`) is treated as **Mentee**.

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

### Where the role is stored (two copies, same string)

| Store | Path / table | Used by |
|-------|----------------|---------|
| Firebase RTDB | `roles/{uid}` → `"SchoolAdmin"` | Android + legacy |
| LMS DB | `roles` (`uid`, `role`) | API gates (`requireRoles`, most services) |

They are meant to stay in sync:

- Appoint / set-role APIs write **DB first**, then **mirror** to RTDB (`roles/{uid}`).
- On `GET /lms/me`, if RTDB has a role and it **differs** from the DB, **RTDB wins** and the DB is updated.

**To test a role manually:** set Firebase RTDB `roles/{yourUid}` to exactly `SuperAdmin` or `SchoolAdmin`, then reload the web app (or call `/lms/me`) so the DB catches up.

Appoint SuperAdmin by email from `/admin` (target must have signed in with Firebase at least once).

### Schools / tenancy

- Default Nelsen tenant: `nelsen-digital`.
- Each user has a `schoolId` (and optionally memberships / `activeSchoolId`).
- **SchoolAdmin** can only manage members and catalog for **their** school.
- **SuperAdmin** is not school-locked; they create partner schools and appoint principals (`SchoolAdmin`).
- Adding people: school admin invites **by email** (mentor or mentee). No Firebase uid needed. When that person signs in with the same email, membership activates; mentors get role `Mentor` automatically.

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

## Live URLs

| Surface | URL |
|---------|-----|
| Web | https://nelsen-savannah.tommlyjumah.dev/ |
| API | https://api.nelsen-savannah.co.ke/ |

CI: `.github/workflows/web.yml` (build + rsync deploy), `.github/workflows/api.yml` (API deploy).

---

## Quick local pointers

- Web: see `web/README.md` — set `VITE_LMS_API_BASE` (or project equivalent) to the API base.
- API: Firebase Admin credentials + DB engine (SQLite/Postgres) via env; LMS schema/migrations in `api/src/db/lmsDb.js`.
- Android: role from RTDB `roles/{uid}` + prefs; shells aligned with API `/lms/me`.

When unsure about a capability, check `LMS_ROADMAP.md` and `api/src/constants/lmsRoles.js`.
