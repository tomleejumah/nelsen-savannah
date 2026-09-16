#!/usr/bin/env node
/**
 * Purge LMS dummy/seed catalog from a live API (primary DB + RTDB mirror).
 * Usage: LMS_API_BASE=https://api.nelsen-savannah.co.ke node scripts/lms-purge-catalog.mjs
 */
import "dotenv/config";
import admin from "../src/config/firebase.js";

const BASE = (process.env.LMS_API_BASE || "https://api.nelsen-savannah.co.ke").replace(
  /\/$/,
  "",
);
const apiKey = "AIzaSyDaFYL-FE94ASTC01s-C7ZlwumPJmhzQD0";

async function token(uid) {
  const custom = await admin.auth().createCustomToken(uid);
  const { idToken } = await (
    await fetch(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: custom, returnSecureToken: true }),
      },
    )
  ).json();
  return idToken;
}

async function main() {
  await admin.database().ref("roles/lms-smoke-admin").set("Admin");
  const tok = await token("lms-smoke-admin");
  await fetch(`${BASE}/lms/me`, {
    headers: { Authorization: `Bearer ${tok}` },
  });

  const res = await fetch(`${BASE}/lms/admin/purge-catalog`, {
    method: "POST",
    headers: { Authorization: `Bearer ${tok}` },
  });
  const json = await res.json();
  console.log(res.status, JSON.stringify(json, null, 2));
  if (!res.ok || !json.ok) process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
