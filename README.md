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


## Course group chats and school announcements (feature branch; not deployed)

Development branch: `feat/course-mentor-groups` — [draft PR #88](https://github.com/tomleejumah/nelsen-savannah/pull/88). **Committed code is not proof of a passing build or working device notifications.**

- **Course groups:** One Firestore `chatRooms/course_<trackId>` per course, created/opened by an assigned mentor. Its membership comes from active school memberships, assigned mentors and eligible course enrollments; all assigned mentors are group admins. Enrollment, unenrollment, mentor reassignment and school status changes trigger synchronization. The API periodically reconciles existing rooms to recover missed events.
- **School announcements:** Separate `chatRooms/school_announcements_<schoolId>` per school. `GET /chat/schools/announcements` lists a user's active schools; `POST /chat/school/:schoolId/announcements` creates/opens the room for an authorized active school admin. Membership derives from active school memberships and is reconciled after status changes and periodically. The old global `announcements` room is retained for migration; Android no longer automatically adds users to it.
- **Notifications:** `POST /chat/message` checks sender membership against the Firestore room and fans out to other members of group/school announcement rooms; direct messages require an authorized receiver. Android sends a notification request after successful text-message submission. The API verifies a matching recent persisted Firestore message and transactionally claims it to prevent duplicate fanout; delivery retry after a partial failure is not yet implemented. School announcement notifications additionally verify active school membership against the LMS database and exclude suspended recipients; Android treats announcements as broadcasts for unread badges. Chat push taps now target MainActivity, which loads the Firestore room and checks membership before opening it. Push delivery and cold-start navigation have not been verified on devices.
- **Security boundary:** Firestore rules are externally managed; review them for school-scoped access, read/write membership and admin-only announcement publishing. API membership sync is best-effort, and the periodic reconciliation is not an immediate-revocation guarantee.
- **Android (partial):** School-announcement picker loads active schools; school admins can initialize missing rooms; non-admins have a read-only composer. Device verification is pending.\n- **Outstanding:** Web school announcement UI and publishing; revocation guarantees and retries; message unread-count and push-delivery integration tests; deep links; CI; multi-school acceptance testing. Do not merge or deploy without verification.

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

Global role records are mirrored between the LMS DB and RTDB for compatibility. **Current hardened behavior:** `GET /lms/me` uses the primary LMS `roles` table for global privilege decisions, not `roles/{uid}` from RTDB. If the primary database is unavailable, the RTDB profile fallback is unprivileged (`Mentee`) and provides no active school staff permission. Authorized role changes must flow through the admin API. Legacy RTDB role mirrors still require an explicit Firebase write-rule audit. School staff scope is verified independently using the selected school's active membership.

**Do not edit `roles/{uid}` manually to grant production access.** Use authorized administrative role and membership APIs/UI and verify both the global and selected-school roles afterward. Treat legacy RTDB reconciliation and its Firebase security rules as a production security review item.

Appoint SuperAdmin by email from `/admin` (target must have signed in with Firebase at least once).

### Assigning a role once

School administrators assign **one role in the selected school** through the membership role API. This updates the school's authoritative membership role and synchronizes the legacy global role mirror for older clients; it does not rewrite other schools' memberships. Platform SuperAdmin remains a separate platform-wide privilege and cannot be removed through school-level assignment. The API returns both `userRole` and `activeSchoolRole` for compatibility, but the user-facing school-role picker is a single action. Changing schools refreshes the selected school's role from its membership record. Invite creation and acceptance now also project the selected school's assigned role—including Mentee—into the legacy role mirror, preserving platform SuperAdmin. Suspending a membership in one school now reconciles the legacy global role from remaining active memberships instead of blindly demoting the user everywhere.

Invite-token claims now require a nonempty authenticated UID and an exact nonempty invited/account email match, a still-invited membership, and a conditional database claim. Suspended, already-accepted, concurrently claimed, or another user's invitations cannot be claimed as fresh invitations. A raced duplicate returns HTTP 409 instead of appearing accepted.

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

Live event hosts must retain active membership in the owning school to mutate school-scoped events; platform administrators retain global moderation access. This has API regression coverage; real streaming/FCM verification remains pending.

Live attendance heartbeats now cap credited viewing time to server-observed elapsed seconds and do not credit stale reconnect gaps. Full two-device streaming, FCM and provider-state acceptance still requires staging.


### Public website policies (feature branch)

The site-wide footer links to full-page Privacy Policy (`/privacy-policy`), Refund Policy (`/refund-policy`), and Shipping Policy (`/shipping-policy`) routes. They use the shared responsive policy layout and existing contact email. **Policy text is a draft requiring owner/legal review before production publication**; in particular confirm refund eligibility, physical-item fulfillment, privacy practices, and applicable law. Web build and browser navigation are not yet verified.


### Chat integration follow-up (fix/chat-integrations-p0)

- School-announcement listing now quotes the PostgreSQL `schoolId` SQL alias so the Android picker receives the expected JSON field on both PostgreSQL and SQLite.
- School announcement notification authorization accepts both `SchoolAdmin` and `school_admin` role spellings (case-insensitive).
- **Still outstanding:** Firestore rule compatibility for AI replies, delivery retries after partially failed notification fanout, immediate membership revocation, Android debug/release CI, and web policy route generation/build verification. These changes have not been verified by CI.


### November 2026 web typography and chat diagnostics

- Homepage hero typography received small improvements to heading spacing, weight, and supporting text readability; no page redesign.
- Android direct-chat creation validates the recipient and existing room membership/type before opening the room. Firestore lookup and creation failures now log which stage failed, with the underlying Firebase exception.
- School announcements are intentionally unchanged pending cross-platform design and Firestore project verification. Firestore billing status and security rules must be checked from the actual Firebase error; neither has been confirmed as the cause. CI/build verification pending.


### Resumable Android in-app updates

- The WorkManager update downloader retains `.apk.part` across network interruptions, app backgrounding, and retries instead of deleting partial progress.
- Android requests `Range: bytes=<offset>-`; the API's authenticated `/lms/app/android/download` endpoint now supports `206 Partial Content`, `Content-Range`, and `416` for invalid offsets. If the server ignores ranges, the downloader safely restarts from byte zero; a checksum mismatch discards the corrupt partial.
- The download remains managed by WorkManager; completion shows **Tap to install** and does not auto-install. Android OS background execution limits can still defer a worker; foreground-service promotion is not included in this patch.
- Requires Android and API CI plus device testing (background/foreground, interrupted at 99%, server range, hash verification). No build has been run for this change.
