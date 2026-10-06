import crypto from "node:crypto";
import { dbGet, dbRun } from "../db/lmsDb.js";
import { loadUserRole } from "../middleware/lmsRoles.js";
import { isSchoolAdmin, isSuperAdmin, ROLES } from "../constants/lmsRoles.js";
import {
  createHubEvent,
  resolveLiveAudience,
} from "./lmsHubEventService.js";

const FORCE_SSL_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl";
const PLATFORM_SCOPE_ID = "nelsen-platform";

function httpError(message, status = 400, code = null) {
  const err = new Error(message);
  err.status = status;
  if (code) err.code = code;
  return err;
}

function requiredEnv(name) {
  const value = String(process.env[name] || "").trim();
  if (!value) throw httpError(`${name} is not configured`, 503, "YOUTUBE_NOT_CONFIGURED");
  return value;
}

function encryptionKey() {
  const raw = requiredEnv("YOUTUBE_TOKEN_ENCRYPTION_KEY");
  if (/^[0-9a-f]{64}$/i.test(raw)) return Buffer.from(raw, "hex");
  try {
    const decoded = Buffer.from(raw, "base64");
    if (decoded.length === 32) return decoded;
  } catch {
    // fall through
  }
  throw httpError(
    "YOUTUBE_TOKEN_ENCRYPTION_KEY must be 32 bytes (64 hex chars or base64)",
    503,
    "YOUTUBE_NOT_CONFIGURED",
  );
}

function encryptSecret(value) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", encryptionKey(), iv);
  const encrypted = Buffer.concat([cipher.update(value, "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return [
    iv.toString("base64url"),
    tag.toString("base64url"),
    encrypted.toString("base64url"),
  ].join(".");
}

function decryptSecret(value) {
  const [ivRaw, tagRaw, dataRaw] = String(value || "").split(".");
  if (!ivRaw || !tagRaw || !dataRaw) throw httpError("Stored YouTube token is invalid", 500);
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    encryptionKey(),
    Buffer.from(ivRaw, "base64url"),
  );
  decipher.setAuthTag(Buffer.from(tagRaw, "base64url"));
  return Buffer.concat([
    decipher.update(Buffer.from(dataRaw, "base64url")),
    decipher.final(),
  ]).toString("utf8");
}

async function actorSchool(uid) {
  const user = await dbGet(
    "SELECT active_school_id FROM users_mirror WHERE uid = ?",
    [uid],
  );
  if (user?.active_school_id) {
    const selected = await dbGet(
      `SELECT school_id FROM school_memberships
       WHERE uid = ? AND school_id = ? AND status = 'active' LIMIT 1`,
      [uid, user.active_school_id],
    );
    if (selected?.school_id) return String(selected.school_id);
  }
  const membership = await dbGet(
    `SELECT school_id FROM school_memberships
     WHERE uid = ? AND status = 'active'
     ORDER BY updated_at DESC
     LIMIT 1`,
    [uid],
  );
  return String(membership?.school_id || "").trim();
}

async function hasActiveMembership(uid, schoolId) {
  const row = await dbGet(
    `SELECT 1 AS ok FROM school_memberships
     WHERE uid = ? AND school_id = ? AND status = 'active'
     LIMIT 1`,
    [uid, schoolId],
  );
  return Boolean(row);
}

async function connectionScopeForAdmin(actor, requestedSchoolId = "") {
  const uid = String(actor?.uid || "");
  const role = await loadUserRole(uid);
  const schoolId = String(requestedSchoolId || "").trim();

  if (isSuperAdmin(role)) {
    return schoolId
      ? { scopeType: "school", scopeId: schoolId }
      : { scopeType: "platform", scopeId: PLATFORM_SCOPE_ID };
  }

  if (!isSchoolAdmin(role)) {
    throw httpError("Only a SchoolAdmin or SuperAdmin can connect a YouTube channel", 403);
  }

  const ownSchool = await actorSchool(uid);
  const target = schoolId || ownSchool;
  if (!target) throw httpError("No active school selected", 400);
  if (target !== ownSchool) {
    throw httpError("Cannot manage another school's YouTube channel", 403);
  }
  return { scopeType: "school", scopeId: target };
}

