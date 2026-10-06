# Nelsen Savannah — LMS Implementation Roadmap
- [x] Live host controls use vector/SVG icons (mic, camera, flip, screen share, share, end) rather than emoji/text-heavy controls.
- [x] Host live feedback polls school-channel telemetry for concurrent viewers and recent live chat while broadcasting.
- [x] Authenticated live attendance records join identity + watch heartbeats; host/admin attendance summary reports unique attendees and watch duration.
- [x] School live channels are school-owned: SchoolAdmin connects the school's channel; mentors reuse that connection; no platform-channel fallback for school lives.
- [ ] Enrich ended-live summaries with processed provider analytics after its 48–72 hour reporting delay; immediate summaries use Nelsen attendance.

- [x] Live notification join path hardened: authenticated Android startup reconciles the current FCM token, course-live fanout includes enrolled learners + assigned mentors, high-priority data messages use the app's direct live PendingIntent in foreground/background, and tapping a live alert opens the live viewer.

This file is the working implementation plan for the Nelsen Savannah LMS across Web and Android.

## Core Decision: IDE Is Course-Based

The IDE is a **course-level feature**, not a lesson type and not limited to individual code lessons.

### Behaviour

- A course can have IDE support enabled or disabled.
- When IDE support is enabled for a course, the course UI always shows a **Go to IDE** button.
- The button remains available while the learner is working through the course; it is not dependent on the current lesson type.
- Video, text, quiz, assignment, and other lessons can coexist in an IDE-enabled course.
- The IDE opens the coding workspace for the current course.
- Lessons may still provide coding instructions/challenges, starter material, or references, but they do not own the IDE itself.
- Courses without IDE support do not show the button.

### Suggested course data

```text
course
├── ideEnabled
├── ideLanguage / environment
├── ideStarterFiles
└── other IDE configuration
```

The exact schema can be adjusted when the existing course API/model is updated.

---

## Phase 1 — Course-Based Web IDE

### Current implementation status

- [x] Course database support for `ide_enabled` already exists.
- [x] Course/catalog API already exposes `ideEnabled`.
- [x] Admin/CMS already reads and writes the course IDE setting.
- [x] Development branch `feature/course-ide` created for isolated IDE work.
- [x] Course sandbox runner endpoint implemented with enrollment/mentor authorization.
- [x] Refactor `CodeWorkspace` from lesson-owned to course-owned.
- [x] Add **Go to IDE** on enrolled course pages and keep it available while navigating lessons.
- [x] Keep React Flow as the course IDE **Flow** tab, independent of lesson progress.
- [x] Give linked course mentors access to the same sandbox from the mentor course cockpit.
- [x] Android uses the native course IDE; WebView integration was intentionally superseded.

### Course/Admin

- [x] Add an `ideEnabled` setting to courses.
- [x] Allow the course creator/admin to enable or disable IDE support.
- [x] Persist the IDE enable/disable configuration with the course.
- [x] Return IDE availability from the course API.
- [ ] Add/return additional course sandbox configuration only if required by the final IDE implementation.

### Learner UI

- [x] Show **Go to IDE** whenever the current course has IDE enabled.
- [x] Do not depend on `lessonType === "code"`.
- [x] Keep the button available throughout the course UI.
- [x] Hide the button for courses without IDE support.

### IDE Workspace

Reuse the existing web IDE stack:

- Monaco Editor
- React Flow
- existing Nelsen styling/theme
- existing code execution API where applicable

The workspace should receive **course context** instead of being owned by one lesson.

Target flow:

```text
Course
   │
   ├── Lesson 1
   ├── Lesson 2
   ├── Lesson 3
   │
   └── Go to IDE
          │
          ▼
      Course IDE
```

### Existing Code to Refactor

Current implementation is lesson-based:

```text
web/src/routes/learning.$trackId.lesson.$lessonId.tsx
web/src/components/lms/CodeWorkspace.tsx
```

Refactor this so `CodeWorkspace` can operate as a course workspace.

---

## Phase 2 — Android Course IDE

Android should provide a **native course IDE experience**. Do not redirect learners into the web IDE/WebView.

Target flow:

```text
Android Course
      │
      ├── Lessons
      │
      └── Go to IDE
              │
              ▼
        Native Course IDE
              │
              ▼
        Nelsen sandbox/execution API
```

### Android work

