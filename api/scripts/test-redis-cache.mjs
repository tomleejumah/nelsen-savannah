import assert from "node:assert/strict";
import net from "node:net";
import test from "node:test";
import {
  redisGetJson, redisSetJson, redisDelete,
  redisGetVersion, redisBumpVersion,
} from "../src/services/lmsRedisCache.js";

function decodeCommand(data) {
  // Fixture-only RESP2 array decoder.
  const text = data.toString("utf8");
  const lines = text.split("\r\n");
  const argc = Number(lines[0].slice(1));
  const args = [];
  let i = 1;
  while (args.length < argc) {
    assert.equal(lines[i++][0], "$");
    args.push(lines[i++]);
  }
  return args;
}
function bulk(value) {
  if (value == null) return "$-1\r\n";
  return "$" + Buffer.byteLength(value) + "\r\n" + value + "\r\n";
}

test("Redis cache protocol: read, write, invalidation and namespace version", async () => {
  const values = new Map();
  const server = net.createServer((socket) => {
    socket.on("data", (chunk) => {
      const [action, key, value] = decodeCommand(chunk);
      if (action === "GET") socket.write(bulk(values.get(key) ?? null));
      else if (action === "SET") {
        values.set(key, value);
        socket.write("+OK\r\n");
      } else if (action === "DEL") {
        const deleted = Number(values.delete(key));
        socket.write(":" + deleted + "\r\n");
      } else if (action === "INCR") {
        const next = Number(values.get(key) || 0) + 1;
        values.set(key, String(next));
        socket.write(":" + next + "\r\n");
      } else socket.write("-ERR unsupported test command\r\n");
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const prior = process.env.REDIS_URL;
  process.env.REDIS_URL = `redis://127.0.0.1:${server.address().port}/0`;
  try {
    assert.deepEqual(await redisGetJson("school"), null);
    assert.equal(await redisSetJson("school", { name: "Nelsen", count: 2 }, 30), true);
    assert.deepEqual(await redisGetJson("school"), { name: "Nelsen", count: 2 });
    assert.equal(await redisGetVersion("catalog-version"), 0);
    assert.equal(await redisBumpVersion("catalog-version"), 1);
    assert.equal(await redisBumpVersion("catalog-version"), 2);
    assert.equal(await redisGetVersion("catalog-version"), 2);
    assert.equal(await redisDelete("school"), true);
    assert.equal(await redisGetJson("school"), null);
  } finally {
    if (prior === undefined) delete process.env.REDIS_URL;
    else process.env.REDIS_URL = prior;
    await new Promise((resolve) => server.close(resolve));
  }
});

test("Redis cache is optional and safe when unset", async () => {
  const prior = process.env.REDIS_URL;
  delete process.env.REDIS_URL;
  try {
    assert.equal(await redisGetJson("offline"), null);
    assert.equal(await redisSetJson("offline", 10, 1), false);
    assert.equal(await redisDelete("offline"), false);
    assert.equal(await redisGetVersion("offline"), null);
  } finally {
    if (prior !== undefined) process.env.REDIS_URL = prior;
  }
});