function oauthRedirectUri() {
  return (
    String(process.env.YOUTUBE_OAUTH_REDIRECT_URI || "").trim()
    || "https://api.nelsen-savannah.co.ke/lms/youtube/oauth/callback"
  );
}

async function cleanupExpiredStates() {
  await dbRun("DELETE FROM youtube_oauth_states WHERE expires_at <= ?", [Date.now()]);
}

export async function createYouTubeConnectUrl(actor, body = {}) {
  const clientId = requiredEnv("YOUTUBE_OAUTH_CLIENT_ID");
  const { scopeType, scopeId } = await connectionScopeForAdmin(actor, body.schoolId);
  await cleanupExpiredStates();

  const state = crypto.randomBytes(32).toString("base64url");
  const now = Date.now();
  await dbRun(
    `INSERT INTO youtube_oauth_states
      (state, uid, scope_type, scope_id, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?, ?)`,
    [state, actor.uid, scopeType, scopeId, now, now + 10 * 60 * 1000],
  );

  const url = new URL("https://accounts.google.com/o/oauth2/v2/auth");
  url.searchParams.set("client_id", clientId);
  url.searchParams.set("redirect_uri", oauthRedirectUri());
  url.searchParams.set("response_type", "code");
  url.searchParams.set("scope", FORCE_SSL_SCOPE);
  url.searchParams.set("access_type", "offline");
  url.searchParams.set("include_granted_scopes", "true");
  url.searchParams.set("prompt", "consent select_account");
  url.searchParams.set("state", state);

  return { authUrl: url.toString(), scopeType, scopeId };
}

async function exchangeCode(code) {
  const body = new URLSearchParams({
    code,
    client_id: requiredEnv("YOUTUBE_OAUTH_CLIENT_ID"),
    client_secret: requiredEnv("YOUTUBE_OAUTH_CLIENT_SECRET"),
    redirect_uri: oauthRedirectUri(),
    grant_type: "authorization_code",
  });
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
    signal: AbortSignal.timeout(15_000),
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw httpError(
      json.error_description || json.error || "YouTube OAuth exchange failed",
      502,
      "YOUTUBE_OAUTH_FAILED",
    );
  }
  return json;
}

async function youtubeJson(url, accessToken, init = {}) {
  const response = await fetch(url, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      Authorization: `Bearer ${accessToken}`,
      ...(init.headers || {}),
    },
    signal: AbortSignal.timeout(20_000),
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message =
      json?.error?.message
      || json?.error_description
      || `YouTube API failed (HTTP ${response.status})`;
    const err = httpError(message, response.status === 401 ? 401 : 502, "YOUTUBE_API_FAILED");
    err.youtube = json;
    throw err;
  }
  return json;
}

async function authorizedChannel(accessToken) {
  const url = new URL("https://www.googleapis.com/youtube/v3/channels");
  url.searchParams.set("part", "id,snippet");
  url.searchParams.set("mine", "true");
  url.searchParams.set("maxResults", "1");
  const json = await youtubeJson(url, accessToken);
  const channel = json.items?.[0];
  if (!channel?.id) {
    throw httpError(
      "The authorized Google account does not have an accessible YouTube channel",
      400,
      "YOUTUBE_CHANNEL_MISSING",
    );
  }
  return {
    channelId: channel.id,
    channelTitle: String(channel.snippet?.title || "YouTube channel"),
  };
}

