import admin from "../config/firebase.js";

function httpError(message, status) {
  const err = new Error(message);
  err.status = status;
  return err;
}

async function storySnapshot(storyId) {
  const ref = admin.database().ref(`stories/${storyId}`);
  const snap = await ref.once("value");
  if (!snap.exists()) throw httpError("Story not found", 404);
  return { ref, snap, story: snap.val() || {} };
}

export async function recordStoryView(actor, storyId) {
  const uid = String(actor?.uid || "").trim();
  if (!uid) throw httpError("Authentication required", 401);

  const { ref, story } = await storySnapshot(storyId);
  const ownerId = String(story.ownerId || "").trim();
  if (ownerId && ownerId === uid) {
    return {
      storyId,
      recorded: false,
      views: Number(story.views) || 0,
    };
  }

  const now = Date.now();
  let recorded = false;
  const result = await ref.transaction((current) => {
    if (!current) return current;
    if (String(current.ownerId || "") === uid) return current;

    const expiresAt = Number(current.expiresAt) || 0;
    if (current.active === false || (expiresAt > 0 && expiresAt <= now)) {
      return current;
    }

    const viewers = current.viewers && typeof current.viewers === "object"
      ? { ...current.viewers }
      : {};
    if (viewers[uid]) return current;

    viewers[uid] = { viewedAt: now };
    current.viewers = viewers;
    current.views = (Number(current.views) || 0) + 1;
    recorded = true;
    return current;
  });

  const updated = result.snapshot?.val() || story;
  return {
    storyId,
    recorded,
    views: Number(updated.views) || 0,
  };
}

function viewerName(user = {}) {
  const display = String(user.displayName || user.name || "").trim();
  if (display) return display;
  const parts = [
    String(user.firstName || "").trim(),
    String(user.lastName || "").trim(),
  ].filter(Boolean);
  return parts.join(" ") || "Nelsen user";
}

export async function listStoryViewers(actor, storyId) {
  const uid = String(actor?.uid || "").trim();
  if (!uid) throw httpError("Authentication required", 401);

  const { ref, story } = await storySnapshot(storyId);
  if (String(story.ownerId || "").trim() !== uid) {
    throw httpError("Only the story owner can view receipts", 403);
  }

  const viewerSnap = await ref.child("viewers").once("value");
  const receipts = viewerSnap.val() || {};
  const rows = await Promise.all(
    Object.entries(receipts).map(async ([viewerUid, receipt]) => {
      const userSnap = await admin.database().ref(`users/${viewerUid}`).once("value");
      const user = userSnap.val() || {};
      return {
        uid: viewerUid,
        name: viewerName(user),
        photoUrl: String(user.photoUrl || user.profileImage || "").trim(),
        viewedAt: Number(receipt?.viewedAt) || 0,
      };
    }),
  );

  rows.sort((a, b) => b.viewedAt - a.viewedAt);
  return {
    storyId,
    count: rows.length,
    views: Number(story.views) || rows.length,
    viewers: rows,
  };
}
