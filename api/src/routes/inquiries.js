import express from "express";
import { sendInquiryEmail } from "../services/inquiryEmail.js";

const router = express.Router();

const ALLOWED_DESKS = new Set(["invest", "tourism", "contact", "careers", "recruit"]);

router.post("/", async (req, res) => {
  try {
    const desk = String(req.body?.desk || "").trim();
    const subject = String(req.body?.subject || "").trim();
    const fields = req.body?.fields;
    const replyTo = String(req.body?.replyTo || req.body?.email || "").trim();

    if (!ALLOWED_DESKS.has(desk)) {
      return res.status(400).json({ ok: false, error: "Invalid desk" });
    }
    if (!subject) {
      return res.status(400).json({ ok: false, error: "Subject required" });
    }
    if (!fields || typeof fields !== "object" || Array.isArray(fields)) {
      return res.status(400).json({ ok: false, error: "fields object required" });
    }

    const lines = [`Desk: ${desk}`];
    for (const [label, value] of Object.entries(fields)) {
      lines.push(`${label}: ${String(value ?? "").trim() || "—"}`);
    }

    const result = await sendInquiryEmail({
      desk,
      subject,
      replyTo: replyTo || undefined,
      lines,
    });

    return res.json({ ok: true, data: result });
  } catch (err) {
    const status = err.status || 500;
    console.error("[inquiries]", err.message);
    return res.status(status).json({
      ok: false,
      error: err.message || "Send failed",
      code: err.code || "INQUIRY_FAILED",
    });
  }
});

export default router;