export async function completeYouTubeOAuth({ code, state }) {
  const stateRow = await dbGet(
    "SELECT * FROM youtube_oauth_states WHERE state = ?",
    [String(state || "")],
  );
  if (!stateRow || Number(stateRow.expires_at) <= Date.now()) {
    throw httpError("YouTube authorization session expired", 400, "YOUTUBE_OAUTH_EXPIRED");
  }

  const tokens = await exchangeCode(String(code || ""));
  const existing = await dbGet(
    `SELECT refresh_token_enc FROM youtube_channel_connections
     WHERE scope_type = ? AND scope_id = ?`,
    [stateRow.scope_type, stateRow.scope_id],
  );
  const refreshToken = String(tokens.refresh_token || "").trim()
    || (existing?.refresh_token_enc ? decryptSecret(existing.refresh_token_enc) : "");
  if (!refreshToken) {
    throw httpError(
      "Google did not return a refresh token; reconnect the channel and approve access",
      400,
      "YOUTUBE_REFRESH_TOKEN_MISSING",
    );
  }

  const channel = await authorizedChannel(tokens.access_token);
  const now = Date.now();
  await dbRun(
    `INSERT INTO youtube_channel_connections
      (scope_type, scope_id, channel_id, channel_title, refresh_token_enc,
       connected_by, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(scope_type, scope_id) DO UPDATE SET
       channel_id = excluded.channel_id,
       channel_title = excluded.channel_title,
       refresh_token_enc = excluded.refresh_token_enc,
       connected_by = excluded.connected_by,
       updated_at = excluded.updated_at`,
    [
      stateRow.scope_type,
      stateRow.scope_id,
      channel.channelId,
      channel.channelTitle,
      encryptSecret(refreshToken),
      stateRow.uid,
      now,
      now,
    ],
  );
  await dbRun("DELETE FROM youtube_oauth_states WHERE state = ?", [state]);

  return {
    ...channel,
    scopeType: stateRow.scope_type,
    scopeId: stateRow.scope_id,
  };
}

export async function getYouTubeConnection(actor, body = {}) {
  const { scopeType, scopeId } = await connectionScopeForAdmin(actor, body.schoolId);
  const row = await dbGet(
    `SELECT scope_type, scope_id, channel_id, channel_title, connected_by, updated_at
     FROM youtube_channel_connections
     WHERE scope_type = ? AND scope_id = ?`,
    [scopeType, scopeId],
  );
  return row
    ? {
        connected: true,
        scopeType: row.scope_type,
        scopeId: row.scope_id,
        channelId: row.channel_id,
        channelTitle: row.channel_title,
        connectedBy: row.connected_by,
        updatedAt: Number(row.updated_at),
      }
    : { connected: false, scopeType, scopeId };
}

async function refreshAccessToken(connection) {
  const body = new URLSearchParams({
    client_id: requiredEnv("YOUTUBE_OAUTH_CLIENT_ID"),
    client_secret: requiredEnv("YOUTUBE_OAUTH_CLIENT_SECRET"),
    refresh_token: decryptSecret(connection.refresh_token_enc),
    grant_type: "refresh_token",
  });
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
    signal: AbortSignal.timeout(15_000),
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok || !json.access_token) {
    throw httpError(
      json.error_description || "YouTube channel authorization must be reconnected",
      401,
      "YOUTUBE_RECONNECT_REQUIRED",
    );
  }
  return json.access_token;
}

async function connectionForAudience(audience) {
  if (audience.audienceScope === "platform") {
    const platform = await dbGet(
      `SELECT * FROM youtube_channel_connections
       WHERE scope_type = 'platform' AND scope_id = ?`,
      [PLATFORM_SCOPE_ID],
    );
    if (!platform) {
      throw httpError(
        "Nelsen YouTube channel is not connected yet",
        409,
        "YOUTUBE_CHANNEL_NOT_CONNECTED",
      );
    }
    return platform;
  }

  if (audience.schoolId) {
    const school = await dbGet(
      `SELECT * FROM youtube_channel_connections
       WHERE scope_type = 'school' AND scope_id = ?`,
      [audience.schoolId],
    );
    if (school) return school;
  }

  const fallback = await dbGet(
    `SELECT * FROM youtube_channel_connections
     WHERE scope_type = 'platform' AND scope_id = ?`,
    [PLATFORM_SCOPE_ID],
  );
  if (fallback) return fallback;

  throw httpError(
    "No YouTube channel is connected for this school or the Nelsen platform",
    409,
    "YOUTUBE_CHANNEL_NOT_CONNECTED",
  );
}