- [x] Show **Go to IDE** for IDE-enabled courses.
- [x] Replace the current WebView IDE implementation with a native Android IDE activity.
- [ ] Finish native editor language tooling: CodeEditor is integrated with monospace editing and course-language selection, but syntax highlighting/language analyzers, indentation behavior, and verified line-number UX still need completion.
- [x] Load course IDE configuration/starter files from the LMS API.
- [x] Connect Run action to the existing authorized course sandbox/execution API. Submit remains deferred until a course-IDE submission contract is defined.
- [x] Persist learner workspace locally so edits survive navigation/restarts.
- [x] Handle execution loading, output, authentication, network, and API error states natively.
- [ ] Add explicit IDE configuration-loading/error UI instead of silently retaining defaults when course IDE config fails.
- [ ] Verify keyboard/input behaviour and larger-screen layouts.

The exact native editor dependency must be approved before implementation.

---

### Android role-specific UI / permissions

- [ ] Audit the Android UI for **Mentee, Mentor, SchoolAdmin, and SuperAdmin** so each role sees only actions, navigation, FABs, menus, course controls, chat/community controls, and learning/admin surfaces that role can actually use.
- [ ] Keep Mentee learning/discovery UI focused on joining schools, enrolling, learning, bookings, chat, groups, stories/status and live viewing; do not expose host/admin controls.
- [ ] Give Mentor Android-native course/learner/material/live/booking workflows only where the API authorizes that mentor's assigned scope.
- [ ] Give SchoolAdmin Android-native school-scoped membership, mentor, course and live-management surfaces; never leak controls for another school.
- [ ] Keep SuperAdmin/high-risk administration Web-first unless a specific native workflow is approved.
- [ ] Add role-switch/sign-out/sign-in regression QA so stale role UI cannot survive identity changes.

### Android role-management UI parity

- [ ] After responsive/multiple-screen-size support, bring mentor and SchoolAdmin management workflows to Android with role-aware layouts and the same permissions/business rules as Web.
- [ ] Share API/domain logic with Web rather than cloning desktop UI; use Android-native phone/tablet navigation and progressively expose management tools by available screen space.
- [ ] Keep Super Admin / high-risk administration Web-first unless a concrete mobile workflow justifies native support.

---

### Deferred Android IDE polish

The remaining native Android IDE editor/tooling work is intentionally deferred and will be revisited after the current LMS roadmap features. It must not block Phase 6 sharing/deep-link work or release stabilization.

---

## Phase 3 — Android Loading, Offline Cache & Study Sync

Android learning screens should remain responsive on slow or unavailable networks and clearly communicate loading state.

### Course action menu

- [x] Replace stacked secondary course action buttons with a top-right **⋮ overflow menu**.
- [x] Move **Go to IDE** into the overflow menu when IDE is enabled.
- [x] Move **Leave Course** into the overflow menu.
- [x] Keep the primary learning/navigation action directly accessible instead of hiding it in the overflow menu.
- [x] Keep destructive actions such as leaving a course clearly identified and confirmed before execution.

### Loading UX

- [x] Show a spinner/progress state while loading course catalogs, tracks, course details, lessons, and study progress.
- [x] Do not show an empty-state message until the corresponding API request has completed.
- [x] Preserve already-cached content while refreshing in the background where possible.
- [x] Show retry/error state when neither network nor cached content is available.

### Offline cache

- [x] Cache course/track metadata required by enrolled learners.
- [x] Cache lesson metadata/content that is safe and practical for offline study.
- [x] Cache learner study/progress state locally.
- [x] Define cache freshness/expiry rules so stale server data is refreshed without destroying offline usability.
- [x] Make cached enrolled-course content available when the device is offline.

### WorkManager sync

- [x] Add a network-constrained WorkManager study-sync worker.
- [x] Queue locally changed study/progress records for synchronization instead of losing them when offline.
- [x] Push pending local progress to the LMS API when connectivity returns.
- [x] Pull relevant server-side progress/content changes and reconcile the local cache.
- [x] Make sync retry-safe/idempotent so repeated worker runs do not duplicate progress.
- [x] Expose syncing/synced/offline state where useful to the learner.
- [x] Show a persistent, compact Telegram-style **Offline / Waiting for network** indicator while connectivity is unavailable; remove it automatically when connectivity/sync recovers.
- [x] Schedule periodic background sync and trigger immediate sync after important local study changes when network is available.
- [x] **Final Phase 3 task:** add an authenticated batch progress-sync API with idempotency keys; update Android WorkManager to batch queued progress, safely retry the same request, and reconcile the authoritative server response without allowing progress to move backwards.

---

### Explicit offline material downloads

