import "dotenv/config";
import express from "express";
import cors from "cors";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import "./config/firebase.js";
import { initLmsDb, getPrimaryEngine } from "./db/lmsDb.js";
import { seedLmsCatalog } from "./services/lmsSeed.js";
import notificationRoutes from "./routes/notifications.js";
import chatRoutes from "./routes/chat.js";
import diditRoute from "./routes/diditRoute.js";
import lmsRoutes from "./routes/lms.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");

const UPLOAD_DIR =
  process.env.UPLOAD_DIR || path.join(ROOT, "uploads");
const DATA_DIR = process.env.LMS_DATA_DIR || path.join(ROOT, "data");

fs.mkdirSync(UPLOAD_DIR, { recursive: true });
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
    service: "nisisi-africa",
    lmsPrimary: getPrimaryEngine(),
    timestamp: new Date().toISOString(),
  });
});

// Serve lesson/media uploads from disk (metadata lives in DB + RTDB)
app.use(
  "/uploads",
  express.static(UPLOAD_DIR, {
    fallthrough: true,
    maxAge: "1d",
  }),
);

// Routes
app.use("/notifications", notificationRoutes);
app.use("/chat", chatRoutes);
app.use("/didit", diditRoute);
app.use("/lms", lmsRoutes);

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
  await seedLmsCatalog({ force: process.env.LMS_SEED_FORCE === "1" });
  console.log(
    `[lms] UPLOAD_DIR=${UPLOAD_DIR} PUBLIC_BASE_URL=${process.env.PUBLIC_BASE_URL || "(unset)"}`,
  );
  app.listen(PORT, () => {
    console.log(`Nisisi Africa service running on port ${PORT}`);
  });
}

start().catch((err) => {
  console.error("Failed to start:", err);
  process.exit(1);
});
