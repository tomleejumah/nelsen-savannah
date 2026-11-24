// Exactly-once-per-recipient chat notification claims with retry after a
// partial fanout failure. State lives on one Firestore doc per message:
//   status: processing | partial | complete | failed
//   deliveredTo: uids whose notification record was written
// A retry only re-sends to recipients not yet in deliveredTo.

export const CLAIM_LEASE_MS = 60_000;
export const MAX_CLAIM_ATTEMPTS = 5;

/**
 * Decide what a request should do given the existing claim doc (or null).
 * Pure function: easy to test, no I/O.
 */
export function planClaim(existing, recipients, now) {
  if (!existing) {
    return { action: "claim", attempts: 1, pending: [...recipients], retry: false };
  }
  const status = existing.status;
  // Legacy claims (pre-retry) have no status: treat as complete, never resend.
  if (!status || status === "complete") return { action: "duplicate" };
  if (status === "failed") return { action: "duplicate" };
  if (status === "processing" && Number(existing.leaseUntil) > now) {
    return { action: "in_progress" };
  }
  const attempts = Number(existing.attempts || 1) + 1;
  if (attempts > MAX_CLAIM_ATTEMPTS) return { action: "exhausted" };
  const done = new Set(Array.isArray(existing.deliveredTo) ? existing.deliveredTo : []);
  const pending = recipients.filter((id) => !done.has(id));
  return { action: "claim", attempts, pending, retry: true };
}

/**
 * Transactionally claim (or re-claim) a message for delivery.
 * Returns { claimed, pending, retry, reason? }.
 */
export async function claimChatNotification({ firestore, claimRef, base, recipients, now = Date.now() }) {
  return firestore.runTransaction(async (tx) => {
    const snap = await tx.get(claimRef);
    const plan = planClaim(snap.exists ? snap.data() : null, recipients, now);
    if (plan.action === "duplicate") return { claimed: false, reason: "duplicate", pending: [] };
    if (plan.action === "in_progress") return { claimed: false, reason: "in_progress", pending: [] };
    if (plan.action === "exhausted") {
      tx.update(claimRef, { status: "failed", leaseUntil: 0 });
      return { claimed: false, reason: "exhausted", pending: [] };
    }
    if (!snap.exists) {
      tx.create(claimRef, {
        ...base, status: "processing", attempts: plan.attempts,
        leaseUntil: now + CLAIM_LEASE_MS, deliveredTo: [],
      });
    } else {
      tx.update(claimRef, { status: "processing", attempts: plan.attempts, leaseUntil: now + CLAIM_LEASE_MS });
    }
    return { claimed: true, pending: plan.pending, retry: plan.retry };
  });
}

/**
 * Record which recipients succeeded. `outcomes` is parallel to `pending`
 * and holds Promise.allSettled results. Returns the final status.
 */
export async function finishChatNotification({ firestore, claimRef, pending, outcomes, FieldValue }) {
  const ok = pending.filter((_, i) => outcomes[i]?.status === "fulfilled");
  const allDone = ok.length === pending.length;
  const status = allDone ? "complete" : "partial";
  await firestore.runTransaction(async (tx) => {
    const snap = await tx.get(claimRef);
    const prev = snap.exists && Array.isArray(snap.data().deliveredTo) ? snap.data().deliveredTo : [];
    tx.update(claimRef, {
      status, leaseUntil: 0,
      deliveredTo: [...new Set([...prev, ...ok])],
      ...(FieldValue ? { updatedAt: FieldValue.serverTimestamp() } : {}),
    });
  });
  return status;
}
