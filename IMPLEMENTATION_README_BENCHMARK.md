# Implementation vs README benchmark — 2026-10-24

This comparison uses the **merged** `main` baseline (`19b5d101`, including PRs #78–#80), not screenshots or assertions from earlier conversation turns. The follow-up fixes in this audit are tracked on `fix/live-followup-stability`, with **no changes to APK installation, signing, versioning or publishing**.

**Benchmark standard:** `Implemented` = source and API/UI route exist; `CI verified` = branch build/test passed; `Field verified` = actual Android devices, school accounts, YouTube channel, FCM and VPS behavior tested. No code-only result counts as field verification.

| README / requirement | What merged code actually provides | Assessment | Follow-up |
| --- | --- | --- | --- |
| Single Firebase-auth LMS API shared by web + Android | `api/src/routes/lms.js`, Android Retrofit `LmsApiService.java`, `web/src/lib/lmsApi.ts` | Implemented; integrated API CI passed | Test authentication continuation and revoked accounts |
| Canonical roles and global RTDB/DB reconciliation | `normalizeRole()` accepts case-insensitive legacy aliases; `getMe()` can copy a differing RTDB global role into the LMS DB | **README inaccurate:** claimed wrong case becomes Mentee | Correct root README; audit Firebase RTDB write rules before changing reconciliation |
| School-specific staff permissions | `school_memberships` + `users_mirror.active_school_id` + `effectiveSchoolStaffRole()`; cross-tenant live API tests | Implemented in API; **Android was using device-wide global fallback** | Remove Android fallback, fetch UID-verified selected-school roles, add pure-Java tests |
| Student learning / assignments | Tracks, enrollment, lessons, assignments, late text submissions, status/feedback history | Implemented; functional device QA pending | Test learner enrollment/deadline/feedback across school switch |
| Catalog cache / deploy safety | `lmsRedisCache.js`, predeploy SQLite snapshot, integrity check, Postgres fail-closed startup | Implemented; API CI checked | Provision/benchmark Redis on VPS and test offsite restore |
| Live host / viewer / chat | Android RTMPS host reconnect and PiP, scoped notifications, attendance, cursor-paginated chat, embedded viewer; school OAuth | Implemented; PR #80 merged with API/Android CI green | Real two-device broadcast, network handover, FCM and replay confirmation still required |
| Events API | `GET /lms/events/public`, `POST /lms/events`, `PATCH /lms/events/:eventId/live-status`, reservations; **legacy `GET /lms/events` and `GET /lms/events/:eventId` still return a TODO/501** | `api/docs/LMS.md` says Events API entirely unbuilt — **partly outdated** | Document actual live/public routes and remaining compatibility TODO |
| Android documentation | `android/README.md` was one-line placeholder | **Missing** | Document build/test, role refresh and device acceptance tests |
| Web documentation | `web/README.md` retained generic Lovable scaffold / `<repository-name>` commands | **Outdated** | Replace with current `web/` development commands and LMS API guidance |
| Full Android school/admin UX | Web has dedicated school/admin consoles; Android maps several staff roles to mentor-like shell | **Incomplete by design** | Role-specific Android UI and tablet/device QA |
| Offline media and optional video sync | Cache and offline banners exist; selective offline PDF/video downloads and retention policy remain planned | Not complete | Keep pending in `NELSEN_LMS_ROADMAP.md` |
| Full populated-course deletion | Only dependency-free unused tracks can be deleted safely | Limited feature, intentional | Design archival/rollback before enabling destructive cleanup |
| PostgreSQL cutover | PostgreSQL adapter present; live migration, schema consistency and offsite backups not verified | Not field verified | Staged migration rehearsal plus rollback |

## Verified against GitHub

- PR #78 (glass navigation / learner/catalog/data safety) and PR #80 (live stability / scoped roles) are **merged**.
- On `main` at `19b5d101`, [API CI run #40118472604](https://github.com/tomleejumah/nelsen-savannah/actions/runs/40118472604) and [Android CI run #40118472601](https://github.com/tomleejumah/nelsen-savannah/actions/runs/40118472601) passed on 2026-10-24.
- **No live-device or server-side restore/Redis proof** was available from those CI checks.

## Next delivery gates

1. Eliminate cross-account and wrong-school Android staff UI fallback; test that a SchoolAdmin in school A is displayed as Mentee in school B while verifying the LMS response.
2. Update the root, API, Android and web READMEs so operators and developers do not follow stale privilege or route instructions.
3. Review legacy RTDB reconciliation rules and role migration **without automatically promoting any membership**.
4. Do two-account video/notification/attendance QA on real devices and capture missing logs; do not declare the original mid-stream failure resolved from CI alone.
5. Keep APK updater and release signing untouched; retain one follow-up branch and one review PR, with no automatic merge.
