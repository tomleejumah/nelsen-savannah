import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import PDFDocument from "pdfkit";

const here = path.dirname(fileURLToPath(import.meta.url));
const LOGO = path.join(here, "../../assets/nelsen-logo.png");

const W = 842;
const H = 595;
const C = {
  paper: "#F4EFE3",
  navy: "#1C2A44",
  ochre: "#C9A24A",
  ochreDeep: "#B8903A",
  brick: "#A84A38",
  brickDeep: "#8E3B2C",
  ink: "#2A221C",
  muted: "#6B5A4A",
  faint: "#8A7A66",
};

function guilloche(doc, cx, cy, scale, color, opacity) {
  const R = 100;
  const r = 37;
  const period = 2 * Math.PI * r; // gcd(100, 37) = 1
  const steps = 2200;
  doc.save().translate(cx, cy).scale(scale);
  doc.lineWidth(0.3 / scale).strokeColor(color).strokeOpacity(opacity);
  for (const d of [95, 80, 65, 50]) {
    for (let i = 0; i <= steps; i++) {
      const t = (period * i) / steps;
      const x = (R - r) * Math.cos(t) + d * Math.cos(((R - r) / r) * t);
      const y = (R - r) * Math.sin(t) - d * Math.sin(((R - r) / r) * t);
      if (i === 0) doc.moveTo(x, y);
      else doc.lineTo(x, y);
    }
    doc.stroke();
  }
  doc.restore();
}

function watermark(doc) {
  doc.save();
  doc.rect(0, 0, W, H).clip();
  doc.rotate(-24, { origin: [W / 2, H / 2] });
  doc.font("Helvetica-Bold").fontSize(9).fillColor(C.brick).fillOpacity(0.05);
  for (let y = -250, row = 0; y < H + 250; y += 52, row++) {
    for (let x = -300; x < W + 300; x += 190) {
      doc.text("NELSEN SAVANNAH", x + (row % 2 ? 95 : 0), y, {
        characterSpacing: 3,
        lineBreak: false,
      });
    }
  }
  doc.restore();
}

function microtext(doc) {
  const line = "NELSEN SAVANNAH · VERIFIED CERTIFICATE · ".repeat(30);
  doc.font("Helvetica").fontSize(3.4).fillColor(C.navy).fillOpacity(0.55);
  const opts = { characterSpacing: 0.9, lineBreak: false };
  const strip = (x, y, w, h, draw) => {
    doc.save().rect(x, y, w, h).clip();
    draw();
    doc.restore();
  };
  strip(36, 36, W - 72, 8, () => doc.text(line, 36, 38, opts));
  strip(36, H - 44, W - 72, 8, () => doc.text(line, 36, H - 42, opts));
  doc.save().translate(40, H - 36).rotate(-90);
  strip(0, 0, H - 72, 8, () => doc.text(line, 0, 1, opts));
  doc.restore();
  doc.save().translate(W - 40, 36).rotate(90);
  strip(0, 0, H - 72, 8, () => doc.text(line, 0, 1, opts));
  doc.restore();
  doc.fillOpacity(1);
}

function cornerDiamond(doc, x, y) {
  doc.save().translate(x, y).rotate(45);
  doc.rect(-10, -10, 20, 20).fill(C.navy);
  doc.lineWidth(0.9).strokeColor(C.ochre).rect(-6, -6, 12, 12).stroke();
  doc.restore();
  doc.circle(x, y, 1.9).fill(C.ochre);
}

function seal(doc, cx, cy) {
  doc.save().translate(cx, cy);
  doc.polygon([-19, 13], [-28, 49], [-15, 42], [-6, 54], [1, 21]).fill(C.brick);
  doc.polygon([19, 13], [28, 49], [15, 42], [6, 54], [-1, 21]).fill(C.brickDeep);
  const n = 30;
  const pts = [];
  for (let i = 0; i < n * 8; i++) {
    const a = (2 * Math.PI * i) / (n * 8);
    const rr = 37 + 3 * Math.cos(n * a);
    pts.push([rr * Math.cos(a), rr * Math.sin(a)]);
  }
  doc.polygon(...pts).fill(C.ochre);
  doc.lineWidth(1).strokeColor(C.paper).strokeOpacity(0.8).circle(0, 0, 31).stroke();
  doc.circle(0, 0, 26).fill(C.ochreDeep);
  doc.lineWidth(0.7).strokeOpacity(0.6).circle(0, 0, 26).stroke();
  const star = [];
  for (let i = 0; i < 10; i++) {
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    const rr = i % 2 ? 5.5 : 13;
    star.push([rr * Math.cos(a), rr * Math.sin(a)]);
  }
  doc.polygon(...star).fill(C.paper);
  doc.restore();
}

