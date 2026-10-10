/**
 * Optional Redis-backed catalog cache (RESP2 over TCP/TLS).
 *
 * No additional npm dependency: production installs continue to use the
 * existing lockfile. Redis is only used when REDIS_URL is set. Every error
 * falls back to the authoritative LMS database; cache availability must never
 * prevent reads, writes or user authentication.
 *
 * Example: REDIS_URL=redis://127.0.0.1:6379/0
 * For hosted Redis use rediss:// and TLS.
 */
import net from "node:net";
import tls from "node:tls";

const MAX_REPLY_BYTES = 8 * 1024 * 1024;
let lastWarning = 0;

function warn(err) {
  if (Date.now() - lastWarning < 60_000) return;
  lastWarning = Date.now();
  console.warn("[lms-cache] Redis unavailable, using database:", err.message);
}

function encode(command) {
  const chunks = [Buffer.from(`*${command.length}\r\n`)];
  for (const part of command) {
    const value = Buffer.from(String(part), "utf8");
    chunks.push(Buffer.from(`$${value.length}\r\n`), value, Buffer.from("\r\n"));
  }
  return Buffer.concat(chunks);
}

function parseReply(buffer, offset = 0) {
  if (offset >= buffer.length) return null;
  const marker = String.fromCharCode(buffer[offset]);
  const eol = buffer.indexOf("\r\n", offset);
  if (eol < 0) return null;
  const header = buffer.toString("utf8", offset + 1, eol);
  if (marker === "+" || marker === "-" || marker === ":") {
    const value = marker === ":" ? Number(header) : header;
    return { value, error: marker === "-", next: eol + 2 };
  }
  if (marker !== "$") throw new Error("Unsupported Redis reply");
  const length = Number(header);
  if (!Number.isSafeInteger(length) || length < -1 || length > MAX_REPLY_BYTES) {
    throw new Error("Invalid Redis bulk reply length");
  }
  if (length === -1) return { value: null, next: eol + 2 };
  const start = eol + 2;
  if (buffer.length < start + length + 2) return null;
  if (buffer[start + length] !== 13 || buffer[start + length + 1] !== 10) {
    throw new Error("Invalid Redis bulk reply terminator");
  }
  return { value: buffer.toString("utf8", start, start + length), next: start + length + 2 };
}

/** Open one bounded connection, execute command, close. No unbounded queues. */
async function execRedis(...command) {
  const urlValue = process.env.REDIS_URL;
  if (!urlValue) return null;
  const url = new URL(urlValue);
  if (url.protocol !== "redis:" && url.protocol !== "rediss:") {
    throw new Error("REDIS_URL requires redis:// or rediss://");
  }
  const port = Number(url.port || 6379);
  const host = url.hostname;
  const database = url.pathname.replace(/^\//, "") || "0";
  if (!/^\d+$/.test(database)) throw new Error("Invalid Redis database");
  const commands = [];
  if (url.password) {
    commands.push(url.username
      ? ["AUTH", decodeURIComponent(url.username), decodeURIComponent(url.password)]
      : ["AUTH", decodeURIComponent(url.password)]);
  }
  if (database !== "0") commands.push(["SELECT", database]);
  commands.push(command);
  const payload = Buffer.concat(commands.map(encode));

  return new Promise((resolve, reject) => {
    const secure = url.protocol === "rediss:";
    const socket = secure
      ? tls.connect({ host, port, servername: host })
      : net.connect({ host, port });
    let buffer = Buffer.alloc(0);
    let expectedReply = 0;
    let settled = false;

    function finish(err, value) {
      if (settled) return;
      settled = true;
      socket.destroy();
      if (err) reject(err);
      else resolve(value);
    }
    socket.setTimeout(900, () => finish(new Error("Redis request timed out")));
    socket.on("error", (error) => finish(error));
    socket.on("close", () => {
      if (!settled) finish(new Error("Redis connection closed prematurely"));
    });
    socket.on("data", (chunk) => {
      if (settled) return;
      buffer = Buffer.concat([buffer, chunk]);
      if (buffer.length > MAX_REPLY_BYTES) return finish(new Error("Redis reply too large"));
      try {
        let offset = 0;
        while (expectedReply < commands.length) {
          const reply = parseReply(buffer, offset);
          if (!reply) break;
          if (reply.error) return finish(new Error(`Redis command failed: ${reply.value}`));
          expectedReply += 1;
          offset = reply.next;
          if (expectedReply === commands.length) return finish(null, reply.value);
        }
        if (offset) buffer = buffer.subarray(offset);
      } catch (error) {
        finish(error);
      }
    });
    socket.once(secure ? "secureConnect" : "connect", () => socket.write(payload));
  });
}

export function redisConfigured() {
  return Boolean(process.env.REDIS_URL);
}

export async function redisGetJson(key) {
  if (!redisConfigured()) return null;
  try {
    const value = await execRedis("GET", key);
    return value == null ? null : JSON.parse(value);
  } catch (error) {
    warn(error);
    return null;
  }
}

export async function redisSetJson(key, value, seconds) {
  if (!redisConfigured()) return false;
  try {
    await execRedis("SET", key, JSON.stringify(value), "EX", String(Math.max(1, Math.floor(seconds))));
    return true;
  } catch (error) {
    warn(error);
    return false;
  }
}

export async function redisDelete(key) {
  if (!redisConfigured()) return false;
  try {
    await execRedis("DEL", key);
    return true;
  } catch (error) {
    warn(error);
    return false;
  }
}

/** A versioned namespace avoids SCAN/KEYS during course catalog invalidation. */
export async function redisBumpVersion(key) {
  if (!redisConfigured()) return null;
  try {
    return await execRedis("INCR", key);
  } catch (error) {
    warn(error);
    return null;
  }
}

export async function redisGetVersion(key) {
  if (!redisConfigured()) return null;
  try {
    return Number((await execRedis("GET", key)) || 0);
  } catch (error) {
    warn(error);
    return null;
  }
}
