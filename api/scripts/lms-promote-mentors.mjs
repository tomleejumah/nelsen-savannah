#!/usr/bin/env node
/**
 * Interactive (or CLI) LMS role upgrade via Firebase Auth email.
 *
 * Interactive:
 *   node scripts/lms-promote-mentors.mjs
 *   → prompts for emails (comma-separated) and role
 *
 * Non-interactive:
 *   node scripts/lms-promote-mentors.mjs "a@x.com, b@y.com" Mentor
 */
import "dotenv/config";
import readline from "node:readline/promises";
import { stdin as input, stdout as output } from "node:process";
import admin from "../src/config/firebase.js";
import { ROLES, normalizeRole } from "../src/constants/lmsRoles.js";
import { initLmsDb } from "../src/db/lmsDb.js";
import { setUserRole } from "../src/services/lmsMeService.js";

const ALLOWED = Object.values(ROLES).filter((r) => r !== "Admin");

function parseEmails(raw) {
  return String(raw || "")
    .split(/[,;\s]+/)
    .map((e) => e.trim().toLowerCase())
    .filter((e) => e.includes("@"));
}

async function promptInputs() {
  const argvEmails = process.argv[2];
  const argvRole = process.argv[3];

  if (argvEmails) {
    return {
      emails: parseEmails(argvEmails),
      role: normalizeRole(argvRole || "Mentor"),
    };
  }

  const rl = readline.createInterface({ input, output });
  try {
    const emailLine = await rl.question(
      "Emails to upgrade (comma-separated):\n> ",
    );
    const roleLine = await rl.question(
      `Role [${ALLOWED.join(" | ")}] (default Mentor):\n> `,
    );
    const roleRaw = roleLine.trim() || "Mentor";
    if (roleRaw && !ALLOWED.map((r) => r.toLowerCase()).includes(roleRaw.toLowerCase()) && roleRaw.toLowerCase() !== "admin") {
      console.error(`Unknown role "${roleRaw}". Use one of: ${ALLOWED.join(", ")}`);
      process.exit(1);
    }
    return {
      emails: parseEmails(emailLine),
      role: normalizeRole(roleRaw),
    };
  } finally {
    rl.close();
  }
}

const { emails, role } = await promptInputs();

if (!emails.length) {
  console.error("No valid emails provided.");
  process.exit(1);
}

console.log(`\nUpgrading ${emails.length} user(s) → ${role}\n`);
await initLmsDb();

let failed = 0;
for (const email of emails) {
  try {
    const user = await admin.auth().getUserByEmail(email);
    const providers = user.providerData.map((p) => p.providerId);
    await setUserRole(user.uid, role);
    await admin.database().ref(`lms/users/${user.uid}`).update({
      email: user.email || email,
      displayName: user.displayName || "",
      userRole: role,
      updatedAt: admin.database.ServerValue.TIMESTAMP,
    });
    console.log(
      `OK  ${email} → ${role} (${user.uid}) providers=${providers.join(",") || "none"}`,
    );
  } catch (e) {
    failed++;
    console.error(`FAIL ${email}: ${e.code || ""} ${e.message}`);
  }
}

console.log(failed ? `\nDone with ${failed} failure(s).` : "\nDone.");
process.exit(failed ? 1 : 0);
