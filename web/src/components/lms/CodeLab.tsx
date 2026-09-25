import { useEffect, useMemo, useState } from "react";
import Editor from "@monaco-editor/react";
import { Play, RotateCcw } from "lucide-react";
import type { User } from "firebase/auth";

import { patchLessonProgress, runLessonLab, type LessonDto } from "@/lib/lmsApi";

const MONACO_LANG: Record<string, string> = {
  python: "python",
  javascript: "javascript",
  typescript: "typescript",
  java: "java",
  c: "c",
  cpp: "cpp",
  html: "html",
};

export function CodeLab({
  user,
  lesson,
  onProgress,
}: {
  user: User;
  lesson: LessonDto;
  onProgress?: (info: { lessonPercent?: number; trackPercent?: number; status?: string }) => void;
}) {
  const lab = lesson.lab;
  const storageKey = `ns-lab:${lesson.lessonId}`;
  const starter = lab?.starter || "print('hello')\n";
  const [source, setSource] = useState(starter);
  const [stdin, setStdin] = useState(lab?.stdin || "");
  const [out, setOut] = useState("");
  const [err, setErr] = useState("");
  const [html, setHtml] = useState<string | null>(null);
  const [passed, setPassed] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem(storageKey);
      setSource(saved || starter);
    } catch {
      setSource(starter);
    }
    setStdin(lab?.stdin || "");
    setOut("");
    setErr("");
    setHtml(null);
    setPassed(null);
  }, [lesson.lessonId, starter, lab?.stdin, storageKey]);

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, source);
    } catch {
      /* ignore quota */
    }
  }, [source, storageKey]);

  const monacoLang = useMemo(
    () => MONACO_LANG[lab?.language || "python"] || "python",
    [lab?.language],
  );

  async function run() {
    setBusy(true);
    setError(null);
    setPassed(null);
    try {
      const token = await user.getIdToken();
      const result = await runLessonLab(token, lesson.lessonId, {
        source,
        stdin,
      });
      if (!result.ok || !result.data) {
        setError(result.error || "Could not run code");
        return;
      }
      setOut(result.data.stdout || "");
      setErr(result.data.stderr || "");
      setHtml(result.data.html || null);
      setPassed(result.data.passed);
      const progress = await patchLessonProgress(token, lesson.lessonId, {
        opened: true,
        contentPct: result.data.contentPct,
        lastPlatform: "web",
      });
      if (progress.ok && progress.data) {
        onProgress?.({
          lessonPercent: progress.data.progress.lessonPercent,
          trackPercent: progress.data.progress.trackPercent,
          status: progress.data.progress.status,
        });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Run failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {lab?.language || "python"} lab · runs in the browser
        </p>
        <div className="flex gap-2">
          <button
            type="button"
            className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-sm hover:bg-accent"
            onClick={() => setSource(starter)}
          >
            <RotateCcw className="h-3.5 w-3.5" /> Reset
          </button>
          <button
            type="button"
            disabled={busy}
            className="inline-flex items-center gap-1.5 rounded-full bg-ember-gradient px-4 py-1.5 text-sm font-semibold text-maroon-foreground disabled:opacity-60"
            onClick={() => void run()}
          >
            <Play className="h-3.5 w-3.5" />
            {busy ? "Running…" : "Run"}
          </button>
        </div>
      </div>
      <div className="overflow-hidden rounded-xl border border-border">
        <Editor
          height="22rem"
          theme="vs-dark"
          language={monacoLang}
          value={source}
          onChange={(v) => setSource(v ?? "")}
          options={{
            minimap: { enabled: false },
            fontSize: 14,
            tabSize: 2,
            automaticLayout: true,
            scrollBeyondLastLine: false,
          }}
        />
      </div>
      {lab?.language !== "html" ? (
        <label className="block text-xs text-muted-foreground">
          stdin (optional)
          <textarea
            className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm text-foreground"
            rows={2}
            value={stdin}
            onChange={(e) => setStdin(e.target.value)}
          />
        </label>
      ) : null}
      {error ? (
        <p className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}
      {passed != null ? (
        <p
          className={`text-sm font-medium ${passed ? "text-emerald-700" : "text-ember"}`}
        >
          {passed
            ? lab?.expectedStdout
              ? "Output matches the expected result."
              : "Ran without errors."
            : "Not quite — check the output and try again."}
        </p>
      ) : null}
      {html ? (
        <iframe
          title="Lab preview"
          className="h-64 w-full rounded-xl border border-border bg-white"
          sandbox="allow-scripts"
          srcDoc={html}
        />
      ) : null}
      {out || err ? (
        <pre className="max-h-56 overflow-auto rounded-xl bg-zinc-950 p-4 font-mono text-xs text-zinc-100">
          {err ? <span className="text-red-400">{err}</span> : null}
          {out}
        </pre>
      ) : null}
    </div>
  );
}
