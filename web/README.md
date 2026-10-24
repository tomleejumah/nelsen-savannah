# Nelsen Savannah Web LMS

TanStack Router + Vite + React frontend of the `nelsen-savannah` monorepo.
The UI shares Firebase authentication and the `/lms` API with Android.

## Local development

```bash
cd web
cp .env.example .env.local
npm ci
npm run dev
```

Edit `.env.local` using the **existing** Firebase configuration for the
project and set `VITE_LMS_API_BASE=http://localhost:5002` for a local API or
`https://api.nelsen-savannah.co.ke` for the authorized hosted API.

Available npm scripts: `dev`, `build`, `preview`, `lint`, and `format`.
Before committing UI changes, run `npm run build` (and `npm run lint` if
changing shared UI code). The PDF worker is copied by the package's
prebuild/predev hooks.

## Role-based workspaces

- `/learning`: Mentee tracks, modules, lessons and coursework
- `/teach`: assigned Mentor coursework and learner progress
- `/school`: SchoolAdmin-only operations for the **active** school
- `/admin`: platform SuperAdmin operations
- `/events`: event catalogue, live/replay cards and reservations

Do not infer a school's permissions from the global Firebase/legacy role.
The API returns `activeSchoolRole` for the selected active membership; the
server checks ownership/enrollment on mutations.

## Live sessions and sharing

Canonical live links use `https://nelsen-savannah.co.ke/live/:eventId` and
fall back to the web event view when Android is unavailable. Live hosting
currently uses school-linked YouTube channels, RTMPS, and embedded playback;
the custom in-app chat/attendance is served by the LMS API.

See [implementation status and device verification](../IMPLEMENTATION_README_BENCHMARK.md).
For API configuration and database safeguards, see [API README](../api/README.md).

**Production deployment:** the repository's `.github/workflows/web.yml`
controls web build/rsync. Do not push directly to protected `main` or
modify the Android release workflow as part of UI-only work.
