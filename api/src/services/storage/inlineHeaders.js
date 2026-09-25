/** Force browsers to display PDFs instead of downloading them. */

export function isPdfAsset({ mimeType, filename, objectKey } = {}) {
  const mime = String(mimeType || "").toLowerCase();
  const name = `${filename || ""} ${objectKey || ""}`.toLowerCase();
  return mime.includes("pdf") || /\.pdf(\?|$)/.test(name.trim());
}

export function safeFilename(filename, fallback = "file") {
  const base = String(filename || fallback)
    .split(/[/\\]/)
    .pop()
    .replace(/["\\\r\n]/g, "")
    .slice(0, 180);
  return base || fallback;
}

export function mimeForAsset(row = {}) {
  if (isPdfAsset(row)) return "application/pdf";
  const mime = String(row.mimeType || row.mime_type || "").trim();
  if (mime && mime !== "application/octet-stream") return mime;
  const name = String(row.filename || row.objectKey || row.object_key || "").toLowerCase();
  if (name.endsWith(".mp4")) return "video/mp4";
  if (name.endsWith(".webm")) return "video/webm";
  if (name.endsWith(".png")) return "image/png";
  if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
  return mime || "application/octet-stream";
}

export function inlineHeaderMap(row = {}) {
  const filename = safeFilename(row.filename, "lesson");
  const mime = mimeForAsset(row);
  return {
    "Content-Type": mime,
    "Content-Disposition": `inline; filename="${filename}"`,
    "X-Content-Type-Options": "nosniff",
  };
}

export function applyInlineHeaders(res, row = {}) {
  const headers = inlineHeaderMap(row);
  for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
  return headers["Content-Type"];
}
