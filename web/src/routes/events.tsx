import { useCallback, useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { onAuthStateChanged } from "firebase/auth";
import { Clock, ExternalLink, MapPin, Radio, Share2, Ticket, UserRound, Users } from "lucide-react";

import { ReserveSeatDialog } from "@/components/site/ReserveSeatDialog";
import { FACILITATORS } from "@/data/site";
import {
  type AppEvent,
  eventDateLabel,
  eventFormat,
  eventTimeRange,
  eventVenue,
  isLiveEvent,
  liveStatusLabel,
  youtubeEmbedUrl,
} from "@/data/events";
import { getFirebaseAuth } from "@/lib/firebase";
import { loadHubEvents } from "@/lib/hubEvents";

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
  const [events, setEvents] = useState<AppEvent[]>([]);
  const [liveReplays, setLiveReplays] = useState<AppEvent[]>([]);
  const [reservedLocal, setReservedLocal] = useState<string[]>(() => {
    try {
      return JSON.parse(localStorage.getItem("ns-reserved-events") || "[]") as string[];
    } catch {
      return [];
    }
  });
  const [activeEvent, setActiveEvent] = useState<AppEvent | null>(null);

  const refreshEvents = useCallback(async () => {
    const user = getFirebaseAuth().currentUser;
    const token = user ? await user.getIdToken() : null;
    const [upcoming, past] = await Promise.all([
      loadHubEvents(token, "upcoming"),
      loadHubEvents(token, "past"),
    ]);
    setEvents(upcoming);
    setLiveReplays(
      past
        .filter((event) => isLiveEvent(event) && event.liveStatus === "ended" && !!event.meetingLink)
        .sort((a, b) => b.date - a.date),
    );
  }, []);

  useEffect(() => {
    const auth = getFirebaseAuth();
    const unsubscribe = onAuthStateChanged(auth, () => {
      void refreshEvents();
    });
    return unsubscribe;
  }, [refreshEvents]);

  // Live deep links arrive before these async event cards are loaded.
  // Scroll after rendering the matching event or replay, not on initial HTML.
  useEffect(() => {
    const encoded = window.location.hash.slice(1);
    if (!encoded || (!events.length && !liveReplays.length)) return;
    let id: string;
    try { id = decodeURIComponent(encoded); } catch { return; }
    const frame = window.requestAnimationFrame(() => {
      document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [events, liveReplays]);

  const featured = events[0];
  const filtered = events.filter((e) => filter === "All" || eventFormat(e) === filter);

  const markReserved = (eventId: string) => {
    setReservedLocal((prev) => {
      const next = prev.includes(eventId) ? prev : [...prev, eventId];
      localStorage.setItem("ns-reserved-events", JSON.stringify(next));
      return next;
    });
    void refreshEvents();
  };

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <header className="mx-auto max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Events</p>
        <h1 className="mt-4 max-w-3xl text-4xl font-bold sm:text-5xl">
          Reserve your free seat
          {featured ? ` — ${eventDateLabel(featured)}` : ""}
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
        {filtered.map((event) => {
          const seats = event.seats ?? 0;
          const taken = event.seatsTaken ?? 0;
          const left = Math.max(0, seats - taken);
          const pct = seats ? Math.round((taken / seats) * 100) : 0;
          const isReserved = reservedLocal.includes(event.eventId);
          const format = eventFormat(event);
          const liveEvent = isLiveEvent(event);
          const liveLabel = liveEvent ? liveStatusLabel(event) : null;
          const embedUrl = liveEvent ? youtubeEmbedUrl(event.meetingLink) : "";

          return (
            <article
              key={event.eventId}
              id={event.eventId}
              className={`scroll-mt-28 grid gap-6 rounded-3xl border border-border/70 bg-card p-6 sm:p-8 ${
                liveEvent ? "lg:grid-cols-1" : "lg:grid-cols-[1fr_auto] lg:items-center"
              }`}
            >
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  {event.program && (
                    <span className="rounded-full bg-maroon/10 px-3 py-1 text-xs font-semibold text-maroon">
                      {event.program}
                    </span>
                  )}
                  {liveEvent ? (
                    <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
                      event.liveStatus === "live"
                        ? "bg-red-500/15 text-red-600"
                        : "bg-brand/10 text-brand-soft"
                    }`}>
                      <Radio className="h-3.5 w-3.5" />
                      {liveLabel}
                    </span>
                  ) : (
                    <span className="rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                      {format}
                    </span>
                  )}
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

                {liveEvent && embedUrl && event.liveAvailability !== "unavailable" ? (
                  <div className="mt-5 overflow-hidden rounded-2xl border border-border/70 bg-black">
                    <div className="aspect-video">
                      <iframe
                        src={embedUrl}
                        title={`${event.title} — Live session`}
                        className="h-full w-full"
                        allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
                        allowFullScreen
                      />
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-3 bg-secondary/40 px-4 py-3 text-xs">
                      <span className="font-semibold">{liveLabel}</span>
                      <a
                        href={event.meetingLink}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1.5 font-semibold text-ember"
                      >
                        Open external player <ExternalLink className="h-3.5 w-3.5" />
                      </a>
                    </div>
                  </div>
                ) : liveEvent && event.liveAvailability === "unavailable" ? (
                  <div className="mt-5 rounded-2xl border border-border/70 bg-secondary/40 p-5">
                    <p className="font-semibold">Live video unavailable</p>
                    <p className="mt-1 text-sm text-muted-foreground">
                      This live session or replay may be private, removed, or no longer accessible.
                    </p>
                  </div>
                ) : null}
                {liveEvent && event.meetingLink ? (
                  <button
                    type="button"
                    onClick={() => {
                      const url = event.meetingLink;
                      if (navigator.share) {
                        void navigator.share({ title: event.title, url });
                      } else {
                        void navigator.clipboard.writeText(url);
                      }
                    }}
                    className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-ember"
                  >
                    <Share2 className="h-3.5 w-3.5" /> Share live
                  </button>
                ) : null}
              </div>

              {!liveEvent && seats > 0 && (
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

      {liveReplays.length > 0 && (
        <section className="mx-auto mt-16 max-w-7xl px-5 sm:px-8">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="eyebrow text-ember">Live history</p>
              <h2 className="mt-2 text-2xl font-bold sm:text-3xl">Watch recent replays</h2>
            </div>
            <p className="text-sm text-muted-foreground">
              Ended live sessions remain available to their original audience.
            </p>
          </div>

          <div className="mt-6 grid gap-4 md:grid-cols-2">
            {liveReplays.map((event) => (
              <article
                key={event.eventId}
                id={event.eventId}
                className="rounded-2xl border border-border/70 bg-card p-5"
              >
                <div className="flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-brand/10 px-3 py-1 text-xs font-semibold text-brand-soft">
                    <Radio className="h-3.5 w-3.5" />
                    REPLAY
                  </span>
                  <span className="text-xs text-muted-foreground">{eventDateLabel(event)}</span>
                </div>
                <h3 className="mt-3 text-lg font-bold">{event.title}</h3>
                {event.program && (
                  <p className="mt-1 text-xs text-muted-foreground">{event.program}</p>
                )}
                {event.liveAvailability === "unavailable" ? (
                  <p className="mt-4 text-sm text-muted-foreground">
                    Replay unavailable — it may be private or removed.
                  </p>
                ) : (
                  <a
                    href={event.meetingLink}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-4 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-ember"
                  >
                    Watch replay externally <ExternalLink className="h-4 w-4" />
                  </a>
                )}
              </article>
            ))}
          </div>
        </section>
      )}

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
