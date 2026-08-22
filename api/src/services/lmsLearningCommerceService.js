import crypto from "crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { isSuperAdmin, normalizeRole } from "../constants/lmsRoles.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { getActorSchoolId } from "./lmsSchoolService.js";
import { upsertUserFromToken } from "./lmsMeService.js";
import { patchLessonProgress } from "./lmsEnrollmentService.js";
import { maybeIssueCertificate } from "./lmsCertificateService.js";

const id = (prefix) => `${prefix}_${crypto.randomBytes(8).toString("hex")}`;
const source = () => getPrimaryEngine();

function required(value, name) {
  if (value === undefined || value === null || value === "") {
    const err = new Error(`${name} required`);
    err.status = 400;
    throw err;
  }
  return value;
}

async function assertSchoolAuthor(actorUid, schoolId) {
  const role = normalizeRole(await loadUserRole(actorUid));
  if (isSuperAdmin(role)) return;
  if (role !== "SchoolAdmin" && role !== "Mentor") {
    const err = new Error("Mentor, SchoolAdmin, or SuperAdmin required");
    err.status = 403;
    throw err;
  }
  if ((await getActorSchoolId(actorUid)) !== schoolId) {
    const err = new Error("Cannot author for another school");
    err.status = 403;
    throw err;
  }
}

async function assertTrackInSchool(trackId, schoolId) {
  const track = await dbGet("SELECT * FROM tracks WHERE track_id = ?", [trackId]);
  if (!track) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }
  if ((track.school_id || "nelsen-digital") !== schoolId) {
    const err = new Error("Track belongs to another school");
    err.status = 403;
    throw err;
  }
  return track;
}

export async function getTrackPrice(trackId) {
  const row = await dbGet(
    `SELECT currency, amount_minor, active FROM track_pricing
     WHERE track_id = ?`,
    [trackId],
  );
  const amountMinor = row?.active ? Number(row.amount_minor) : 0;
  return {
    isPaid: amountMinor > 0,
    amountMinor,
    currency: row?.currency || "USD",
  };
}

export async function assertEnrollmentEntitlement(uid, trackId) {
  const price = await getTrackPrice(trackId);
  if (!price.isPaid) return price;
  const entitlement = await dbGet(
    `SELECT purchase_id FROM entitlements
     WHERE uid = ? AND track_id = ? AND status = 'active'
       AND (expires_at IS NULL OR expires_at > ?)`,
    [uid, trackId, Date.now()],
  );
  if (entitlement) return { ...price, entitled: true };
  const err = new Error("Payment required");
  err.status = 402;
  err.details = {
    code: "TRACK_PAYMENT_REQUIRED",
    trackId,
    price,
    payment: {
      provider: "demo",
      checkoutEndpoint: "/lms/checkout",
      method: "POST",
    },
  };
  throw err;
}

export async function checkout(profile, body = {}) {
  const trackId = required(body.trackId, "trackId");
  await upsertUserFromToken(profile);
  const track = await dbGet(
    "SELECT track_id, school_id FROM tracks WHERE track_id = ? AND published = 1",
    [trackId],
  );
  if (!track) {
    const err = new Error("Track not found");
    err.status = 404;
    throw err;
  }
  const price = await getTrackPrice(trackId);
  if (!price.isPaid) {
    const err = new Error("Track is free; enroll directly");
    err.status = 400;
    throw err;
  }
  const current = await dbGet(
    `SELECT e.purchase_id, p.created_at FROM entitlements e
     JOIN purchases p ON p.purchase_id = e.purchase_id
     WHERE e.uid = ? AND e.track_id = ? AND e.status = 'active'`,
    [profile.uid, trackId],
  );
  if (current) {
    return {
      source: source(),
      data: {
        purchaseId: current.purchase_id,
        trackId,
        status: "paid",
        entitlement: { status: "active" },
        alreadyOwned: true,
      },
    };
  }
  const purchaseId = id("pur");
  const now = Date.now();
  await dbRun(
    `INSERT INTO purchases (
      purchase_id, uid, school_id, track_id, currency, amount_minor,
      status, provider, provider_reference, created_at, paid_at
    ) VALUES (?, ?, ?, ?, ?, ?, 'paid', 'demo', ?, ?, ?)`,
    [
      purchaseId,
      profile.uid,
      track.school_id || "nelsen-digital",
      trackId,
      price.currency,
      price.amountMinor,
      body.paymentReference || purchaseId,
      now,
      now,
    ],
  );
  await dbRun(
    `INSERT INTO entitlements
      (uid, track_id, purchase_id, status, granted_at, expires_at)
     VALUES (?, ?, ?, 'active', ?, NULL)
     ON CONFLICT(uid, track_id) DO UPDATE SET
       purchase_id = excluded.purchase_id,
       status = 'active',
       granted_at = excluded.granted_at,
       expires_at = NULL`,
    [profile.uid, trackId, purchaseId, now],
  );
  return {
    source: source(),
    data: {
      purchaseId,
      trackId,
      status: "paid",
      amountMinor: price.amountMinor,
      currency: price.currency,
      provider: "demo",
      paidAt: now,
      entitlement: { status: "active", grantedAt: now },
    },
  };
}

