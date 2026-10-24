# Implementation vs README benchmark — 2026-10-24

This comparison uses the **merged** `main` baseline (`19b5d101`, including PRs #78–#80), not screenshots or assertions from earlier conversation turns. The follow-up fixes in this audit are tracked on `fix/live-followup-stability`, with **no changes to APK installation, signing, versioning or publishing**.

**Benchmark standard:** `Implemented` = source and API/UI route exist; `CI verified` = branch build/test passed; `Field verified` = actual Android devices, school accounts, YouTube channel, FCM and VPS behavior tested. No code-only result counts as field verification.

| README / requirement | What merged code actually provides | Assessment | Follow-up |
| --- | --- | --- | --- |
| Single Firebase-auth LMS API shared by web + Android | `api/src/routes/lms.js`, Android Retrofit `LmsApiService.java`, `web/src/lib/lmsApi.ts` | Implemented; integrated API CI passed | Test authentication continuation and revoked accounts |
| Canonical roles / RTDB boundary | `normalizeRole()` accepts legacy aliases; `getMe()` now reads global privilege only from the primary roles table, with Mentee-only RTDB fallback on primary failure | Hardened on PR #81; API CI regression guard added | Audit Firebase RTDB security rules and legacy role writers, confirm production migration compatibility |
| School-specific staff permissions | `school_memberships` + `users_mirror.active_school_id` + `effectiveSchoolStaffRole()`; cross-tenant live API tests | Implemented in API; **Android was using device-wide global fallback** | Remove Android fallback, fetch UID-verified selected-school roles, add pure-Java tests |
| Student learning / assignments | Tracks, enrollment, lessons, assignments, late text submissions, status/feedback history | Implemented; functional device QA pending | Test learner enrollment/deadline/feedback across school switch |
| Catalog cache / deploy safety | `lmsRedisCache.js`, predeploy SQLite snapshot, integrity check, Postgres fail-closed startup | Implemented; API CI checked | Provision/benchmark Redis on VPS and test offsite restore |
| Live host / viewer / chat | Android RTMPS host reconnect and PiP, scoped notifications, attendance, cursor-paginated chat, embedded viewer; school OAuth | Implemented; PR #80 merged with API/Android CI green | Real two-device broadcast, network handover, FCM and replay confirmation still required |
| Events API | `GET /lms/events/public`, `POST /lms/events`, `PATCH /lms/events/:eventId/live-status`, reservations; legacy authenticated list/detail now reuse scoped Hub handlers instead of returning 501 | Original `api/docs/LMS.md` described events as entirely unbuilt — corrected in follow-up | Exercise list/detail and reservation routes with learner/school staff accounts |
| Android documentation | `android/README.md` was one-line placeholder | **Missing** | Document build/test, role refresh and device acceptance tests |
| Web documentation | `web/README.md` retained generic Lovable scaffold / `<repository-name>` commands | **Outdated** | Replace with current `web/` development commands and LMS API guidance |
| Bottom navigation rounded blur | `android/app/src/main/res/layout/main_bottom_bar.xml` and `layout-sw600dp/main_bottom_bar.xml`: outer clipped BlurView, inner transparent 22dp CardView | Implemented; visual fix user-confirmed | Only general release input/inset/performance QA remains, not blur implementation |
| Android profile editing | `EditProfileActivity.java` + `activity_edit_profile.xml`: profile text/photo editor, upload, authenticated canonical LMS update and cached-user refresh | Implemented in source | Cross-device and photo/permission field QA |
| Full Android school/admin UX | Web has dedicated school/admin consoles; Android maps several staff roles to mentor-like shell | **Incomplete by design** | Role-specific Android UI and tablet/device QA |
| Offline media and optional video sync | Cache and offline banners exist; selective offline PDF/video downloads and retention policy remain planned | Not complete | Keep pending in `NELSEN_LMS_ROADMAP.md` |
| Full populated-course deletion | Only dependency-free unused tracks can be deleted safely | Limited feature, intentional | Design archival/rollback before enabling destructive cleanup |
| PostgreSQL cutover | PostgreSQL adapter present; live migration, schema consistency and offsite backups not verified | Not field verified | Staged migration rehearsal plus rollback |

## Follow-up improvements after the baseline

- Updated documentation from the source implementation, without changing the installer/signing pipeline.
- Added UID-bound Android role validation, a fail-closed Mentee UI pending server confirmation and Java unit tests.
- Replaced the legacy authenticated event GET 501 stubs with the same school/course-aware handlers used by the newer public catalogue endpoints. The service authorization is reused; device/API integration still requires acceptance testing.

## Verified against GitHub

- PR #78 (glass navigation / learner/catalog/data safety) and PR #80 (live stability / scoped roles) are **merged**.
- On `main` at `19b5d101`, [API CI run #40118472604](https://github.com/tomleejumah/nelsen-savannah/actions/runs/40118472604) and [Android CI run #40118472601](https://github.com/tomleejumah/nelsen-savannah/actions/runs/40118472601) passed on 2026-10-24.
- The PR #81 head `05d3c7e` subsequently passed API, Web CI, Android CI and Android Role Tests on October 24, 2026.
- Bottom-nav blur is user-confirmed fixed; this does not automatically verify unrelated touch/inset cases.
- **No live-device or server-side restore/Redis proof** was available from those CI checks.

## Next delivery gates

1. UID-scoped Android staff-role code and tests are implemented; field-test SchoolAdmin at school A vs Mentee at school B and account switching.
2. Root, API, Android and web README repairs have landed on PR #81; keep capability checklists synchronized with the implemented code.
3. Review legacy RTDB reconciliation rules and role migration **without automatically promoting any membership**.
4. Do two-account video/notification/attendance QA on real devices and capture missing logs; do not declare the original mid-stream failure resolved from CI alone.
5. Keep APK updater and release signing untouched; retain one follow-up branch and one review PR, with no automatic merge.
