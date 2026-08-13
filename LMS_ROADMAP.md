# Nelsen Savannah LMS — capability map & roadmap

**Purpose:** Pitch-ready learning platform. Same API for web + Android. Schools get their own wing; Nelsen keeps a digital school.

Repos: `nelsen-savannah-api` · `nelsen-savannah-web` · `nelsen-savannah-android`  
Roles today: `Mentee` (student) · `Mentor` · `Admin`  
Auth: Firebase ID token → `GET /lms/me`

Say **“do Ln”** or **“next”**. Status: `pending` → `in_progress` → `done`.

---

## 1. Product vision (what “LMS” means here)

| Layer | Meaning |
|-------|---------|
| **Learning** | Tracks → modules → lessons (video / read / quiz / assignment) + progress % |
| **Teaching** | Mentors/teachers see students, assign work, mark submissions |
| **Admin** | Create catalog, roles, school/org settings, stats |
| **Tenant / school wing** | Each partner school has isolated catalog, users, branding, reporting |
| **Nelsen digital school** | Our own tenant (default org) — Sela, Trailblazers, etc. |

---

## 2. What it **can** do today

### API (`nelsen-savannah-api` — M0–M5 mostly shipped)

| Area | Works |
|------|--------|
| Auth identity | `GET /lms/me` + role + capabilities |
| Catalog | Tracks / modules / lessons list+detail |
| Enroll | Create / list mine / unenroll |
| Progress | Patch lesson progress; get my progress map; server % math |
| Media | Upload (Mentor/Admin); status; signed play |
| Assignments API | Submit; my submissions; mentor queue; mark |
| Quiz submit | `POST /lms/lessons/:id/quiz` |
| Like track | `POST /lms/tracks/:id/like` |
| Certificates list | `GET /lms/certificates/me` |
| Admin CMS (API) | Create/update track, module, lesson; set role; stats; mentee progress; force seed |
| Dual store | SQLite (or Postgres) primary + RTDB mirror |

### Web (SPA on nginx)

| Screen | Works |
|--------|--------|
| Login (Google) | Calls `/lms/me` |
| `/learning` | Catalog + enroll filter + enroll CTA + % |
| `/learning/$trackId` | Modules list, enroll, open lessons when enrolled |
| `/learning/$trackId/lesson/$lessonId` | Lesson view + mark progress |

### Android

| Screen | Works |
|--------|--------|
| Courses rail / All Courses | `GET /lms/tracks` (Firebase fallback) |
| `TrackLearnActivity` | Track detail, enroll, lesson open, patch progress |

---

## 3. What it **cannot** do yet (gaps)

### Multi-tenant / schools — **missing entirely**

- No `schoolId` / org on users, tracks, enrollments
- No school admin vs Nelsen admin
- No per-school branding, domains, or isolated reporting
- No “invite school cohort” / roster import

### Role-split product UI — **weak**

| Role | Reality today |
|------|----------------|
| **Student (Mentee)** | Can browse/enroll/learn on web+Android — but no coursework inbox, no “my assignments”, thin progress dashboard |
| **Mentor** | API for queue/mark/mentees exists — **no dedicated mentor UI** on web; Android not wired to LMS mentor flows |
| **Admin** | API CMS exists — **no admin UI** on web/Android |

### Learning depth — **partial**

- Quiz: submit score only — no question-bank UI / authoring UI
- Assignments: API yes — **no student submit UI**, **no mentor mark UI** on web/Android
- Certificates: list API — no PDF / verify page / share card
- Events: `/lms/events*` → **501** (not built)
- Media hardening: public uploads gate / HLS still TODO

### Platform polish

- API still branded `nisisi-africa` on URL/PM2 (rename pending)
- Web has no mentor/admin shells
- Android Learning is course-list oriented, not a full “Learning mode” with role home

---

## 4. Endpoint vs UI matrix (what’s missing from UI)

Legend: **API** = exists · **Web** · **Android**

