#!/usr/bin/env node
/** L8 CI smoke — hit LMS health (no auth). */
const base = (
  process.env.LMS_SMOKE_BASE ||
  process.env.PUBLIC_BASE_URL ||
  "https://api.tommlyjumah.dev/nisisi-africa"
).replace(/\/$/, "");

const url = `${base}/lms/health`;
const res = await fetch(url);
const json = await res.json().catch(() => ({}));
if (!res.ok || json.ok !== true) {
  console.error("smoke failed", res.status, json);
  process.exit(1);
}
console.log("smoke ok", url, json.source || json.data?.engine);