export async function listMyPurchases(uid) {
  const rows = await dbAll(
    `SELECT p.*, t.title AS course_title,
            e.status AS entitlement_status, e.granted_at, e.expires_at
     FROM purchases p
     JOIN tracks t ON t.track_id = p.track_id
     LEFT JOIN entitlements e
       ON e.purchase_id = p.purchase_id AND e.uid = p.uid
     WHERE p.uid = ? ORDER BY p.created_at DESC`,
    [uid],
  );
  return {
    source: source(),
    data: {
      purchases: rows.map((row) => ({
        purchaseId: row.purchase_id,
        trackId: row.track_id,
        courseTitle: row.course_title || row.track_id,
        schoolId: row.school_id,
        amountMinor: Number(row.amount_minor),
        currency: row.currency,
        status: row.status,
        provider: row.provider,
        providerReference: row.provider_reference || null,
        createdAt: Number(row.created_at),
        paidAt: row.paid_at ? Number(row.paid_at) : null,
        entitlement: row.entitlement_status
          ? {
              status: row.entitlement_status,
              grantedAt: Number(row.granted_at),
              expiresAt: row.expires_at ? Number(row.expires_at) : null,
            }
          : null,
      })),
    },
  };
}

export async function setTrackPricing(actorUid, schoolId, trackId, body = {}) {
  await assertSchoolAuthor(actorUid, schoolId);
  await assertTrackInSchool(trackId, schoolId);
  const amountMinor = Number(required(body.amountMinor, "amountMinor"));
  if (!Number.isInteger(amountMinor) || amountMinor < 0) {
    const err = new Error("amountMinor must be a non-negative integer");
    err.status = 400;
    throw err;
  }
  const currency = String(body.currency || "USD").trim().toUpperCase();
  const now = Date.now();
  const existing = await dbGet(
    "SELECT track_id FROM track_pricing WHERE track_id = ?",
    [trackId],
  );
  if (existing) {
    await dbRun(
      `UPDATE track_pricing SET school_id = ?, currency = ?, amount_minor = ?,
       active = ?, updated_by = ?, updated_at = ? WHERE track_id = ?`,
      [
        schoolId,
        currency,
        amountMinor,
        body.active === false ? 0 : 1,
        actorUid,
        now,
        trackId,
      ],
    );
  } else {
    await dbRun(
      `INSERT INTO track_pricing
       (track_id, school_id, currency, amount_minor, active, updated_by, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)`,
      [
        trackId,
        schoolId,
        currency,
        amountMinor,
        body.active === false ? 0 : 1,
        actorUid,
        now,
      ],
    );
  }
  return {
    source: source(),
    data: { trackId, price: await getTrackPrice(trackId) },
  };
}

