/**
 * Certificates — issued when a learner reaches the course pass threshold
 * and all required mentor-marked assignments have passed.
 */

import crypto from "node:crypto";
import { dbAll, dbGet, dbRun, getPrimaryEngine } from "../db/lmsDb.js";
import { dualWrite, mirrorCertificate } from "./lmsMirror.js";
import { PASS_THRESHOLD } from "./lmsProgressMath.js";
import { publicBaseUrl } from "./lmsMediaService.js";

function certificateNumber(uid, trackId, issuedAt) {
  const digest = crypto
    .createHash("sha256")
    .update(`${uid}:${trackId}:${issuedAt}`)
    .digest("hex")
    .slice(0, 12)
    .toUpperCase();
  return `NS-${digest}`;
}

export async function maybeIssueCertificate(uid, trackId) {
  const enroll = await dbGet(
    "SELECT * FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (!enroll || Number(enroll.track_percent) < PASS_THRESHOLD) return null;

  // Every course assignment configured as required must have a passed mark.
  const required = await dbAll(
    "SELECT lesson_id FROM lessons WHERE track_id = ? AND has_assignment = 1",
    [trackId],
  );
  for (const lesson of required) {
    const submission = await dbGet(
      `SELECT status FROM submissions
       WHERE uid = ? AND lesson_id = ? AND status = 'passed'
       ORDER BY marked_at DESC LIMIT 1`,
      [uid, lesson.lesson_id],
    );
    if (!submission) return null;
  }

  const existing = await dbGet(
    "SELECT * FROM certificates WHERE uid = ? AND track_id = ?",
    [uid, trackId],
  );
  if (existing) return existing;

  const now = Date.now();
  const certUrl = `${publicBaseUrl()}/lms/certificates/verify/${encodeURIComponent(uid)}/${encodeURIComponent(trackId)}`;
  await dualWrite({
    label: `cert:${uid}:${trackId}`,
    writeFn: async () => {
      await dbRun(
        `INSERT INTO certificates (uid, track_id, issued_at, cert_url, track_percent)
         VALUES (?, ?, ?, ?, ?)`,
        [uid, trackId, now, certUrl, Number(enroll.track_percent)],
      );
      await dbRun(
        "UPDATE enrollments SET status = 'certified' WHERE uid = ? AND track_id = ?",
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

function pdfEscape(value) {
  return String(value ?? "")
    .normalize("NFKD")
    .replace(/[^\\x20-\\x7E]/g, "")
    .replace(/\\\\/g, "\\\\\\\\")
    .replace(/\\(/g, "\\\\(")
    .replace(/\\)/g, "\\\\)");
}

function makeCertificatePdf({ learnerName, courseTitle, score, issuedAt, certificateId, verifyUrl }) {
  const date = new Date(issuedAt).toLocaleDateString("en-GB", {
    day: "2-digit", month: "long", year: "numeric", timeZone: "UTC",
  });
  const lines = [
    { text: "NELSEN SAVANNAH", y: 510, size: 17, font: "F2" },
    { text: "CERTIFICATE OF COMPLETION", y: 455, size: 27, font: "F2" },
    { text: "This certificate is proudly presented to", y: 390, size: 13, font: "F1" },
    { text: learnerName, y: 335, size: 30, font: "F2" },
    { text: "for successfully completing", y: 275, size: 13, font: "F1" },
    { text: courseTitle, y: 235, size: 23, font: "F2" },
    { text: `Overall course result: ${score}%`, y: 180, size: 14, font: "F2" },
    { text: `Issued: ${date}`, y: 125, size: 11, font: "F1" },
    { text: `Certificate ID: ${certificateId}`, y: 95, size: 10, font: "F1" },
    { text: `Verify: ${verifyUrl}`, y: 65, size: 8, font: "F1" },
  ];
  const commands = [
    "0.55 w 32 32 778 531 re S",
    "0.25 w 42 42 758 511 re S",
    "0.65 0.32 0.12 RG 42 42 m 800 42 l S",
  ];
  for (const line of lines) {
    const safe = pdfEscape(line.text);
    const approxWidth = safe.length * line.size * 0.25;
    const x = Math.max(48, 421 - approxWidth);
    commands.push(`BT /${line.font} ${line.size} Tf 0.12 0.12 0.12 rg 1 0 0 1 ${x.toFixed(1)} ${line.y} Tm (${safe}) Tj ET`);
  }
  const stream = commands.join("\n");
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>",
    `<< /Length ${Buffer.byteLength(stream, "ascii")} >>\\nstream\\n${stream}\\nendstream`,
  ];
  let pdf = "%PDF-1.4\\n";
  const offsets = [0];
  for (let i = 0; i < objects.length; i++) {
    offsets.push(Buffer.byteLength(pdf, "ascii"));
    pdf += `${i + 1} 0 obj\\n${objects[i]}\\nendobj\\n`;
  }
  const xrefOffset = Buffer.byteLength(pdf, "ascii");
  pdf += `xref\\n0 ${objects.length + 1}\\n0000000000 65535 f \\n`;
  for (const offset of offsets.slice(1)) {
    pdf += `${String(offset).padStart(10, "0")} 00000 n \\n`;
  }
  pdf += `trailer\\n<< /Size ${objects.length + 1} /Root 1 0 R >>\\nstartxref\\n${xrefOffset}\\n%%EOF`;
  return Buffer.from(pdf, "ascii");
}

async function certificateRecord(uid, trackId) {
  return dbGet(
    `SELECT c.*, t.title AS course_title, u.display_name, u.first_name, u.last_name
     FROM certificates c
     JOIN tracks t ON t.track_id = c.track_id
     JOIN users_mirror u ON u.uid = c.uid
     WHERE c.uid = ? AND c.track_id = ?`,
    [uid, trackId],
  );
}

export async function getMyCertificatePdf(uid, trackId) {
  const row = await certificateRecord(uid, trackId);
  if (!row) {
    const err = new Error("Certificate not available. Reach the course pass threshold and complete required assignments first.");
    err.status = 403;
    throw err;
  }
  const learnerName =
    [row.first_name, row.last_name].filter(Boolean).join(" ").trim() ||
    row.display_name ||
    "Learner";
  const id = certificateNumber(uid, trackId, Number(row.issued_at));
  const pdf = makeCertificatePdf({
    learnerName,
    courseTitle: row.course_title,
    score: Number(row.track_percent),
    issuedAt: Number(row.issued_at),
    certificateId: id,
    verifyUrl: row.cert_url || "",
  });
  return {
    pdf,
    filename: `nelsen-certificate-${id}.pdf`,
  };
}

export async function listMyCertificates(uid) {
  const rows = await dbAll(
    `SELECT c.*, t.title AS course_title, u.display_name, u.first_name, u.last_name
     FROM certificates c
     JOIN tracks t ON t.track_id = c.track_id
     JOIN users_mirror u ON u.uid = c.uid
     WHERE c.uid = ?
     ORDER BY c.issued_at DESC`,
    [uid],
  );
  return {
    source: getPrimaryEngine(),
    data: {
      certificates: rows.map((row) => ({
        trackId: row.track_id,
        learnerName:
          [row.first_name, row.last_name].filter(Boolean).join(" ").trim() ||
          row.display_name ||
          "Learner",
        courseTitle: row.course_title,
        issuedAt: Number(row.issued_at),
        verifyUrl: row.cert_url,
        pdfUrl: null,
        certificateId: certificateNumber(uid, row.track_id, Number(row.issued_at)),
        trackPercent: Number(row.track_percent),
      })),
    },
  };
}
