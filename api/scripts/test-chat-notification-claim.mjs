import test from "node:test";
import assert from "node:assert/strict";
import { planClaim, claimChatNotification, finishChatNotification, CLAIM_LEASE_MS, MAX_CLAIM_ATTEMPTS }
  from "../src/services/chatNotificationClaim.js";

// Minimal in-memory Firestore double: one doc, serial transactions.
function fake() {
  let doc = null;
  const ref = {};
  const firestore = { async runTransaction(fn) {
    return fn({
      get: async () => ({ exists: doc !== null, data: () => doc }),
      create: (_r, d) => { if (doc) throw new Error("exists"); doc = { ...d }; },
      update: (_r, d) => { if (!doc) throw new Error("missing"); doc = { ...doc, ...d }; },
    });
  } };
  return { firestore, ref, get: () => doc, set: (d) => { doc = d; } };
}
const R = ["a", "b", "c"];
const ok = { status: "fulfilled", value: {} };
const bad = { status: "rejected", reason: new Error("x") };

test("planClaim: fresh, complete, legacy, in-progress", () => {
  assert.equal(planClaim(null, R, 0).action, "claim");
  assert.equal(planClaim({ status: "complete" }, R, 0).action, "duplicate");
  assert.equal(planClaim({ createdAt: 1 }, R, 0).action, "duplicate"); // legacy
  assert.equal(planClaim({ status: "processing", leaseUntil: 100 }, R, 50).action, "in_progress");
});

test("planClaim: expired lease and partial resume with only undelivered", () => {
  const p = planClaim({ status: "processing", leaseUntil: 10, attempts: 1, deliveredTo: ["a"] }, R, 50);
  assert.deepEqual(p.pending, ["b", "c"]);
  assert.equal(p.attempts, 2);
  assert.equal(planClaim({ status: "partial", attempts: MAX_CLAIM_ATTEMPTS }, R, 0).action, "exhausted");
});

test("partial failure then retry delivers only the failed recipient", async () => {
  const f = fake();
  const c1 = await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: { m: 1 }, recipients: R, now: 1000 });
  assert.deepEqual(c1.pending, R);
  const status1 = await finishChatNotification({ firestore: f.firestore, claimRef: f.ref, pending: c1.pending, outcomes: [ok, bad, ok] });
  assert.equal(status1, "partial");
  assert.deepEqual(f.get().deliveredTo, ["a", "c"]);

  const c2 = await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: R, now: 2000 });
  assert.equal(c2.claimed, true); assert.equal(c2.retry, true);
  assert.deepEqual(c2.pending, ["b"]);
  assert.equal(await finishChatNotification({ firestore: f.firestore, claimRef: f.ref, pending: c2.pending, outcomes: [ok] }), "complete");

  const c3 = await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: R, now: 3000 });
  assert.equal(c3.claimed, false); assert.equal(c3.reason, "duplicate");
});

test("concurrent request during lease is rejected; after lease it can resume", async () => {
  const f = fake();
  await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: R, now: 0 });
  const dup = await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: R, now: 10 });
  assert.equal(dup.reason, "in_progress");
  const later = await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: R, now: CLAIM_LEASE_MS + 1 });
  assert.equal(later.claimed, true);
});

test("retry never re-adds a recipient who was removed (suspended) meanwhile", async () => {
  const f = fake();
  await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: R, now: 0 });
  await finishChatNotification({ firestore: f.firestore, claimRef: f.ref, pending: R, outcomes: [ok, bad, bad] });
  const c = await claimChatNotification({ firestore: f.firestore, claimRef: f.ref, base: {}, recipients: ["a", "c"], now: 5 });
  assert.deepEqual(c.pending, ["c"]);
});
