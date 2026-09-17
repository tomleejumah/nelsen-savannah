import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";

import type { MeDto } from "@/lib/lmsApi";
import {
  toolsForShell,
  workspacesForMe,
  type LmsTool,
} from "@/lib/lmsCapabilities";
import { shellFromMe, type LmsShell } from "@/lib/lmsRoles";
import { cn } from "@/lib/utils";

type Props = {
  me: MeDto;
  /** Shell of the page we’re on (filters tools). */
  activeShell?: LmsShell;
  /** compact = pills; rail = sticky side progress; full = legacy cards (unused on shells). */
  variant?: "full" | "compact" | "rail";
  className?: string;
};

const SHELL_LABEL: Record<LmsShell, string> = {
  student: "Learning",
  mentor: "Teach",
  school: "School",
  admin: "Admin",
};

function ToolLink({
  tool,
  className,
}: {
  tool: LmsTool;
  className?: string;
}) {
  return (
    <Link to={tool.to} hash={tool.hash} className={className}>
      {tool.label}
    </Link>
  );
}

function useActiveHash(hashes: string[]) {
  const [active, setActive] = useState(() =>
    typeof window !== "undefined" ? window.location.hash.replace(/^#/, "") : "",
  );

  useEffect(() => {
    const onHash = () => setActive(window.location.hash.replace(/^#/, ""));
    window.addEventListener("hashchange", onHash);

    const ids = hashes.filter(Boolean);
    if (ids.length === 0) {
      return () => window.removeEventListener("hashchange", onHash);
    }

    const observed = new Map<string, number>();
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          observed.set(entry.target.id, entry.intersectionRatio);
        }
        let bestId = "";
        let bestRatio = 0;
        for (const id of ids) {
          const r = observed.get(id) ?? 0;
          if (r > bestRatio) {
            bestRatio = r;
            bestId = id;
          }
        }
        if (bestId && bestRatio > 0.12) setActive(bestId);
      },
      { rootMargin: "-15% 0px -55% 0px", threshold: [0, 0.15, 0.35, 0.6] },
    );

    for (const id of ids) {
      const el = document.getElementById(id);
      if (el) io.observe(el);
    }

    return () => {
      window.removeEventListener("hashchange", onHash);
      io.disconnect();
    };
  }, [hashes.join("|")]);

  return active;
}

export function CapabilitiesBoard({
  me,
  activeShell,
  variant = "full",
  className,
}: Props) {
  const focus = activeShell ?? shellFromMe(me);
  const workspaces = workspacesForMe(me);
  const tools = toolsForShell(me, focus).filter((t) => t.id !== "profile");
  const hashes = tools.map((t) => t.hash || "").filter(Boolean);
  const activeHash = useActiveHash(hashes);

  if (variant === "compact") {
    return (
      <div className={cn("space-y-5", className)}>
        {workspaces.length > 1 ? (
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              Workspace
            </p>
            <div className="mt-2 flex flex-wrap items-center justify-center gap-2">
              {workspaces.map((w) => (
                <Link
                  key={w.shell}
                  to={w.to}
                  className={cn(
                    "rounded-full border px-4 py-2 text-sm font-medium transition-colors",
                    w.shell === focus
                      ? "border-transparent bg-ember-gradient text-maroon-foreground"
                      : "border-border bg-card text-foreground hover:bg-accent",
                  )}
                >
                  {w.label}
                </Link>
              ))}
            </div>
          </div>
        ) : null}

        <div>
          <p className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
            In {SHELL_LABEL[focus]}
          </p>
          <div className="mt-2 flex flex-wrap items-center justify-center gap-2">
            {tools.slice(0, 8).map((t) => (
              <ToolLink
                key={t.id}
                tool={t}
                className="rounded-full border border-border/70 bg-card px-4 py-2 text-sm font-medium text-foreground transition-colors hover:border-ember/40 hover:text-maroon"
              />
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (variant === "rail") {
    return (
      <nav className={cn("space-y-6", className)} aria-label="Section progress">
        {workspaces.length > 1 ? (
          <div>
            <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
              Workspace
            </p>
            <ul className="mt-2 space-y-0.5">
              {workspaces.map((w) => (
                <li key={w.shell}>
                  <Link
                    to={w.to}
                    className={cn(
                      "block border-l-2 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.12em] transition-colors",
                      w.shell === focus
                        ? "border-foreground bg-secondary/60 text-foreground"
                        : "border-transparent text-muted-foreground hover:text-foreground",
                    )}
                  >
                    {w.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ) : null}

        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
            In {SHELL_LABEL[focus]}
          </p>
          <ul className="mt-2 space-y-0.5">
            {tools.map((t) => {
              const isActive = Boolean(t.hash) && t.hash === activeHash;
              return (
                <li key={`${t.id}-${t.hash || t.to}`}>
                  <Link
                    to={t.to}
                    hash={t.hash}
                    className={cn(
                      "block border-l-2 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.12em] transition-colors",
                      isActive
                        ? "border-foreground bg-secondary/60 text-foreground"
                        : "border-transparent text-muted-foreground hover:text-foreground",
                    )}
                    title={t.blurb}
                  >
                    {t.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>

        <div>
          <Link
            to="/profile"
            className="inline-flex w-full items-center justify-center bg-ember-gradient px-3 py-2.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-maroon-foreground"
          >
            Profile
          </Link>
        </div>
      </nav>
    );
  }

  return (
    <div className={cn("space-y-6", className)}>
      {workspaces.length > 1 ? (
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            Workspace
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {workspaces.map((w) => (
              <Link
                key={w.shell}
                to={w.to}
                className={cn(
                  "rounded-full border px-4 py-2 text-sm font-medium transition-colors",
                  w.shell === focus
                    ? "border-transparent bg-ember-gradient text-maroon-foreground"
                    : "border-border bg-card text-foreground hover:bg-accent",
                )}
              >
                {w.label}
              </Link>
            ))}
          </div>
        </div>
      ) : null}

      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          In {SHELL_LABEL[focus]}
        </p>
        <ul className="mt-3 grid gap-2 sm:grid-cols-2">
          {tools.map((t) => (
            <li key={t.id}>
              <Link
                to={t.to}
                hash={t.hash}
                className="block rounded-2xl border border-border/60 bg-card/40 px-4 py-3 transition-colors hover:border-ember/40 hover:bg-card"
              >
                <p className="font-display text-sm font-semibold text-foreground">
                  {t.label}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">{t.blurb}</p>
              </Link>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Account
        </p>
        <div className="mt-2 flex flex-wrap gap-2">
          <Link
            to="/profile"
            className="rounded-full border border-border bg-card px-4 py-2 text-sm font-medium text-foreground hover:bg-accent"
          >
            Profile
          </Link>
        </div>
      </div>
    </div>
  );
}
