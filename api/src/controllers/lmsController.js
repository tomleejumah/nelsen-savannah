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

export async function patchActiveSchool(req, res) {
  try {
    const {
      setActiveSchool,
    } = await import("../services/lmsMembershipService.js");
    const result = await setActiveSchool(req.user.uid, req.body?.schoolId);
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[PATCH /lms/me/active-school]", err);
    return lmsErr(
      res,
      err.message || "Failed to set school",
      err.status || 500,
      getPrimaryEngine() || "sqlite",
    );
  }
}

export async function getSchoolMoney(req, res) {
  try {
    const { schoolMoneyStub } = await import(
      "../services/lmsMembershipService.js"
    );
    const data = await schoolMoneyStub(req.params.id);
    return lmsOk(res, data, getPrimaryEngine());
  } catch (err) {
    return lmsErr(res, err.message || "Failed", err.status || 500);
  }
}

export async function getSchoolTutorPayouts(req, res) {
  try {
    const { tutorPayoutsStub } = await import(
      "../services/lmsMembershipService.js"
    );
    const data = await tutorPayoutsStub(req.params.id);
    return lmsOk(res, data, getPrimaryEngine());
  } catch (err) {
    return lmsErr(res, err.message || "Failed", err.status || 500);
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

export async function postMediaUploadUrl(req, res) {
  try {
    const { createUploadTicket } = await import(
      "../services/lmsMediaService.js"
    );
    const body = req.body || {};
    const result = await createUploadTicket({
      uid: req.user.uid,
      role: req.user.role,
      filename: body.filename,
      contentType: body.contentType,
      sizeBytes: body.sizeBytes,
      scope: body.scope || (body.lessonId ? "lesson" : "misc"),
      scopeId: body.scopeId || body.lessonId || null,
      schoolId: body.schoolId,
      durationSeconds: body.durationSeconds,
    });
    return lmsOk(res, result.data, result.source, 201);
  } catch (err) {
    console.error("[POST /lms/media/upload-url]", err);
    return lmsErr(
      res,
      err.message || "Could not create upload URL",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

export async function postMediaFinalize(req, res) {
  try {
    const { finalizeUpload } = await import("../services/lmsMediaService.js");
    const result = await finalizeUpload({
      uid: req.user.uid,
      role: req.user.role,
      mediaId: req.params.mediaId,
      durationSeconds: req.body?.durationSeconds,
    });
    return lmsOk(res, result.data, result.source);
  } catch (err) {
    console.error("[POST /lms/media/:id/finalize]", err);
    return lmsErr(
      res,
      err.message || "Finalize failed",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

export async function getMediaPlaybackUrl(req, res) {
  try {
    const { resolvePlaybackUrl } = await import(
      "../services/lmsMediaService.js"
    );
    const result = await resolvePlaybackUrl(req.params.mediaId, req.user.uid);
    return lmsOk(res, result, getPrimaryEngine());
  } catch (err) {
    if (!err.status || err.status >= 500) {
      console.error("[GET /lms/media/:id/url]", err);
    }
    return lmsErr(
      res,
      err.message || "Could not sign playback URL",
      err.status || 500,
      getPrimaryEngine(),
    );
  }
}

/**
 * Disk-driver blob endpoint. Stands in for the bucket when driver=local: the
 * signature in the query string is what makes the URL time-limited, exactly as
 * a presigned S3 URL would be. Not mounted behind Bearer auth so <video> and
 * ExoPlayer can issue Range requests directly.
 */
export async function mediaBlob(req, res) {
  try {
    const { verifyLocalBlobSignature, resolveLocalPath } = await import(
      "../services/storage/localDriver.js"
    );
    const { key, mode, exp, sig } = req.query;
    const method = req.method === "PUT" ? "put" : "get";
    if (mode !== method) {
      return res.status(403).json({ error: "Signature mode mismatch" });
    }
    if (!verifyLocalBlobSignature({ objectKey: key, mode, exp, sig })) {
      return res.status(403).json({ error: "Invalid or expired signature" });
    }
    const abs = resolveLocalPath(key);

    if (method === "put") {
      fs.mkdirSync(path.dirname(abs), { recursive: true });
      const sink = fs.createWriteStream(abs);
      req.pipe(sink);
      sink.on("finish", () => res.status(200).json({ ok: true }));
      sink.on("error", (err) => {
        console.error("[PUT /lms/media/blob]", err);
        res.status(500).json({ error: "Write failed" });
      });
      return undefined;
    }

    if (!fs.existsSync(abs)) {
      return res.status(404).json({ error: "Object not found" });
    }
    return res.sendFile(abs);
  } catch (err) {
    console.error("[/lms/media/blob]", err);
    return res
      .status(err.status || 500)
      .json({ error: err.message || "Blob request failed" });
  }
}

export async function postMediaReap(req, res) {
  try {
    const { reapOrphanedMedia } = await import(
      "../services/lmsMediaReaper.js"
    );
    const summary = await reapOrphanedMedia({
      pruneOrphanFiles:
        req.body?.pruneOrphanFiles === true ? true : undefined,
    });
    return lmsOk(res, summary, getPrimaryEngine());
  } catch (err) {
    console.error("[POST /lms/admin/media/reap]", err);
    return lmsErr(res, err.message || "Reap failed", 500, getPrimaryEngine());
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

function handle(label, fn) {
  return async (req, res) => {
    try {
      const result = await fn(req);
      const status = result.status || 200;
      return lmsOk(res, result.data, result.source || getPrimaryEngine(), status);
    } catch (err) {
      console.error(label, err);
      return lmsErr(
        res,
        err.message || "Error",
        err.status || 500,
        getPrimaryEngine(),
      );
    }
  };
}

export const postSubmission = handle("[POST /lms/submissions]", async (req) => {
  const { createSubmission } = await import("../services/lmsSubmissionService.js");
  const result = await createSubmission(profileFromReq(req), req.body || {});
  return { ...result, status: 201 };
});

export const getMySubmissions = handle("[GET /lms/submissions/me]", async (req) => {
  const { listMySubmissions } = await import("../services/lmsSubmissionService.js");
  return listMySubmissions(req.user.uid, {
    trackId: req.query.trackId,
    status: req.query.status,
  });
});

export const getSubmissionQueue = handle("[GET /lms/submissions/queue]", async (req) => {
  const { listSubmissionQueue } = await import("../services/lmsSubmissionService.js");
  return listSubmissionQueue(req.user.uid);
});

export const markSubmission = handle("[PATCH /lms/submissions/:id/mark]", async (req) => {
  const { markSubmission: mark } = await import("../services/lmsSubmissionService.js");
  return mark(profileFromReq(req), req.params.id, req.body || {});
});

export const getCertificatesMe = handle("[GET /lms/certificates/me]", async (req) => {
  const { listMyCertificates } = await import("../services/lmsCertificateService.js");
  return listMyCertificates(req.user.uid);
});

export const likeTrack = handle("[POST /lms/tracks/:trackId/like]", async (req) => {
  const { toggleTrackLike } = await import("../services/lmsAdminService.js");
  return toggleTrackLike(req.user.uid, req.params.trackId, req.body?.liked);
});

export const submitQuiz = handle("[POST /lms/lessons/:lessonId/quiz]", async (req) => {
  const { submitQuiz: submit } = await import("../services/lmsAdminService.js");
  return submit(profileFromReq(req), req.params.lessonId, req.body || {});
});

export const adminCreateTrack = handle("[POST /lms/admin/tracks]", async (req) => {
  const svc = await import("../services/lmsAdminService.js");
  const result = await svc.adminCreateTrack(req.user.uid, req.body || {});
  return { ...result, status: 201 };
});

export const adminUpdateTrack = handle("[PUT /lms/admin/tracks/:trackId]", async (req) => {
  const svc = await import("../services/lmsAdminService.js");
  return svc.adminUpdateTrack(req.user.uid, req.params.trackId, req.body || {});
});

export const adminCreateModule = handle("[POST /lms/admin/modules]", async (req) => {
  const svc = await import("../services/lmsAdminService.js");
  const result = await svc.adminCreateModule(req.user.uid, req.body || {});
  return { ...result, status: 201 };
});

export const adminCreateLesson = handle("[POST /lms/admin/lessons]", async (req) => {
  const svc = await import("../services/lmsAdminService.js");
  const result = await svc.adminCreateLesson(req.user.uid, req.body || {});
  return { ...result, status: 201 };
});

export const adminSetRole = handle("[PATCH /lms/admin/users/:uid/role]", async (req) => {
  const svc = await import("../services/lmsAdminService.js");
  return svc.adminSetRole(req.user.uid, req.params.uid, req.body?.userRole);
});

export const adminSetRoleByEmail = handle(
  "[POST /lms/admin/users/role-by-email]",
  async (req) => {
    const svc = await import("../services/lmsAdminService.js");
    return svc.adminSetRoleByEmail(
      req.user.uid,
      req.body?.email,
      req.body?.userRole,
    );
  },
);

export const adminStats = handle("[GET /lms/admin/stats]", async (req) => {
  const svc = await import("../services/lmsAdminService.js");
  return svc.adminStats(req.user.uid);
});

export const adminMenteeProgress = handle(
  "[GET /lms/admin/mentees/:mentorId/progress]",
  async (req) => {
    const svc = await import("../services/lmsAdminService.js");
    return svc.adminMenteeProgress(req.params.mentorId, req.user.uid);
  },
);

export const postAssignment = handle("[POST /lms/assignments]", async (req) => {
  const svc = await import("../services/lmsAssignmentService.js");
  const result = await svc.createAssignment(profileFromReq(req), req.body || {});
  return { ...result, status: 201 };
});

export const getMyAssignments = handle("[GET /lms/assignments/me]", async (req) => {
  const svc = await import("../services/lmsAssignmentService.js");
  return svc.listMyAssignments(req.user.uid);
});

export const getAssignedOutbox = handle(
  "[GET /lms/assignments/assigned]",
  async (req) => {
    const svc = await import("../services/lmsAssignmentService.js");
    return svc.listAssignedByMe(req.user.uid);
  },
);

export const listSchools = handle("[GET /lms/schools]", async (req) => {
  const svc = await import("../services/lmsSchoolService.js");
  return svc.listSchools(req.user.uid);
});

export const createSchool = handle("[POST /lms/schools]", async (req) => {
  const svc = await import("../services/lmsSchoolService.js");
  const result = await svc.createSchool(req.user.uid, req.body || {});
  return { ...result, status: 201 };
});

export const patchSchoolAdmins = handle(
  "[PATCH /lms/schools/:id/admins]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    return svc.appointSchoolAdmins(req.user.uid, req.params.id, req.body || {});
  },
);

export const listSchoolMembers = handle(
  "[GET /lms/schools/:id/members]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    return svc.listSchoolMembers(req.user.uid, req.params.id);
  },
);

export const postSchoolMentor = handle(
  "[POST /lms/schools/:id/mentors]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    const result = await svc.registerSchoolMentor(
      req.user.uid,
      req.params.id,
      req.body || {},
    );
    return { ...result, status: 201 };
  },
);

export const postSchoolMentee = handle(
  "[POST /lms/schools/:id/mentees]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    const result = await svc.registerSchoolMentee(
      req.user.uid,
      req.params.id,
      req.body || {},
    );
    return { ...result, status: 201 };
  },
);

export const patchSchoolMemberRole = handle(
  "[PATCH /lms/schools/:id/members/:uid/role]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    return svc.patchSchoolMemberRole(
      req.user.uid,
      req.params.id,
      req.params.uid,
      req.body || {},
    );
  },
);

export const patchSchoolBranding = handle(
  "[PATCH /lms/schools/:id]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    return svc.updateSchoolBranding(req.user.uid, req.params.id, req.body || {});
  },
);

export const postSchoolRoster = handle(
  "[POST /lms/schools/:id/roster]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    return svc.importSchoolRoster(req.user.uid, req.params.id, req.body || {});
  },
);

export const getSchoolDashboard = handle(
  "[GET /lms/schools/:id/dashboard]",
  async (req) => {
    const svc = await import("../services/lmsSchoolService.js");
    return svc.schoolDashboard(req.user.uid, req.params.id);
  },
);

export const adminForceSeed = handle("[POST /lms/admin/seed]", async () => {
  const { seedLmsCatalog } = await import("../services/lmsSeed.js");
  const result = await seedLmsCatalog({ force: true });
  return { source: result.engine, data: result };
});

/** L8 — events deferred; live sessions stay out of core LMS for now. */
export function eventsTodo(_req, res) {
  return res.status(501).json({
    ok: false,
    source: getPrimaryEngine() || "sqlite",
    data: {
      decision: "defer",
      reason:
        "Events/reservations deferred. Use school dashboard + catalog until then.",
    },
    error: "Events API deferred (L8 decision: defer)",
  });
}
