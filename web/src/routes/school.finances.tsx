import { createFileRoute } from "@tanstack/react-router";
import type { User } from "firebase/auth";

import { RoleShellPage } from "@/components/lms/RoleShellPage";
import { SchoolAdminChrome } from "@/components/lms/schoolAdmin/SchoolAdminChrome";
import { useSchoolAdmin } from "@/components/lms/schoolAdmin/useSchoolAdmin";
import type { MeDto } from "@/lib/lmsApi";

export const Route = createFileRoute("/school/finances")({
  head: () => ({
    meta: [{ title: "Finances — School admin" }],
  }),
  component: FinancesPage,
});

function FinancesPage() {
  return (
    <RoleShellPage
      shell="school"
      title="Finances"
      blurb="School balance, seats, and tutor payouts."
    >
      {({ user, me }) => <FinancesConsole user={user} me={me} />}
    </RoleShellPage>
  );
}

function FinancesConsole({ user, me }: { user: User; me: MeDto }) {
  const a = useSchoolAdmin(user, me);

  return (
    <SchoolAdminChrome
      title="Finances"
      blurb="Revenue, seat purchases, and tutor payouts for this school."
      msg={a.msg}
    >
      <section className="space-y-3 rounded-2xl border border-dashed border-border/70 bg-card/30 p-5">
        <h3 className="font-display text-lg font-semibold">Revenue</h3>
        <p className="text-sm text-muted-foreground">
          Coming soon. Seat balance shown for ops only:{" "}
          <span className="font-semibold text-ember">
            KES {a.balance.toLocaleString()}
          </span>
        </p>
        {a.moneyNote ? (
          <p className="text-xs text-muted-foreground">{a.moneyNote}</p>
        ) : null}
      </section>

      <section className="space-y-3 rounded-2xl border border-border/70 bg-card/50 p-5">
        <h3 className="font-display text-lg font-semibold">Tutor payouts</h3>
        <p className="text-sm text-muted-foreground">
          Mentors/tutors are paid from <em>this school’s</em> balance — not a
          shared platform pot.
        </p>
        {a.payoutNote ? (
          <p className="text-xs text-muted-foreground">{a.payoutNote}</p>
        ) : null}
        <p className="text-sm text-muted-foreground">
          No payout rows yet (prototype stub).
        </p>
      </section>

      <section className="space-y-3">
        <h3 className="font-display text-lg font-semibold">
          Buy seats (prototype)
        </h3>
        <p className="text-sm text-muted-foreground">
          Checkout stub — funds will credit <strong>this school’s</strong>{" "}
          ledger after the cut.
        </p>
        <div className="flex flex-wrap gap-2">
          <label className="text-xs text-muted-foreground">
            Seats
            <input
              type="number"
              min={1}
              max={500}
              value={a.seats}
              onChange={(e) => a.setSeats(Number(e.target.value) || 1)}
              className="ml-2 w-24 rounded-xl border border-border bg-background px-3 py-2 text-sm text-foreground"
            />
          </label>
          <input
            value={a.phone}
            onChange={(e) => a.setPhone(e.target.value)}
            placeholder="M-Pesa phone 254…"
            className="min-w-[10rem] flex-1 rounded-xl border border-border bg-background px-3 py-2 text-sm"
          />
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled
            title="Card payments coming soon"
            className="cursor-not-allowed rounded-full bg-ember-gradient/50 px-4 py-2 text-sm font-semibold text-maroon-foreground opacity-70"
          >
            Pay with card (coming soon)
          </button>
          <button
            type="button"
            onClick={() => {
              a.setPayMethod("mpesa");
              if (!a.phone.trim()) {
                a.setPayMsg("Enter an M-Pesa phone (254…) first.");
                return;
              }
              a.setPayMsg(
                `M-Pesa STK UI ready for ${a.phone.trim()} · ${a.seats} seats. Provider not wired yet.`,
              );
            }}
            className="rounded-full border border-border px-4 py-2 text-sm font-medium"
          >
            Pay with M-Pesa
          </button>
        </div>
        {a.payMsg ? (
          <p className="rounded-xl border border-border/60 bg-card/40 px-4 py-3 text-sm text-muted-foreground">
            <span className="font-medium text-ember">
              {a.payMethod === "mpesa" ? "M-Pesa" : "Card"}
            </span>
            {" — "}
            {a.payMsg}
          </p>
        ) : null}
      </section>
    </SchoolAdminChrome>
  );
}
