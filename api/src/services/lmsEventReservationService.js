import crypto from "crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { getEventCap } from "../data/siteEvents.js";
import { sendInquiryEmail } from "./inquiryEmail.js";

const id = () => `rsv_${crypto.randomBytes(8).toString("hex")}`;

function normalizeEmail(email) {
  return String(email || "")
    .trim()
    .toLowerCase();
}

function normalizePhone(phone) {
  return String(phone || "").trim();
}

export async function getReservationCounts() {
  const rows = await dbAll(
    `SELECT event_id, COUNT(*) AS taken FROM event_reservations GROUP BY event_id`,
  );
  const counts = {};
  for (const row of rows) {
    counts[row.event_id] = Number(row.taken) || 0;
  }
  return { counts, source: getPrimaryEngine() || "sqlite" };
}

export async function reserveEventSeat(eventId, body = {}) {
  const cap = getEventCap(eventId);
  if (!cap) {
    const err = new Error("Unknown event");
    err.status = 404;
    throw err;
  }

  const fullName = String(body.fullName || body.name || "").trim();
  const email = normalizeEmail(body.email);
  const phone = normalizePhone(body.phone);
  const program = String(body.program || "").trim();

  if (!fullName || fullName.length < 2) {
    const err = new Error("Full name is required");
    err.status = 400;
    throw err;
  }
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    const err = new Error("Valid email is required");
    err.status = 400;
    throw err;
  }

  const existing = await dbGet(
    `SELECT reservation_id FROM event_reservations WHERE event_id = ? AND email = ?`,
    [eventId, email],
  );
  if (existing) {
    const err = new Error("This email already has a seat reserved for this event");
    err.status = 409;
    err.code = "ALREADY_RESERVED";
    throw err;
  }

  const countRow = await dbGet(
    `SELECT COUNT(*) AS taken FROM event_reservations WHERE event_id = ?`,
    [eventId],
  );
  const taken = Number(countRow?.taken) || 0;
  if (taken >= cap.seats) {
    const err = new Error("No seats left for this event");
    err.status = 409;
    err.code = "SOLD_OUT";
    throw err;
  }

  const reservationId = id();
  const now = Date.now();
  await dbRun(
    `INSERT INTO event_reservations (
      reservation_id, event_id, full_name, email, phone, program, created_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?)`,
    [reservationId, eventId, fullName, email, phone || null, program || null, now],
  );

  try {
    await sendInquiryEmail({
      desk: "contact",
      subject: `Seat reserved — ${cap.title}`,
      replyTo: email,
      lines: [
        `Event: ${cap.title}`,
        `Event ID: ${eventId}`,
        `Reservation: ${reservationId}`,
        `Full name: ${fullName}`,
        `Email: ${email}`,
        `Phone: ${phone || "—"}`,
        `Programme: ${program || "—"}`,
        `Price: ${cap.price}`,
      ],
    });
  } catch (err) {
    console.warn("[event-reserve] email notify failed:", err.message);
  }

  const newTaken = taken + 1;
  return {
    reservationId,
    eventId,
    seatsTaken: newTaken,
    seatsLeft: Math.max(0, cap.seats - newTaken),
    seatsTotal: cap.seats,
    source: getPrimaryEngine() || "sqlite",
  };
}
