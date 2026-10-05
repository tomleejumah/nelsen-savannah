import { dbAll, dbRun, isDbReady } from "../db/lmsDb.js";
import { updateHubLiveStatus } from "./lmsHubEventService.js";

function intEnv(name, fallback) {
  const raw = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(raw) && raw > 0 ? raw : fallback;
}

function youtubeVideoId(raw) {
  try {
    const url = new URL(String(raw || "").trim());
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    if (host === "youtu.be") {
      return url.pathname.split("/").filter(Boolean)[0] || "";
    }
    if (host === "youtube.com" || host.endsWith(".youtube.com")) {
      const watch = url.searchParams.get("v");
      if (watch) return watch;
      const parts = url.pathname.split("/").filter(Boolean);
      if (["embed", "live", "shorts"].includes(parts[0] || "")) {
        return parts[1] || "";
      }
    }
  } catch {
    return "";
  }
  return "";
}

function deriveLiveStatus(video, currentStatus) {
  const snippetState = String(video?.snippet?.liveBroadcastContent || "").toLowerCase();
  const details = video?.liveStreamingDetails || {};

  if (details.actualEndTime) return "ended";
  if (snippetState === "live") return "live";
  if (snippetState === "upcoming") return "scheduled";

  // YouTube can briefly report "none" around state transitions while retaining
  // liveStreamingDetails. Preserve the stronger timestamps when available.
  if (details.actualStartTime && !details.actualEndTime) return "live";
  if (details.scheduledStartTime && !details.actualStartTime) return "scheduled";

  return currentStatus;
}

async function fetchYoutubeVideos(ids, apiKey) {
  if (!ids.length) return new Map();

  const url = new URL("https://www.googleapis.com/youtube/v3/videos");
  url.searchParams.set("part", "snippet,liveStreamingDetails");
  url.searchParams.set("id", ids.join(","));
  url.searchParams.set("key", apiKey);

  const response = await fetch(url, {
    headers: { Accept: "application/json" },
    signal: AbortSignal.timeout(12_000),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`YouTube videos.list failed ${response.status}: ${text.slice(0, 240)}`);
  }

  const json = await response.json();
  return new Map((json.items || []).map((item) => [item.id, item]));
}

export async function syncYouTubeLiveStatuses() {
  if (!isDbReady()) return { skipped: "db-not-ready" };

  const apiKey = String(process.env.YOUTUBE_API_KEY || "").trim();
  if (!apiKey) return { skipped: "youtube-api-key-missing" };

  const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
  const rows = await dbAll(
    `SELECT event_id, meeting_link, status
     FROM hub_events
     WHERE event_type = 'live'
       AND status IN (0, 1)
       AND date_ms >= ?
     ORDER BY date_ms ASC`,
    [cutoff],
  );

  const candidates = rows
    .map((row) => ({
      eventId: row.event_id,
      videoId: youtubeVideoId(row.meeting_link),
      currentStatus: Number(row.status) === 1 ? "live" : "scheduled",
    }))
    .filter((row) => row.videoId);

  if (!candidates.length) {
    return { checked: 0, changed: 0, missing: 0 };
  }

  let changed = 0;
  let missing = 0;

  for (let offset = 0; offset < candidates.length; offset += 50) {
    const batch = candidates.slice(offset, offset + 50);
    const videos = await fetchYoutubeVideos(
      [...new Set(batch.map((row) => row.videoId))],
      apiKey,
    );

    for (const candidate of batch) {
      const video = videos.get(candidate.videoId);
      if (!video) {
        missing += 1;
        await dbRun(
          "UPDATE hub_events SET live_availability = 'unavailable', updated_at = ? WHERE event_id = ?",
          [Date.now(), candidate.eventId],
        );
        continue;
      }

      await dbRun(
        "UPDATE hub_events SET live_availability = 'available', updated_at = ? WHERE event_id = ?",
        [Date.now(), candidate.eventId],
      );
      const nextStatus = deriveLiveStatus(video, candidate.currentStatus);
      if (nextStatus === candidate.currentStatus) continue;

      await updateHubLiveStatus(candidate.eventId, { liveStatus: nextStatus });
      changed += 1;
      console.log(
        `[youtube-live-sync] ${candidate.eventId} ${candidate.currentStatus} -> ${nextStatus}`,
      );
    }
  }

  return {
    checked: candidates.length,
    changed,
    missing,
  };
}

let timer = null;

export function startYouTubeLiveSync() {
  if (timer) return timer;

  if (process.env.YOUTUBE_LIVE_SYNC_ENABLED === "0") {
    console.log("[youtube-live-sync] disabled by YOUTUBE_LIVE_SYNC_ENABLED=0");
    return null;
  }

  if (!String(process.env.YOUTUBE_API_KEY || "").trim()) {
    console.log("[youtube-live-sync] disabled (YOUTUBE_API_KEY is not set)");
    return null;
  }

  const seconds = Math.max(60, intEnv("YOUTUBE_LIVE_SYNC_INTERVAL_SECONDS", 60));
  const run = () =>
    syncYouTubeLiveStatuses().catch((err) =>
      console.error("[youtube-live-sync] run failed:", err.message),
    );

  setTimeout(run, 10_000).unref();
  timer = setInterval(run, seconds * 1000);
  timer.unref();
  console.log(`[youtube-live-sync] scheduled every ${seconds}s`);
  return timer;
}
