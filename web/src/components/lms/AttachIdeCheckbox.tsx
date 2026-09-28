type Props = {
  checked: boolean;
  onChange: (next: boolean) => void;
  /** Extra line under the label (create vs edit copy). */
  hint?: string;
};

/** Course-level flag: attach in-browser Monaco code lab for coding lessons. */
export function AttachIdeCheckbox({ checked, onChange, hint }: Props) {
  return (
    <label className="flex cursor-pointer items-start gap-2 text-xs">
      <input
        type="checkbox"
        className="mt-0.5"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span>
        <span className="font-medium text-foreground">Attach IDE</span>
        {hint ? (
          <span className="mt-0.5 block text-muted-foreground">{hint}</span>
        ) : null}
      </span>
    </label>
  );
}
