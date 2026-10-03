# Nelsen Savannah — LMS Implementation Roadmap

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
- [ ] Add Android WebView course IDE integration.

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
- [ ] Replace the current WebView IDE implementation with a native Android IDE activity.
- [ ] Add a native code editor with syntax highlighting, line numbers, indentation, and course-language support.
- [ ] Load course IDE configuration/starter files from the LMS API.
- [ ] Connect Run/Submit actions to the existing authorized course sandbox/execution API.
- [ ] Persist learner workspace locally so edits survive navigation/restarts.
- [ ] Handle editor loading, execution, output, and error states natively.
- [ ] Verify keyboard/input behaviour and larger-screen layouts.

The exact native editor dependency must be approved before implementation.

---

## Phase 3 — Android Loading, Offline Cache & Study Sync

Android learning screens should remain responsive on slow or unavailable networks and clearly communicate loading state.

### Loading UX

- [ ] Show a spinner/progress state while loading course catalogs, tracks, course details, lessons, and study progress.
- [ ] Do not show an empty-state message until the corresponding API request has completed.
- [ ] Preserve already-cached content while refreshing in the background where possible.
- [ ] Show retry/error state when neither network nor cached content is available.

### Offline cache

- [ ] Cache course/track metadata required by enrolled learners.
- [ ] Cache lesson metadata/content that is safe and practical for offline study.
- [ ] Cache learner study/progress state locally.
- [ ] Define cache freshness/expiry rules so stale server data is refreshed without destroying offline usability.
- [ ] Make cached enrolled-course content available when the device is offline.

### WorkManager sync

- [ ] Add a network-constrained WorkManager study-sync worker.
- [ ] Queue locally changed study/progress records for synchronization instead of losing them when offline.
- [ ] Push pending local progress to the LMS API when connectivity returns.
- [ ] Pull relevant server-side progress/content changes and reconcile the local cache.
- [ ] Make sync retry-safe/idempotent so repeated worker runs do not duplicate progress.
- [ ] Expose syncing/synced/offline state where useful to the learner.
- [ ] Schedule periodic background sync and trigger immediate sync after important local study changes when network is available.

---

## Phase 4 — Video Lesson Controls

Improve video lessons on both Web and Android.

Controls:

- [ ] Play / Pause
- [ ] Mute / Unmute
- [ ] Volume
- [ ] Seek/progress bar
- [ ] Current time / duration
- [ ] Fullscreen
- [ ] Playback speed:
  - 0.5x
  - 0.75x
  - 1x
  - 1.25x
  - 1.5x
  - 2x
- [ ] Optional 10-second rewind/forward controls.

Android can use the native video player controls while Web exposes equivalent controls.

---

## Phase 5 — YouTube Live Learning

Nelsen will **not process, relay, transcode, or store the live video stream**.

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

### Mentee

- [ ] Add **Go Live** UI.
- [ ] Create/link a YouTube Live session.
- [ ] Associate the live session with the Nelsen user/course/event.
- [ ] Store live metadata in Nelsen.
- [ ] Track live status: scheduled/live/ended.

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

- [ ] Send FCM live-start notification.
- [ ] Notification opens the correct live session.
- [ ] Avoid duplicate live notifications.

### Viewer

- [ ] Add **Live Now** UI/section.
- [ ] Play/embed the YouTube Live stream inside Nelsen Web.
- [ ] Play/embed the YouTube Live stream inside Nelsen Android.
- [ ] Show relevant Nelsen UI around the stream.
- [ ] Handle scheduled, live, offline, and ended states.

### History

- [ ] Store live session metadata/history.
- [ ] Show ended sessions where appropriate.
- [ ] Link to replay when a YouTube replay is available.

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

## Final Deferred Decision — Didit SDK / APK Size

Do this **after the LMS/live roadmap work above**.

The current Android app bundles `me.didit:didit-sdk` and its native/media dependencies, which materially increases distribution size.

- [ ] Measure the final APK/AAB contribution from Didit and its transitive dependencies.
- [ ] Decide whether KYC should remain bundled, move to Didit's hosted/web verification flow, or be isolated into an Android dynamic feature where distribution supports it.
- [ ] Do not implement arbitrary post-install loading of the Maven/native SDK as an asset; choose a supported delivery architecture first.
- [ ] Preserve the existing KYC flow until this decision is made.
