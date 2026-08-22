import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";

import { PROGRAMS } from "@/data/site";
import { type AppEvent, eventDateLabel, eventVenue } from "@/data/events";
import { LMS_API_BASE, reserveEventSeat } from "@/lib/lmsApi";

type Props = {
  event: AppEvent;
  open: boolean;
  onClose: () => void;
  onReserved: (eventId: string) => void;
};

export function ReserveSeatDialog({ event, open, onClose, onReserved }: Props) {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [program, setProgram] = useState(PROGRAMS[0]?.title ?? "");
  const [submitting, setSubmitting] = useState(false);

  if (!open) return null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!fullName.trim() || !email.trim()) {
      toast.error("Full name and email are required");
      return;
    }
    setSubmitting(true);
    try {
      await reserveEventSeat(event.eventId, {
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        program: program.trim() || undefined,
      });
      onReserved(event.eventId);
      toast.success("Seat reserved", {
        description: `${event.title} · ${eventDateLabel(event)}. We will email you at ${email.trim()}.`,
      });
      onClose();
      setFullName("");
      setEmail("");
      setPhone("");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not reserve seat");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 p-4 sm:items-center"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reserve-seat-title"
      onClick={onClose}
    >
      <form
        onSubmit={submit}
        onClick={(ev) => ev.stopPropagation()}
        className="w-full max-w-md rounded-3xl border border-border/70 bg-card p-6 shadow-elevated sm:p-8"
      >
        <p className="eyebrow text-ember">Free reservation</p>
        <h2 id="reserve-seat-title" className="mt-2 text-xl font-bold">
          {event.title}
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {eventDateLabel(event)} · {eventVenue(event)} · {event.price ?? "Free"}
        </p>

        <div className="mt-5 grid gap-3">
          <label className="grid gap-1.5 text-sm">
            <span className="font-medium">Full name</span>
            <input
              required
              autoFocus
              value={fullName}
              onChange={(ev) => setFullName(ev.target.value)}
              placeholder="Your full name"
              className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </label>
          <label className="grid gap-1.5 text-sm">
            <span className="font-medium">Email</span>
            <input
              required
              type="email"
              value={email}
              onChange={(ev) => setEmail(ev.target.value)}
              placeholder="you@email.com"
              className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </label>
          <label className="grid gap-1.5 text-sm">
            <span className="font-medium">Phone</span>
            <input
              type="tel"
              value={phone}
              onChange={(ev) => setPhone(ev.target.value)}
              placeholder="+254 7…"
              className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </label>
          <label className="grid gap-1.5 text-sm">
            <span className="font-medium">Programme of interest</span>
            <select
              value={program}
              onChange={(ev) => setProgram(ev.target.value)}
              className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            >
              {PROGRAMS.map((p) => (
                <option key={p.slug} value={p.title}>
                  {p.title}
                </option>
              ))}
            </select>
          </label>
        </div>

        <div className="mt-6 flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-full border border-border px-4 py-2.5 font-display text-sm font-semibold text-muted-foreground"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="flex flex-1 items-center justify-center gap-2 rounded-full bg-ember-gradient px-4 py-2.5 font-display text-sm font-semibold text-maroon-foreground disabled:opacity-70"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            Reserve free seat
          </button>
        </div>
        <p className="mt-3 text-center text-xs text-muted-foreground">
          Saved securely · confirmation via {LMS_API_BASE.includes("localhost") ? "email when live" : "email"}
        </p>
      </form>
    </div>
  );
}