- [ ] Add opt-in **Download for offline** controls; never automatically download large lesson media/materials just because a learner enrolls.
- [ ] Let learners cherry-pick downloads at course, module, lesson, and individual material/media level where practical.
- [ ] Show estimated/download size before starting and current storage used by offline LMS material.
- [ ] Add Wi-Fi-only preference for large downloads, plus download progress, pause/cancel/retry, and clear/remove controls.
- [ ] Store downloaded PDFs/images/video/material files safely and make lesson playback/viewing resolve to the local copy while offline.
- [ ] Detect changed/removed server material and refresh or invalidate the local file without deleting unrelated offline study data.


## Phase 4 — Video Lesson Controls

Improve video lessons on both Web and Android.

Controls:

- [x] Play / Pause
- [x] Mute / Unmute
- [x] Volume
- [x] Seek/progress bar
- [x] Current time / duration
- [x] Fullscreen
- [x] Playback speed:
  - 0.5x
  - 0.75x
  - 1x
  - 1.25x
  - 1.5x
  - 2x
- [x] Optional 10-second rewind/forward controls.

Android can use the native video player controls while Web exposes equivalent controls.

---

## Phase 5 — YouTube Live Learning

**Approved architecture:** Only Mentor, SchoolAdmin, and Admin roles may create/start a live session. Mentees are viewers only. All live broadcasts use the single Nelsen-owned YouTube channel; creators do not connect personal YouTube channels.

### Future school-owned YouTube channels

For the current rollout, Nelsen owns the channel and therefore retains the platform's learning-video/replay library. Multi-school channel ownership is a later Super Admin milestone.

- [ ] Add optional YouTube channel connection/configuration when a Super Admin creates or manages a school.
- [ ] Allow each school to use its own YouTube channel for school-owned live sessions and replays.
- [ ] Keep the Nelsen-owned YouTube channel as the default/fallback for schools without a connected channel.
- [ ] Store school-level channel ownership/connection metadata securely; never expose YouTube OAuth credentials or stream keys to learner clients.
- [ ] Route live creation, replay ownership, moderation, and history to the correct school channel.
- [ ] Add channel connection/health/reconnect controls to the Super Admin school dashboard.

Nelsen will **not process, relay, transcode, or store the live video stream**.

The current live transport is explicitly **YouTube-hosted streaming**. Nelsen owns the channel/integration, permissions, metadata, UI, notifications, and replay references; YouTube carries the live video bytes.

YouTube handles the video infrastructure.

Architecture:

```text
VIDEO

Mentee
   │
   ▼
YouTube Live
   │
   ▼
Viewer


APPLICATION

Mentee
   │
   ▼
Nelsen API
   │
   ▼
Nelsen Users


NOTIFICATIONS

Nelsen
   │
   ▼
FCM
   │
   ▼
Users
```

### Hosts — Mentor / SchoolAdmin / SuperAdmin

- [x] Replace manual **Link YouTube Live** with **Go Live**: hosts never create or paste a YouTube link.
- [x] Create a YouTube broadcast + ingest stream through the Live Streaming API, bind them, create the Nelsen live event, and return the ephemeral RTMPS ingest target to Android.
- [x] Publish Android camera + microphone directly to YouTube over RTMPS; Nelsen does not relay video bytes.
- [x] Associate the live session with the Nelsen creator and an explicit course, school, or platform audience.
- [x] Store live metadata in Nelsen using the existing `hub_events` model plus `youtube_live_sessions` broadcast/stream metadata. Never persist the RTMPS stream secret.
- [x] Track live status as scheduled/live/ended and sync those transitions server-side from YouTube Data API when `YOUTUBE_API_KEY` is configured.
- [x] Use one Nelsen Google OAuth project/client with encrypted channel refresh tokens stored per platform or per school. School lives prefer their own connected channel and fall back to the Nelsen platform channel.
- [x] Add SchoolAdmin and SuperAdmin channel connection UI. SchoolAdmin can connect only their school; SuperAdmin can connect the platform/default channel and any school channel.
- [ ] Complete production Google OAuth configuration/verification and add the production redirect URI before enabling automatic Go Live in production. Do not leave the OAuth app in Testing for school rollout because non-basic test authorizations expire after seven days.
- [ ] Request a YouTube Data API quota increase before high-volume multi-school rollout. Creating a fresh live currently uses three 50-unit write calls (broadcast insert + stream insert + bind), before low-cost status reads.

### Mentees / viewers

- [x] Mentees can discover, join, and watch linked live sessions but cannot create/start broadcasts.
- [x] Apply scoped visibility before exposing a live session: course → enrolled learners/linked staff, school → active school members, platform → signed-in Nelsen users.

### Notifications

