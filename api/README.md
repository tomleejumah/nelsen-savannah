# Nelsen Savannah LMS API

## Running

From `api/`: `npm ci && npm start`. Firebase Admin credentials and
`.env` must be configured on the server; see `.env.example`.
Without `DATABASE_URL`, the LMS uses sql.js SQLite stored in
`LMS_DATA_DIR/lms.sqlite`. Catalog seeding is opt-in.

## Optional Redis school/course cache

Set `REDIS_URL=redis://127.0.0.1:6379/0` in the server `.env`, or
`rediss://...` for an encrypted remote connection, and restart the API.
The client uses bounded RESP2 TCP/TLS requests and fails open to the DB if
Redis is unavailable. There is a short circuit breaker during outages.

Cached: the public school directory and published per-school course *base*
(the same catalog metadata for everyone). Never cached: authentication,
member visibility decisions, a learner's enrollment/progress/likes, signed
lesson playback links, or mentor-specific course visibility. Redis entries
expire within 30–60 seconds; local hot copies are shorter. School changes,
course CMS changes, mentor assignments, and pricing updates bump the catalog
namespace version. Do **not** put learner-specific data into shared keys.

Check the cache protocol tests: `node --test scripts/test-redis-cache.mjs`.
Redis is optional for local and production development.

## SQLite snapshots, validation and recovery

Both GitHub self-hosted deployment workflows and
`scripts/manual-deploy.sh` run `node scripts/backup-lms.mjs "$APP_DIR"`
**before rsync**. The snapshot is written under `data/backups/`
(or `LMS_DATA_DIR/backups/`). Backups are private (0600); newest 14 are
kept unless `LMS_BACKUP_RETENTION` specifies another retention, up to 90.

After startup, deployments run `node scripts/check-lms-db.mjs "$APP_DIR"`.
It checks `PRAGMA integrity_check`, required LMS tables, and whether row
counts in schools, tracks, modules, lessons or enrollments unexpectedly fell
below the pre-deploy snapshot. Optional `LMS_MIN_TRACKS`,
`LMS_MIN_LESSONS`, etc. provide hard baselines. If a planned purge is
approved, `LMS_ALLOW_DATA_DECREASE=1` bypasses the relative count check
**only**; integrity validation still runs.

The sql.js file is persisted via fsynced temporary file and atomic rename,
not overwritten in place.

**Manual snapshot and check** (run on the server with the appropriate account):

```bash
cd /home/server/Apis/nelsen-savannah
node scripts/backup-lms.mjs "$PWD"
node scripts/check-lms-db.mjs "$PWD"
```

**Restore**: stop the API first; copy the current file to a quarantine
location, copy a verified snapshot from `data/backups/` to
`data/lms.sqlite`, secure its permissions, then restart the API and
rerun the integrity check. Never overwrite a live sql.js file while the
process runs. A SQLite snapshot does not back up PostgreSQL; use `pg_dump`
for PostgreSQL deployments.

These snapshots protect against deploy regressions but are on the same
server, **not** offsite disaster recovery. Copy encrypted snapshots to
separate storage according to your retention policy.

## Learner course outline

`GET /lms/tracks/:trackId/lessons` returns the authenticated course
outline with all modules and lessons in one HTTP request. Enrollment and
school access checks run before the query; no signed playback URLs are
returned. Clients fetch media URLs only when a lesson is actually opened.
If the primary database is unavailable, this endpoint responds 503 rather
than falling back to an unprotected source.

## CI tests

```bash
cd api
npm ci
node --test scripts/test-redis-cache.mjs
node --test scripts/test-db-safety.mjs
```
