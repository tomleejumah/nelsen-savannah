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

- [ ] Show **Go to IDE** whenever the current course has IDE enabled.
- [ ] Do not depend on `lessonType === "code"`.
- [ ] Keep the button available throughout the course UI.
- [ ] Hide the button for courses without IDE support.

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

Do **not** rebuild Monaco/React Flow as a native Android editor.

Android should open the same Nelsen web IDE inside a WebView.

Target flow:

```text
Android Course
      │
      ├── Lessons
      │
      └── Go to IDE
              │
              ▼
        IDE Activity/WebView
              │
              ▼
        Nelsen Web Course IDE
```

### Android work

- [ ] Show **Go to IDE** for IDE-enabled courses.
- [ ] Create an IDE Activity/Fragment with WebView.
- [ ] Enable JavaScript and DOM storage.
- [ ] Load the course-specific IDE URL.
- [ ] Handle back navigation/fullscreen correctly.
- [ ] Handle loading/error states.
- [ ] Implement secure authentication/session handoff between Android Firebase authentication and the web IDE.
- [ ] Verify Monaco keyboard/input behaviour inside Android WebView.

Possible route:

```text
/learning/{trackId}/ide
```

or another course-specific route selected during implementation.

---

## Phase 3 — Video Lesson Controls

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

## Phase 4 — YouTube Live Learning

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
5. Add Android WebView IDE integration.
6. Solve Android → Web IDE authentication/session handoff.
7. Improve Web/Android video controls.
8. Add YouTube Live session creation/linking.
9. Add FCM live notifications.
10. Add Web/Android live viewers.
11. Add live session history and ended-live handling.

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