async function createBroadcast(accessToken, body) {
  const url = new URL("https://www.googleapis.com/youtube/v3/liveBroadcasts");
  url.searchParams.set("part", "id,snippet,contentDetails,status");
  const scheduled = Math.max(Number(body.date) || 0, Date.now() + 10_000);
  return youtubeJson(url, accessToken, {
    method: "POST",
    body: JSON.stringify({
      snippet: {
        title: String(body.title || "Nelsen Live").slice(0, 100),
        description: String(body.description || "").slice(0, 5000),
        scheduledStartTime: new Date(scheduled).toISOString(),
      },
      status: {
        privacyStatus: ["public", "private", "unlisted"].includes(body.youtubePrivacy)
          ? body.youtubePrivacy
          : "unlisted",
      },
      contentDetails: {
        enableAutoStart: true,
        enableAutoStop: true,
        enableEmbed: true,
        enableDvr: true,
        recordFromStart: true,
        latencyPreference: "low",
      },
    }),
  });
}

async function createStream(accessToken, title) {
  const url = new URL("https://www.googleapis.com/youtube/v3/liveStreams");
  url.searchParams.set("part", "id,snippet,cdn,contentDetails,status");
  return youtubeJson(url, accessToken, {
    method: "POST",
    body: JSON.stringify({
      snippet: { title: String(title || "Nelsen Live").slice(0, 100) },
      cdn: {
        ingestionType: "rtmp",
        resolution: "720p",
        frameRate: "30fps",
      },
      contentDetails: { isReusable: false },
    }),
  });
}

async function bindBroadcast(accessToken, broadcastId, streamId) {
  const url = new URL("https://www.googleapis.com/youtube/v3/liveBroadcasts/bind");
  url.searchParams.set("id", broadcastId);
  url.searchParams.set("streamId", streamId);
  url.searchParams.set("part", "id,contentDetails,status");
  return youtubeJson(url, accessToken, { method: "POST" });
}

