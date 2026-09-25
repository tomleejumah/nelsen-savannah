#!/usr/bin/env node
/**
 * Copy pdf.js worker to public/ at a stable URL.
 * Hashed Vite assets break after rsync --delete when a tab still references
 * an old /assets/pdf.worker.min-*.mjs hash (fake-worker setup then fails).
 */
import { copyFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const src = join(root, "node_modules/pdfjs-dist/build/pdf.worker.min.mjs");
const destJs = join(root, "public/pdf.worker.min.js");
const destMjs = join(root, "public/pdf.worker.min.mjs");

if (!existsSync(src)) {
  console.error("[copy-pdf-worker] missing", src);
  process.exit(1);
}
mkdirSync(dirname(destJs), { recursive: true });
copyFileSync(src, destJs);
copyFileSync(src, destMjs);
console.log("[copy-pdf-worker] -> public/pdf.worker.min.js");
