#!/usr/bin/env node
/**
 * Promote Firebase users to Mentor by email (Auth exists → RTDB roles + LMS DB).
 * Usage: node scripts/lms-promote-mentors.mjs
 */
import "dotenv/config";
import admin from "../src/config/firebase.js";
import { initLmsDb } from "../src/db/lmsDb.js";
import { setUserRole } from "../src/services/lmsMeService.js";

const emails = process.argv.slice(2).length
  ? process.argv.slice(2)
  : ["maxnjeru9@gmail.com", "shirleyambatsa@gmail.com"];

await initLmsDb();

let failed = 0;
for (const email of emails) {
  const trimmed = email.trim().toLowerCase();
  try {
    const user = await admin.auth().getUserByEmail(trimmed);
    const providers = user.providerData.map((p) => p.providerId);
    await setUserRole(user.uid, "Mentor");
    await admin.database().ref(`roles/${user.uid}`).set("Mentor");
    await admin.database().ref(`lms/users/${user.uid}`).update({
      email: user.email || trimmed,
      displayName: user.displayName || "",
      userRole: "Mentor",
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });
    console.log(`OK  ${trimmed} → Mentor (${user.uid}) providers=${providers.join(",") || "none"}`);
  } catch (e) {
    failed++;
    console.error(`FAIL ${trimmed}: ${e.code || ""} ${e.message}`);
  }
}
process.exit(failed ? 1 : 0);