When a live session starts:

```text
Live starts
   ↓
Nelsen updates live status
   ↓
FCM notification
   ↓
Users receive "X is live now"
   ↓
Watch Live
```

- [x] Keep live state monotonic so provider sync never downgrades an active `live` session back to `scheduled` during propagation delay.
- [x] Reconcile the signed-in Android user's FCM token on every app start so reinstall/token rotation/account switching cannot leave live recipients without a registered token.
- [x] Support Android Picture-in-Picture for both publishing and watching a live session so leaving the app does not intentionally stop the active live experience.
- [x] Add Android host-studio controls for microphone mute/unmute, camera pause/resume, camera flip, screen sharing, canonical Nelsen live sharing, and end-live without reconnecting the active broadcast.
- [x] Run Android screen capture under a media-projection foreground service for modern Android background/security requirements.
- [x] Remove the current device token from the signed-out user's FCM token slot before account switching.
- [ ] Add browser live publishing through a Nelsen-owned WebRTC/WHIP-to-RTMP relay. Browser camera/screen capture cannot publish directly to the existing RTMPS ingest endpoint, so do not ship a fake web Go Live button until this transport is deployed.
- [x] Keep live-session Android UX provider-neutral; users see Nelsen Live rather than upstream video-provider branding.
- [x] Send FCM live-start notification when Nelsen transitions the session to `live`.
- [x] Notification opens the correct linked live session on Android, including foreground and background notification-tap paths.
- [x] Avoid duplicate live notifications with a one-time `live_notified_at` claim before fanout.
- [x] Detect the YouTube broadcast state automatically and drive scheduled → live → ended transitions without a manual client status update when `YOUTUBE_API_KEY` is configured.

### Viewer

- [x] Add **Live Now / Scheduled Live / Replay** state to Nelsen event cards.
- [x] Play/embed the linked YouTube Live/replay stream inside Nelsen Web.
- [x] Play/embed the linked YouTube Live/replay stream inside Nelsen Android.
- [x] Show Nelsen event title/status/actions around the stream on Web and Android.
- [ ] Handle scheduled, live, offline, and ended states.

### History

- [x] Store live session metadata/history in `hub_events`; ending a live does not delete its audience association or YouTube URL.
- [x] Show ended sessions in Android **Past** schedules and the Web **Live history** replay surface.
- [x] Link ended sessions back to the same YouTube video for replay when the replay remains available.
- [ ] Detect replay/offline availability from YouTube rather than assuming every ended YouTube URL is still playable.

---

## Social Milestone — Story/Status Viewer Receipts

Current stories maintain an aggregate view count and local seen state. To show the owner exactly who viewed a status/story:

- [x] Record one authenticated viewer receipt per story in the private LMS `story_view_receipts` table instead of exposing viewer identities under the client-readable RTDB story node.
- [x] Prevent duplicate viewer receipts with a `(story_id, uid)` primary key while keeping the RTDB aggregate `views` count.
- [x] Add an owner-only **Viewed by** bottom sheet with avatar, name, and viewed time.
- [x] Enforce receipt privacy through authenticated API endpoints: viewers can only record their own authenticated view and only the story owner can list receipts.
- [x] Expire viewer identities with the story's `expiresAt`; the server reaper removes expired receipts hourly, including receipts for stories closed/deleted before that original expiry.

---

## Phase 6 — Android Deep Links & Sharing

Make app content shareable with links that open the exact destination in Nelsen Android when installed and fall back safely to Web when it is not.

### Link targets

- [x] Share/open a specific **community post** using `/posts/{communityId}/{postId}`; native routing lands on `PostDetailActivity`.
- [x] Share/open a specific **story/status** by canonical story ID, including cold-start loading and expired/deleted handling.
- [x] Share/open a specific **course/track** from `/courses/{trackId}` in native `TrackLearnActivity`.
- [x] Share/open a specific **school** from `/schools/{schoolId}` in its filtered native course catalog (school title long-press Share).
- [x] Share/open `/groups/{groupId}` directly in native `CommunityDetailActivity`; opening does not auto-join the recipient.
- [x] Preserve the target through onboarding, Login/SignUp, PIN/LockScreen, and email-verification navigation so authentication returns to the originally shared content.

### Android App Links

