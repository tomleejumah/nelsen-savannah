/**
 * Dual-write: primary DB first, then RTDB mirror (best-effort).
 * RTDB paths from lms-roadmap DATA_STORES.realtime.paths
 */

import admin from "../config/firebase.js";

const db = () => admin.database();

export async function mirrorRole(uid, role) {
  await db().ref(`roles/${uid}`).set(role);
}

export async function mirrorUserMeta(uid, meta) {
  await db().ref(`lms/users/${uid}`).update({
    ...meta,
    updatedAt: admin.database.ServerValue.TIMESTAMP,
  });
}

export async function mirrorTrack(trackId, data) {
  await db().ref(`lms/tracks/${trackId}`).update(data);
}

export async function mirrorModule(moduleId, data) {
  await db().ref(`lms/modules/${moduleId}`).update(data);
}

export async function mirrorLesson(lessonId, data) {
  await db().ref(`lms/lessons/${lessonId}`).update(data);
}

export async function mirrorEnrollment(uid, trackId, data) {
  await db().ref(`lms/enrollments/${uid}/${trackId}`).update(data);
}

export async function mirrorProgress(uid, lessonId, data) {
  await db().ref(`lms/progress/${uid}/${lessonId}`).update(data);
}

export async function mirrorSubmission(submissionId, data) {
  await db().ref(`lms/submissions/${submissionId}`).update(data);
}

export async function mirrorCertificate(uid, trackId, data) {
  await db().ref(`lms/certificates/${uid}/${trackId}`).update(data);
}

export async function mirrorMedia(mediaId, data) {
  await db().ref(`lms/media/${mediaId}`).update(data);
}

/**
 * Write primary via `writeFn`, then run RTDB `mirrorFn`.
 * Mirror failures are logged; primary success still returns.
 */
export async function dualWrite({ writeFn, mirrorFn, label = "lms" }) {
  const result = await writeFn();
  try {
    await mirrorFn(result);
  } catch (err) {
    console.error(`[lms-mirror] ${label} RTDB mirror failed:`, err.message);
  }
  return result;
}

export async function readRoleFromRtdb(uid) {
  const snap = await db().ref(`roles/${uid}`).once("value");
  return snap.val() ?? null;
}

export async function readUserFromRtdb(uid) {
  const snap = await db().ref(`lms/users/${uid}`).once("value");
  return snap.val() ?? null;
}

export async function checkRtdbHealth() {
  try {
    await db().ref(".info/connected").once("value");
    return { ok: true };
  } catch (err) {
    return { ok: false, error: err.message };
  }
}
