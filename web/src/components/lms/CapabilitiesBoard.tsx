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
  /** Compact = workspace chips + tool links in one strip (learning hero). */
  variant?: "full" | "compact";
  className?: string;
};

function toolHref(tool: LmsTool): string {
  return tool.hash ? `${tool.to}#${tool.hash}` : tool.to;
}

export function CapabilitiesBoard({
  me,
  activeShell,
  variant = "full",
  className,
}: Props) {
  const focus = activeShell ?? shellFromMe(me);
  const workspaces = workspacesForMe(me);
  const tools = toolsForShell(me, focus);

  if (variant === "compact") {
    return (
      <div className={cn("space-y-3", className)}>
        <div className="flex flex-wrap items-center justify-center gap-2">
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
        <div className="flex flex-wrap items-center justify-center gap-2">
          {tools.slice(0, 8).map((t) => (
            <a
              key={t.id}
              href={toolHref(t)}
              className="rounded-full border border-border/70 bg-card/60 px-3 py-1.5 text-xs font-medium text-muted-foreground transition-colors hover:text-maroon"
            >
              {t.label}
            </a>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className={cn("space-y-6", className)}>
      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Workspaces
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

      <div>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          Tools
        </p>
        <ul className="mt-3 grid gap-2 sm:grid-cols-2">
          {tools.map((t) => (
            <li key={t.id}>
              <a
                href={toolHref(t)}
                className="block rounded-2xl border border-border/60 bg-card/40 px-4 py-3 transition-colors hover:border-ember/40 hover:bg-card"
              >
                <p className="font-display text-sm font-semibold text-foreground">
                  {t.label}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">{t.blurb}</p>
              </a>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
