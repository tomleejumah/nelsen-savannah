import { useEffect, useRef, useState } from "react";

import { loadPdf, renderPdfPage } from "@/components/lms/pdfLib";

export function PdfThumb({
  url,
  title,
  className = "",
}: {
  url: string;
  title?: string;
  className?: string;
}) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !url) return;
    let cancelled = false;
    setFailed(false);
    void (async () => {
      try {
        await loadPdf(url);
        if (cancelled) return;
        await renderPdfPage(url, 1, canvas, 160);
      } catch {
        if (!cancelled) setFailed(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [url]);

  if (!url || failed) {
    return (
      <span
        className={`flex h-14 w-11 shrink-0 items-center justify-center rounded-md border border-border/70 bg-muted text-[10px] font-semibold uppercase tracking-wide text-muted-foreground ${className}`}
        aria-hidden
      >
        PDF
      </span>
    );
  }

  return (
    <canvas
      ref={canvasRef}
      title={title}
      className={`h-14 w-11 shrink-0 rounded-md border border-border/70 bg-white object-cover shadow-sm ${className}`}
    />
  );
}
