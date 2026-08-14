import { Resend } from "resend";

const TO = () =>
  process.env.INQUIRY_TO_EMAIL ||
  process.env.ORG_EMAIL ||
  "hello@nelsensavanna.co.ke";

const FROM = () =>
  process.env.RESEND_FROM || "Nelsen Savannah <onboarding@resend.dev>";

/**
 * @param {{ desk: string, subject: string, replyTo?: string, lines: string[] }} payload
 */
export async function sendInquiryEmail(payload) {
  const key = process.env.RESEND_API_KEY;
  if (!key) {
    const err = new Error(
      "RESEND_API_KEY not set — add it to the API env to send inquiry mail",
    );
    err.status = 503;
    err.code = "RESEND_UNCONFIGURED";
    throw err;
  }

  const text = payload.lines.join("\n");
  const resend = new Resend(key);
  const { data, error } = await resend.emails.send({
    from: FROM(),
    to: [TO()],
    replyTo: payload.replyTo || undefined,
    subject: payload.subject.slice(0, 200),
    text,
  });

  if (error) {
    const err = new Error(error.message || "Resend send failed");
    err.status = 502;
    err.code = "RESEND_FAILED";
    throw err;
  }

  return { id: data?.id || null, to: TO() };
}
