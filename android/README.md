# Nelsen Savannah Android

Android member app in the `nelsen-savannah` monorepo. APK package is
`com.app.nisisiafrica` (legacy namespace, intentionally unchanged).
The API and website share the same Firebase project and LMS backend.

## Local build

Use Android Studio with JDK 17 and a configured Android SDK, or:

```bash
cd android
cp ../web/.env.example /tmp/nelsen-web-env-example  # optional web reference only
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Configure `app/google-services.json` from the authorized Firebase project
outside version control. Release signing secrets belong in GitHub Actions, not
in the repository. **Do not change application ID, signing keys, update URLs
or the APK installer as part of normal LMS UI work.**

## Authentication, roles and school switching

- Firebase Authentication identifies the current UID. `GET /lms/me` is the
  client bootstrap and returns `role` (global identity),
  `activeSchoolId` and `activeSchoolRole` (specific active membership).
- Android displays SchoolAdmin/Mentor controls **only from a current,
  UID-verified LMS response**. The legacy device-wide `USER_ROLE` preference
  is not permission to display staff controls.
- On activity resume (including login, user/school switches) Android briefly
  defaults to Mentee UI until the API verifies the active-school role. If
  unavailable, remain unprivileged; the server always checks actual
  permissions before mutations.
- A user can be SchoolAdmin at one school and Mentee at another. A platform
  SuperAdmin is distinct from school staff. For role semantics see
  [the root README](../README.md).

## Course and live flows

- Courses/lessons are fetched from the LMS API, with enrollment and active
  school checks. Assignments include late text submission and mentor feedback.
- Hosts stream to their school's YouTube channel via RTMPS. They can toggle
  camera/mic, share the screen and reconnect transient stream interruptions.
- Viewers use embedded playback, PiP, attendance and paginated chat.
  A production two-device test is required before declaring streaming stable.
- The canonical App Links domain is `nelsen-savannah.co.ke`; verify
  `.well-known/assetlinks.json` on a physical Android device before release.

## Testing

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

The role-policy unit tests cover SchoolAdmin at school A versus Mentee at school
B, unknown/offline roles, and verified platform SuperAdmin. Android release CI
builds the APK, but **does not** replace a physical-device test.

Check [the current implementation roadmap](../NELSEN_LMS_ROADMAP.md) for
phone/tablet layouts, deep links, offline state, audio/video, permission and
notification acceptance cases. The
[README benchmark](../IMPLEMENTATION_README_BENCHMARK.md) records known doc/
implementation differences and outstanding QA.
