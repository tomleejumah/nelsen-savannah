import { useCallback, useEffect, useState } from "react";
import type { User } from "firebase/auth";

import {
  createYouTubeConnectUrl,
  fetchYouTubeConnection,
  type YouTubeConnectionDto,
} from "@/lib/lmsApi";

export function YouTubeConnectionPanel({
  user,
  schoolId,
  title = "YouTube Live channel",
  blurb,
}: {
  user: User;
  schoolId?: string;
  title?: string;
  blurb?: string;
}) {
  const [connection, setConnection] = useState<YouTubeConnectionDto | null>(null);
  const [busy, setBusy] = useState(true);
  const [msg, setMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    setBusy(true);
    try {
      const token = await user.getIdToken();
      const result = await fetchYouTubeConnection(token, schoolId);
      setConnection(result.ok && result.data ? result.data : null);
      if (!result.ok) setMsg(result.error || "Could not load YouTube connection.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not load YouTube connection.");
    } finally {
      setBusy(false);
    }
  }, [user, schoolId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function connect() {
    setBusy(true);
    setMsg(null);
    try {
      const token = await user.getIdToken();
      const result = await createYouTubeConnectUrl(token, schoolId);
      if (!result.ok || !result.data?.authUrl) {
        setMsg(result.error || "Could not start YouTube authorization.");
        return;
      }
      window.open(result.data.authUrl, "_blank", "noopener,noreferrer");
      setMsg("Authorize the YouTube channel in the new tab, then refresh the status here.");
    } catch (err) {
      setMsg(err instanceof Error ? err.message : "Could not connect YouTube.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="space-y-3 rounded-2xl border border-border/70 bg-card/50 p-5">
      <div>
        <h3 className="font-display text-lg font-semibold">{title}</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          {blurb ||
            "Connect the YouTube channel Nelsen should create and stream live sessions through."}
        </p>
      </div>

      {connection?.connected ? (
        <div className="rounded-xl border border-border/60 bg-background px-4 py-3 text-sm">
          <p className="font-semibold">{connection.channelTitle || "Connected channel"}</p>
          <p className="mt-1 font-mono text-xs text-muted-foreground">
            {connection.channelId}
          </p>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          {busy ? "Checking channel…" : "No YouTube channel connected yet."}
        </p>
      )}

      {msg ? <p className="text-xs text-ember">{msg}</p> : null}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={busy}
          onClick={() => void connect()}
          className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground disabled:opacity-60"
        >
          {connection?.connected ? "Reconnect YouTube" : "Connect YouTube"}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => void load()}
          className="rounded-full border border-border px-5 py-2 text-sm font-semibold disabled:opacity-60"
        >
          Refresh status
        </button>
      </div>
    </section>
  );
}
