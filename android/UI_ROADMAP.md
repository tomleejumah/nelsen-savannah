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

## Round 2 — Chat, community and app polish (Nov 2026)

Status key as above. Nothing here is built unless marked `done`. Android changes are not compiled in CI; every chunk needs a device pass.

### Already shipped (android-chat-fixes-1)
**Status:** `done` (device QA pending)
- Chat list live from a Firestore snapshot listener, newest activity first; rooms without messages fall back to `createdAt`; one unreadable room no longer empties the list.
- AI rooms show "AI Assistant" instead of "Unknown User".
- Rating card only on mentor profiles (never on a mentee's).
- Group header "N participants" opens a member list (dialog; replaced by a full screen in R2-4).
- Backend: Firestore `chatRooms` list rule fixed (`request.auth.uid in resource.data.userIds`).

### R2-1 — Chat list correctness
**Status:** `in_progress` (done: group filter/search shows group name, hide empty direct chats, picker search; single filter menu; remaining: nav unread badge)
- Direct chats that were created but never messaged are **not listed** (no empty rows). Create the room lazily on first send, or hide rooms with no `lastMessage` for the creator.
- Group filter bug: filtering to Groups shows the *sender's* name instead of the group name. Group rows must use the room `name`.
- Replace the scattered filter buttons (All / Direct / Groups) with one hamburger/overflow menu or filter drawer.
- Search inside the New Chat picker.
- Unread counter on the Chats bottom-nav item.

### R2-2 — Message status ticks
**Status:** `pending`
**Approach:** see NELSEN_LMS_ROADMAP.md → NEXT-AGENT HANDOFF → item E (room-level `lastReadAt[uid]`, do after reactions because both change rules).
- Sent = single grey tick, delivered = double grey, **read = double blue**. Needs a per-message `readBy` or room-level `lastReadAt[uid]`; rules must allow a participant to write only their own read marker.
- Open question: confirm "no tick when sent" vs one grey tick for sent.

### R2-3 — New chat reaches everyone in the school
**Status:** `in_progress` (API `GET /lms/me/school-directory` done and tested; Android picker pending; booking-gate rule still undecided)
- API: school members endpoint (uid, name, avatar, role), scoped to the caller's active school memberships.
- Picker: searchable, grouped by role; keeps mentor/mentee rules for booking-gated chats if product wants them (decide).

### R2-4 — Group / community chat features
**Status:** `pending`
**Related new item:** message reactions (HANDOFF item D) share the same rules work as pinning/group photo; design rules for both together.
- Members list as a **full screen** (not a dialog): avatar, name, role tag, search.
- Mentor view: each student's progress on that unit/course inside the members screen (API endpoint to confirm/add).
- `@mention` for any member (WhatsApp style suggestion list, stored as `mentions: [uid]` on the message, highlighted in the bubble, push + notification entry).
- Mentor/admin can change the group photo and pin/unpin messages. Needs Firestore rules additions: admins may update `photoUrl` and `pinnedMessageId`; storage upload path and rules.
- Course group membership stays limited to learners enrolled in the course (server already enforces; add periodic reconcile so dropped learners are removed).

### R2-5 — Media picking
**Status:** `pending`
**Approach:** see NELSEN_LMS_ROADMAP.md → HANDOFF → item C (C1 bottom-sheet grid + system photo picker multi-select first; C2 inline gallery grid later).
- Replace the attachment dialog with a Telegram-style bottom sheet: recent gallery grid, camera, file, multi-select with a send count, preview before send.
- Multiple images/files in one action; upload progress per item.

### R2-6 — Chat screen behavior
**Status:** `pending`
- Top section (header) scrolls away on scroll-up and returns on scroll-down.
- Transitions: search expand/collapse, list-to-chat, filter menu (shared-element or slide/fade; respect reduced-motion).
- Add the missing chat icon (confirm placement: bottom nav / Home top bar).

### R2-7 — Notifications
**Status:** `pending`
- New chat messages and mentions appear in the Notifications screen.
- Unread badge on the bottom-nav chat tab (counts rooms with unread, and mentions).
- FCM payload carries `roomId`, `messageId`, `type` so taps deep-link into the chat.

### R2-8 — Home and navigation
**Status:** `in_progress` (done: Community rebrand, Book mentor label, logout icon, greeting; remaining: confirm bottom-bar create FAB on Home)
- Greeting: "Hello, {first name}".
- Remove the Home FAB (Create Story already has a top button; Create Group does not belong on Home).
- Rebrand "Groups" to **Community** everywhere user-facing; the Community tab has a visible Create button; posts stay inside communities.
- Book button label: **Book mentor**.
- Logout row gets an icon to match the rest of Settings.

### R2-9 — Profile redesign
**Status:** `pending`
- Fix the user icon/avatar disappearing under the profile tab view; redesign header and tab area.

### R2-10 — Book mentor
**Status:** `pending`
- Fix the booking calendar (date selection, timezones, unavailable slots).
- Loading spinners on booking confirm and slot fetch.

### R2-11 — Loading and progress states
**Status:** `pending`
- Spinners/progress for: book mentor, story upload, chat send/upload, profile save, lists on first load.

### R2-12 — Offline and caching
**Status:** `pending`
- Firestore offline persistence enabled and verified; chat list and recent messages readable offline (Room cache for chat rooms/messages where Firestore cache is not enough).
- Use the existing offline flag to show an "You're offline" banner; disable send/upload with a clear message.

### R2-13 — Stories and school branding
**Status:** `pending`
- Mentor story upload uses the same form/flow as mentee upload.
- Only **school admins** may publish corporate (brand) or personal-brand stories; others get the standard story only (confirm exact rule).
- School admin can upload/replace the school logo (API + storage + rules + Android and web).

### R2-14 — App optimization
**Status:** `pending`
- Startup time, list diffing/paging, image sizes (Glide), R8/shrink, baseline profile, remove unused dependencies, Firestore listener lifecycle (detach when not visible).

### Suggested order
R2-1 → R2-12 (offline, so everything after is testable) → R2-3 → R2-4 → R2-5 → R2-2 → R2-7 → R2-6 → R2-8 → R2-9 → R2-10 → R2-11 → R2-13 → R2-14.

---

## Explicit do-nots (from MentUI brief)

- No hardcoded preview copy (“Trailblazers”, “82 seats left”, etc.).
- No nav graph churn beyond All courses, Course track, Our programmes list.
- No ViewModel/repo/network rewrites except Chunk 1 event fields (+ programmes data source in Chunk 5).
- Keep existing empty-state logic; restyle, don’t replace.

---

## Future TODOs

### Announcements vs school groups
**Status:** `pending`

- **Announcements** = Nelsen Savannah platform channel. Only **system admin** may post / communicate there (hub-wide broadcast).
- **Groups** = per-school spaces. Each school has its own group(s); members of that school use those, not a single global group dump.

Today `chatRooms/announcements` is a single shared room — split posting rights + school-scoped groups when we pick this up.

---

## How we work

Say **“do chunk N”** (or “next”) — implement that chunk only, build/debug, then mark it `done` here.
