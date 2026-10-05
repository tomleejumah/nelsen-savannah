import crypto from "crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { notifyLiveStarted } from "./lmsLiveNotificationService.js";

const id = () => `evt_${crypto.randomBytes(8).toString("hex")}`;

const LIVE_STATUS = Object.freeze({ scheduled: 0, live: 1, ended: 2 });

function liveStatusName(status) {
  if (Number(status) === LIVE_STATUS.live) return "live";
  if (Number(status) === LIVE_STATUS.ended) return "ended";
  return "scheduled";
}

function parseLiveStatus(value, fallback = LIVE_STATUS.scheduled) {
  if (typeof value === "string") {
    const key = value.trim().toLowerCase();
    if (Object.prototype.hasOwnProperty.call(LIVE_STATUS, key)) return LIVE_STATUS[key];
  }
  const n = Number(value);
  return [0, 1, 2].includes(n) ? n : fallback;
}

function isYoutubeUrl(value) {
  try {
    const url = new URL(String(value || "").trim());
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    return host === "youtube.com" || host.endsWith(".youtube.com") || host === "youtu.be";
  } catch {
    return false;
  }
}

function parseJson(raw, fallback = null) {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

const LIVE_AUDIENCE_SCOPES = new Set(["course", "school", "platform"]);

async function actorRole(uid) {
  const row = await dbGet("SELECT role FROM roles WHERE uid = ?", [uid]);
  return String(row?.role || "");
}

async function actorSchool(uid) {
  const user = await dbGet(
    "SELECT active_school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  if (user?.active_school_id) {
    const active = await dbGet(
      `SELECT school_id FROM school_memberships
       WHERE uid = ? AND school_id = ? AND status = 'active' LIMIT 1`,
      [uid, user.active_school_id],
    );
    if (active?.school_id) return String(active.school_id);
  }
  const membership = await dbGet(
    `SELECT school_id FROM school_memberships
     WHERE uid = ? AND status = 'active'
     ORDER BY updated_at DESC
     LIMIT 1`,
    [uid],
  );
  return String(membership?.school_id || "").trim();
}

async function hasActiveSchoolMembership(uid, schoolId) {
  if (!uid || !schoolId) return false;
  const row = await dbGet(
    `SELECT 1 AS ok FROM school_memberships
     WHERE uid = ? AND school_id = ? AND status = 'active'
     LIMIT 1`,
    [uid, schoolId],
  );
  return Boolean(row);
}

export async function resolveLiveAudience(actor, body) {
  const uid = String(actor?.uid || "");
  const role = await actorRole(uid);
  const isPlatformAdmin = role === "Admin" || role === "SuperAdmin";

  let scope = String(
    body.audienceScope ?? body.audience_scope ?? body.scope ?? "",
  ).trim().toLowerCase();
  let schoolId = String(body.schoolId ?? body.school_id ?? "").trim();
  let trackId = String(body.trackId ?? body.track_id ?? "").trim();

  const ownSchool = await actorSchool(uid);

  if (!scope) {
    if (trackId) scope = "course";
    else if (schoolId || ownSchool) {
      scope = "school";
      if (!schoolId) schoolId = ownSchool;
    } else if (isPlatformAdmin) {
      scope = "platform";
    } else {
      const err = new Error("Live audience must be a course or school");
      err.status = 400;
      throw err;
    }
  }

  if (!LIVE_AUDIENCE_SCOPES.has(scope)) {
    const err = new Error("Live audience must be course, school, or platform");
    err.status = 400;
    throw err;
  }

  if (scope === "platform") {
    if (!isPlatformAdmin) {
      const err = new Error("Only Admin or SuperAdmin can notify the whole platform");
      err.status = 403;
      throw err;
    }
    return { audienceScope: scope, schoolId: "", trackId: "" };
  }

  if (scope === "course") {
    if (!trackId) {
      const err = new Error("trackId is required for a course live");
      err.status = 400;
      throw err;
    }
    const track = await dbGet(
      "SELECT track_id, school_id FROM tracks WHERE track_id = ?",
      [trackId],
    );
    if (!track) {
      const err = new Error("Unknown course");
      err.status = 400;
      throw err;
    }
    schoolId = schoolId || String(track.school_id || "").trim();
  }

  if (scope === "school" && !schoolId) {
    schoolId = ownSchool;
  }
  if (scope === "school" && !schoolId) {
    const err = new Error("schoolId is required for a school live");
    err.status = 400;
    throw err;
  }

  if (!isPlatformAdmin && schoolId) {
    const allowed = ownSchool === schoolId || await hasActiveSchoolMembership(uid, schoolId);
    if (!allowed) {
      const err = new Error("You are not an active member of that school");
      err.status = 403;
      throw err;
    }
  }

  if (!isPlatformAdmin && role === "Mentor" && scope === "course") {
    const assigned = await isTrackMentor(uid, trackId);
    if (!assigned) {
      const err = new Error("Mentor must be assigned to this course to host its live session");
      err.status = 403;
      throw err;
    }
  }

  return { audienceScope: scope, schoolId, trackId };
}

async function hasActiveTrackEnrollment(uid, trackId) {
  if (!uid || !trackId) return false;
  const row = await dbGet(
    `SELECT 1 AS ok FROM enrollments
     WHERE uid = ? AND track_id = ?
       AND lower(COALESCE(status, 'in_progress')) NOT IN ('cancelled', 'dropped', 'suspended')
     LIMIT 1`,
    [uid, trackId],
  );
  return Boolean(row);
}

async function isTrackMentor(uid, trackId) {
  if (!uid || !trackId) return false;
  const row = await dbGet(
    "SELECT 1 AS ok FROM track_mentors WHERE uid = ? AND track_id = ? LIMIT 1",
    [uid, trackId],
  );
  return Boolean(row);
}

export async function canViewHubEvent(event, uid = null) {
  if (!event || !event.isPublic) return false;
  if (event.eventType !== "live" || !event.audienceScope) return true;
  if (!uid) return false;

  const viewerUid = String(uid);
  if (event.createdBy && String(event.createdBy) === viewerUid) return true;

  const role = await actorRole(viewerUid);
  if (role === "Admin" || role === "SuperAdmin") return true;

  if (event.audienceScope === "platform") return true;

  if (event.audienceScope === "school") {
    if (!event.schoolId) return false;
    return (await actorSchool(viewerUid)) === event.schoolId
      || await hasActiveSchoolMembership(viewerUid, event.schoolId);
  }

  if (event.audienceScope === "course") {
    if (!event.trackId) return false;
    if (await hasActiveTrackEnrollment(viewerUid, event.trackId)) return true;
    if (await isTrackMentor(viewerUid, event.trackId)) return true;
    if (role === "SchoolAdmin" && event.schoolId) {
      return (await actorSchool(viewerUid)) === event.schoolId
        || await hasActiveSchoolMembership(viewerUid, event.schoolId);
    }
    return false;
  }

  return false;
}

async function notifyLiveIfNeeded(eventId) {
  const event = await getHubEvent(eventId);
  if (!event || event.eventType !== "live" || event.status !== LIVE_STATUS.live || event.liveNotifiedAt) {
    return event;
  }

  const notifiedAt = Date.now();
  const claim = await dbRun(
    `UPDATE hub_events
     SET live_notified_at = ?, updated_at = ?
     WHERE event_id = ? AND live_notified_at IS NULL`,
    [notifiedAt, notifiedAt, eventId],
  );
  const changed = Number(claim?.changes ?? claim?.rowCount ?? 0);
  if (changed <= 0) return getHubEvent(eventId);

  const claimedEvent = {
    ...event,
    liveNotifiedAt: notifiedAt,
    updatedAt: notifiedAt,
  };

  try {
    const result = await notifyLiveStarted(claimedEvent);
    console.log("[live-notify]", eventId, result);
  } catch (err) {
    console.error("[live-notify] failed", eventId, err);
    await dbRun(
      "UPDATE hub_events SET live_notified_at = NULL WHERE event_id = ? AND live_notified_at = ?",
      [eventId, notifiedAt],
    );
  }

  return getHubEvent(eventId);
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
    liveStatus: row.event_type === "live" ? liveStatusName(row.status) : null,
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
    audienceScope: row.audience_scope || null,
    schoolId: row.school_id || "",
    trackId: row.track_id || "",
    liveNotifiedAt: row.live_notified_at ? Number(row.live_notified_at) : null,
    liveAvailability: row.event_type === "live"
      ? String(row.live_availability || "unknown")
      : null,
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

export async function listPublicHubEvents({ filter = "upcoming", uid = null } = {}) {
  const now = Date.now();
  let rows;
  if (filter === "past") {
    rows = await dbAll(
      `SELECT * FROM hub_events WHERE is_public = 1 AND date_ms < ? ORDER BY date_ms DESC`,
      [now],
    );
  } else if (filter === "all") {
    rows = await dbAll(
      `SELECT * FROM hub_events WHERE is_public = 1 ORDER BY date_ms ASC`,
    );
  } else {
    rows = await dbAll(
      `SELECT * FROM hub_events WHERE is_public = 1 AND date_ms >= ? ORDER BY date_ms ASC`,
      [now],
    );
  }
  const candidates = rows.map((row) => rowToEvent(row, 0));
  const visibility = await Promise.all(
    candidates.map((event) => canViewHubEvent(event, uid)),
  );
  const visibleRows = rows.filter((_row, index) => visibility[index]);

  const ids = visibleRows.map((r) => r.event_id);
  const taken = await seatsTakenMap(ids);
  let reservedIds = new Set();
  if (uid) {
    const rsv = await dbAll(
      `SELECT event_id FROM event_reservations WHERE uid = ?`,
      [uid],
    );
    reservedIds = new Set(rsv.map((r) => r.event_id));
  }
  return {
    events: visibleRows.map((row) => ({
      ...rowToEvent(row, taken[row.event_id] || 0),
      reservedByMe: reservedIds.has(row.event_id),
    })),
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

  const eventType = String(body.eventType || body.event_type || "event").trim().toLowerCase() || "event";
  const isLive = eventType === "live";
  const mode = isLive ? "online" : body.mode === "online" ? "online" : "physical";
  const location = String(body.location || "").trim();
  const meetingLink = String(body.meetingLink || body.meeting_link || "").trim();
  if (mode === "online" && !meetingLink) {
    const err = new Error(isLive ? "YouTube Live link is required" : "Meeting link is required for online events");
    err.status = 400;
    throw err;
  }
  if (isLive && !isYoutubeUrl(meetingLink)) {
    const err = new Error("Live sessions must use a YouTube link");
    err.status = 400;
    throw err;
  }
  if (mode === "physical" && !location) {
    const err = new Error("Location is required for in-person events");
    err.status = 400;
    throw err;
  }

  const seats = isLive ? 0 : Math.max(0, Number(body.seats) || 0);
  const status = isLive
    ? parseLiveStatus(body.liveStatus ?? body.status)
    : Number(body.status) || 0;
  const liveAudience = isLive
    ? await resolveLiveAudience(actor, body)
    : { audienceScope: null, schoolId: "", trackId: "" };
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
      audience_scope, school_id, track_id, live_notified_at,
      created_by, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      eventId,
      title,
      dateMs,
      String(body.startTime || body.start_time || "").trim(),
      String(body.endTime || body.end_time || "").trim(),
      eventType,
      String(body.mentorId || uid).trim(),
      String(body.menteeId || uid).trim(),
      String(body.mentorName || displayName).trim(),
      String(body.menteeName || displayName).trim(),
      status,
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
      liveAudience.audienceScope,
      liveAudience.schoolId,
      liveAudience.trackId,
      null,
      uid,
      now,
      now,
    ],
  );

  if (isLive && status === LIVE_STATUS.live) {
    return notifyLiveIfNeeded(eventId);
  }
  return getHubEvent(eventId);
}

export async function updateHubLiveStatus(eventId, body = {}, actorUid = null) {
  const existing = await getHubEvent(eventId);
  if (!existing) {
    const err = new Error("Unknown event");
    err.status = 404;
    throw err;
  }
  if (existing.eventType !== "live") {
    const err = new Error("Event is not a live session");
    err.status = 400;
    throw err;
  }

  if (actorUid) {
    const role = await actorRole(actorUid);
    const platformAdmin = role === "Admin" || role === "SuperAdmin";
    const owner = String(existing.createdBy || "") === String(actorUid);
    let schoolAdmin = false;
    if (role === "SchoolAdmin" && existing.schoolId) {
      schoolAdmin = await hasActiveSchoolMembership(actorUid, existing.schoolId);
    }
    if (!platformAdmin && !owner && !schoolAdmin) {
      const err = new Error("Only the live host or an active school admin can change this session");
      err.status = 403;
      throw err;
    }
  }

  const status = parseLiveStatus(body.liveStatus ?? body.status, existing.status);
  let meetingLink = existing.meetingLink;
  if (body.meetingLink !== undefined || body.meeting_link !== undefined) {
    meetingLink = String(body.meetingLink ?? body.meeting_link ?? "").trim();
    if (!isYoutubeUrl(meetingLink)) {
      const err = new Error("Live sessions must use a YouTube link");
      err.status = 400;
      throw err;
    }
  }

  await dbRun(
    `UPDATE hub_events SET status = ?, meeting_link = ?, updated_at = ? WHERE event_id = ?`,
    [status, meetingLink, Date.now(), eventId],
  );
  if (status === LIVE_STATUS.live) {
    return notifyLiveIfNeeded(eventId);
  }
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
        audience_scope, school_id, track_id, live_notified_at,
        created_by, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, 'event', '', '', ?, '', 0, ?, ?, ?, ?, NULL, ?, ?, ?, 1, ?, NULL, '', '', NULL, 'seed', ?, ?)`,
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
