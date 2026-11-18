# Live streaming acceptance — release gate

Status: **not yet device-verified**. Automated checks cannot prove network handover, YouTube state or push delivery. Use a staging school/channel, two real Android devices and test accounts: SchoolAdmin host, assigned Mentor, enrolled Mentee and unrelated-school Mentee.

## 1. Network handover / reconnect
- Host starts broadcast on Wi-Fi; viewer joins. Switch host Wi-Fi → mobile data → Wi-Fi and record interruption, retry count, restored upstream ingest and any stale callback.
- Switch viewer network independently; confirm embedded playback recovers, chat/presence resume, and there is no duplicate attendance time for offline gaps.
- Repeat while host/viewer in PiP, screen sharing and background/foreground. Record device/OS, logs and pass/fail.

## 2. Notifications
- Register FCM tokens for SchoolAdmin, assigned Mentor and enrolled Mentee. Confirm live-start notification, deep link and expected recipients.
- Verify unrelated-school users, pending applicants and unenrolled Mentees receive no course-live push. Compare API [live-notify] aggregate recipients/sent/missingToken/failed counts; do not expose UIDs/tokens.

## 3. Media controls
- Host camera pause/resume and screen share; confirm viewer sees correct output and stream does not terminate.
- Enter/exit PiP on both devices; verify audio/video, chat and attendance behavior and Android permission handling.

## 4. Chat and attendance
- Send 60+ messages, paginate older history, scroll up while new messages arrive, verify stable scroll, ordering and no duplicates.
- Confirm chat disabled after ended; viewer leave/heartbeat stops. Compare server attendance unique viewers, current viewers and credited watch seconds with observed time.

## 5. State transitions and provider
- Schedule → go live → join → end → replay. Confirm API, both clients and YouTube broadcast state agree. Ensure ended broadcasts cannot be resurrected or continue accepting chat.
- Test reconnect after YouTube has ended broadcast: Android must stop retrying terminal sessions.

## 6. OAuth and termination
- Connect school YouTube channel via authorized OAuth; confirm channel identity, refresh-token rotation/refresh and restricted school access.
- End from host and independently confirm upstream YouTube broadcast actually ended; test failed provider response and subsequent reconciliation. Do not log credentials or stream keys.

## Evidence / release decision
Record each scenario's result, timestamp, Android versions, API commit, YouTube broadcast ID (not stream key), FCM aggregate logs, and redacted Android logcat. **Do not mark this milestone complete until all scenarios pass or known failures are documented and resolved.**
