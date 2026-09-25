/**
 * In-browser coding labs: enrolled learners submit source; we run it on Piston
 * (or PISTON_URL) so untrusted code never executes on this API process.
 */

import { dbGet } from "../db/lmsDb.js";

const PISTON = (process.env.PISTON_URL || "https://emkc.org/api/v2/piston").replace(
  /\/$/,
  "",
);
const MAX_SOURCE = 80_000;
const runs = new Map();

const LANG_FILES = {
  python: "main.py",
  javascript: "main.js",
  typescript: "main.ts",
  java: "Main.java",
  c: "main.c",
  cpp: "main.cpp",
  html: "index.html",
};

export function parseLab(row = {}) {
  let spec = {};
  const raw = row.lab_json || row.labJson;
  if (raw) {
    try {
      spec = typeof raw === "string" ? JSON.parse(raw) : raw;
    } catch {
      spec = {};
    }
  }
  let language = String(spec.language || "python").toLowerCase().trim();
  if (language === "js") language = "javascript";
  if (language === "py") language = "python";
  if (language === "c++") language = "cpp";
  const lang = LANG_FILES[language] ? language : "python";
  return {
    language: lang,
    starter: String(spec.starter ?? defaultStarter(lang)),
    stdin: String(spec.stdin || ""),
    expectedStdout:
      spec.expectedStdout != null ? String(spec.expectedStdout) : null,
  };
}

export function serializeLab(body = {}) {
  const lab = parseLab({
    lab_json: JSON.stringify({
      language: body.language,
      starter: body.starter,
      stdin: body.stdin,
      expectedStdout: body.expectedStdout,
    }),
  });
  return JSON.stringify(lab);
}

function defaultStarter(lang) {
  if (lang === "javascript") return "console.log('hello');\n";
  if (lang === "html") return "<h1>hello</h1>\n";
  if (lang === "java") {
    return "public class Main {\n  public static void main(String[] args) {\n    System.out.println(\"hello\");\n  }\n}\n";
  }
  if (lang === "c") return "#include <stdio.h>\nint main() {\n  printf(\"hello\\n\");\n  return 0;\n}\n";
  if (lang === "cpp") {
    return "#include <iostream>\nint main() {\n  std::cout << \"hello\\n\";\n  return 0;\n}\n";
  }
  if (lang === "typescript") return "console.log('hello');\n";
  return "print('hello')\n";
}

function normalizeOut(s) {
  return String(s || "")
    .replace(/\r\n/g, "\n")
    .replace(/[ \t]+$/gm, "")
    .trim();
}

function rateLimit(uid) {
  const now = Date.now();
  const windowMs = 60_000;
  const prev = (runs.get(uid) || []).filter((t) => now - t < windowMs);
  if (prev.length >= 20) {
    const err = new Error("Too many runs — wait a minute");
    err.status = 429;
    throw err;
  }
  prev.push(now);
  runs.set(uid, prev);
}

async function pistonRuntime(language) {
  const res = await fetch(`${PISTON}/runtimes`, { signal: AbortSignal.timeout(8000) });
  if (!res.ok) throw new Error(`Runner unavailable (${res.status})`);
  const list = await res.json();
  const hit = (list || []).find(
    (r) =>
      r.language === language ||
      (Array.isArray(r.aliases) && r.aliases.includes(language)),
  );
  if (!hit) {
    const err = new Error(`Language ${language} is not available`);
    err.status = 400;
    throw err;
  }
  return hit;
}

export async function runLessonLab(uid, lessonId, body = {}) {
  const lesson = await dbGet("SELECT * FROM lessons WHERE lesson_id = ?", [
    lessonId,
  ]);
  if (!lesson) {
    const err = new Error("Lesson not found");
    err.status = 404;
    throw err;
  }
  const type = String(lesson.type || "").toLowerCase();
  if (type !== "code" && type !== "lab") {
    const err = new Error("This lesson is not a coding lab");
    err.status = 400;
    throw err;
  }
  const enrolled = await dbGet(
    "SELECT uid FROM enrollments WHERE uid = ? AND track_id = ?",
    [uid, lesson.track_id],
  );
  if (!enrolled) {
    const err = new Error("Enroll in this course to run code");
    err.status = 403;
    throw err;
  }

  const lab = parseLab(lesson);
  const source = String(body.source ?? "");
  if (!source.trim()) {
    const err = new Error("Write some code first");
    err.status = 400;
    throw err;
  }
  if (source.length > MAX_SOURCE) {
    const err = new Error("Source is too large");
    err.status = 413;
    throw err;
  }
  rateLimit(uid);

  if (lab.language === "html") {
    return {
      language: "html",
      html: source,
      stdout: "",
      stderr: "",
      passed: true,
      contentPct: 100,
    };
  }

  const runtime = await pistonRuntime(lab.language);
  const res = await fetch(`${PISTON}/execute`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      language: runtime.language,
      version: runtime.version,
      files: [
        {
          name: LANG_FILES[lab.language] || "main.txt",
          content: source,
        },
      ],
      stdin: String(body.stdin ?? lab.stdin ?? ""),
      compile_timeout: 10000,
      run_timeout: 8000,
    }),
    signal: AbortSignal.timeout(20000),
  });
  if (!res.ok) {
    const err = new Error("Code runner failed");
    err.status = 502;
    throw err;
  }
  const data = await res.json();
  const stdout = `${data.compile?.stdout || ""}${data.run?.stdout || ""}`;
  const stderr = `${data.compile?.stderr || ""}${data.run?.stderr || ""}`;
  const exitCode = Number(data.run?.code ?? 1);
  let passed;
  if (lab.expectedStdout != null && lab.expectedStdout !== "") {
    passed = normalizeOut(stdout) === normalizeOut(lab.expectedStdout);
  } else {
    passed = exitCode === 0 && !stderr.trim();
  }
  return {
    language: lab.language,
    stdout,
    stderr,
    exitCode,
    passed,
    contentPct: passed ? 100 : 50,
  };
}
