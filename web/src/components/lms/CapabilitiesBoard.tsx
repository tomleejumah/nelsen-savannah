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
  /** Compact = labeled strips for muscle memory (learning hero). */
  variant?: "full" | "compact";
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
    <Link
      to={tool.to}
      hash={tool.hash}
      className={className}
    >
      {tool.label}
    </Link>
  );
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