| Capability | Endpoint(s) | API | Web UI | Android UI |
|------------|-------------|:---:|:------:|:----------:|
| Me / role | `GET /lms/me` | ✅ | ✅ login | ✅ auth |
| Catalog tracks | `GET /lms/tracks`, `…/:id` | ✅ | ✅ | ✅ |
| Modules / lessons | `GET /lms/modules/:id`, `…/lessons/:id` | ✅ | ✅ | ✅ partial |
| Enroll | `POST/GET/DELETE …/enrollments…` | ✅ | ✅ | ✅ |
| Progress | `PATCH/GET …/progress…` | ✅ | ✅ lesson | ✅ lesson |
| Like | `POST …/tracks/:id/like` | ✅ | ❌ | ❌ |
| Media upload/play | `POST/GET …/media…` | ✅ | ❌ upload · ✅ play if URL | ❌ upload · ✅ if URL |
| Quiz submit | `POST …/lessons/:id/quiz` | ✅ | ❌ | ❌ |
| Submit assignment | `POST /lms/submissions` | ✅ | ❌ | ❌ |
| My submissions | `GET /lms/submissions/me` | ✅ | ❌ | ❌ |
| Mentor queue | `GET /lms/submissions/queue` | ✅ | ❌ | ❌ |
| Mark submission | `PATCH /lms/submissions/:id/mark` | ✅ | ❌ | ❌ |
| Certificates | `GET /lms/certificates/me` | ✅ | ❌ | ❌ |
| Admin CRUD catalog | `POST/PUT /lms/admin/tracks|modules|lessons` | ✅ | ❌ | ❌ |
| Admin set role | `PATCH /lms/admin/users/:uid/role` | ✅ | ❌ | ❌ |
| Admin stats | `GET /lms/admin/stats` | ✅ | ❌ | ❌ |
| Mentor: mentee progress | `GET /lms/admin/mentees/:mentorId/progress` | ✅ | ❌ | ❌ |
| Events | `GET/POST /lms/events…` | ❌ 501 | ❌ | Firebase events only |
| **Schools / tenants** | *(none)* | ❌ | ❌ | ❌ |
| **Assignments (assign work to students)** | *(no assign endpoint — only student-initiated submit; mark queue exists)* | ❌ | ❌ | ❌ |
| **Class / roster** | *(none)* | ❌ | ❌ | ❌ |
| **School-branded login** | *(none)* | ❌ | ❌ | ❌ |

**Biggest holes** = features a school buyer expects in a demo that we **cannot show end-to-end today** (API alone doesn’t count — needs UI):

1. **Assignments loop** — assign → student submits → mentor marks (assign API missing; submit/mark UI missing)  
2. **Mentor board** — see my students + progress (API partial; **no UI**)  
3. **School Admin ops** — register mentors, create mentees, escalate roles **inside one school** (not built)  
4. **School tenancy** — their wing vs ours (not built)  
5. **Payments** — seats/fees (not built)  

API without web/Android screens = still a hole for pitching.

---

## 5. Role experience targets (controlled UIs)

**Hierarchy (important for school partners):**

| Role | Who | Powers |
|------|-----|--------|
| **Super Admin** | Nelsen owners (platform) | All schools, create schools, billing, global catalog, promote school admins |
| **School Admin** | Partner school’s ops lead | *Inside their school only:* register mentors, create/invite mentees, escalate staff → Mentor, school catalog, roster, (later) seats/payments |
| **Mentor** | Teachers / staff | Students, assign/mark work, progress |
| **Mentee** | Students | Learn, coursework, certificates |

One login; `userRole` (+ `schoolId`) from `/lms/me` selects shell.  
School Admin ≠ Super Admin — school admin never sees another school.

### Student / Mentee (priority — build first)

1. Learning home: enrolled tracks, continue, %  
2. Track → modules → lessons (video/read/quiz/assignment)  
3. **My coursework** — due assignments, submit, status (pending/marked)  
4. Progress dashboard — track/module/lesson breakdown  
5. Certificates when earned  

### Mentor (second)

1. Mentor home — my students / mentees  
2. Student progress drill-down  
3. Assignment queue — mark / feedback  
4. **Assign** coursework to student or cohort (needs new API)  
5. Optional: upload media for lessons they own  

### School Admin (school wing ops — pitch-critical)

1. Register / invite **mentors** (staff → Mentor)  
2. Create / invite **mentees** (students)  
3. Escalate staff role (Mentee/Staff → Mentor) inside school  
4. Roster view (who’s in this school)  
5. School-scoped catalog / enroll policies (after L5)  
6. Later: seats, invoices (L6b)  

### Super Admin (Nelsen owners)

1. Create / suspend schools  
2. Appoint School Admins  
3. Global stats + cross-school support  
4. Platform billing / plans  
5. Global content library (optional share into schools)  

### Platform Admin CMS (catalog — L4; can be Super Admin + School Admin scoped)

1. Catalog CMS — tracks/modules/lessons (scoped by school when tenant exists)  
2. Users & roles (school-scoped vs global)  
3. Stats & export  
4. Seed / content publish  

**Rule:** API + **web UI** (+ Android where students/mentors need mobile) ship **in the same Ln** — no API-only milestones without a usable screen.

---

