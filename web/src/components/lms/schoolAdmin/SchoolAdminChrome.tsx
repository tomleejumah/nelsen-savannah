import { Link } from "@tanstack/react-router";

export function SchoolAdminChrome({
  title,
  blurb,
  msg,
  lastInviteUrl,
  onCopyInvite,
  children,
}: {
  title: string;
  blurb: string;
  msg?: string | null;
  lastInviteUrl?: string | null;
  onCopyInvite?: (url: string) => void;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-8">
      <div>
        <Link
          to="/school"
          className="text-xs font-medium text-ember underline-offset-2 hover:underline"
        >
          ← School admin
        </Link>
        <h2 className="mt-3 font-display text-2xl font-semibold">{title}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{blurb}</p>
      </div>
      {msg ? <p className="text-sm text-ember">{msg}</p> : null}
      {lastInviteUrl ? (
        <div className="flex flex-wrap items-center gap-2 rounded-xl border border-border/70 bg-card/40 px-4 py-3 text-sm">
          <span className="text-muted-foreground">Join link:</span>
          <code className="max-w-full break-all text-xs">{lastInviteUrl}</code>
          {onCopyInvite ? (
            <button
              type="button"
              onClick={() => onCopyInvite(lastInviteUrl)}
              className="cursor-pointer rounded-full border border-border px-3 py-1 text-xs font-medium"
            >
              Copy
            </button>
          ) : null}
        </div>
      ) : null}
      {children}
    </div>
  );
}
