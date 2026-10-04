import "./loadEnv.js";
import express from "express";
import cors from "cors";
import fs from "fs";
import path from "path";
import { ROOT } from "./loadEnv.js";
import "./config/firebase.js";
import { initLmsDb, getPrimaryEngine } from "./db/lmsDb.js";
import { seedLmsCatalog } from "./services/lmsSeed.js";
import { startMediaReaper } from "./services/lmsMediaReaper.js";
import { startYouTubeLiveSync } from "./services/lmsYouTubeLiveSync.js";
import { startStoryViewReceiptReaper } from "./services/lmsStoryViewService.js";
import { configuredDriverName } from "./services/storage/index.js";
import notificationRoutes from "./routes/notifications.js";
import chatRoutes from "./routes/chat.js";
import diditRoute from "./routes/diditRoute.js";
import lmsRoutes from "./routes/lms.js";
import inquiryRoutes from "./routes/inquiries.js";
import mediaRoutes from "./routes/media.js";

const UPLOAD_DIR =
  process.env.UPLOAD_DIR || path.join(ROOT, "uploads");
const DATA_DIR = process.env.LMS_DATA_DIR || path.join(ROOT, "data");

fs.mkdirSync(UPLOAD_DIR, { recursive: true });
fs.mkdirSync(path.join(UPLOAD_DIR, "app"), { recursive: true });
fs.mkdirSync(path.join(UPLOAD_DIR, "apk"), { recursive: true });
fs.mkdirSync(DATA_DIR, { recursive: true });

const app = express();
app.use(
  cors({
    origin: true,
    credentials: true,
  }),
);
app.use(express.json());

// Health check
app.get("/health", (req, res) => {
  res.json({
    status: "OK",
    service: "nelsen-savannah",
    lmsPrimary: getPrimaryEngine(),
    timestamp: new Date().toISOString(),
  });
});

// App media (stories, groups, chat) — always public under /uploads/app.
app.use(
  "/uploads/app",
  express.static(path.join(UPLOAD_DIR, "app"), {
    fallthrough: true,
    maxAge: "7d",
  }),
);

// R2 objects are not on disk. Stream them at the same public path the app saves.
app.get("/uploads/app/:folder/:name", async (req, res, next) => {
  try {
    const folder = String(req.params.folder || "").replace(/[^a-z0-9_\-]/g, "");
    const name = path.basename(String(req.params.name || ""));
    if (!folder || !name || name !== req.params.name) return next();
    const { getStorageDriver } = await import("./services/storage/index.js");
    const driver = getStorageDriver();
    if (driver.name !== "r2" || typeof driver.getObject !== "function") return next();
    const obj = await driver.getObject({ objectKey: `app/${folder}/${name}` });
    if (!obj?.body) return next();
    if (obj.contentType) res.setHeader("Content-Type", obj.contentType);
    if (obj.contentLength) res.setHeader("Content-Length", String(obj.contentLength));
    res.setHeader("Cache-Control", "public, max-age=604800");
    if (typeof obj.body.pipe === "function") return obj.body.pipe(res);
    const { Readable } = await import("node:stream");
    return Readable.fromWeb(obj.body).pipe(res);
  } catch (err) {
    console.error("[GET /uploads/app]", err);
    return next();
  }
});

// Media is private by default: bytes are only reachable through a time-limited
// signed URL (/lms/media/:id/url). Set MEDIA_PUBLIC_UPLOADS=1 to restore the
// old unauthenticated /uploads mount.
if (process.env.MEDIA_PUBLIC_UPLOADS === "1") {
  console.warn(
    "[media] MEDIA_PUBLIC_UPLOADS=1 — /uploads is world-readable, presigned expiry does not apply",
  );
  app.use(
    "/uploads",
    express.static(UPLOAD_DIR, {
      fallthrough: true,
      maxAge: "1d",
    }),
  );
}

// Routes
app.use("/notifications", notificationRoutes);
app.use("/chat", chatRoutes);
app.use("/didit", diditRoute);
app.use("/lms", lmsRoutes);
app.use("/inquiries", inquiryRoutes);
app.use("/media", mediaRoutes);

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: "Route not found" });
});

// Error handler
app.use((err, req, res, next) => {
  console.error("Error:", err);
  res.status(500).json({ error: "Internal server error" });
});

const PORT = process.env.PORT || 5002;

async function start() {
  await initLmsDb();
  // Catalog seed is opt-in — empty DB stays empty until LMS_SEED_ENABLE=1 or admin seed.
  if (process.env.LMS_SEED_ENABLE === "1" || process.env.LMS_SEED_FORCE === "1") {
    await seedLmsCatalog({ force: process.env.LMS_SEED_FORCE === "1" });
  } else {
    console.log("[lms-seed] skipped (set LMS_SEED_ENABLE=1 to seed empty catalog)");
  }
  const { seedHubEvents } = await import("./services/lmsHubEventService.js");
  await seedHubEvents({ force: process.env.LMS_SEED_FORCE === "1" });
  console.log(
    `[lms] UPLOAD_DIR=${UPLOAD_DIR} PUBLIC_BASE_URL=${process.env.PUBLIC_BASE_URL || "(unset)"} mediaDriver=${configuredDriverName()}`,
  );
  startMediaReaper();
  startYouTubeLiveSync();
  startStoryViewReceiptReaper();
  app.listen(PORT, () => {
    console.log(`Nelsen Savannah service running on port ${PORT}`);
  });
}

start().catch((err) => {
  console.error("Failed to start:", err);
  process.exit(1);
});
