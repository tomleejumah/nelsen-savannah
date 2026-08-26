/**
 * Branded HTML email — same wordmark as web BrandMark (ne + SVG l + sen savannah)
 * + company colors (--logo-red / brand navy).
 */

const SITE_URL =
  process.env.SITE_PUBLIC_URL || "https://nelsen-savannah.co.ke";

const BRAND = {
  hub: "Innovation Hub",
  logoRed: "#E31E24",
  navy: "#1B2A4A",
  ink: "#1B2A4A",
  muted: "#5A6B82",
  soft: "#F3F6FA",
  accent: "#3D6B9A",
  border: "#D5DEE9",
  white: "#FFFFFF",
};

const L_PATH = `M 4 0
            C 4 0, 4 95, 4 108
            C 4 132, 18 146, 38 144
            C 48 143, 50 133, 46 126
            C 42 119, 33 122, 28 117
            C 25 113, 25 108, 25 100
            L 25 0
            Z`;

/** Mirrors web `.brand-logo` / BrandMark.tsx (inline styles for email clients). */
function brandWordmarkHtml(color = BRAND.logoRed) {
  const font = "'Baloo 2',Nunito,'Segoe UI',Arial,sans-serif";
  return `
  <span style="display:inline-flex;align-items:flex-end;white-space:nowrap;color:${color};line-height:1;font-size:28px;" aria-label="nelsen savannah">
    <span style="font-family:${font};font-weight:800;font-style:normal;line-height:1;color:inherit;">ne</span>
    <svg width="10" height="30" viewBox="0 0 46 148" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false" style="display:block;flex-shrink:0;width:10px;height:30px;margin:0 -1px;transform:skewX(-8deg);-ms-transform:skewX(-8deg);transform-origin:bottom center;overflow:visible;">
      <path d="${L_PATH}" fill="${color}"></path>
    </svg>
    <span style="font-family:${font};font-weight:800;font-style:normal;line-height:1;color:inherit;">sen&nbsp;savannah</span>
  </span>`;
}

/**
 * @param {{
 *   title: string,
 *   intro?: string,
 *   rows?: Array<{ label: string, value: string }>,
 *   cta?: { label: string, href: string },
 *   footerNote?: string,
 * }} opts
 */
export function renderBrandedEmail(opts) {
  const { title, intro, rows = [], cta, footerNote } = opts;
  const rowHtml = rows
    .map(
      (r) => `
      <tr>
        <td style="padding:10px 0;border-bottom:1px solid ${BRAND.border};font-family:Georgia,'Times New Roman',serif;font-size:13px;color:${BRAND.muted};width:34%;vertical-align:top;">${escapeHtml(r.label)}</td>
        <td style="padding:10px 0;border-bottom:1px solid ${BRAND.border};font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:14px;color:${BRAND.ink};font-weight:600;vertical-align:top;">${escapeHtml(r.value)}</td>
      </tr>`,
    )
    .join("");

  const ctaHtml = cta
    ? `<tr><td style="padding:28px 0 8px;">
        <a href="${escapeAttr(cta.href)}" style="display:inline-block;background:${BRAND.navy};color:${BRAND.white};text-decoration:none;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:14px;font-weight:600;padding:12px 22px;border-radius:6px;">${escapeHtml(cta.label)}</a>
      </td></tr>`
    : "";

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>${escapeHtml(title)}</title>
  <link href="https://fonts.googleapis.com/css2?family=Baloo+2:wght@800&display=swap" rel="stylesheet" />
</head>
<body style="margin:0;padding:0;background:${BRAND.soft};">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:${BRAND.soft};padding:32px 12px;">
    <tr>
      <td align="center">
        <table role="presentation" width="560" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%;background:${BRAND.white};border-radius:12px;overflow:hidden;border:1px solid ${BRAND.border};">
          <tr>
            <td style="background:${BRAND.white};padding:28px 32px 18px;text-align:center;border-bottom:3px solid ${BRAND.logoRed};">
              ${brandWordmarkHtml(BRAND.logoRed)}
              <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:11px;letter-spacing:0.16em;text-transform:uppercase;color:${BRAND.navy};margin-top:14px;font-weight:600;">${BRAND.hub}</div>
            </td>
          </tr>
          <tr>
            <td style="height:4px;line-height:4px;font-size:0;background:${BRAND.navy};">&nbsp;</td>
          </tr>
          <tr>
            <td style="padding:32px;">
              <h1 style="margin:0 0 12px;font-family:Georgia,'Times New Roman',serif;font-size:22px;line-height:1.3;color:${BRAND.ink};font-weight:400;">${escapeHtml(title)}</h1>
              ${
                intro
                  ? `<p style="margin:0 0 22px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:15px;line-height:1.55;color:${BRAND.muted};">${escapeHtml(intro)}</p>`
                  : ""
              }
              ${
                rows.length
                  ? `<table role="presentation" width="100%" cellpadding="0" cellspacing="0">${rowHtml}</table>`
                  : ""
              }
              ${ctaHtml}
              ${
                footerNote
                  ? `<p style="margin:28px 0 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:12px;line-height:1.5;color:${BRAND.muted};">${escapeHtml(footerNote)}</p>`
                  : ""
              }
            </td>
          </tr>
          <tr>
            <td style="padding:18px 32px;background:${BRAND.soft};border-top:1px solid ${BRAND.border};text-align:center;">
              <p style="margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:11px;color:${BRAND.muted};">
                © ${new Date().getFullYear()} Nelsen Savannah Organization + Company Limited · Nairobi, Kenya
              </p>
              <p style="margin:8px 0 0;">
                <a href="${escapeAttr(SITE_URL)}" style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;font-size:11px;color:${BRAND.accent};text-decoration:none;">${SITE_URL.replace(/^https?:\/\//, "")}</a>
              </p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;
}

/** @param {string[]} lines */
export function linesToRows(lines) {
  return lines.map((line) => {
    const i = line.indexOf(":");
    if (i === -1) return { label: "Detail", value: line };
    return {
      label: line.slice(0, i).trim(),
      value: line.slice(i + 1).trim() || "—",
    };
  });
}

function escapeHtml(s) {
  return String(s ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function escapeAttr(s) {
  return escapeHtml(s).replace(/'/g, "&#39;");
}
