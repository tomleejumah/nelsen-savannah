import crypto from "crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";

const id = () => `evt_${crypto.randomBytes(8).toString("hex")}`;

function parseJson(raw, fallback = null) {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function rowToEvent(row, seatsTaken = 0) {
  return {
    eventId: row.event_id,
    title: row.title,
    date: Number(row.date_ms),
    startTime: row.start_time || "",
    endTime: row.end_time || "",
    eventType: row.event_type || "event",
    mentorId: row.mentor_id || "",
    menteeId: row.mentee_id || "",
    mentorName: row.mentor_name || "",
    menteeName: row.mentee_name || "",
    status: Number(row.status) || 0,
    description: row.description || null,
    mode: row.mode || "physical",
    location: row.location || "",
    meetingLink: row.meeting_link || "",
    participants: parseJson(row.participants_json, null),
    program: row.program || "",
    seats: Number(row.seats) || 0,
    seatsTaken,
    price: row.price || "",
    facilitators: parseJson(row.facilitators_json, []),
    isPublic: Boolean(row.is_public),
    createdBy: row.created_by || "",
    createdAt: Number(row.created_at),
    updatedAt: Number(row.updated_at),
  };
}

async function seatsTakenMap(eventIds = null) {
  let rows;
  if (eventIds?.length) {
    const placeholders = eventIds.map(() => "?").join(",");
    rows = await dbAll(
      `SELECT event_id, COUNT(*) AS taken FROM event_reservations WHERE event_id IN (${placeholders}) GROUP BY event_id`,
      eventIds,
    );
  } else {
    rows = await dbAll(
      `SELECT event_id, COUNT(*) AS taken FROM event_reservations GROUP BY event_id`,
    );
  }
  const map = {};
  for (const row of rows) {
    map[row.event_id] = Number(row.taken) || 0;
  }
  return map;
}

export async function getHubEvent(eventId) {
  const row = await dbGet(`SELECT * FROM hub_events WHERE event_id = ?`, [eventId]);
  if (!row) return null;
  const taken = await seatsTakenMap([eventId]);
  return rowToEvent(row, taken[eventId] || 0);
}

/**
 * Mentor/Admin delete — removes hub event and its reservations.
 */
export async function deleteHubEvent(eventId) {
  const existing = await getHubEvent(eventId);
  if (!existing) {
    const err = new Error("Unknown event");
    err.status = 404;
    throw err;
  }
  // Announcements from Firebase are not in hub_events — only API hub rows.
  if (existing.eventType === "announcement") {
    const err = new Error("Announcements cannot be deleted here");
    err.status = 400;
    throw err;
  }
  await dbRun(`DELETE FROM event_reservations WHERE event_id = ?`, [eventId]);
  await dbRun(`DELETE FROM hub_events WHERE event_id = ?`, [eventId]);
  return {
    deleted: true,
    eventId,
    source: getPrimaryEngine() || "sqlite",
  };
}

export async function listPublicHubEvents() {
  const now = Date.now();
  const rows = await dbAll(
    `SELECT * FROM hub_events WHERE is_public = 1 AND date_ms >= ? ORDER BY date_ms ASC`,
    [now],
  );
  const ids = rows.map((r) => r.event_id);
  const taken = await seatsTakenMap(ids);
  return {
    events: rows.map((row) => rowToEvent(row, taken[row.event_id] || 0)),
    source: getPrimaryEngine() || "sqlite",
  };
}

export async function createHubEvent(actor, body = {}) {
  const title = String(body.title || "").trim();
  if (!title) {
    const err = new Error("Title is required");
    err.status = 400;
    throw err;
  }

  const dateMs = Number(body.date ?? body.dateMs);
  if (!Number.isFinite(dateMs) || dateMs <= 0) {
    const err = new Error("Valid date is required");
    err.status = 400;
    throw err;
  }

  const mode = body.mode === "online" ? "online" : "physical";
  const location = String(body.location || "").trim();
  const meetingLink = String(body.meetingLink || body.meeting_link || "").trim();
  if (mode === "online" && !meetingLink) {
    const err = new Error("Meeting link is required for online events");
    err.status = 400;
    throw err;
  }
  if (mode === "physical" && !location) {
    const err = new Error("Location is required for in-person events");
    err.status = 400;
    throw err;
  }

  const seats = Math.max(0, Number(body.seats) || 0);
  const eventId = id();
  const now = Date.now();
  const uid = actor?.uid || "";
  const displayName = actor?.displayName || actor?.email || "";

  await dbRun(
    `INSERT INTO hub_events (
      event_id, title, date_ms, start_time, end_time, event_type,
      mentor_id, mentee_id, mentor_name, mentee_name, status,
      description, mode, location, meeting_link, participants_json,
      program, seats, price, is_public, facilitators_json,
      created_by, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      eventId,
      title,
      dateMs,
      String(body.startTime || body.start_time || "").trim(),
      String(body.endTime || body.end_time || "").trim(),
      String(body.eventType || body.event_type || "event").trim() || "event",
      String(body.mentorId || uid).trim(),
      String(body.menteeId || uid).trim(),
      String(body.mentorName || displayName).trim(),
      String(body.menteeName || displayName).trim(),
      Number(body.status) || 0,
      body.description ? String(body.description).trim() : null,
      mode,
      location,
      meetingLink,
      body.participants ? JSON.stringify(body.participants) : null,
      String(body.program || "").trim(),
      seats,
      String(body.price || "").trim(),
      body.isPublic === false || body.is_public === 0 ? 0 : 1,
      body.facilitators ? JSON.stringify(body.facilitators) : null,
      uid,
      now,
      now,
    ],
  );

  return getHubEvent(eventId);
}

const SEED_EVENTS = [
  {
    event_id: "evt-intake-aug-2026",
    title: "Innovation Hub — First Intake",
    date_ms: Date.parse("2026-08-29T09:00:00+03:00"),
    start_time: "9:00 AM",
    end_time: "4:00 PM",
    description:
      "Opening intake for Nelsen Savannah Innovation Hub — meet facilitators, tour the programmes, and reserve your free seat for the first cohort.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    program: "All programmes",
    seats: 100,
    price: "Free",
    facilitators_json: JSON.stringify(["Tomee Juma", "Evans Nyairo"]),
    mentor_name: "Tomee Juma & Evans Nyairo",
  },
  {
    event_id: "evt-future-safari-open",
    title: "Future Safari: Innovation Open Day",
    date_ms: Date.parse("2026-09-12T09:00:00+03:00"),
    start_time: "9:00 AM",
    end_time: "1:00 PM",
    description:
      "Digital literacy, design thinking, and emerging tech awareness — explore the innovation cycle.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    program: "Future Safari",
    seats: 60,
    price: "Free",
  },
  {
    event_id: "evt-robotics-lab",
    title: "Robotics & Automation Lab: Build Session",
    date_ms: Date.parse("2026-09-24T17:00:00+03:00"),
    start_time: "5:00 PM",
    end_time: "8:00 PM",
    description:
      "Hands-on Arduino, sensors, and actuators — introductory build for new robotics cohort members.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    program: "Savannah Robotics & Automation Lab",
    seats: 40,
    price: "Free",
  },
  {
    event_id: "evt-data-ai-capstone",
    title: "Data & AI Academy: Capstone Showcase",
    date_ms: Date.parse("2026-10-04T14:00:00+03:00"),
    start_time: "2:00 PM",
    end_time: "5:00 PM",
    description:
      "Learners present data and AI projects — evidence-led decisions and responsible use of emerging tools.",
    mode: "online",
    location: "",
    meeting_link: "https://meet.google.com/",
    program: "Savannah Data & AI Academy",
    seats: 80,
    price: "Free",
  },
  {
    event_id: "evt-kijiji-demo-day",
    title: "Kijiji Hub: Community Demo Day",
    date_ms: Date.parse("2026-10-18T10:00:00+03:00"),
    start_time: "10:00 AM",
    end_time: "4:00 PM",
    description:
      "Community innovation pitches — from problem identification through MVPs, business models, and launch.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    program: "Kijiji Hub",
    seats: 60,
    price: "Free",
  },
  {
    event_id: "evt-creative-lab",
    title: "Savannah Creative Lab: Portfolio Review",
    date_ms: Date.parse("2026-11-07T15:00:00+03:00"),
    start_time: "3:00 PM",
    end_time: "6:00 PM",
    description:
      "Creative outputs, storytelling, and design critique — build a portfolio that communicates your work clearly.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    program: "Savannah Creative Lab",
    seats: 40,
    price: "Free",
  },
];

export async function seedHubEvents({ force = false } = {}) {
  const existing = await dbGet("SELECT COUNT(*) AS c FROM hub_events");
  const count = Number(existing?.c ?? 0);
  if (count > 0 && !force) {
    return { seeded: false, count, source: getPrimaryEngine() || "sqlite" };
  }
  if (force) {
    await dbRun("DELETE FROM hub_events");
  }
  const now = Date.now();
  for (const e of SEED_EVENTS) {
    await dbRun(
      `INSERT OR IGNORE INTO hub_events (
        event_id, title, date_ms, start_time, end_time, event_type,
        mentor_id, mentee_id, mentor_name, mentee_name, status,
        description, mode, location, meeting_link, participants_json,
        program, seats, price, is_public, facilitators_json,
        created_by, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, 'event', '', '', ?, '', 0, ?, ?, ?, ?, NULL, ?, ?, ?, 1, ?, 'seed', ?, ?)`,
      [
        e.event_id,
        e.title,
        e.date_ms,
        e.start_time,
        e.end_time,
        e.mentor_name || "Nelsen Savannah Innovation Hub",
        e.description || null,
        e.mode,
        e.location || "",
        e.meeting_link || "",
        e.program || "",
        e.seats || 0,
        e.price || "",
        e.facilitators_json || null,
        now,
        now,
      ],
    );
  }
  const after = await dbGet("SELECT COUNT(*) AS c FROM hub_events");
  return {
    seeded: true,
    count: Number(after?.c ?? 0),
    source: getPrimaryEngine() || "sqlite",
  };
}
