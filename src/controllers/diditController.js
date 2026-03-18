import https from "https";
import config from "../config/didit.js";
import { success, error } from "../utils/response.js";
import admin from "firebase-admin";
import crypto from "crypto";
import fs from "fs";

const serviceAccount = JSON.parse(
  fs.readFileSync(
    new URL("../../firebase-service-account.json", import.meta.url),
    "utf-8"
  )
);

// Initialize Firebase Admin (once)
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: "https://nisisi-default-rtdb.firebaseio.com/",
  });
}

const db = admin.database();

let accessToken = null;
let tokenExpiry = null;

async function getAccessToken() {
  if (accessToken && tokenExpiry > Date.now()) {
    return accessToken;
  }

  return new Promise((resolve, reject) => {
    const params = new URLSearchParams({
      grant_type: "client_credentials",
      client_id: config.clientId,
      client_secret: config.clientSecret,
    }).toString();

    const options = {
      hostname: "apx.didit.me",
      port: 443,
      path: "/oauth3/token",
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "Content-Length": Buffer.byteLength(params),
      },
    };

    const req = https.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => (data += chunk));
      res.on("end", () => {
        try {
          const response = JSON.parse(data);
          accessToken = response.access_token;
          tokenExpiry = Date.now() + response.expires_in * 1000 - 60000;
          resolve(accessToken);
        } catch (err) {
          reject(err);
        }
      });
    });

    req.on("error", reject);
    req.write(params);
    req.end();
  });
}

const createSession = async (req, res) => {
  const { firebaseUid } = req.body;

  if (!firebaseUid) {
    return error(res, "Firebase UID required", 400);
  }

  try {
    const params = JSON.stringify({
      workflow_id: config.workflowId,
      vendor_data: firebaseUid,
    });

    const options = {
      hostname: "verification.didit.me",
      port: 443,
      path: "/v3/session/",
      method: "POST",
      headers: {
        "x-api-key": config.clientSecret,
        "Content-Type": "application/json",
        "Content-Length": Buffer.byteLength(params),
      },
    };

    const diditReq = https.request(options, (diditRes) => {
      let data = "";
      diditRes.on("data", (chunk) => (data += chunk));

      diditRes.on("end", async () => {
        try {
          const response = JSON.parse(data);

          if (response.session_id) {
            await db.ref(`users/${firebaseUid}`).update({
              verificationStatus: "pending",
              verificationStartedAt: admin.database.ServerValue.TIMESTAMP,
            });

            return success(
              res,
              {
                sessionToken: response.url.split("/").pop(),
                sessionId: response.session_id,
              },
              "Session created",
            );
          } else {
            return error(res, JSON.stringify(response), 400);
          }
        } catch {
          return error(res, "Failed to parse response: " + data, 500);
        }
      });
    });

    diditReq.on("error", (err) => error(res, err.message, 500));

    diditReq.write(params);
    diditReq.end();
  } catch (err) {
    return error(res, err.message, 500);
  }
};

const handleWebhook = async (req, res) => {
  const signature = req.headers["x-signature-v2"];
  const timestamp = req.headers["x-timestamp"];

  const event = req.body;
  const firebaseUid = event.vendor_data;
  const status = event.status;

  console.log("Didit Webhook:", { firebaseUid, status });

  if (!firebaseUid) return res.sendStatus(200);

  try {
    let verificationStatus =
      status === "Approved"
        ? "approved"
        : status === "Declined"
          ? "rejected"
          : "pending";

    const updates = { verificationStatus };

    if (verificationStatus === "approved") {
      updates.verifiedAt = admin.database.ServerValue.TIMESTAMP;
    }

    await db.ref(`users/${firebaseUid}`).update(updates);

    console.log(`User ${firebaseUid} verification: ${verificationStatus}`);
  } catch (err) {
    console.error("Webhook error:", err);
  }

  res.sendStatus(200);
};

export default {
  createSession,
  handleWebhook,
};
