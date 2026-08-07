import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Clock, MapPin, Ticket, Users } from "lucide-react";
import { toast } from "sonner";

import eventsData from "@/data/events.json";

type Event = (typeof eventsData)[number];

export const Route = createFileRoute("/events")({
  head: () => ({
    meta: [
      { title: "Events — Expos, Practice Labs & Mentor Mixers | Nelsen Savanna" },
      {
        name: "description",
        content:
          "Reserve a seat at Nelsen Savanna events: career expos, live practice panels, wellbeing campus tours and mentor mixers across Kenya.",
      },
      { property: "og:title", content: "Events | Nelsen Savanna" },
      {
        property: "og:description",
        content: "Career expos, practice panels, campus tours and mentor mixers — reserve a seat.",
      },
    ],
  }),
  component: EventsPage,
});

function EventsPage() {
  const [filter, setFilter] = useState<"All" | "In person" | "Online">("All");
  const [reserved, setReserved] = useState<string[]>([]);

  const events = eventsData.filter((e) => filter === "All" || e.format === filter);

  const reserve = (event: Event) => {
    if (reserved.includes(event.id)) return;
    setReserved((prev) => [...prev, event.id]);
    toast.success(`Seat reserved: ${event.title}`, {
      description: `${event.venue}. A confirmation email with the exact date is on its way.`,
    });
  };

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <header className="mx-auto max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Events</p>
        <h1 className="mt-4 max-w-3xl text-4xl font-bold sm:text-5xl">
          Show up once and the path gets clearer
        </h1>
        <p className="mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
          Seats are limited and released per cohort. Reserve early — we confirm exact dates by email
          once your seat is held.
        </p>

        <div className="mt-8 inline-flex gap-1.5 rounded-full bg-secondary p-1.5">
          {(["All", "In person", "Online"] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={`rounded-full px-4 py-2 font-display text-sm font-semibold transition-colors ${
                filter === f
                  ? "bg-ember-gradient text-maroon-foreground"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              {f}
            </button>
          ))}
        </div>
      </header>

      <section className="mx-auto mt-10 grid max-w-7xl gap-5 px-5 sm:px-8">
        {events.map((event) => {
          const left = event.seats - event.seatsTaken;
          const pct = Math.round((event.seatsTaken / event.seats) * 100);
          const isReserved = reserved.includes(event.id);

          return (
            <article
              key={event.id}
              className="grid gap-6 rounded-3xl border border-border/70 bg-card p-6 sm:p-8 lg:grid-cols-[1fr_auto] lg:items-center"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="rounded-full bg-maroon/10 px-3 py-1 text-xs font-semibold text-maroon">
                    {event.program}
                  </span>
                  <span className="rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                    {event.format}
                  </span>
                </div>
                <h2 className="mt-3 text-xl font-bold sm:text-2xl">{event.title}</h2>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{event.summary}</p>
                <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground">
                  <li className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5" /> {event.time}
                  </li>
                  <li className="flex items-center gap-1.5">
                    <MapPin className="h-3.5 w-3.5" /> {event.venue}
                  </li>
                  <li className="flex items-center gap-1.5">
                    <Ticket className="h-3.5 w-3.5" /> {event.price}
                  </li>
                </ul>
              </div>

              <div className="w-full lg:w-52">
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span className="flex items-center gap-1.5">
                    <Users className="h-3.5 w-3.5" /> {left} seats left
                  </span>
                  <span>{pct}%</span>
                </div>
                <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-secondary">
                  <div className="h-full bg-ember-gradient" style={{ width: `${pct}%` }} />
                </div>
                <button
                  type="button"
                  onClick={() => reserve(event)}
                  disabled={isReserved || left <= 0}
                  className={`mt-4 w-full rounded-full px-5 py-2.5 font-display text-sm font-semibold transition-transform ${
                    isReserved
                      ? "border border-border bg-secondary text-muted-foreground"
                      : "bg-ember-gradient text-maroon-foreground shadow-ember-glow hover:-translate-y-0.5"
                  }`}
                >
                  {isReserved ? "Seat reserved" : left <= 0 ? "Sold out" : "Reserve a seat"}
                </button>
              </div>
            </article>
          );
        })}
      </section>
    </div>
  );
}