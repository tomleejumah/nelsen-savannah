# MentUI roadmap (Android)

Chunked UI restyle + shared Firebase shapes. One chunk per agent turn unless you say otherwise. Mark status as we go: `pending` → `in_progress` → `done`.

## Shared MentUI + roles

Same design tokens/chrome for **Mentee, Mentor, and Admin**. Visibility differs:

| Capability | Mentee | Mentor | Admin |
|------------|--------|--------|-------|
| MentUI tokens / screens | ✓ | ✓ | ✓ |
| Browse / book mentors | ✓ | — | — |
| Create stories/events | — | ✓ | ✓ |
| Manage banners / app | — | — | ✓ |
| Talk via Chats | ✓ | ✓ (mentees) | ✓ |

Use `Roles.browsesMentors()` / `Roles.canCreate()` / `Roles.canManageApp()` — never raw role strings.

---

## Shared event shape (best approach)

**Yes — web and Android should share one Firebase `Events/{eventId}` document.**

Web (`nelsen-savanna/src/data/events.ts` → `AppEvent`) already expects:

| Field | Notes |
|-------|--------|
| Existing Android fields | `title`, `date`, `startTime`, `endTime`, `mode`, `location`, `meetingLink`, `description`, … |
| `program` | Optional linked programme name/id (tag on card) |
| `seats` | Capacity |
| `seatsTaken` | Filled seats (derive % / “N seats left”) |
| `price` | e.g. `"Free"` / `"KES 500"` |

**Approach (pick this):**

1. Extend Android `Event` + `CreateEventActivity` write path with `program`, `seats`, `seatsTaken` (default `0`), `price`.
2. Home “Your schedule” card binds those fields (no hardcoded sample copy).
3. Web keeps reading the same keys — no parallel schema.

Do **not** invent a second events collection or rename keys. Optional programme dropdown should load from the same programmes source Home uses (once programmes are data-driven).

---

## Chunk order

### Chunk 0 — Design tokens + shared chrome
**Status:** `done`  
**Files:** MentUI colors (light+night), semantic aliases, dimens (radii 22/16/10), fonts (`font_display` / `font_ui`), step-mark drawable, tag/button styles, card/progress drawables, theme wire. Roles: `browsesMentors()` for mentee vs mentor/admin (same hide-book logic for Admin as Mentor).  
**Out of scope:** screen layout restyles (later chunks).

### Chunk 1 — Event shape + Create Event + schedule card
**Status:** `done`  
**Work:** `program` / `seats` / `seatsTaken` / `price` on Event + create form + toMap; MentUI schedule card; empty-state restyle; getNext3Items preserves full event.

### Chunk 2 — Home shell restyle
**Status:** `done`  
Top bar wordmark, stories always visible with pinned +Add + empty hint, step-mark section headers, MentUI empty schedule + quick actions.

### Chunk 3 — Top courses rail + All courses screen
**Status:** `done`  
Course card chrome; `AllCoursesActivity` list rows; Home “See all” → All courses; row tap → TrackLearn (Chunk 4 polish).

### Chunk 4 — Course track screen
**Status:** `done`  
Hero + 3-up stats + lesson nodes + sticky Continue. LMS-bound when live; stubs when not. Flag: lesson progress offline not tracked.

### Chunk 5 — Programmes (Home rail + full list)
**Status:** `done`  
`programmes` RTDB + seed defaults; Home rail; All programmes; Create Event programme dropdown.

### Chunk 6 — Mentors restyle
**Status:** `done`  
Find-a-mentor hero, list cards, tag rule `★ rating · N students` / `New mentor`, sticky Book now.

### Chunk 7 — Communities / posts / notifications
**Status:** `done`  
Token restyle: community/post cards, notifications unread `maroon_050` + `maroon_500` dot.

### Chunk 8 — Chat chrome
**Status:** `done`  
Bubbles (`surface_2` / `maroon_700`), reply chip, input bar, list rows, send button.

### Chunk 9 — Profile / Settings
**Status:** `done`  
Profile header + Settings toolbar/cards on MentUI tokens.

### Chunk 10 — Create Story polish
**Status:** `done`  
MentUI form; brand/company name required (validation copy strengthened).

---

## Explicit do-nots (from MentUI brief)

- No hardcoded preview copy (“Trailblazers”, “82 seats left”, etc.).
- No nav graph churn beyond All courses, Course track, Our programmes list.
- No ViewModel/repo/network rewrites except Chunk 1 event fields (+ programmes data source in Chunk 5).
- Keep existing empty-state logic; restyle, don’t replace.

---

## How we work

Say **“do chunk N”** (or “next”) — implement that chunk only, build/debug, then mark it `done` here.
