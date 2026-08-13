/**
 * S3-compatible driver. Targets Cloudflare R2 but the same code path works for
 * AWS S3, Backblaze B2 and MinIO by changing MEDIA_S3_* env vars only.
 * The bucket must stay private — every URL handed to a client is presigned.
 */

import {
  DeleteObjectCommand,
  GetObjectCommand,
  HeadObjectCommand,
  PutObjectCommand,
  S3Client,
} from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";

function requireEnv(names) {
  const missing = names.filter((n) => !process.env[n]?.trim());
  if (missing.length) {
    throw new Error(`missing env: ${missing.join(", ")}`);
  }
}

function resolveEndpoint() {
  const explicit = process.env.MEDIA_S3_ENDPOINT?.trim();
  if (explicit) return explicit.replace(/\/$/, "");
  const account = process.env.R2_ACCOUNT_ID?.trim();
  if (account) return `https://${account}.r2.cloudflarestorage.com`;
  throw new Error("MEDIA_S3_ENDPOINT or R2_ACCOUNT_ID");
}

export function createS3Driver() {
  requireEnv([
    "MEDIA_S3_ACCESS_KEY_ID",
    "MEDIA_S3_SECRET_ACCESS_KEY",
    "MEDIA_S3_BUCKET",
  ]);
  const endpoint = resolveEndpoint();
  const bucket = process.env.MEDIA_S3_BUCKET.trim();
  const region = process.env.MEDIA_S3_REGION?.trim() || "auto";

  const client = new S3Client({
    region,
    endpoint,
    forcePathStyle: process.env.MEDIA_S3_FORCE_PATH_STYLE === "1",
    credentials: {
      accessKeyId: process.env.MEDIA_S3_ACCESS_KEY_ID.trim(),
      secretAccessKey: process.env.MEDIA_S3_SECRET_ACCESS_KEY.trim(),
    },
  });

  return {
    name: "r2",
    bucket,
    endpoint,

    async presignPut({ objectKey, contentType, ttlSeconds }) {
      const url = await getSignedUrl(
        client,
        new PutObjectCommand({
          Bucket: bucket,
          Key: objectKey,
          ContentType: contentType || "application/octet-stream",
        }),
        { expiresIn: ttlSeconds },
      );
      return {
        url,
        method: "PUT",
        headers: { "Content-Type": contentType || "application/octet-stream" },
        expiresAt: Math.floor(Date.now() / 1000) + ttlSeconds,
      };
    },

    async presignGet({ objectKey, ttlSeconds, filename, contentType }) {
      const url = await getSignedUrl(
        client,
        new GetObjectCommand({
          Bucket: bucket,
          Key: objectKey,
          ...(contentType ? { ResponseContentType: contentType } : {}),
          ...(filename
            ? {
                ResponseContentDisposition: `inline; filename="${String(filename).replace(/["\\]/g, "")}"`,
              }
            : {}),
        }),
        { expiresIn: ttlSeconds },
      );
      return {
        url,
        expiresAt: Math.floor(Date.now() / 1000) + ttlSeconds,
      };
    },

    async head({ objectKey }) {
      try {
        const out = await client.send(
          new HeadObjectCommand({ Bucket: bucket, Key: objectKey }),
        );
        return {
          sizeBytes: Number(out.ContentLength) || 0,
          contentType: out.ContentType || null,
          checksum: out.ETag ? out.ETag.replace(/"/g, "") : null,
        };
      } catch (err) {
        if (
          err?.$metadata?.httpStatusCode === 404 ||
          err?.name === "NotFound" ||
          err?.name === "NoSuchKey"
        ) {
          return null;
        }
        throw err;
      }
    },

    async remove({ objectKey }) {
      await client.send(
        new DeleteObjectCommand({ Bucket: bucket, Key: objectKey }),
      );
    },
  };
}
