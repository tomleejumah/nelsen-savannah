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

/**
 * Notify an applicant that a school admin decided their mentor app or join request.
 * Never throws — approval must succeed even if mail fails.
 */
export async function notifySchoolDecisionEmail({
  to,
  displayName,
  schoolName,
  kind,
  approved,
}) {
  const email = String(to || "").trim();
  if (!email || !email.includes("@")) return null;
  const name = String(displayName || "").trim() || "there";
  const school = String(schoolName || "").trim() || "the school";
  const isMentor = kind === "mentor";
  const ok = Boolean(approved);
  const title = ok
    ? isMentor
      ? "Your mentor application was approved"
      : "Your school join request was approved"
    : isMentor
      ? "Your mentor application was not approved"
      : "Your school join request was not approved";
  const intro = ok
    ? isMentor
      ? `Hi ${name}, you can now teach at ${school}. Open Learning to get started.`
      : `Hi ${name}, you are now a member of ${school}. Open Learning to enroll in courses.`
    : `Hi ${name}, your request for ${school} was not approved at this time. You can apply again later or contact the school admin.`;
  try {
    return await sendGuestEmail({
      to: email,
      subject: title,
      title,
      intro,
      rows: [
        { label: "School", value: school },
        { label: "Decision", value: ok ? "Approved" : "Not approved" },
      ],
    });
  } catch (err) {
    console.warn("[school-mail] notify decision:", err.message || err);
    return null;
  }
}

/**
 * Door-A invite email with join-link CTA.
 * Best-effort — invite row always saves even if Resend fails.
 * TODO: verify delivery in prod; surface mail failures in school admin UI.
 */
export async function notifySchoolInviteEmail({
  to,
  displayName,
  schoolName,
  role,
  inviteUrl,
}) {
  const email = String(to || "").trim();
  if (!email || !email.includes("@")) return null;
  const url = String(inviteUrl || "").trim();
  if (!url) return null;
  const name = String(displayName || "").trim() || "there";
  const school = String(schoolName || "").trim() || "the school";
  const isMentor = String(role || "").toLowerCase() === "mentor";
  const title = isMentor
    ? `You're invited to mentor at ${school}`
    : `You're invited to join ${school}`;
  const intro = isMentor
    ? `Hi ${name}, you've been invited to teach at ${school}. Open the link below — sign in (or create an account) and you'll land in the school as a mentor.`
    : `Hi ${name}, you've been invited to learn at ${school}. Open the link below — sign in (or create an account) and you'll join automatically.`;
  try {
    return await sendGuestEmail({
      to: email,
      subject: title,
      title,
      intro,
      rows: [
        { label: "School", value: school },
        { label: "Role", value: isMentor ? "Mentor" : "Student" },
      ],
      cta: { label: "Accept invite", href: url },
    });
  } catch (err) {
    console.warn("[school-mail] notify invite:", err.message || err);
    return null;
  }
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
