/**
 * Cleanup for uploads that were presigned but never finalized:
 *  - `pending` media_assets rows past their TTL (plus their object, in case the
 *    client PUT the bytes and then never called finalize)
 *  - stale files in UPLOAD_DIR/_tmp left by the legacy multipart route
 *  - optionally, files on disk with no matching media_assets row
 */

import fs from "fs";
import path from "path";
import { dbAll, dbRun, isDbReady } from "../db/lmsDb.js";
import { getStorageDriver } from "./storage/index.js";
import { localUploadRoot } from "./storage/localDriver.js";

function intEnv(name, fallback) {
  const raw = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
}

function pendingTtlMs() {
  return intEnv("MEDIA_PENDING_TTL_SECONDS", 24 * 3600) * 1000;
}

function tmpTtlMs() {
  return intEnv("MEDIA_TMP_TTL_SECONDS", 24 * 3600) * 1000;
}

async function reapPendingRows(cutoff) {
  const rows = await dbAll(
    `SELECT media_id, object_key, storage_path, storage_driver
     FROM media_assets
     WHERE status = 'pending' AND created_at < ?`,
    [cutoff],
  );
  let objectsDeleted = 0;
  for (const row of rows) {
    if (row.object_key) {
      try {
        const driver = getStorageDriver();
        if ((row.storage_driver || driver.name) === driver.name) {
          await driver.remove({ objectKey: row.object_key });
          objectsDeleted += 1;
        }
      } catch (err) {
        console.warn(
          `[media-reaper] could not delete object ${row.object_key}: ${err.message}`,
        );
      }
    }
    await dbRun("DELETE FROM media_assets WHERE media_id = ?", [row.media_id]);
  }
  return { rowsDeleted: rows.length, objectsDeleted };
}

function reapTmpDir(cutoff) {
  const tmpDir = path.join(localUploadRoot(), "_tmp");
  if (!fs.existsSync(tmpDir)) return 0;
  let deleted = 0;
  for (const entry of fs.readdirSync(tmpDir)) {
    const abs = path.join(tmpDir, entry);
    try {
      const stat = fs.statSync(abs);
      if (stat.isFile() && stat.mtimeMs < cutoff) {
        fs.rmSync(abs, { force: true });
        deleted += 1;
      }
    } catch (err) {
      console.warn(`[media-reaper] tmp ${entry}: ${err.message}`);
    }
  }
  return deleted;
}

function walkFiles(dir, root, out) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === "_tmp" || entry.name === ".gitkeep") continue;
    const abs = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkFiles(abs, root, out);
    } else if (entry.isFile()) {
      out.push(path.relative(root, abs));
    }
  }
  return out;
}

async function reapOrphanFiles(cutoff) {
  const root = localUploadRoot();
  if (!fs.existsSync(root)) return 0;
  const known = new Set(
    (await dbAll("SELECT object_key FROM media_assets WHERE object_key IS NOT NULL"))
      .map((r) => r.object_key)
      .filter(Boolean),
  );
  let deleted = 0;
  for (const rel of walkFiles(root, root, [])) {
    if (known.has(rel)) continue;
    const abs = path.join(root, rel);
    try {
      if (fs.statSync(abs).mtimeMs >= cutoff) continue;
      fs.rmSync(abs, { force: true });
      deleted += 1;
    } catch (err) {
      console.warn(`[media-reaper] orphan ${rel}: ${err.message}`);
    }
  }
  return deleted;
}

export async function reapOrphanedMedia({ pruneOrphanFiles = null } = {}) {
  if (!isDbReady()) {
    return { skipped: "db-not-ready" };
  }
  const now = Date.now();
  const pendingCutoff = now - pendingTtlMs();
  const tmpCutoff = now - tmpTtlMs();

  const pending = await reapPendingRows(pendingCutoff);
  const tmpDeleted = reapTmpDir(tmpCutoff);

  const shouldPrune =
    pruneOrphanFiles ?? process.env.MEDIA_REAPER_PRUNE_ORPHAN_FILES === "1";
  const orphanFilesDeleted = shouldPrune
    ? await reapOrphanFiles(pendingCutoff)
    : 0;

  const summary = {
    ranAt: now,
    pendingRowsDeleted: pending.rowsDeleted,
    pendingObjectsDeleted: pending.objectsDeleted,
    tmpFilesDeleted: tmpDeleted,
    orphanFilesDeleted,
    orphanFilePruneEnabled: shouldPrune,
  };
  if (
    summary.pendingRowsDeleted ||
    summary.tmpFilesDeleted ||
    summary.orphanFilesDeleted
  ) {
    console.log(`[media-reaper] ${JSON.stringify(summary)}`);
  }
  return summary;
}

let timer = null;

export function startMediaReaper() {
  if (timer) return timer;
  const minutes = intEnv("MEDIA_REAPER_INTERVAL_MINUTES", 60);
  if (process.env.MEDIA_REAPER_ENABLED === "0") {
    console.log("[media-reaper] disabled by MEDIA_REAPER_ENABLED=0");
    return null;
  }
  const run = () =>
    reapOrphanedMedia().catch((err) =>
      console.error("[media-reaper] run failed:", err.message),
    );
  setTimeout(run, 60_000).unref();
  timer = setInterval(run, minutes * 60_000);
  timer.unref();
  console.log(`[media-reaper] scheduled every ${minutes}m`);
  return timer;
}
