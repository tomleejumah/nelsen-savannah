export function SchoolPctBar({ label, pct }: { label: string; pct: number }) {
  const n = Math.max(0, Math.min(100, Math.round(Number(pct) || 0)));
  return (
    <div className="mt-3">
      <div className="flex justify-between text-xs text-muted-foreground">
        <span>{label}</span>
        <span className="tabular-nums text-foreground">{n}%</span>
      </div>
      <div className="mt-1 h-2 overflow-hidden rounded-full bg-muted">
        <div
          className="h-2 rounded-full bg-ember"
          style={{ width: `${n}%` }}
        />
      </div>
    </div>
  );
}