export async function createCohort(actorUid, schoolId, body = {}) {
  await assertSchoolAuthor(actorUid, schoolId);
  const cohortId = body.cohortId || id("coh");
  const name = required(String(body.name || "").trim(), "name");
  const now = Date.now();
  await dbRun(
    `INSERT INTO cohorts
     (cohort_id, school_id, name, status, starts_at, ends_at, created_by, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      cohortId,
      schoolId,
      name,
      body.status || "active",
      body.startsAt || null,
      body.endsAt || null,
      actorUid,
      now,
      now,
    ],
  );
  return {
    source: source(),
    data: {
      cohort: {
        cohortId,
        schoolId,
        name,
        status: body.status || "active",
        startsAt: body.startsAt || null,
        endsAt: body.endsAt || null,
      },
    },
  };
}

export async function listSchoolCohorts(actorUid, schoolId) {
  await assertSchoolAuthor(actorUid, schoolId);
  const rows = await dbAll(
    `SELECT c.*,
            (SELECT COUNT(*) FROM cohort_members m WHERE m.cohort_id = c.cohort_id) AS member_count,
            (SELECT COUNT(*) FROM cohort_track_runs r WHERE r.cohort_id = c.cohort_id) AS run_count
     FROM cohorts c
     WHERE c.school_id = ?
     ORDER BY c.created_at DESC`,
    [schoolId],
  );
  return {
    source: source(),
    data: {
      cohorts: rows.map((row) => ({
        cohortId: row.cohort_id,
        schoolId: row.school_id,
        name: row.name,
        status: row.status,
        startsAt: row.starts_at ? Number(row.starts_at) : null,
        endsAt: row.ends_at ? Number(row.ends_at) : null,
        memberCount: Number(row.member_count || 0),
        runCount: Number(row.run_count || 0),
      })),
    },
  };
}

export async function addCohortMember(actorUid, schoolId, cohortId, body = {}) {
  await assertSchoolAuthor(actorUid, schoolId);
  const cohort = await dbGet(
    "SELECT cohort_id FROM cohorts WHERE cohort_id = ? AND school_id = ?",
    [cohortId, schoolId],
  );
  if (!cohort) {
    const err = new Error("Cohort not found");
    err.status = 404;
    throw err;
  }
  const uid = required(body.uid, "uid");
  const member = await dbGet("SELECT school_id FROM users_mirror WHERE uid = ?", [
    uid,
  ]);
  if (!member || (member.school_id || "nelsen-digital") !== schoolId) {
    const err = new Error("Member must belong to this school");
    err.status = 400;
    throw err;
  }
  await dbRun(
    `INSERT INTO cohort_members (cohort_id, uid, role, joined_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(cohort_id, uid) DO UPDATE SET role = excluded.role`,
    [cohortId, uid, body.role || "learner", Date.now()],
  );
  return {
    source: source(),
    data: { member: { cohortId, uid, role: body.role || "learner" } },
  };
}

export async function createCohortTrackRun(
  actorUid,
  schoolId,
  cohortId,
  body = {},
) {
  await assertSchoolAuthor(actorUid, schoolId);
  const cohort = await dbGet(
    "SELECT cohort_id FROM cohorts WHERE cohort_id = ? AND school_id = ?",
    [cohortId, schoolId],
  );
  if (!cohort) {
    const err = new Error("Cohort not found");
    err.status = 404;
    throw err;
  }
  const trackId = required(body.trackId, "trackId");
  await assertTrackInSchool(trackId, schoolId);
  const runId = body.runId || id("run");
  await dbRun(
    `INSERT INTO cohort_track_runs
     (run_id, cohort_id, track_id, starts_at, ends_at, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    [
      runId,
      cohortId,
      trackId,
      body.startsAt || null,
      body.endsAt || null,
      actorUid,
      Date.now(),
    ],
  );
  return {
    source: source(),
    data: {
      run: {
        runId,
        cohortId,
        trackId,
        startsAt: body.startsAt || null,
        endsAt: body.endsAt || null,
      },
    },
  };
}

export async function createMilestone(actorUid, schoolId, runId, body = {}) {
  await assertSchoolAuthor(actorUid, schoolId);
  const run = await dbGet(
    `SELECT r.track_id FROM cohort_track_runs r
     JOIN cohorts c ON c.cohort_id = r.cohort_id
     WHERE r.run_id = ? AND c.school_id = ?`,
    [runId, schoolId],
  );
  if (!run) {
    const err = new Error("Cohort track run not found");
    err.status = 404;
    throw err;
  }
  const lessonId = required(body.lessonId, "lessonId");
  const lesson = await dbGet(
    "SELECT track_id FROM lessons WHERE lesson_id = ?",
    [lessonId],
  );
  if (!lesson || lesson.track_id !== run.track_id) {
    const err = new Error("Lesson must belong to the run track");
    err.status = 400;
    throw err;
  }
  const milestoneId = body.milestoneId || id("mil");
  const now = Date.now();
  await dbRun(
    `INSERT INTO milestones
     (milestone_id, run_id, lesson_id, title, release_at, due_at,
      requires_previous_completion, sort_order, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      milestoneId,
      runId,
      lessonId,
      body.title || null,
      Number(required(body.releaseAt, "releaseAt")),
      body.dueAt ? Number(body.dueAt) : null,
      body.requiresPreviousCompletion === false ? 0 : 1,
      Number(body.order || 0),
      now,
      now,
    ],
  );
  return {
    source: source(),
    data: {
      milestone: {
        milestoneId,
        runId,
        lessonId,
        title: body.title || null,
        releaseAt: Number(body.releaseAt),
        dueAt: body.dueAt ? Number(body.dueAt) : null,
        requiresPreviousCompletion: body.requiresPreviousCompletion !== false,
        order: Number(body.order || 0),
      },
    },
  };
}

export async function getTrackMilestones(uid, trackId) {
  const run = await dbGet(
    `SELECT r.run_id, r.cohort_id FROM cohort_track_runs r
     JOIN cohort_members cm ON cm.cohort_id = r.cohort_id
     WHERE cm.uid = ? AND r.track_id = ?
     ORDER BY r.created_at DESC LIMIT 1`,
    [uid, trackId],
  );
  if (!run) return { runId: null, cohortId: null, milestones: [] };
  const rows = await dbAll(
    `SELECT m.*, p.lesson_percent
     FROM milestones m
     LEFT JOIN progress p ON p.lesson_id = m.lesson_id AND p.uid = ?
     WHERE m.run_id = ? ORDER BY m.sort_order ASC, m.release_at ASC`,
    [uid, run.run_id],
  );
  let previousComplete = true;
  const now = Date.now();
  const milestones = rows.map((row) => {
    const released = Number(row.release_at) <= now;
    const requiresPrevious = Boolean(row.requires_previous_completion);
    const available = released && (!requiresPrevious || previousComplete);
    const completed = Number(row.lesson_percent || 0) >= 80;
    const dueAt = row.due_at ? Number(row.due_at) : null;
    const result = {
      milestoneId: row.milestone_id,
      lessonId: row.lesson_id,
      title: row.title || null,
      releaseAt: Number(row.release_at),
      dueAt,
      order: Number(row.sort_order),
      requiresPreviousCompletion: requiresPrevious,
      released,
      previousComplete,
      available,
      completed,
      overdue: Boolean(dueAt && dueAt < now && !completed),
      lockedReason: !released
        ? "release_date"
        : requiresPrevious && !previousComplete
          ? "previous_milestone_incomplete"
          : null,
    };
    previousComplete = completed;
    return result;
  });
  return { runId: run.run_id, cohortId: run.cohort_id, milestones };
}

export async function assertLessonMilestoneAvailable(uid, lesson) {
  const context = await getTrackMilestones(uid, lesson.track_id);
  const milestone = context.milestones.find(
    (item) => item.lessonId === lesson.lesson_id,
  );
  if (!milestone || milestone.available) return milestone;
  const err = new Error("Milestone is locked");
  err.status = 403;
  err.details = {
    code: "MILESTONE_LOCKED",
    trackId: lesson.track_id,
    lessonId: lesson.lesson_id,
    milestone,
  };
  throw err;
}

export async function authorQuiz(actorUid, schoolId, lessonId, body = {}) {
  await assertSchoolAuthor(actorUid, schoolId);
  const lesson = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
    lessonId,
  ]);
  if (!lesson) {
    const err = new Error("Lesson not found");
    err.status = 404;
    throw err;
  }
  await assertTrackInSchool(lesson.track_id, schoolId);
  const prompt = required(String(body.prompt || "").trim(), "prompt");
  const options = Array.isArray(body.options) ? body.options : [];
  if (options.length < 2 || options.some((option) => !option?.id || !option?.text)) {
    const err = new Error("options must contain at least two {id, text} choices");
    err.status = 400;
    throw err;
  }
  const correctOptionId = required(body.correctOptionId, "correctOptionId");
  if (!options.some((option) => option.id === correctOptionId)) {
    const err = new Error("correctOptionId must match an option");
    err.status = 400;
    throw err;
  }
  const runId = String(body.runId || "").trim() || null;
  if (runId) {
    const run = await dbGet(
      `SELECT r.run_id FROM cohort_track_runs r
       JOIN cohorts c ON c.cohort_id = r.cohort_id
       WHERE r.run_id = ? AND r.track_id = ? AND c.school_id = ?`,
      [runId, lesson.track_id, schoolId],
    );
    if (!run) {
      const err = new Error("Cohort run does not match this lesson and school");
      err.status = 400;
      throw err;
    }
    const current = await dbGet(
      `SELECT MAX(version) AS version FROM cohort_quiz_versions
       WHERE run_id = ? AND lesson_id = ?`,
      [runId, lessonId],
    );
    const version = Number(current?.version || 0) + 1;
    const now = Date.now();
    await dbRun(
      `INSERT INTO cohort_quiz_versions
       (run_id, lesson_id, version, prompt, options_json, correct_option_id,
        passing_score, created_by, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        runId,
        lessonId,
        version,
        prompt,
        JSON.stringify(options),
        correctOptionId,
        Number(body.passingScore ?? 80),
        actorUid,
        now,
      ],
    );
    await dbRun(
      "UPDATE lessons SET has_quiz = 1, updated_at = ? WHERE lesson_id = ?",
      [now, lessonId],
    );
    return {
      source: source(),
      data: {
        quiz: { runId, lessonId, version, prompt, options, scope: "cohort" },
      },
    };
  }
  const existing = await dbGet("SELECT * FROM quizzes WHERE lesson_id = ?", [
    lessonId,
  ]);
  const quizId = existing?.quiz_id || id("quiz");
  const version = Number(existing?.current_version || 0) + 1;
  const now = Date.now();
  if (!existing) {
    await dbRun(
      `INSERT INTO quizzes
       (quiz_id, lesson_id, current_version, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?)`,
      [quizId, lessonId, version, now, now],
    );
  }
  await dbRun(
    `INSERT INTO quiz_versions
     (quiz_id, version, prompt, options_json, correct_option_id, passing_score, created_by, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      quizId,
      version,
      prompt,
      JSON.stringify(options),
      correctOptionId,
      Number(body.passingScore ?? 80),
      actorUid,
      now,
    ],
  );
  if (existing) {
    await dbRun(
      "UPDATE quizzes SET current_version = ?, updated_at = ? WHERE quiz_id = ?",
      [version, now, quizId],
    );
  }
  await dbRun(
    "UPDATE lessons SET has_quiz = 1, updated_at = ? WHERE lesson_id = ?",
    [now, lessonId],
  );
  return {
    source: source(),
    data: {
      quiz: { quizId, lessonId, version, prompt, options, scope: "base" },
    },
  };
}

async function getLearnerRun(uid, trackId) {
  return dbGet(
    `SELECT r.run_id, r.cohort_id FROM cohort_track_runs r
     JOIN cohort_members cm ON cm.cohort_id = r.cohort_id
     WHERE cm.uid = ? AND r.track_id = ?
     ORDER BY r.created_at DESC LIMIT 1`,
    [uid, trackId],
  );
}

export async function getAuthoredQuiz(uid, lessonId, trackId) {
  const run = await getLearnerRun(uid, trackId);
  if (run) {
    const override = await dbGet(
      `SELECT * FROM cohort_quiz_versions
       WHERE run_id = ? AND lesson_id = ?
       ORDER BY version DESC LIMIT 1`,
      [run.run_id, lessonId],
    );
    if (override) {
      return {
        runId: run.run_id,
        version: Number(override.version),
        mode: "single_answer",
        scope: "cohort",
        prompt: override.prompt,
        options: JSON.parse(override.options_json),
        passingScore: Number(override.passing_score),
      };
    }
  }
  const row = await dbGet(
    `SELECT q.quiz_id, q.current_version, v.prompt, v.options_json,
            v.passing_score
     FROM quizzes q JOIN quiz_versions v
       ON v.quiz_id = q.quiz_id AND v.version = q.current_version
     WHERE q.lesson_id = ?`,
    [lessonId],
  );
  if (!row) return null;
  return {
    quizId: row.quiz_id,
    version: Number(row.current_version),
    mode: "single_answer",
    scope: "base",
    prompt: row.prompt,
    options: JSON.parse(row.options_json),
    passingScore: Number(row.passing_score),
  };
}

export async function submitAuthoredQuiz(profile, lesson, body = {}) {
  await upsertUserFromToken(profile);
  const enrollment = await dbGet(
    "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
    [profile.uid, lesson.track_id],
  );
  if (!enrollment) {
    const err = new Error("Not enrolled");
    err.status = 403;
    throw err;
  }
  await assertLessonMilestoneAvailable(profile.uid, lesson);
  const run = await getLearnerRun(profile.uid, lesson.track_id);
  let quiz = run
    ? await dbGet(
        `SELECT run_id, lesson_id, version AS current_version, options_json,
                correct_option_id, passing_score
         FROM cohort_quiz_versions
         WHERE run_id = ? AND lesson_id = ?
         ORDER BY version DESC LIMIT 1`,
        [run.run_id, lesson.lesson_id],
      )
    : null;
  const isCohortQuiz = Boolean(quiz);
  if (!quiz) {
    quiz = await dbGet(
      `SELECT q.quiz_id, q.current_version, v.options_json,
              v.correct_option_id, v.passing_score
       FROM quizzes q JOIN quiz_versions v
         ON v.quiz_id = q.quiz_id AND v.version = q.current_version
       WHERE q.lesson_id = ?`,
      [lesson.lesson_id],
    );
  }
  if (!quiz) return null;
  const selectedOptionId = required(body.selectedOptionId, "selectedOptionId");
  const options = JSON.parse(quiz.options_json);
  if (!options.some((option) => option.id === selectedOptionId)) {
    const err = new Error("selectedOptionId must match an option");
    err.status = 400;
    throw err;
  }
  const score = selectedOptionId === quiz.correct_option_id ? 100 : 0;
  const passed = score >= Number(quiz.passing_score);
  const attemptId = id("qat");
  const now = Date.now();
  if (isCohortQuiz) {
    await dbRun(
      `INSERT INTO cohort_quiz_attempts
       (attempt_id, run_id, lesson_id, quiz_version, uid, selected_option_id,
        score, passed, submitted_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        attemptId,
        quiz.run_id,
        lesson.lesson_id,
        quiz.current_version,
        profile.uid,
        selectedOptionId,
        score,
        passed ? 1 : 0,
        now,
      ],
    );
  } else {
    await dbRun(
      `INSERT INTO quiz_attempts
       (attempt_id, quiz_id, quiz_version, lesson_id, uid, selected_option_id,
        score, passed, submitted_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [
        attemptId,
        quiz.quiz_id,
        quiz.current_version,
        lesson.lesson_id,
        profile.uid,
        selectedOptionId,
        score,
        passed ? 1 : 0,
        now,
      ],
    );
  }
  const progress = await patchLessonProgress(profile, lesson.lesson_id, {
    opened: true,
    quizPct: score,
    lastPlatform: body.lastPlatform || "web",
  });
  await maybeIssueCertificate(profile.uid, lesson.track_id);
  return {
    source: source(),
    data: {
      attempt: {
        attemptId,
        quizId: quiz.quiz_id || null,
        runId: quiz.run_id || null,
        scope: isCohortQuiz ? "cohort" : "base",
        quizVersion: Number(quiz.current_version),
        selectedOptionId,
        score,
        passed,
        submittedAt: now,
      },
      lessonId: lesson.lesson_id,
      quizPct: score,
      lessonPercent: progress.data?.progress?.lessonPercent,
      trackPercent: progress.data?.progress?.trackPercent,
    },
  };
}
