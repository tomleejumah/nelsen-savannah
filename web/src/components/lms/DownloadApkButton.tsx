import { useCallback, useEffect, useState } from "react";
import type { User } from "firebase/auth";
import { Download, Smartphone } from "lucide-react";

import {
  fetchAndroidApkDownloadUrl,
  fetchAndroidAppRelease,
  type AndroidAppReleaseDto,
} from "@/lib/lmsApi";
import { cn } from "@/lib/utils";

type Props = {
  user: User;
  className?: string;
  /** compact = single row for shells; card = learning hero block */
  variant?: "card" | "compact";
};

function formatBytes(n?: number) {
  if (n == null || !Number.isFinite(n) || n <= 0) return null;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

export function DownloadApkButton({
  user,
  className,
  variant = "card",
}: Props) {
  const [meta, setMeta] = useState<AndroidAppReleaseDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchAndroidAppRelease(token);
      if (!envelope.ok || !envelope.data) {
        setMeta({ available: false });
        setError(envelope.error || null);
        return;
      }
      setMeta(envelope.data);
    } catch (err) {
      setMeta({ available: false });
      setError(err instanceof Error ? err.message : "Could not check app build");
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onDownload() {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const envelope = await fetchAndroidApkDownloadUrl(token);
      if (!envelope.ok || !envelope.data?.downloadUrl) {
        throw new Error(envelope.error || "Could not start download");
      }
      // Navigate so the browser owns the transfer (size + % in its download UI).
      window.location.assign(envelope.data.downloadUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Download failed");
      setBusy(false);
    }
  }

  const sizeLabel = formatBytes(meta?.sizeBytes ?? undefined);
  const versionLabel = meta?.available
    ? `v${meta.versionName || "?"}${meta.versionCode != null ? ` (${meta.versionCode})` : ""}`
    : null;

  if (variant === "compact") {
    return (
      <div
        className={cn(
          "flex flex-wrap items-center gap-2 rounded-2xl border border-border/60 bg-card/40 px-4 py-3",
          className,
        )}
      >
        <Smartphone className="h-4 w-4 shrink-0 text-ember" />
        <div className="min-w-0 flex-1 text-left">
          <p className="text-sm font-medium text-foreground">Android app</p>
          <p className="text-xs text-muted-foreground">
            {loading
              ? "Checking for a build…"
              : meta?.available
                ? [versionLabel, sizeLabel].filter(Boolean).join(" · ")
                : "No APK published yet"}
          </p>
        </div>
        <button
          type="button"
          disabled={loading || busy || !meta?.available}
          onClick={() => void onDownload()}
          className="inline-flex items-center gap-1.5 rounded-full bg-ember-gradient px-3.5 py-1.5 text-xs font-semibold text-maroon-foreground disabled:opacity-50"
        >
          <Download className="h-3.5 w-3.5" />
          {busy ? "Starting…" : "Download APK"}
        </button>
        {error ? (
          <p className="w-full text-xs text-destructive">{error}</p>
        ) : null}
      </div>
    );
  }

  return (
    <div
      className={cn(
        "rounded-2xl border border-border/70 bg-card/60 px-5 py-4 text-left",
        className,
      )}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 rounded-xl bg-ember/10 p-2">
          <Smartphone className="h-5 w-5 text-ember" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Mobile app
          </p>
          <p className="mt-1 font-display text-lg font-semibold">
            Download our Android APK
          </p>
          <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
            Install the Nelsen Savannah app on your phone. Your browser will
            show download progress — then open the file and allow installs if
            asked.
          </p>
          <p className="mt-2 text-xs text-muted-foreground">
            {loading
              ? "Checking for a build…"
              : meta?.available
                ? [versionLabel, sizeLabel].filter(Boolean).join(" · ")
                : "Waiting for the next successful Android CI publish on main."}
          </p>
          {error ? (
            <p className="mt-2 text-xs text-destructive">{error}</p>
          ) : null}
          <button
            type="button"
            disabled={loading || busy || !meta?.available}
            onClick={() => void onDownload()}
            className="mt-4 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 disabled:opacity-50 disabled:hover:translate-y-0"
          >
            <Download className="h-4 w-4" />
            {busy ? "Starting…" : "Download APK"}
          </button>
        </div>
      </div>
    </div>
  );
}
