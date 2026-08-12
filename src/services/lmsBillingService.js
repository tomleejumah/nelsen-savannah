/**
 * L7 — seats / license checkout (M-Pesa + card stubs) + entitlements.
 */

import crypto from "crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import {
  isSchoolAdmin,
  isSuperAdmin,
  DEFAULT_SCHOOL_ID,
} from "../constants/lmsRoles.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { getActorSchoolId } from "./lmsSchoolService.js";

const SEAT_PRICE_KES = Number(process.env.LMS_SEAT_PRICE_KES || 500);

function newPaymentId() {
  return `pay_${crypto.randomBytes(8).toString("hex")}`;
}

async function assertBillingAccess(actorUid, schoolId) {
  const role = await loadUserRole(actorUid);
  if (isSuperAdmin(role)) return;
  if (!isSchoolAdmin(role)) {
    const err = new Error("School admin required");
    err.status = 403;
    throw err;
  }
  const mine = await getActorSchoolId(actorUid);
  if (mine !== schoolId) {
    const err = new Error("Cannot bill another school");
    err.status = 403;
    throw err;
  }
}

export async function getSchoolBilling(actorUid, schoolId) {
  await assertBillingAccess(actorUid, schoolId);
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (!school) {
    const err = new Error("School not found");
    err.status = 404;
    throw err;
  }
  const payments = await dbAll(
    `SELECT * FROM payments WHERE school_id = ?
     ORDER BY created_at DESC LIMIT 50`,
    [schoolId],
  );
  return {
    source: getPrimaryEngine(),
    data: {
      schoolId,
      seatsTotal: Number(school.seats_total || 0),
      seatsUsed: Number(school.seats_used || 0),
      seatsAvailable: Math.max(
        0,
        Number(school.seats_total || 0) - Number(school.seats_used || 0),
      ),
      seatPriceKes: SEAT_PRICE_KES,
      payments: payments.map((p) => ({
        id: p.payment_id,
        method: p.method,
        seats: Number(p.seats),
        amountKes: Number(p.amount_kes),
        status: p.status,
        phone: p.phone || null,
        checkoutRef: p.checkout_ref || null,
        createdAt: Number(p.created_at),
        updatedAt: Number(p.updated_at),
      })),
    },
  };
}

/**
 * Start checkout. M-Pesa: STK stub (pending until webhook). Card: auto-complete in demo.
 */
export async function startCheckout(actorUid, body = {}) {
  const schoolId = body.schoolId || (await getActorSchoolId(actorUid));
  await assertBillingAccess(actorUid, schoolId);
  const seats = Math.max(1, Math.min(500, Number(body.seats) || 1));
  const method = body.method === "mpesa" ? "mpesa" : "card";
  const phone = body.phone || null;
  if (method === "mpesa" && !phone) {
    const err = new Error("phone required for M-Pesa");
    err.status = 400;
    throw err;
  }
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (!school) {
    const err = new Error("School not found");
    err.status = 404;
    throw err;
  }

  const now = Date.now();
  const paymentId = newPaymentId();
  const amountKes = seats * SEAT_PRICE_KES;
  const checkoutRef = `${method}_${crypto.randomBytes(4).toString("hex")}`;
  const status = method === "card" ? "paid" : "pending";

  await dbRun(
    `INSERT INTO payments (
      payment_id, school_id, method, seats, amount_kes, status, phone,
      checkout_ref, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      paymentId,
      schoolId,
      method,
      seats,
      amountKes,
      status,
      phone,
      checkoutRef,
      now,
      now,
    ],
  );

  if (status === "paid") {
    await applySeats(schoolId, seats);
  }

  return {
    source: getPrimaryEngine(),
    data: {
      payment: {
        id: paymentId,
        schoolId,
        method,
        seats,
        amountKes,
        status,
        phone,
        checkoutRef,
        /** Demo: call webhook with this id to complete M-Pesa */
        webhookHint:
          method === "mpesa"
            ? `POST /lms/billing/webhook { paymentId, status: "paid" }`
            : null,
      },
      seatsTotal: Number(
        (await dbGet("SELECT seats_total FROM schools WHERE school_id = ?", [
          schoolId,
        ]))?.seats_total || 0,
      ),
    },
  };
}

async function applySeats(schoolId, seats) {
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  const next = Number(school?.seats_total || 0) + Number(seats);
  await dbRun(
    "UPDATE schools SET seats_total = ?, updated_at = ? WHERE school_id = ?",
    [next, Date.now(), schoolId],
  );
}

export async function billingWebhook(body = {}) {
  const paymentId = body.paymentId || body.CheckoutRequestID;
  const status = body.status === "failed" ? "failed" : "paid";
  if (!paymentId) {
    const err = new Error("paymentId required");
    err.status = 400;
    throw err;
  }
  const row = await dbGet("SELECT * FROM payments WHERE payment_id = ?", [
    paymentId,
  ]);
  if (!row) {
    const err = new Error("Payment not found");
    err.status = 404;
    throw err;
  }
  if (row.status === "paid") {
    return {
      source: getPrimaryEngine(),
      data: { paymentId, status: "paid", alreadyApplied: true },
    };
  }
  const now = Date.now();
  await dbRun(
    "UPDATE payments SET status = ?, updated_at = ? WHERE payment_id = ?",
    [status, now, paymentId],
  );
  if (status === "paid") {
    await applySeats(row.school_id, row.seats);
  }
  return {
    source: getPrimaryEngine(),
    data: { paymentId, status, seatsAdded: status === "paid" ? Number(row.seats) : 0 },
  };
}

/** Block enroll when school is out of seats (used > total). */
export async function assertSeatAvailable(uid) {
  const user = await dbGet(
    "SELECT school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  const schoolId = user?.school_id || DEFAULT_SCHOOL_ID;
  const school = await dbGet("SELECT * FROM schools WHERE school_id = ?", [
    schoolId,
  ]);
  if (!school) return schoolId;
  const total = Number(school.seats_total ?? 100);
  const used = Number(school.seats_used || 0);
  if (used >= total) {
    const err = new Error("No seats left — school admin must purchase licenses");
    err.status = 402;
    throw err;
  }
  return schoolId;
}

export async function incrementSeatUsed(schoolId) {
  await dbRun(
    `UPDATE schools SET seats_used = COALESCE(seats_used, 0) + 1, updated_at = ?
     WHERE school_id = ?`,
    [Date.now(), schoolId],
  );
}
