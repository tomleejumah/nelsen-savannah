import admin from "../config/firebase.js";

/** Attach req.user when a valid Bearer token is present; never 401. */
export async function optionalAuthenticate(req, _res, next) {
  const token = req.headers.authorization?.split("Bearer ")[1];
  if (!token) return next();
  try {
    const decodedToken = await admin.auth().verifyIdToken(token);
    req.user = {
      uid: decodedToken.uid,
      email: decodedToken.email || "",
      displayName: decodedToken.name || "",
      photoUrl: decodedToken.picture || "",
    };
  } catch {
    /* treat as anonymous */
  }
  next();
}
