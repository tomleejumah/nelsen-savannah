import { Resend } from "resend";
import { linesToRows, renderBrandedEmail } from "./emailBrand.js";

const TO = () =>
  process.env.INQUIRY_TO_EMAIL ||
  process.env.ORG_EMAIL ||
  "info@nelsen-savannah.co.ke";

const RECRUIT_TO = () =>
  process.env.RECRUIT_TO_EMAIL || "recruit@nelsen-savannah.co.ke";

const FROM = () =>
  process.env.RESEND_FROM ||
  "Nelsen Savannah <info@nelsen-savannah.co.ke>";

function requireKey() {
  const key = process.env.RESEND_API_KEY;
  if (!key) {
    const err = new Error(
      "RESEND_API_KEY not set — add it to the API env to send inquiry mail",
    );
    err.status = 503;
    err.code = "RESEND_UNCONFIGURED";
    throw err;
  }
  return key;
}

function inboxForDesk(desk) {
  const d = String(desk || "").toLowerCase();
  if (d === "careers" || d === "recruit") return RECRUIT_TO();
  return TO();
}

/**
 * Staff desk / alert mail (branded HTML + plain text).
 * @param {{ desk: string, subject: string, replyTo?: string, lines: string[], intro?: string, to?: string }} payload
 */
export async function sendInquiryEmail(payload) {
  const text = payload.lines.join("\n");
  const html = renderBrandedEmail({
    title: payload.subject,
    intro:
      payload.intro ||
      `New ${payload.desk || "desk"} message for Nelsen Savannah Innovation Hub.`,
    rows: linesToRows(payload.lines),
  });

  return sendRaw({
    to: payload.to || inboxForDesk(payload.desk),
    subject: payload.subject,
    replyTo: payload.replyTo,
    text,
    html,
  });
}

/**
 * Guest-facing confirmation (e.g. seat reserved).
 * @param {{ to: string, subject: string, title: string, intro: string, rows: Array<{label:string,value:string}>, cta?: {label:string,href:string} }} payload
 */
export async function sendGuestEmail(payload) {
  const text = [
    payload.title,
    payload.intro,
    "",
    ...payload.rows.map((r) => `${r.label}: ${r.value}`),
  ].join("\n");
  const html = renderBrandedEmail({
    title: payload.title,
    intro: payload.intro,
    rows: payload.rows,
    cta: payload.cta,
  });

  return sendRaw({
    to: payload.to,
    subject: payload.subject,
    replyTo: TO(),
    text,
    html,
  });
}

async function sendRaw({ to, subject, replyTo, text, html }) {
  const resend = new Resend(requireKey());
  const { data, error } = await resend.emails.send({
    from: FROM(),
    to: [to],
    replyTo: replyTo || undefined,
    subject: subject.slice(0, 200),
    text,
    html,
  });

  if (error) {
    const err = new Error(error.message || "Resend send failed");
    err.status = 502;
    err.code = "RESEND_FAILED";
    throw err;
  }

  return { id: data?.id || null, to };
}
