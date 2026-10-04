import admin from "../config/firebase.js";
import { dbAll, dbRun, isDbReady } from "../db/lmsDb.js";

function httpError(message, status) {
  const err = new Error(message);
  err.status = status;
  return err;
}

async function storySnapshot(storyId) {
  const ref = admin.database().ref(`stories/${storyId}`);
  const snap = await ref.once("value");
  if (!snap.exists()) throw httpError("Story not found", 404);
  return { ref, story: snap.val() || {} };
}

export async function recordStoryView(actor, storyId) {
  const uid = String(actor?.uid || "").trim();
  if (!uid) throw httpError("Authentication required", 401);
  if (!isDbReady()) throw httpError("Viewer receipt store unavailable", 503);

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
  const expiresAt = Number(story.expiresAt) || (now + 24 * 60 * 60 * 1000);
  if (story.active === false || expiresAt <= now) {
    return {
      storyId,
      recorded: false,
      views: Number(story.views) || 0,
    };
  }

  const inserted = await dbRun(
    `INSERT INTO story_view_receipts (story_id, uid, viewed_at, expires_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(story_id, uid) DO NOTHING`,
    [storyId, uid, now, expiresAt],
  );
  const changed = Number(inserted?.changes ?? inserted?.rowCount ?? 0);
  if (changed <= 0) {
    return {
      storyId,
      recorded: false,
      views: Number(story.views) || 0,
    };
  }

  try {
    const countTxn = await ref.child("views").transaction((current) =>
      (Number(current) || 0) + 1,
    );

    return {
      storyId,
      recorded: true,
      views: Number(countTxn.snapshot?.val()) || (Number(story.views) || 0) + 1,
    };
  } catch (err) {
    // Keep aggregate + unique receipt consistent enough for a safe retry.
    await dbRun(
      "DELETE FROM story_view_receipts WHERE story_id = ? AND uid = ?",
      [storyId, uid],
    );
    throw err;
  }
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
  if (!isDbReady()) throw httpError("Viewer receipt store unavailable", 503);

  const { story } = await storySnapshot(storyId);
  if (String(story.ownerId || "").trim() !== uid) {
    throw httpError("Only the story owner can view receipts", 403);
  }

  const receipts = await dbAll(
    `SELECT uid, viewed_at
     FROM story_view_receipts
     WHERE story_id = ?
     ORDER BY viewed_at DESC`,
    [storyId],
  );

  const rows = await Promise.all(
    receipts.map(async (receipt) => {
      const viewerUid = String(receipt.uid || "");
      const userSnap = await admin.database().ref(`users/${viewerUid}`).once("value");
      const user = userSnap.val() || {};
      return {
        uid: viewerUid,
        name: viewerName(user),
        photoUrl: String(user.photoUrl || user.profileImage || "").trim(),
        viewedAt: Number(receipt.viewed_at) || 0,
      };
    }),
  );

  return {
    storyId,
    count: rows.length,
    views: Number(story.views) || rows.length,
    viewers: rows,
  };
}

export async function reapExpiredStoryViewReceipts() {
  if (!isDbReady()) return { skipped: "db-not-ready" };
  const result = await dbRun(
    "DELETE FROM story_view_receipts WHERE expires_at <= ?",
    [Date.now()],
  );
  return {
    deleted: Number(result?.changes ?? result?.rowCount ?? 0),
  };
}

let timer = null;

export function startStoryViewReceiptReaper() {
  if (timer) return timer;
  const run = () =>
    reapExpiredStoryViewReceipts().catch((err) =>
      console.error("[story-view-reaper] run failed:", err.message),
    );
  setTimeout(run, 30_000).unref();
  timer = setInterval(run, 60 * 60 * 1000);
  timer.unref();
  console.log("[story-view-reaper] scheduled every 60m");
  return timer;
}
