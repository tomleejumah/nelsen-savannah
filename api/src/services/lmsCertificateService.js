/**
 * Certificates — issue when track % >= PASS_THRESHOLD.
 */

import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { dualWrite, mirrorCertificate } from "./lmsMirror.js";
import { PASS_THRESHOLD } from "./lmsProgressMath.js";
import { publicBaseUrl } from "./lmsMediaService.js";

export async function maybeIssueCertificate(uid, trackId) {
  const enroll = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (!enroll || Number(enroll.track_percent) < PASS_THRESHOLD) {
    return null;
  }

  // required assignments: all has_assignment lessons must be passed
  const required = await dbAll(
    `SELECT lesson_id FROM lessons WHERE track_id = ? AND has_assignment = 1`,
    [trackId],
  );
  for (const les of required) {
    const sub = await dbGet(
      `SELECT status FROM submissions
       WHERE uid = ? AND lesson_id = ? AND status = 'passed'
       ORDER BY marked_at DESC LIMIT 1`,
      [uid, les.lesson_id],
    );
    if (!sub) return null;
  }

  const existing = await dbGet(
    "SELECT * FROM certificates WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (existing) return existing;

  const now = Date.now();
  const certUrl = `${publicBaseUrl()}/lms/certificates/verify/${uid}/${trackId}`;

  await dualWrite({
    label: `cert:${uid}:${trackId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO certificates (uid, track_id, issued_at, cert_url, track_percent)
         VALUES (?, ?, ?, ?, ?)`,
        [uid, trackId, now, certUrl, Number(enroll.track_percent)],
      );
      await dbRun(
        `UPDATE enrollments SET status = 'certified' WHERE uid = ? AND track_id = ?`,
        [uid, trackId],
      );
      return true;
    },
    mirrorFn: async () => {
      await mirrorCertificate(uid, trackId, {
        issuedAt: now,
        certUrl,
        trackPercent: Number(enroll.track_percent),
      });
    },
  });

  return dbGet("SELECT * FROM certificates WHERE uid = ? AND track_id = ?", [
    uid,
    trackId,
  ]);
}

export async function listMyCertificates(uid) {
  const rows = await dbAll(
    `SELECT c.*, t.title AS course_title
     FROM certificates c
     JOIN tracks t ON t.track_id = c.track_id
     WHERE c.uid = ?
     ORDER BY c.issued_at DESC`,
    [uid],
  );
  return {
    source: getPrimaryEngine(),
    data: {
      certificates: rows.map((r) => ({
        trackId: r.track_id,
        courseTitle: r.course_title,
        issuedAt: Number(r.issued_at),
        verifyUrl: r.cert_url,
        pdfUrl: null,
        trackPercent: Number(r.track_percent),
      })),
    },
  };
}