## 6. New roadmap (L-series)

### L0 — Role shells (web + Android) — API + UI together  
**Status:** `done`  
**API:** `/lms/me` returns `schoolId`, `schoolName`, `shell`, roles `SchoolAdmin`/`SuperAdmin` (legacy `Admin` → SuperAdmin).  
**Web:** `/learning` student · `/teach` mentor · `/school` school-admin · `/admin` super-admin (gated). Login CTA routes by shell.  
**Android:** `Roles.lmsShell()` + All Courses title by role.  
**Accept:** login lands in the right shell; wrong role blocked on school/super routes.

### L1 — Student coursework loop (web first, then Android) — API + UI  
**Status:** `done`  
Wire existing APIs + screens:  
- My submissions + submit UI  
- Quiz UI on lesson  
- Certificates page  
**Accept:** student completes video → quiz → assignment → sees mark on web & Android.

### L2 — Mentor board — API + UI  
**Status:** `done`  
- Students list (`/lms/admin/mentees/:mentorId/progress` or school-scoped successor)  
- Submission queue + mark UI  
- Student progress detail  
**Accept:** mentor marks → student % updates both clients.

### L3 — Assignments as “assigned work” — API + UI  
**Status:** `done`  
**API:**  
- `POST /lms/assignments` — mentor/school-admin assigns to uid|cohort|track  
- `GET /lms/assignments/me` — student inbox  
- `GET /lms/assignments/assigned` — mentor outbox  
Submissions link `assignmentId`.  
**Web/Android:** assign form + coursework inbox.  
**Accept:** assign → student sees it → submit → mark.

### L4 — School Admin + Super Admin people ops — API + UI  
**Status:** `done`  
**API (school-scoped):**  
- `POST /lms/schools/:id/mentors` — register / invite mentor  
- `POST /lms/schools/:id/mentees` — create / invite mentee  
- `PATCH /lms/schools/:id/members/:uid/role` — escalate staff → Mentor (School Admin)  
- Super Admin: `POST /lms/schools`, `PATCH /lms/schools/:id/admins`  
**Web:** School Admin console — Mentors, Mentees, Roles. Super Admin — Schools list.  
**Accept:** school admin adds teacher + students; cannot touch another school; super admin creates the school.

### L5 — Catalog CMS UI (scoped) — API already mostly there + UI  
**Status:** `done`  
Web: create track/module/lesson, upload media, stats — **scoped by school** for School Admin; global for Super Admin.  
**Accept:** school admin publishes a track without seed JSON; other schools don’t see it.

### L6 — Schools / multi-tenant wings (finish isolation)  
**Status:** `done`  
**Data:** `schools`; `schoolId` on users, tracks, enrollments, assignments.  
Roster import CSV; branding.  
**Accept:** School A ⟂ School B; Nelsen = default digital school tenant.

### L7 — School pitch pack  
**Status:** `done` (product) · **Payments:** `deferred` — provider not chosen yet  
- Dashboard: roster, completion %, at-risk  
- **Payments:** out of scope until you pick the rail (M-Pesa / card / other)  
**Accept:** school admin sees roster health; students enroll without payment gates.

### L8 — Hardening  
**Status:** `done`  
Events decision (**defer**), Postgres-ready schema, media signed-only (play requires token), package rename `nelsen-savannah-api` (public URL `/nisisi-africa` until nginx cutover), CI smoke.

---

## 7. Suggested build order for client pitch

| Week | Milestone | Demo line |
|------|-----------|-----------|
| 1 | **L0 + L1** | Students learn on web + app |
| 2 | **L2** | Teachers see students & mark work |
| 3 | **L3** | Teachers assign coursework |
| 4 | **L4** | School admin registers mentors & mentees |
| 5 | **L5** | School publishes its own content |
| 6 | **L6 + L7** | Isolated school wing + school dashboard |

**Rule for every Ln:** ship **API change + web UI** in the same pass; Android same milestone when the flow is student/mentor-facing.

---

## 8. Explicit do-nots (for now)

- Don’t build a second microservice — extend `/lms`  
- Don’t fork Android into separate student/mentor APKs — role shells only  
- Don’t ship API milestones without the matching web UI in the same Ln  
- Don’t let School Admin act globally — always school-scoped  
- Don’t implement payments until a provider is chosen explicitly  

---

## 9. Quick reference — wire existing APIs first (still need UI)

1. Student: submissions + quiz + certificates + progress/me  
2. Mentor: submissions/queue + mark + mentees progress  
3. Then **new** School Admin people APIs (L4) + assign (L3) + tenants (L6) + pay (L7)  
