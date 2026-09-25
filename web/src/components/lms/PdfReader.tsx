import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Minus, Plus } from "lucide-react";

import { loadPdf, renderPdfPage } from "@/components/lms/pdfLib";

export function PdfReader({
  url,
  title,
  initialPage = 1,
  onPageProgress,
}: {
  url: string;
  title?: string;
  initialPage?: number;
  onPageProgress?: (info: {
    page: number;
    pages: number;
    maxPage: number;
    contentPct: number;
  }) => void;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const maxPageRef = useRef(1);
  const lastReport = useRef(0);
  const [page, setPage] = useState(Math.max(1, initialPage));
  const [pages, setPages] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(true);

  const report = useCallback(
    (current: number, total: number) => {
      if (!onPageProgress || total <= 0) return;
      const maxPage = Math.max(maxPageRef.current, current);
      maxPageRef.current = maxPage;
      const contentPct = Math.min(100, Math.round((maxPage / total) * 100));
      const now = Date.now();
      if (now - lastReport.current < 1200 && contentPct < 100) return;
      lastReport.current = now;
      onPageProgress({ page: current, pages: total, maxPage, contentPct });
    },
    [onPageProgress],
  );

  useEffect(() => {
    maxPageRef.current = Math.max(1, initialPage);
  }, [url, initialPage]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !url) return;
    let cancelled = false;
    setBusy(true);
    setError(null);
    void (async () => {
      try {
        const doc = await loadPdf(url);
        if (cancelled) return;
        const total = doc.numPages;
        setPages(total);
        const next = Math.min(Math.max(1, page), total);
        if (next !== page) {
          setPage(next);
          return;
        }
        const width = wrapRef.current?.clientWidth || 720;
        await renderPdfPage(url, next, canvas, width * zoom);
        if (cancelled) return;
        setBusy(false);
        report(next, total);
      } catch (err) {
        if (!cancelled) {
          setBusy(false);
          setError(
            err instanceof Error ? err.message : "Could not open this PDF in the browser.",
          );
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [url, page, zoom, report]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <p className="text-muted-foreground">
          {pages
            ? `Page ${page} of ${pages} · ${Math.min(100, Math.round((maxPageRef.current / pages) * 100))}% read`
            : "Loading PDF…"}
        </p>
        <div className="flex items-center gap-1">
          <button
            type="button"
            className="rounded-full border border-border p-1.5 hover:bg-accent disabled:opacity-40"
            disabled={zoom <= 0.7}
            onClick={() => setZoom((z) => Math.max(0.7, +(z - 0.15).toFixed(2)))}
            aria-label="Zoom out"
          >
            <Minus className="h-4 w-4" />
          </button>
          <button
            type="button"
            className="rounded-full border border-border p-1.5 hover:bg-accent disabled:opacity-40"
            disabled={zoom >= 2}
            onClick={() => setZoom((z) => Math.min(2, +(z + 0.15).toFixed(2)))}
            aria-label="Zoom in"
          >
            <Plus className="h-4 w-4" />
          </button>
          <button
            type="button"
            className="rounded-full border border-border p-1.5 hover:bg-accent disabled:opacity-40"
            disabled={page <= 1}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            aria-label="Previous page"
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
          <button
            type="button"
            className="rounded-full border border-border p-1.5 hover:bg-accent disabled:opacity-40"
            disabled={!pages || page >= pages}
            onClick={() => setPage((p) => Math.min(pages, p + 1))}
            aria-label="Next page"
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      </div>
      <div
        ref={wrapRef}
        className="overflow-auto rounded-xl border border-border bg-neutral-100"
      >
        {error ? (
          <p className="px-4 py-10 text-center text-sm text-destructive">{error}</p>
        ) : (
          <canvas
            ref={canvasRef}
            title={title}
            className={`mx-auto block max-w-full bg-white shadow-sm ${busy ? "opacity-60" : ""}`}
          />
        )}
      </div>
    </div>
  );
}