function displayName(name) {
  const n = String(name || "").trim();
  if (n !== n.toLowerCase() && n !== n.toUpperCase()) return n;
  return n.toLowerCase().replace(/(^|[\s'-])(\p{L})/gu, (_, a, b) => a + b.toUpperCase());
}

function fitted(doc, text, font, size, maxWidth) {
  let s = size;
  doc.font(font);
  while (s > 10 && doc.fontSize(s).widthOfString(text) > maxWidth) s -= 1;
  return s;
}

function centered(doc, text, y, font, size, color, extra = {}) {
  doc.font(font).fontSize(size).fillColor(color).fillOpacity(1);
  doc.text(text, 0, y, { width: W, align: "center", lineBreak: false, ...extra });
}

export function renderCertificate(doc, { learnerName: rawName, courseTitle, score, date, certificateId, verifyUrl }) {
  const learnerName = displayName(rawName);
  doc.rect(0, 0, W, H).fill(C.paper);
  watermark(doc);

  doc.save().rect(46, 46, W - 92, H - 92).clip();
  guilloche(doc, W / 2, H / 2 - 20, 2.25, C.ochre, 0.3);
  doc.restore();

  doc.lineWidth(2.2).strokeColor(C.navy).strokeOpacity(1).rect(21, 21, W - 42, H - 42).stroke();
  doc.lineWidth(0.9).strokeColor(C.ochre).rect(28, 28, W - 56, H - 56).stroke();
  doc.lineWidth(0.6).strokeColor(C.ochre).rect(45, 45, W - 90, H - 90).stroke();
  microtext(doc);
  for (const [x, y] of [[45, 45], [W - 45, 45], [45, H - 45], [W - 45, H - 45]]) cornerDiamond(doc, x, y);

  if (fs.existsSync(LOGO)) doc.image(LOGO, W / 2 - 80, 72, { width: 160 });

  centered(doc, "CERTIFICATE OF COMPLETION", 176, "Helvetica-Bold", 10, C.brick, { characterSpacing: 4.5 });
  doc.lineWidth(0.75).strokeColor(C.ochre);
  doc.moveTo(W / 2 - 128, 201).lineTo(W / 2 - 15, 201).stroke();
  doc.moveTo(W / 2 + 15, 201).lineTo(W / 2 + 128, 201).stroke();
  doc.save().translate(W / 2, 201).rotate(45).rect(-3, -3, 6, 6).fill(C.ochre).restore();

  centered(doc, "This certifies that", 224, "Times-Italic", 12, C.muted);
  const nameSize = fitted(doc, learnerName, "Times-Italic", 46, 560);
  centered(doc, learnerName, 258 - nameSize * 0.35, "Times-Italic", nameSize, C.navy);
  doc.lineWidth(1).strokeColor(C.ochre).moveTo(W / 2 - 195, 298).lineTo(W / 2 + 195, 298).stroke();

  centered(doc, "has successfully completed the learning track", 329, "Times-Italic", 12, C.muted);
  const titleSize = fitted(doc, courseTitle, "Times-Bold", 25, 620);
  centered(doc, courseTitle, 352, "Times-Bold", titleSize, C.ink);
  if (score != null) centered(doc, `with an overall result of ${score}%`, 389, "Helvetica", 10.5, C.muted);

  seal(doc, W / 2, 482);

  const meta = (x, align, value, label) => {
    doc.font("Helvetica").fontSize(10).fillColor("#4A3B2E").text(value, x, 466, { width: 140, align, lineBreak: false });
    doc.lineWidth(0.7).strokeColor(C.ochre).moveTo(x, 481).lineTo(x + 140, 481).stroke();
    doc.font("Helvetica").fontSize(7.5).fillColor(C.faint).text(label, x, 489, { width: 140, align, characterSpacing: 1.2, lineBreak: false });
  };
  meta(157, "left", date, "DATE ISSUED");
  meta(W - 157 - 140, "right", certificateId, "CERTIFICATE ID");

  if (verifyUrl) centered(doc, `Verify: ${verifyUrl}`, 540, "Helvetica", 6, C.faint);
}

export function makeCertificatePdf(data) {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({
      size: [W, H],
      margin: 0,
      info: { Title: "Certificate of completion", Author: "Nelsen Savannah" },
    });
    const chunks = [];
    doc.on("data", (c) => chunks.push(c));
    doc.on("end", () => resolve(Buffer.concat(chunks)));
    doc.on("error", reject);
    renderCertificate(doc, data);
    doc.end();
  });
}
