# Cross-repo harmonize roadmap (Android catch-up)

Sibling folders keep **separate git roots** — no mono-repo / no git restructuring.
Source of truth for courses = the Nelsen Savannah API (`api/`). Mentors = Firebase RTDB. UI labels = MentUI screenshots.

Mark status: `pending` → `in_progress` → `done`. Say **“do Hn”** or **“next”**.

| Repo | Role |
|------|------|
| `api/` | LMS API — courses/tracks only so far |
| `web/` | Web Learning UI (tracks naming) |
| `android/` | Mobile — close UI + data gaps |

---

## H1 — Bottom nav shell
**Status:** `done`  
**Target:** Home · Groups · center **+** · Chat · Profile  
**Work:**
- Relayout bar so **+** sits in the center (not a side FAB)
- Labels: **Groups** (was Communities), **Chat** (was Chats)
- Remove scroll hide/show; **keep blur / transparency**
- Still hide entire bar only when a chat conversation is open
**Out of scope:** fragment class renames (`CommunitiesFragment` stays)

## H2 — Spelling / label pass
**Status:** `done`  
Fix hardcoded typos & nav-facing copy: `Calender`→Calendar, `Recorgnize`→Recognize, `ShowALl`→Show more, Groups screen toolbar, speed-dial “Community” vs Groups, toast “Chats”→Chat where user-facing.

## H3 — Courses from API on Home
**Status:** `done`  
Courses are the only LMS catalog fetch so far (`GET /lms/tracks`).  
**Work:** surface failures (no silent empty), fix Home `CoursesAdapter` DiffUtil (`courseId`), confirm Home / All courses rails bind LMS tracks.

## H4 — Mentors rail (Firebase → UI)
**Status:** `done`  
Match MentUI mentor cards: avatar initials, verified badge, bio, `★ rating · N students` / `New mentor`, **Book now**, header **Show more**. Confirm mentee-only gate still works.

## H5 — Profile tab + Settings
**Status:** `done`  
Align Profile header (name, role badge, email, Edit profile, verification card) and Settings sections (GENERAL / DISPLAY / HELP / Connect with us) with screenshots. Keep nested Settings under Profile tab.

---

## Explicit do-nots
- Do not merge the three git repos
- Do not invent a mentors LMS API (Firebase stays)
- Do not rename RTDB / API field keys just for labels
- Do not restore bottom-nav scroll hiding

## Naming map (user-facing)

| UI label | Internal (ok to keep) |
|----------|------------------------|
| Groups | `CommunitiesFragment`, `communities/` |
| Chat | `ChatFragment`, `chatFragment` |
| Mentors | Firebase `mentors/` |
| Courses / Top courses | LMS `tracks` (`courseId` === `trackId`) |
| Profile | `ProfileFragment` |
