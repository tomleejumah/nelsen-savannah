type Props = {
  emails: string;
  onEmailsChange: (v: string) => void;
  role: "Mentee" | "Mentor";
  onRoleChange: (v: "Mentee" | "Mentor") => void;
  onSubmit: (e: React.FormEvent) => void;
  /** Page default label context */
  defaultHint?: string;
};

/** Single invite: comma-separated emails + role. Sends join-link mail. */
export function SchoolInviteForm({
  emails,
  onEmailsChange,
  role,
  onRoleChange,
  onSubmit,
  defaultHint,
}: Props) {
  return (
    <form onSubmit={onSubmit} className="space-y-3">
      <h3 className="font-display text-lg font-semibold">Invite by email</h3>
      <p className="text-xs text-muted-foreground">
        {defaultHint ||
          "We try to email a join link when mail is configured; you can always copy from Invites sent below."}{" "}
        Multiple addresses: separate with commas.
      </p>
      <input
        required
        value={emails}
        onChange={(e) => onEmailsChange(e.target.value)}
        placeholder="jane@school.com, bob@school.com"
        className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
      />
      <label className="block space-y-1 text-xs">
        <span className="text-muted-foreground">Role</span>
        <select
          value={role}
          onChange={(e) =>
            onRoleChange(e.target.value === "Mentor" ? "Mentor" : "Mentee")
          }
          className="w-full rounded-xl border border-border bg-background px-3 py-2 text-sm"
        >
          <option value="Mentee">Student (Mentee)</option>
          <option value="Mentor">Mentor</option>
        </select>
      </label>
      <button
        type="submit"
        className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground"
      >
        Send invite
      </button>
    </form>
  );
}
