import * as pdfjs from "pdfjs-dist";

const docs = new Map<string, Promise<pdfjs.PDFDocumentProxy>>();

let workerReady: Promise<void> | null = null;

function looksLikeWorkerSource(code: string) {
  const head = code.slice(0, 200).trimStart();
  if (!head || head.startsWith("<!") || head.startsWith("<html")) return false;
  return (
    head.includes("webpack") ||
    head.includes("pdfjs") ||
    head.includes("PDFWorker") ||
    head.startsWith("//") ||
    head.startsWith("use strict") ||
    head.startsWith("(function") ||
    head.startsWith("!function") ||
    head.startsWith("import ") ||
    /["']use strict["']/.test(head.slice(0, 80))
  );
}

async function fetchWorkerCode(url: string) {
  const res = await fetch(url, { cache: "force-cache" });
  if (!res.ok) return null;
  const ct = (res.headers.get("content-type") || "").toLowerCase();
  if (ct.includes("text/html")) return null;
  const code = await res.text();
  if (!looksLikeWorkerSource(code)) return null;
  return code;
}

/**
 * nginx serves .mjs as application/octet-stream (breaks module import) and may
 * SPA-fallback .js to index.html. Load worker bytes into a JS blob, or CDN.
 */
function ensurePdfWorker() {
  if (!workerReady) {
    workerReady = (async () => {
      const localPaths = ["/pdf.worker.min.mjs", "/pdf.worker.min.js"];
      let code: string | null = null;
      for (const path of localPaths) {
        code = await fetchWorkerCode(path);
        if (code) break;
      }
      if (!code) {
        const cdn = `https://unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;
        code = await fetchWorkerCode(cdn);
      }
      if (!code) {
        // Last resort: same-origin CDN script tag path via unpkg as workerSrc
        // (unpkg sends a proper JS content-type).
        pdfjs.GlobalWorkerOptions.workerSrc = `https://unpkg.com/pdfjs-dist@${pdfjs.version}/build/pdf.worker.min.mjs`;
        return;
      }
      const blob = new Blob([code], { type: "text/javascript" });
      pdfjs.GlobalWorkerOptions.workerSrc = URL.createObjectURL(blob);
    })().catch((err) => {
      workerReady = null;
      throw err;
    });
  }
  return workerReady;
}

export function loadPdf(url: string) {
  let pending = docs.get(url);
  if (!pending) {
    pending = ensurePdfWorker()
      .then(() =>
        pdfjs.getDocument({
          url,
          withCredentials: false,
          disableRange: true,
          disableStream: true,
        }).promise,
      )
      .catch((err) => {
        docs.delete(url);
        throw err;
      });
    docs.set(url, pending);
  }
  return pending;
}

export async function renderPdfPage(
  url: string,
  pageNumber: number,
  canvas: HTMLCanvasElement,
  maxWidth: number,
) {
  const doc = await loadPdf(url);
  const page = await doc.getPage(pageNumber);
  const unscaled = page.getViewport({ scale: 1 });
  const scale = Math.min(2.2, Math.max(0.4, maxWidth / unscaled.width));
  const viewport = page.getViewport({ scale });
  const ctx = canvas.getContext("2d");
  if (!ctx) return { pages: doc.numPages, width: viewport.width, height: viewport.height };
  canvas.width = Math.floor(viewport.width);
  canvas.height = Math.floor(viewport.height);
  await page.render({ canvasContext: ctx, viewport }).promise;
  return { pages: doc.numPages, width: viewport.width, height: viewport.height };
}
