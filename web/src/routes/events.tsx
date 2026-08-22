import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Clock, MapPin, Ticket, UserRound, Users } from "lucide-react";

import { ReserveSeatDialog } from "@/components/site/ReserveSeatDialog";
import { FACILITATORS } from "@/data/site";
import {
  EVENTS,
  FEATURED_EVENT,
  eventDateLabel,
  eventFormat,
  eventTimeRange,
  eventVenue,
  type AppEvent,
} from "@/data/events";
import { fetchEventReservationCounts } from "@/lib/lmsApi";

export const Route = createFileRoute("/events")({
  head: () => ({
    meta: [
      { title: "Events — Reserve a Free Seat | Nelsen Savannah Innovation Hub" },
      {
        name: "description",
        content:
          "Reserve a free seat at Nelsen Savannah Innovation Hub events — first intake Friday 29 August 2026 with facilitators Tomee Juma and Evans Nyairo.",
      },
      { property: "og:title", content: "Events | Nelsen Savannah Innovation Hub" },
    ],
  }),
  component: EventsPage,
});

function EventsPage() {
  const [filter, setFilter] = useState<"All" | "In person" | "Online">("All");
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [reservedLocal, setReservedLocal] = useState<string[]>(() => {
    try {
      return JSON.parse(localStorage.getItem("ns-reserved-events") || "[]") as string[];
    } catch {
      return [];
    }
  });
  const [activeEvent, setActiveEvent] = useState<AppEvent | null>(null);

  const refreshCounts = useCallback(async () => {
    const res = await fetchEventReservationCounts();
    if (res.ok && res.data?.counts) {
      setCounts(res.data.counts);
    }
  }, []);

  useEffect(() => {
    void refreshCounts();
  }, [refreshCounts]);

  const events = EVENTS.filter((e) => filter === "All" || eventFormat(e) === filter);

  const markReserved = (eventId: string) => {
    setReservedLocal((prev) => {
      const next = prev.includes(eventId) ? prev : [...prev, eventId];
      localStorage.setItem("ns-reserved-events", JSON.stringify(next));
      return next;
    });
    void refreshCounts();
  };

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <header className="mx-auto max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Events</p>
        <h1 className="mt-4 max-w-3xl text-4xl font-bold sm:text-5xl">
          Reserve your free seat — first intake {eventDateLabel(FEATURED_EVENT)}
        </h1>
        <p className="mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
          Opening intake with {FACILITATORS.map((f) => f.name).join(" and ")}. Fill in your details
          — no payment required.
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
          const seats = event.seats ?? 0;
          const taken = counts[event.eventId] ?? event.seatsTaken ?? 0;
          const left = Math.max(0, seats - taken);
          const pct = seats ? Math.round((taken / seats) * 100) : 0;
          const isReserved = reservedLocal.includes(event.eventId);
          const format = eventFormat(event);

          return (
            <article
              key={event.eventId}
              id={event.eventId}
              className="scroll-mt-28 grid gap-6 rounded-3xl border border-border/70 bg-card p-6 sm:p-8 lg:grid-cols-[1fr_auto] lg:items-center"
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  {event.program && (
                    <span className="rounded-full bg-maroon/10 px-3 py-1 text-xs font-semibold text-maroon">
                      {event.program}
                    </span>
                  )}
                  <span className="rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                    {format}
                  </span>
                  <span className="text-xs text-muted-foreground">{eventDateLabel(event)}</span>
                </div>
                <h2 className="mt-3 text-xl font-bold sm:text-2xl">{event.title}</h2>
                {event.description && (
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                    {event.description}
                  </p>
                )}
                {event.facilitators?.length ? (
                  <p className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <UserRound className="h-3.5 w-3.5" />
                    Facilitators: {event.facilitators.join(" · ")}
                  </p>
                ) : null}
                <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground">
                  <li className="flex items-center gap-1.5">
                    <Clock className="h-3.5 w-3.5" /> {eventTimeRange(event)}
                  </li>
                  <li className="flex items-center gap-1.5">
                    <MapPin className="h-3.5 w-3.5" /> {eventVenue(event)}
                  </li>
                  {event.price && (
                    <li className="flex items-center gap-1.5">
                      <Ticket className="h-3.5 w-3.5" /> {event.price}
                    </li>
                  )}
                </ul>
              </div>

              {seats > 0 && (
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
                    onClick={() => setActiveEvent(event)}
                    disabled={isReserved || left <= 0}
                    className={`mt-4 w-full rounded-full px-5 py-2.5 font-display text-sm font-semibold transition-transform ${
                      isReserved
                        ? "border border-border bg-secondary text-muted-foreground"
                        : "bg-ember-gradient text-maroon-foreground shadow-ember-glow hover:-translate-y-0.5"
                    }`}
                  >
                    {isReserved ? "Seat reserved" : left <= 0 ? "Full" : "Reserve free seat"}
                  </button>
                </div>
              )}
            </article>
          );
        })}
      </section>

      {activeEvent && (
        <ReserveSeatDialog
          event={activeEvent}
          open={!!activeEvent}
          onClose={() => setActiveEvent(null)}
          onReserved={markReserved}
        />
      )}
    </div>
  );
}