- [x] Define stable HTTPS namespaces for posts, stories/statuses, courses, schools, and groups. Community posts use `/posts/{communityId}/{postId}` because native authorization/loading requires community context.
- [x] Add Android `autoVerify` intent filters for canonical HTTPS App Link namespaces on the Nelsen domain.
- [ ] Publish and verify `assetlinks.json` for the production Android signing certificate.
- [ ] Route each link to the correct native Activity/screen and validate missing/deleted/private content gracefully.
- [x] Upgrade the existing Community post Share action from text-only sharing to a canonical HTTPS post link.
- [x] Add native **Share** actions for stories/statuses, courses, schools, and groups using canonical HTTPS links rather than app-only custom schemes.
- [x] Keep non-looping Web fallback routes for shared posts/stories/groups and exact Web redirects for courses/schools when the app is not installed.
- [ ] Respect membership/enrollment/privacy rules when opening shared school/course/community/group content; a shared group link must verify membership/join policy before opening the group.

---

## Android Release Quality — Responsive Screen Sizes

- [ ] Audit all Android screens across compact phones, standard phones, large phones, tablets, and landscape orientation. **In progress:** repository-wide Android layout pass underway: main shell/Home/Profile/Groups/Chat, auth/onboarding, stories/media, school/course catalog and dialogs.
- [ ] Replace fixed dimensions/positioning that clip, overlap, or leave excessive whitespace with responsive ConstraintLayout/Compose sizing and resource qualifiers where appropriate. **In progress:** course catalog header can now scroll independently on compact-height/font-scaled screens; Login no longer assumes a 300dp hero width; Add Course no longer assumes a fixed 300×550dp dialog and can scroll on compact displays; onboarding/profile/media previews now scale within available screen bounds.
- [ ] Add adaptive spacing, typography, image/media sizing, dialogs/sheets, lists, navigation, and form layouts for different screen widths/heights and display densities. **In progress:** tablet bottom navigation + Chat master/detail are wired; school discovery now uses a `sw600dp` master/detail shell (schools left → selected school's courses right), tablet school deep links enter that shell, and course learning is a true chapters-left/material-right split with chapter taps loading material in-screen. Phone navigation remains full-screen. Remaining responsive QA covers other screens, IME/system bars and font scaling.
- [ ] Finish the tablet LMS/discovery **progressive master-detail shell** instead of stretching phone Activities:
  - Home → Schools: school list/navigation pane + selected school preview/details pane.
  - School → Courses: selected school becomes parent context on the left; its course catalog/detail occupies the right.
  - Course → Learning: course/chapter/lesson navigation stays left; selected lesson/material (video/PDF/text/quiz/assignment/IDE where applicable) stays right.
  - Back moves one hierarchy level outward while preserving the parent context instead of tearing down the entire tablet surface.
  - Deep links into school/course/lesson should reconstruct the correct pane hierarchy.
  - Phones retain the existing single-pane/full-screen Activity flow.
  - Do **not** force this shell onto unrelated screens: Chat keeps conversation master/detail; Profile, Settings, stories/status viewer, forms and dialogs remain normal adaptive surfaces.
- [ ] Complete **scheduled + instant Live** end-to-end on Android/API:
  - Mentor/School Admin/Super Admin can choose **Go live now** or **Schedule live**; scheduled live keeps the selected date/start time instead of forcing `System.currentTimeMillis()`.
  - **Host targeting UX:** School is the default radio-style audience and automatically uses the host's active school (currently Nelsen for the test accounts). Course reveals a course dropdown; Mentor only receives courses assigned to them, while SchoolAdmin can choose courses in their active school. YouTube is linked once per school and reused for later lives; SuperAdmin manages the per-school YouTube connection when creating/updating schools.
  - Scheduled live creates the YouTube broadcast + Nelsen Hub event ahead of time, is listed on Home/All Schedules for the permitted audience, and remains `scheduled` until YouTube reports it live.
  - **Audience isolation is enforced by the API, not only UI:** mentors and School Admin see live sessions only for their active school; mentees see school-wide lives for their active school and course-scoped lives only for courses they are actively enrolled in; course mentors see course lives they teach. Admin/SuperAdmin platform broadcasts remain the explicit cross-school exception.
  - Live cards never use seat reservation semantics: CTA is **Join live** for viewers (scheduled/live; replay after end). The creator gets **Go live** for scheduled/instant sessions and enters the existing `GoLiveActivity` host flow.
  - Add canonical app link `https://nelsen-savannah.co.ke/live/{eventId}`; sharing a live shares the Nelsen link, not the raw YouTube URL. Deep-link resolution fetches the event, enforces audience access, then routes creator → host/Go live and everyone else → `LiveViewerActivity`.
  - Preserve instant-live behavior as the fast path through the same event/deep-link model; never expose/persist the RTMPS ingest key in links or event records.
  - Add reminders/notifications for scheduled live and regression coverage for creator/viewer roles, course/school/platform audience, scheduled/live/ended states, deep links and unavailable YouTube streams.
- [ ] Extend the **adaptive pane policy across tab-owned content where it naturally improves continuity**:
  - Home schedule/events: list/timeline stays as parent context; selected event/live opens in the detail pane on tablets. Create/edit event/live uses an adaptive detail/form pane rather than a narrow phone dialog stretched across the tablet.
  - Groups: group/community list → selected group; inside a group, feed/post list → selected post/comments in the detail pane where width permits.
  - Posts/status/stories opened from tab content should use the tab's available detail pane on large screens where practical; immersive media/story viewing may still take the full surface when that is the better experience.
  - Keep bottom-nav/tab selection stable while opening/closing tablet detail panes; Back closes/drills out of detail before changing tabs.
  - Reuse the same width/inset/font-scale/IME rules across these adaptive surfaces; phones retain existing single-pane navigation.

- [ ] Verify keyboard/IME, system bars, display cutouts, gesture navigation, and accessibility font scaling do not hide actionable content.
- [ ] Add representative multi-device screenshot/layout tests and release QA for supported screen-size buckets.

---

## Identity & Role Upgrade Reconciliation

- [x] Keep Firebase UID as the permanent identity when Mentee/other users are promoted to Mentor.
- [x] Preserve/backfill the canonical base user during role upgrades instead of moving/deleting user data.
- [x] Reconcile legacy Android `/users/{uid}`, LMS `lms/users/{uid}`, `roles/{uid}`, and Mentor `/mentors/{uid}` data by UID.
- [x] Make Mentor promotion idempotently create/backfill Mentor profile data.
- [x] Android email login falls back to authenticated `GET /lms/me` when legacy user data is missing, allowing previously broken upgraded accounts to self-repair.
- [x] Web and Android consume the same canonical LMS identity/role source.
- [ ] Add regression tests for Mentee → Mentor → sign-out → sign-in on Web and Android, including legacy accounts missing `/users/{uid}` or `/mentors/{uid}`.

---

## Stabilization Milestone — Profiles, Mentor/SchoolAdmin & Live Release

This milestone tracks the current production-hardening work before the remaining deferred LMS features.

### Canonical profiles and mentor materials

- [x] Add canonical profile name, bio, and profile-photo updates through the LMS API.
- [x] Keep Web and Android profile surfaces synchronized with canonical LMS profile data.
- [x] Expose uploaded mentor materials through the LMS API with access-aware authorization.
- [x] Preserve canonical profile fields during authentication hydration instead of overwriting them with stale identity-provider values.

### Mentor / SchoolAdmin integration

- [x] Resolve school context from active school memberships rather than stale mirrored school fields.
- [x] Scope mentor learner/progress data to courses assigned to that mentor.
- [x] Scope the Web mentor workspace to assigned courses.
- [x] Keep SchoolAdmin student, mentor, membership, join-request, and course-management surfaces school-scoped.
- [ ] Validate mentor course/learner/progress scoping against production data and close any remaining authorization edge cases.
- [ ] Validate SchoolAdmin student, mentor, membership, assignment, join approval/rejection, and course controls against active school membership.
- [ ] Keep SuperAdmin expansion as a later milestone rather than blocking the current school release.

### Live hardening and end-to-end validation

- [ ] Fix canonical live joining end-to-end: `/live/{eventId}` must resolve the authorized event and open the native viewer/host destination.
- [ ] Verify school-wide live notification fanout reaches every active school member with a current FCM token; log recipient/sent/missing-token/failed counts and make failed delivery diagnosable.
- [ ] Persist live-start alerts in the same in-app Notifications feed as other notifications and make tapping a live notification open the linked live session.
- [ ] Add/verify the Firebase Realtime Database `.indexOn: "timestamp"` rule for `Notifications/$uid` so notification feed queries are server-indexed.

- [x] Require active school membership for school-scoped live hosting.
- [x] Require mentors to be assigned to a course before hosting a course-scoped live session.
- [x] Authorize manual live-status changes by the acting host/admin.
- [x] Track YouTube playback availability and expose unavailable-live state to Web and Android.
- [x] Add live sharing on Web and Android.
- [x] Move ended live sessions into replay/history immediately.
- [ ] Run the production end-to-end Android live test: connect channel → create live → Android camera/mic → RTMPS → YouTube → learner playback.
- [ ] Verify FCM notification taps, deep links, and Share actions against a real live session.
- [ ] End the real broadcast and verify scheduled/live/ended transitions plus replay/history placement.
- [ ] Test private, deleted, unavailable, and replay-disabled YouTube broadcasts and ensure Web/Android fail gracefully.
- [ ] Translate provider/API live-start failures into Nelsen-facing messages (for example, channel live access still activating) instead of exposing a generic 502 or provider-specific wording.
- [ ] Verify YouTube OAuth/channel connection health after production credentials are configured.
- [ ] Add browser hosting later: Web camera/mic → WebRTC publishing layer/gateway → YouTube RTMP/RTMPS. This is deferred and must not turn the Nelsen API into a video relay/transcoding server.

### Course authoring and destructive actions

- [x] Restore standalone lesson quizzes with multiple questions/options and selected correct answers for automatic marking.
- [x] Restore lesson-level mentor-marked assignments in the course editor.
- [x] Remove IDE/code-lab from lesson media choices; IDE remains course-owned.
- [x] Add multi-select lesson deletion in the course editor.
- [ ] Add safe backend course/track deletion with transactional cleanup of dependent LMS records.
- [ ] Add single-course deletion UI backed by the safe deletion service.
- [ ] Add multi-select course deletion with one confirmation and clear partial/failure handling.
- [ ] Replace per-module course-editor loading with one course-authoring metadata request/query returning chapters + lesson metadata only (never PDF/video bytes).

### Deployment and database protection — required before production deployment

- [ ] Create an automatic timestamped SQLite snapshot before deployment.
- [ ] Abort deployment if the database backup cannot be created or validated.
- [ ] Add pre-deploy and post-deploy LMS database sanity checks for critical tables/counts/relationships.
- [ ] Ensure normal deployment/startup cannot seed, purge, clear, recreate, or silently replace the production LMS database.
- [ ] Fix the `school_memberships.invite_token` migration-order warning.
- [ ] Verify all current lesson/media records still resolve to valid storage paths before deployment.
- [ ] Keep the recovered SQLite database and known-good backup available as rollback material until PostgreSQL migration is complete.

### PostgreSQL migration — after SQLite stabilization

- [ ] Provision the production PostgreSQL database.
- [ ] Apply/validate the LMS PostgreSQL schema and migrations.
- [ ] Migrate the complete SQLite dataset while preserving IDs and relationships.
- [ ] Compare critical row counts and relationship integrity between SQLite and PostgreSQL.
- [ ] Switch production to `DATABASE_URL` only after validation and confirm health reports PostgreSQL as the LMS primary.
- [ ] Keep SQLite as a temporary rollback source during cutover.
- [ ] Add automated PostgreSQL backups/retention and an off-host backup strategy.



### LMS performance, school access and applications

- [ ] Add Redis-backed API caching for expensive read-heavy LMS/catalog/course/school queries with explicit TTLs and invalidation on writes; measure slow endpoints before and after caching.
- [ ] Complete the school application flow across API/Web/Android: Apply to school → pending/application status → approved/active membership → rejected state where applicable.
- [x] Android school course list exposes Apply to school and pending membership state.
- [x] Android blocks opening course learning content unless the user is actually enrolled in that course.
- [x] Harden module/lesson learning access at the API boundary with active-school-membership + course-enrollment checks (staff roles follow scoped staff access); legacy module fallback cannot bypass the gate.
- [ ] Finish deep-link regression verification so direct/shared course and lesson links cannot bypass the same membership/enrollment rules.
- [ ] Add regression coverage for non-member, pending applicant, active school member but unenrolled course, and enrolled course access.

### Android status / story media

- [x] Allow users to select and upload either photos or videos when creating a status/story.
- [x] Render video statuses in the story viewer while preserving legacy image stories.
- [x] Pause story progress and video playback while the viewer is held down; resume on release/cancel.
- [x] Match video-status progress to the video's actual playback duration; image statuses keep the fixed image duration.

### Chat reliability and attribution

- [x] Purge rejected local-only Room messages after a successful authoritative Firestore sync so stale failed test sends do not remain in chat.
- [x] Show a visible failed-send flag on outgoing messages whose Firestore send fails.
- [x] Shared announcements include a canonical app link that routes recipients back into the Announcements chat.
- [x] Show sender names on incoming bubbles in group/system conversations so multi-user messages are attributable.

### Android community reactions and app distribution polish

- [ ] Replace the generic heart-like group post reaction with a Reddit-style upvote interaction using an original Nelsen SVG (do not copy Reddit artwork); preserve current vote persistence/count behavior.
- [x] Remove the main FAB shadow halo so it visually belongs with the bottom glass navigation.
- [x] Place Log out directly below Privacy policy in Profile settings.
- [x] Until the Play Store listing is live, Share app uses the Nelsen browser download URL instead of a Play Store URL.
- [ ] Re-enable Rate us when the public Play Store listing is available and route it to the store listing.

### Chat composer and media picker polish

- [ ] Redesign the in-chat attachment/media/data selection sheet to match the interaction quality of **Samsung Messages / Telegram** while keeping Nelsen's own visual language.
- [ ] Add a compact attachment launcher for gallery/photos, camera, video, files/documents, and other supported learning/chat data.
- [ ] Add voice-note recording and sending, including record/cancel/preview/send states and playback in chat.
- [ ] Keep attachment selection fast, touch-friendly, and consistent across direct chats, groups, and announcement-capable chat surfaces.
- [ ] Harden media upload progress, retry, cancellation, failed-send state, and attachment permissions before release.


---
## Implementation Order

1. Convert IDE availability from lesson-based to **course-based**.
2. Add course IDE configuration/API support.
3. Refactor the Web `CodeWorkspace` into a course workspace.
4. Add persistent **Go to IDE** course UI.
5. Replace Android WebView IDE with the native course IDE.
6. Add Android loading states/spinners across course/track/lesson fetches.
7. Add Android offline course/study cache.
8. Add WorkManager offline study-progress/API synchronization.
9. Improve Web/Android video controls.
10. Add YouTube Live session creation/linking.
11. Add FCM live notifications.
12. Add Web/Android live viewers.
13. Add live session history and ended-live handling.
14. Add verified Android deep links + sharing for community posts, stories/statuses, courses, and schools.

---

## Architecture Rules

### IDE

```text
Course owns IDE availability.
Lessons provide learning content.
IDE provides the shared coding workspace.
```

### Live Video

```text
YouTube owns the video stream.
Nelsen owns users, courses, metadata, permissions, UI and notifications.
```

The Nelsen backend should not become a video streaming server.

---

## Completed Platform Maintenance — Android In-App Updates

- [x] Check the release API before allowing normal app launch when a newer mandatory version exists.
- [x] Download APK updates with WorkManager so downloads survive backgrounding/activity changes.
- [x] Periodically check for updates and prefetch newer APKs in the background.
- [x] Reuse a prefetched APK and prompt the user to install when it is ready.
- [x] Verify downloaded APK SHA-256 when the release API provides a checksum.
- [x] Prevent duplicate update downloads with unique WorkManager jobs.
- [x] Clean stale APKs and incomplete update files to control storage usage.
- [x] Install through FileProvider / Android package installer.
- [x] Release the launcher splash when a mandatory update is detected so the update sheet is visible without continuing into the app.
- [x] Show the update sheet immediately when a newer version is detected while WorkManager downloads the APK in the background.
- [x] Keep normal app navigation blocked for a mandatory update until the update path is handled.

---

## Final Deferred Android — Profile Update

Do this later after the current LMS roadmap work.

- [ ] Add Android **Edit profile** UI.
- [ ] Allow the learner to update supported profile fields and profile photo where applicable.
- [ ] Validate fields and show saving/saved/error states.
- [ ] Persist changes through the existing profile API/data source and refresh the Android user/profile cache after a successful update.
- [ ] Keep role/identity/security-sensitive fields read-only unless the backend explicitly allows them.

---

## Final Deferred Infrastructure — Piston Code Runner

Do this **after the LMS/live roadmap work above**, alongside the final Didit/distribution work.

- [ ] Self-host Piston code execution on Nelsen infrastructure.
- [ ] Set `PISTON_URL` to the private Nelsen runner.
- [ ] Verify Web and Android Run/Submit execution against the private runner.
- [ ] Verify runner isolation, resource/time limits, supported course languages, failure handling, and production monitoring.
- [ ] Remove dependence on the former public EMKC Piston endpoint. Until the private runner is deployed, non-HTML Run requests may return 502 on both Web and Android.

---

## Final Deferred Decision — Didit SDK / APK Size

Do this **after the LMS/live roadmap work above**.

The current Android app bundles `me.didit:didit-sdk` and its native/media dependencies, which materially increases distribution size.

- [ ] Measure the final APK/AAB contribution from Didit and its transitive dependencies.
- [ ] Decide whether KYC should remain bundled, move to Didit's hosted/web verification flow, or be isolated into an Android dynamic feature where distribution supports it.
- [ ] Do not implement arbitrary post-install loading of the Maven/native SDK as an asset; choose a supported delivery architecture first.
- [ ] Preserve the existing KYC flow until this decision is made.
