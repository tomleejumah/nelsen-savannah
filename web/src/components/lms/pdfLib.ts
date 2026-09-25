import * as pdfjs from "pdfjs-dist";

const docs = new Map<string, Promise<pdfjs.PDFDocumentProxy>>();

let workerReady: Promise<void> | null = null;

/** nginx often serves .mjs as octet-stream, which blocks `import()` of the worker. */
function ensurePdfWorker() {
  if (!workerReady) {
    workerReady = (async () => {
      const paths = ["/pdf.worker.min.js", "/pdf.worker.min.mjs"];
      let code = "";
      for (const path of paths) {
        const res = await fetch(path);
        if (!res.ok) continue;
        code = await res.text();
        if (code) break;
      }
      if (!code) throw new Error("PDF worker file missing");
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
