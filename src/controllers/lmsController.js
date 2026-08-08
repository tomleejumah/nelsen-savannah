import { getMe, getStoreHealth } from "../services/lmsMeService.js";
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
