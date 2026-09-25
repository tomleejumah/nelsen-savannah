import * as pdfjs from "pdfjs-dist";

// Stable public path (copied from pdfjs-dist on build/install). Avoids
// hashed /assets/pdf.worker.min-*.mjs 404s after deploy asset churn.
pdfjs.GlobalWorkerOptions.workerSrc = "/pdf.worker.min.mjs";

const docs = new Map<string, Promise<pdfjs.PDFDocumentProxy>>();

export function loadPdf(url: string) {
  let pending = docs.get(url);
  if (!pending) {
    pending = pdfjs
      .getDocument({
        url,
        withCredentials: false,
        disableRange: true,
        disableStream: true,
      })
      .promise.catch((err) => {
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
