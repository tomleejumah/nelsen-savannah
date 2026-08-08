import { getMe, getStoreHealth } from "../services/lmsMeService.js";
import {
  getLessonById,
  getModuleById,
  getTrackById,
  getTracks,
} from "../services/lmsCatalogService.js";
import {
  enrollUser,
  getMyProgress,
  listMyEnrollments,
  patchLessonProgress,
  unenrollUser,
} from "../services/lmsEnrollmentService.js";
import { getPrimaryEngine } from "../db/lmsDb.js";
import { lmsErr, lmsOk } from "../utils/lmsResponse.js";
import path from "path";
import fs from "fs";

function profileFromReq(req) {
  return {
    uid: req.user.uid,
    email: req.user.email,
    displayName: req.user.displayName,
    photoUrl: req.user.photoUrl,
  };
}

export async function getLmsMe(req, res) {
  try {
    const result = await getMe(profileFromReq(req));
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/me]", err);
    return lmsErr(
      res,
      "Failed to load profile",
      500,
      getPrimaryEngine() || "sqlite",
    );
  }
}

export async function getLmsHealth(_req, res) {
  try {
    const health = await getStoreHealth();
    return lmsOk(res, health, health.engine || "sqlite");
  } catch (err) {
    return lmsErr(res, err.message, 500);
  }
}

export async function listTracks(req, res) {
  try {
    const result = await getTracks(req.user.uid, {
      audience: req.query.audience,
      enrolled: req.query.enrolled,
    });
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/tracks]", err);
    return lmsErr(res, "Failed to list tracks", 500, getPrimaryEngine());
  }
}

export async function getTrack(req, res) {
  try {
    const result = await getTrackById(req.user.uid, req.params.trackId);
    if (result.notFound) {
      return lmsErr(res, "Track not found", 404, result.source);
    }
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/tracks/:id]", err);
    return lmsErr(res, "Failed to load track", 500, getPrimaryEngine());
  }
}

export async function getModule(req, res) {
  try {
    const result = await getModuleById(req.user.uid, req.params.moduleId);
    if (result.notFound) {
      return lmsErr(res, "Module not found", 404, result.source);
    }
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/modules/:id]", err);
    return lmsErr(res, "Failed to load module", 500, getPrimaryEngine());
  }
}

export async function getLesson(req, res) {
  try {
    const result = await getLessonById(req.user.uid, req.params.lessonId);
    if (result.notFound) {
      return lmsErr(res, "Lesson not found", 404, result.source);
    }
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/lessons/:id]", err);
    return lmsErr(res, "Failed to load lesson", 500, getPrimaryEngine());
  }
}

export async function postEnrollment(req, res) {
  try {
    const result = await enrollUser(profileFromReq(req), {
      trackId: req.body?.trackId,
      platform: req.body?.platform || "web",
    });
    return lmsOk(res, result.data, result.source, 201);
  } catch (err) {
    console.error("[POST /lms/enrollments]", err);
    return lmsErr(
      res,
      err.message || "Enroll failed",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

export async function getMyEnrollments(req, res) {
  try {
    const result = await listMyEnrollments(req.user.uid);
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/enrollments/me]", err);
    return lmsErr(res, "Failed to list enrollments", 500, getPrimaryEngine());
  }
}

export async function deleteEnrollment(req, res) {
  try {
    const result = await unenrollUser(req.user.uid, req.params.trackId);
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[DELETE /lms/enrollments/:trackId]", err);
    return lmsErr(
      res,
      err.message || "Unenroll failed",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

export async function patchProgress(req, res) {
  try {
    const result = await patchLessonProgress(
      profileFromReq(req),
      req.params.lessonId,
      req.body || {},
    );
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[PATCH /lms/progress/:lessonId]", err);
    return lmsErr(
      res,
      err.message || "Progress update failed",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

export async function getProgressMe(req, res) {
  try {
    const result = await getMyProgress(req.user.uid, {
      trackId: req.query.trackId,
    });
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/progress/me]", err);
    return lmsErr(res, "Failed to load progress", 500, getPrimaryEngine());
  }
}

export async function uploadMedia(req, res) {
  try {
    const { saveUploadedMedia } = await import("../services/lmsMediaService.js");
    const { dbGet } = await import("../db/lmsDb.js");
    const roleRow = await dbGet("SELECT role FROM roles WHERE uid = ?", [
      req.user.uid,
    ]);
    const result = await saveUploadedMedia({
      uid: req.user.uid,
      role: roleRow?.role,
      file: req.file,
      lessonId: req.body?.lessonId || null,
    });
    return lmsOk(res, result.data, result.source, 201);
  } catch (err) {
    console.error("[POST /lms/media/upload]", err);
    return lmsErr(
      res,
      err.message || "Upload failed",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

export async function getMedia(req, res) {
  try {
    const { getMediaStatus } = await import("../services/lmsMediaService.js");
    const result = await getMediaStatus(req.params.mediaId);
    if (result.notFound) {
      return lmsErr(res, "Media not found", 404, result.source);
    }
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[GET /lms/media/:id]", err);
    return lmsErr(res, "Failed to load media", 500, getPrimaryEngine());
  }
}

/** Signed play — query uid+token (no Bearer) so <video>/ExoPlayer can load. */
export async function playMedia(req, res) {
  try {
    const {
      getMediaFileRow,
      userMayPlayMedia,
      verifyMediaPlayToken,
    } = await import("../services/lmsMediaService.js");
    const mediaId = req.params.mediaId;
    const uid = req.query.uid;
    const token = req.query.token;
    if (!uid || !token || !verifyMediaPlayToken(mediaId, uid, token)) {
      return res.status(403).json({ error: "Invalid or expired playback token" });
    }
    if (!(await userMayPlayMedia(uid, mediaId))) {
      return res.status(403).json({ error: "Not enrolled" });
    }
    const row = await getMediaFileRow(mediaId);
    if (!row || !row.storage_path) {
      return res.status(404).json({ error: "Media not found" });
    }
    const abs = path.isAbsolute(row.storage_path)
      ? row.storage_path
      : path.resolve(process.cwd(), row.storage_path);
    if (!fs.existsSync(abs)) {
      return res.status(404).json({ error: "File missing on disk" });
    }
    return res.sendFile(abs);
  } catch (err) {
    console.error("[GET /lms/media/:id/play]", err);
    return res.status(500).json({ error: "Playback failed" });
  }
}
