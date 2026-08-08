import { getMe, getStoreHealth } from "../services/lmsMeService.js";
import {
  getLessonById,
  getModuleById,
  getTrackById,
  getTracks,
} from "../services/lmsCatalogService.js";
import { getPrimaryEngine } from "../db/lmsDb.js";
import { lmsErr, lmsOk } from "../utils/lmsResponse.js";

export async function getLmsMe(req, res) {
  try {
    const result = await getMe({
      uid: req.user.uid,
      email: req.user.email,
      displayName: req.user.displayName,
      photoUrl: req.user.photoUrl,
    });
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