async function deleteYoutubeResource(accessToken, resource, id) {
  if (!id) return;
  try {
    const url = new URL(`https://www.googleapis.com/youtube/v3/${resource}`);
    url.searchParams.set("id", id);
    await fetch(url, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${accessToken}` },
      signal: AbortSignal.timeout(10_000),
    });
  } catch (err) {
    console.warn(`[youtube-live] cleanup ${resource}/${id}: ${err.message}`);
  }
}

export async function createYouTubeLiveSession(actor, body = {}) {
  const role = await loadUserRole(actor.uid);
  if (![ROLES.Mentor, ROLES.SchoolAdmin, ROLES.SuperAdmin].includes(role)) {
    throw httpError("Only mentors, school admins and super admins can go live", 403);
  }

  const audience = await resolveLiveAudience(actor, body);
  const connection = await connectionForAudience(audience);
  const accessToken = await refreshAccessToken(connection);

  let broadcast = null;
  let stream = null;
  try {
    broadcast = await createBroadcast(accessToken, body);
    stream = await createStream(accessToken, body.title);
    await bindBroadcast(accessToken, broadcast.id, stream.id);

    const ingestion = stream.cdn?.ingestionInfo || {};
    const base = String(
      ingestion.rtmpsIngestionAddress || ingestion.ingestionAddress || "",
    ).replace(/\/$/, "");
    const streamName = String(ingestion.streamName || "");
    if (!base || !streamName) {
      throw httpError("YouTube did not return an ingest endpoint", 502, "YOUTUBE_INGEST_MISSING");
    }

    const youtubeUrl = `https://www.youtube.com/watch?v=${broadcast.id}`;
    const event = await createHubEvent(actor, {
      ...body,
      ...audience,
      eventType: "live",
      liveStatus: "scheduled",
      mode: "online",
      meetingLink: youtubeUrl,
      seats: 0,
      price: "",
    });

    try {
      await dbRun(
        `INSERT INTO youtube_live_sessions
          (event_id, broadcast_id, stream_id, channel_id, scope_type, scope_id, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(event_id) DO UPDATE SET
           broadcast_id = excluded.broadcast_id,
           stream_id = excluded.stream_id,
           channel_id = excluded.channel_id,
           scope_type = excluded.scope_type,
           scope_id = excluded.scope_id`,
        [
          event.eventId,
          broadcast.id,
          stream.id,
          connection.channel_id,
          connection.scope_type,
          connection.scope_id,
          Date.now(),
        ],
      );
    } catch (metadataErr) {
      console.warn("[youtube-live] session metadata:", metadataErr.message);
    }

    return {
      event,
      broadcastId: broadcast.id,
      streamId: stream.id,
      channelId: connection.channel_id,
      channelTitle: connection.channel_title,
      youtubeUrl,
      ingestUrl: `${base}/${streamName}`,
    };
  } catch (err) {
    await deleteYoutubeResource(accessToken, "liveBroadcasts", broadcast?.id);
    await deleteYoutubeResource(accessToken, "liveStreams", stream?.id);
    throw err;
  }
}


export async function getYouTubeLiveTelemetry(actor, eventId) {
  const session = await dbGet(
    "SELECT * FROM youtube_live_sessions WHERE event_id = ?",
    [String(eventId || "")],
  );
  if (!session) throw httpError("Live session metadata not found", 404);

  const event = await dbGet(
    "SELECT created_by, school_id FROM hub_events WHERE event_id = ?",
    [eventId],
  );
  const role = await loadUserRole(actor.uid);
  const ownSchool = await actorSchool(actor.uid);
  const allowed = String(event?.created_by || "") === String(actor.uid)
    || isSuperAdmin(role)
    || (isSchoolAdmin(role) && ownSchool && ownSchool === String(event?.school_id || ""));
  if (!allowed) throw httpError("You cannot view this live studio", 403);

  const connection = await dbGet(
    `SELECT * FROM youtube_channel_connections
     WHERE scope_type = ? AND scope_id = ?`,
    [session.scope_type, session.scope_id],
  );
  if (!connection) throw httpError("Channel connection not found", 409);
  const accessToken = await refreshAccessToken(connection);

  const videoUrl = new URL("https://www.googleapis.com/youtube/v3/videos");
  videoUrl.searchParams.set("part", "liveStreamingDetails,statistics");
  videoUrl.searchParams.set("id", session.broadcast_id);
  const videoJson = await youtubeJson(videoUrl, accessToken);
  const video = videoJson.items?.[0] || {};
  const details = video.liveStreamingDetails || {};
  const stats = video.statistics || {};
  const liveChatId = String(details.activeLiveChatId || "");

  let chat = [];
  if (liveChatId) {
    const chatUrl = new URL("https://www.googleapis.com/youtube/v3/liveChat/messages");
    chatUrl.searchParams.set("liveChatId", liveChatId);
    chatUrl.searchParams.set("part", "id,snippet,authorDetails");
    chatUrl.searchParams.set("maxResults", "20");
    const chatJson = await youtubeJson(chatUrl, accessToken);
    chat = (chatJson.items || []).slice(-20).map((item) => ({
      id: item.id,
      message: String(item.snippet?.displayMessage || ""),
      publishedAt: item.snippet?.publishedAt || null,
      author: String(item.authorDetails?.displayName || "Viewer"),
      avatarUrl: item.authorDetails?.profileImageUrl || null,
      isOwner: Boolean(item.authorDetails?.isChatOwner),
      isModerator: Boolean(item.authorDetails?.isChatModerator),
    }));
  }

  return {
    eventId,
    concurrentViewers: Number(details.concurrentViewers || 0),
    viewCount: Number(stats.viewCount || 0),
    likeCount: Number(stats.likeCount || 0),
    liveChatId: liveChatId || null,
    chat,
  };
}
