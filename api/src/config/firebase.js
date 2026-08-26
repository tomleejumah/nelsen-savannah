import "../loadEnv.js";
import admin from "firebase-admin";
import { readFileSync } from "fs";
import path from "path";
import { ROOT } from "../loadEnv.js";

const saPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || "./firebase-service-account.json";
const serviceAccount = JSON.parse(
  readFileSync(path.isAbsolute(saPath) ? saPath : path.join(ROOT, saPath), "utf8"),
);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: process.env.FIREBASE_DATABASE_URL,
});

console.log("Firebase Admin initialized");

export default admin;
