import { useMemo, useState } from "react";
import Editor from "@monaco-editor/react";
import {
  Background,
  Controls,
  MiniMap,
  Position,
  ReactFlow,
  type Edge,
  type Node,
} from "@xyflow/react";
import { Play, RotateCcw, X } from "lucide-react";
import type { User } from "firebase/auth";

import { patchLessonProgress, runLessonLab, type LessonDto } from "@/lib/lmsApi";

import "@xyflow/react/dist/style.css";

const MONACO_LANG: Record<string, string> = {
  python: "python",
  javascript: "javascript",
  typescript: "typescript",
  java: "java",
  c: "c",
  cpp: "cpp",
  html: "html",
};

export function CodeWorkspace({
  user,
  lesson,
  onClose,
  onProgress,
}: {
  user: User;
  lesson: LessonDto;
  onClose: () => void;
  onProgress?: (info: {
    lessonPercent?: number;
    trackPercent?: number;
    status?: string;
  }) => void;
}) {
  const lab = lesson.lab;

  const starter = lab?.starter || "print('hello')\n";

  const [source, setSource] = useState(starter);
  const [stdin, setStdin] = useState(lab?.stdin || "");

  const [out, setOut] = useState("");
  const [err, setErr] = useState("");
  const [html, setHtml] = useState<string | null>(null);

  const [passed, setPassed] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<"code" | "flow">("code");

  const monacoLang = useMemo(
    () => MONACO_LANG[lab?.language || "python"] || "python",
    [lab?.language],
  );

  /*
   * React Flow graph.
   *
   * This is intentionally simple for now.
   * Later we can make the graph represent the actual
   * lesson/programming workflow dynamically.
   */
  const nodes = useMemo<Node[]>(
    () => [
      {
        id: "lesson",
        type: "input",
        position: { x: 40, y: 150 },
        data: {
          label: "Lesson",
        },
        sourcePosition: Position.Right,
      },
      {
        id: "code",
        position: { x: 300, y: 150 },
        data: {
          label: "Write Code",
        },
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
      },
      {
        id: "run",
        position: { x: 560, y: 150 },
        data: {
          label: "Run",
        },
        sourcePosition: Position.Right,
        targetPosition: Position.Left,
      },
      {
        id: "result",
        type: "output",
        position: { x: 800, y: 150 },
        data: {
          label: passed === true
            ? "Passed"
            : passed === false
              ? "Try Again"
              : "Check Output",
        },
        targetPosition: Position.Left,
      },
    ],
    [passed],
  );

  const edges = useMemo<Edge[]>(
    () => [
      {
        id: "lesson-code",
        source: "lesson",
        target: "code",
        animated: true,
      },
      {
        id: "code-run",
        source: "code",
        target: "run",
        animated: true,
      },
      {
        id: "run-result",
        source: "run",
        target: "result",
        animated: true,
      },
    ],
    [],
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

      /*
       * Keep the existing LMS progress behaviour.
       */
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

  function resetCode() {
    setSource(starter);
    setStdin(lab?.stdin || "");
    setOut("");
    setErr("");
    setHtml(null);
    setPassed(null);
    setError(null);
  }

  return (
    <div className="fixed inset-0 z-50 flex bg-background">
      {/* =========================
          LESSON SIDE
          ========================= */}
      <section className="hidden min-w-0 flex-1 flex-col border-r border-border lg:flex">
        <header className="flex h-14 shrink-0 items-center border-b border-border px-5">
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Lesson
            </p>

            <p className="truncate font-semibold">
              {lesson.title}
            </p>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto p-8">
          <div className="max-w-3xl">
            <p className="text-sm leading-7 text-muted-foreground">
              {lesson.does ||
                "Work through the lesson and use the IDE to practice."}
            </p>

            {lesson.bodyHtml ? (
              <div
                className="prose mt-6 max-w-none dark:prose-invert"
                dangerouslySetInnerHTML={{
                  __html: lesson.bodyHtml,
                }}
              />
            ) : null}
          </div>
        </div>
      </section>

      {/* =========================
          IDE SIDE
          ========================= */}
      <section className="flex w-full min-w-0 flex-col lg:w-[48%] lg:min-w-[520px]">
        {/* HEADER */}
        <header className="flex h-14 shrink-0 items-center justify-between border-b border-border px-3">
          {/* TABS */}
          <div className="flex items-center gap-1 rounded-lg bg-secondary p-1">
            <button
              type="button"
              onClick={() => setActiveTab("code")}
              className={`rounded-md px-3 py-1.5 text-sm transition ${
                activeTab === "code"
                  ? "bg-background font-medium shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Code
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("flow")}
              className={`rounded-md px-3 py-1.5 text-sm transition ${
                activeTab === "flow"
                  ? "bg-background font-medium shadow-sm"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              Flow
            </button>
          </div>

          {/* CLOSE */}
          <button
            type="button"
            onClick={onClose}
            className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-sm hover:bg-accent"
          >
            <X className="h-4 w-4" />
            Close IDE
          </button>
        </header>

        {/* =========================
            FLOW TAB
            ========================= */}
        {activeTab === "flow" ? (
          <div className="min-h-0 flex-1">
            <ReactFlow
              nodes={nodes}
              edges={edges}
              fitView
              fitViewOptions={{
                padding: 0.25,
              }}
              attributionPosition="bottom-left"
            >
              <Background />
              <MiniMap />
              <Controls />
            </ReactFlow>
          </div>
        ) : (
          /* =========================
             CODE TAB
             ========================= */
          <div className="min-h-0 flex-1 overflow-y-auto p-3">
            {/* TOOLBAR */}
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  {lab?.language || "python"} lab
                </p>

                <p className="text-xs text-muted-foreground">
                  Write, run and test your code
                </p>
              </div>

              <div className="flex gap-2">
                {/* RESET */}
                <button
                  type="button"
                  onClick={resetCode}
                  className="inline-flex items-center gap-1.5 rounded-full border border-border px-3 py-1.5 text-sm hover:bg-accent"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  Reset
                </button>

                {/* RUN */}
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void run()}
                  className="inline-flex items-center gap-1.5 rounded-full bg-ember-gradient px-4 py-1.5 text-sm font-semibold text-maroon-foreground disabled:opacity-60"
                >
                  <Play className="h-3.5 w-3.5" />

                  {busy ? "Running…" : "Run"}
                </button>
              </div>
            </div>

            {/* MONACO */}
            <div className="overflow-hidden rounded-xl border border-border">
              <Editor
                height="calc(100vh - 18rem)"
                theme="vs-dark"
                language={monacoLang}
                value={source}
                onChange={(value) => setSource(value ?? "")}
                options={{
                  minimap: {
                    enabled: false,
                  },
                  fontSize: 14,
                  tabSize: 2,
                  automaticLayout: true,
                  scrollBeyondLastLine: false,
                  padding: {
                    top: 12,
                    bottom: 12,
                  },
                }}
              />
            </div>

            {/* STDIN */}
            {lab?.language !== "html" ? (
              <label className="mt-3 block text-xs text-muted-foreground">
                stdin (optional)

                <textarea
                  className="mt-1 w-full rounded-lg border border-border bg-background px-3 py-2 font-mono text-sm text-foreground outline-none focus:ring-2 focus:ring-maroon/30"
                  rows={2}
                  value={stdin}
                  onChange={(e) => setStdin(e.target.value)}
                  placeholder="Input passed to your program..."
                />
              </label>
            ) : null}

            {/* ERROR */}
            {error ? (
              <p className="mt-3 rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {error}
              </p>
            ) : null}

            {/* RESULT STATUS */}
            {passed !== null ? (
              <div
                className={`mt-3 rounded-lg px-3 py-2 text-sm font-medium ${
                  passed
                    ? "bg-emerald-500/10 text-emerald-700"
                    : "bg-ember/10 text-ember"
                }`}
              >
                {passed
                  ? lab?.expectedStdout
                    ? "Output matches the expected result."
                    : "Code ran without errors."
                  : "Not quite — check the output and try again."}
              </div>
            ) : null}

            {/* HTML PREVIEW */}
            {html ? (
              <div className="mt-3">
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Preview
                </p>

                <iframe
                  title="Lab preview"
                  className="h-64 w-full rounded-xl border border-border bg-white"
                  sandbox="allow-scripts"
                  srcDoc={html}
                />
              </div>
            ) : null}

            {/* OUTPUT */}
            {out || err ? (
              <div className="mt-3">
                <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Output
                </p>

                <pre className="max-h-56 overflow-auto rounded-xl bg-zinc-950 p-4 font-mono text-xs text-zinc-100">
                  {err ? (
                    <span className="text-red-400">
                      {err}
                    </span>
                  ) : null}

                  {out}
                </pre>
              </div>
            ) : null}
          </div>
        )}
      </section>
    </div>
  );
}
