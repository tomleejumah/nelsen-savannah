import { Link, createFileRoute } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { ArrowRight, Briefcase, Compass, Quote } from "lucide-react";

import heroImg from "@/assets/hero-mentorship.jpg";
import { type AppEvent, eventDateLabel, eventVenue } from "@/data/events";
import { FACILITATORS, FIRST_INTAKE } from "@/data/site";
import { loadHubEvents } from "@/lib/hubEvents";
import {
  CORE_VALUES,
  INNOVATION_CYCLE,
  LEARNING_MODEL,
  ORG,
  OUTCOMES,
  PRACTICAL_APPLICATION,
  PROGRAMS,
  ROADMAP,
  STATS,
} from "@/data/site";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Nelsen Savannah Innovation Hub — Programmes & Learning" },
      {
        name: "description",
        content:
          "Nelsen Savannah Innovation Hub — seven programmes combining technology education, creativity, communication, and practical problem-solving for Kenya's digital economy.",
      },
      { property: "og:title", content: "Nelsen Savannah Innovation Hub" },
      {
        property: "og:description",
        content:
          "Explore, create, code, build, and launch — Future Safari, Robotics, Data & AI, Creative, Software Engineering, Sauti, and Kijiji Hub.",
      },
    ],
  }),
  component: Index,
});

const toneClass = {
  brand: "bg-brand/10 text-brand-soft",
  ember: "bg-ember/10 text-ember",
  maroon: "bg-maroon/10 text-maroon",
} as const;

function Index() {
  const [hubEvents, setHubEvents] = useState<AppEvent[]>([]);
  const featured = hubEvents[0];

  useEffect(() => {
    void loadHubEvents().then(setHubEvents);
  }, []);

  return (
    <div>
      {/* Hero */}
      <section className="relative overflow-hidden bg-background">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,oklch(0.52_0.21_25_/_0.08),transparent_55%),radial-gradient(ellipse_at_bottom_left,oklch(0.28_0.09_264_/_0.06),transparent_50%)]"
        />
        <div className="relative mx-auto grid max-w-7xl items-center gap-12 px-5 pb-20 pt-32 sm:px-8 sm:pt-40 lg:grid-cols-[1.05fr_1fr] lg:pb-28">
          <div>
            <h1 className="text-4xl font-bold leading-[1.05] text-foreground sm:text-6xl lg:text-[4.2rem]">
              An innovation ecosystem where ideas become{" "}
              <span className="bg-ember-gradient bg-clip-text text-transparent">
                practical solutions
              </span>
              .
            </h1>
            <p className="mt-6 max-w-xl text-base leading-relaxed text-muted-foreground sm:text-lg">
              {ORG.vision}
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                to="/programs"
                className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
              >
                Explore programmes <ArrowRight className="h-4 w-4" />
              </Link>
              <Link
                to="/learning"
                className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-6 py-3.5 font-display text-sm font-semibold text-foreground transition-colors hover:bg-accent"
              >
                Start learning
              </Link>
            </div>

            <dl className="mt-14 grid grid-cols-2 gap-x-6 gap-y-8 sm:grid-cols-4 sm:gap-8">
              {STATS.map((s, index) => (
                <div key={s.label} className="flex min-w-0 flex-col">
                  <dt className="h-[58px] font-display text-2xl font-bold leading-none tracking-tight text-foreground sm:text-3xl">
                    <span className="whitespace-nowrap">{s.value}</span>
                  </dt>

                  <dd className="mt-2 text-xs leading-snug text-muted-foreground">{s.label}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="relative">
            <div className="absolute -inset-6 rounded-[2.5rem] bg-ember/10 blur-3xl" />
            <img
              src={heroImg}
              alt="Learners collaborating at the Nelsen Savannah Innovation Hub"
              width={1200}
              height={1408}
              className="relative w-full rounded-[2rem] border border-border/70 object-cover shadow-elevated"
            />
            <div className="glass-panel absolute -bottom-6 left-4 right-4 rounded-2xl p-4 sm:left-8 sm:right-8">
              <p className="text-xs text-muted-foreground">
                <span className="font-display font-semibold text-foreground">{ORG.hubName}</span>{" "}
                — {ORG.location}. Programmes from Future Safari to Kijiji Hub.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8">
        <div className="max-w-2xl">
          <p className="eyebrow text-ember">Learning model</p>
          <h2 className="mt-4 text-3xl font-bold sm:text-4xl">From exposure to real-world impact</h2>
          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-muted-foreground sm:text-base">
            {LEARNING_MODEL}
          </p>
        </div>
        <div className="mt-12 grid gap-6 lg:grid-cols-3">
          {OUTCOMES.map(({ title, body }, i) => (
            <article
              key={title}
              className="rounded-3xl border border-border/70 bg-card p-8 transition-shadow hover:shadow-elevated"
            >
              <div className="flex items-center justify-between">
                <span className="icon-chip-lg">
                  <Compass className="h-5 w-5" />
                </span>
                <span className="font-display text-sm text-muted-foreground/50">0{i + 1}</span>
              </div>
              <h3 className="mt-6 text-xl font-bold">{title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{body}</p>
            </article>
          ))}
        </div>
      </section>

      {/* Practical application */}
      <section className="border-y border-border/60 bg-background py-24 dark:bg-hero-gradient">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 sm:px-8 lg:grid-cols-[1fr_1.1fr] lg:items-center">
          <div>
            <p className="eyebrow text-ember">Practical application</p>
            <h2 className="mt-4 text-3xl font-bold text-foreground sm:text-4xl dark:text-on-dark">
              Build, test, and present — not just study
            </h2>
            <p className="mt-5 max-w-xl text-base leading-relaxed text-muted-foreground dark:text-on-dark/70">
              Learners apply concepts through robotics builds, data capstones, creative portfolios,
              software projects, and community innovation challenges.
            </p>
            <Link
              to="/learning"
              className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Browse learning tracks <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          <div className="grid gap-4">
            {PRACTICAL_APPLICATION.map((item) => (
              <article
                key={item}
                className="flex gap-4 rounded-3xl border border-border/70 bg-card p-6 dark:border-transparent dark:bg-transparent dark:glass-dark"
              >
                <span className="icon-chip-lg">
                  <Briefcase className="h-5 w-5" />
                </span>
                <p className="text-sm leading-relaxed text-muted-foreground dark:text-on-dark/70">
                  {item}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Programs */}
      <section className="border-y border-border/60 bg-secondary/40 py-24">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="max-w-2xl">
              <p className="eyebrow text-ember">Our programs</p>
              <h2 className="mt-4 text-3xl font-bold sm:text-4xl">
                Seven connected pathways
              </h2>
              <p className="mt-3 text-sm text-muted-foreground">
                Enter through exploration and grow specialist technical, creative, and entrepreneurial
                capabilities.
              </p>
            </div>
            <Link
              to="/programs"
              className="inline-flex items-center gap-1.5 font-display text-sm font-semibold hover:text-ember"
            >
              All programs <ArrowRight className="h-4 w-4" />
            </Link>
          </div>

          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {PROGRAMS.map((p) => (
              <Link
                key={p.slug}
                to="/programs"
                hash={p.slug}
                className="group rounded-3xl border border-border/70 bg-card p-7 transition-all hover:-translate-y-1 hover:shadow-elevated"
              >
                <span
                  className={`rounded-full px-3 py-1 text-xs font-semibold ${toneClass[p.tone]}`}
                >
                  {p.audience}
                </span>
                <h3 className="mt-5 text-xl font-bold group-hover:text-ember">{p.title}</h3>
                <p className="mt-1 text-xs font-medium text-ember">{p.subtitle}</p>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{p.blurb}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* Events teaser */}
      <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div className="max-w-2xl">
            <p className="eyebrow text-ember">First intake · {FIRST_INTAKE.label}</p>
            <h2 className="mt-4 text-3xl font-bold sm:text-4xl">
              Reserve your free seat
              {featured ? ` — ${featured.title}` : ""}
            </h2>
            <p className="mt-3 text-sm text-muted-foreground">
              With {FACILITATORS.map((f) => f.name).join(" and ")}
              {featured ? ` · ${eventVenue(featured)}` : ""}
            </p>
          </div>
          <Link
            to="/events"
            hash={featured?.eventId}
            className="inline-flex items-center gap-1.5 font-display text-sm font-semibold hover:text-ember"
          >
            Reserve free seat <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
        <div className="mt-12 grid gap-5 md:grid-cols-3">
          {hubEvents.slice(0, 3).map((e) => (
            <Link
              key={e.eventId}
              to="/events"
              hash={e.eventId}
              className="group flex flex-col rounded-3xl border border-border/70 bg-card p-7 transition-all hover:-translate-y-1 hover:shadow-elevated"
            >
              <span className="eyebrow text-ember">{eventDateLabel(e)}</span>
              <h3 className="mt-3 text-lg font-bold leading-snug">{e.title}</h3>
              <p className="mt-2 flex-1 text-sm text-muted-foreground">{eventVenue(e)}</p>
              <span className="mt-5 text-xs text-muted-foreground">
                {e.price ?? "Free"} · Reserve online
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Core values */}
      <section className="border-t border-border/60 bg-background py-24 dark:bg-hero-gradient">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="max-w-2xl">
            <p className="eyebrow text-ember">Core values</p>
            <h2 className="mt-4 text-3xl font-bold text-foreground sm:text-4xl dark:text-on-dark">
              How we work at the Hub
            </h2>
          </div>
          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {CORE_VALUES.map((v) => (
              <figure
                key={v.key}
                className="rounded-3xl border border-border/70 bg-card p-7 dark:border-transparent dark:bg-transparent dark:glass-dark"
              >
                <Quote className="h-6 w-6 text-ember" />
                <figcaption className="mt-4">
                  <span className="block font-display text-sm font-semibold text-foreground dark:text-on-dark">
                    {v.key}
                  </span>
                  <span className="mt-2 block text-sm leading-relaxed text-muted-foreground dark:text-on-dark/70">
                    {v.detail}
                  </span>
                </figcaption>
              </figure>
            ))}
          </div>
          <div className="mt-12 flex flex-wrap gap-2">
            {INNOVATION_CYCLE.map((step, i) => (
              <span
                key={step}
                className="rounded-full border border-border/70 bg-card px-3 py-1.5 text-xs font-semibold text-muted-foreground"
              >
                {i > 0 ? "→ " : ""}
                {step}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* Roadmap */}
      <section className="border-t border-border/60 bg-secondary/40 py-24">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="max-w-2xl">
            <p className="eyebrow text-ember">Roadmap</p>
            <h2 className="mt-4 text-3xl font-bold sm:text-4xl">What we are building next</h2>
            <p className="mt-5 text-sm leading-relaxed text-muted-foreground sm:text-base">
              Programmes and the learning platform are live. Cohort milestones, payments, and
              certificates are next on the roadmap.
            </p>
          </div>
          <ol className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-4">
            {ROADMAP.map((r) => (
              <li
                key={r.title}
                className="rounded-3xl border border-border/70 bg-card p-7 transition-shadow hover:shadow-elevated"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="eyebrow text-muted-foreground/70">{r.phase}</span>
                  <span className="rounded-full bg-maroon/10 px-3 py-1 text-xs font-semibold text-maroon">
                    {r.status}
                  </span>
                </div>
                <h3 className="mt-5 text-lg font-bold leading-snug">{r.title}</h3>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{r.body}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* CTA */}
      <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8">
        <div className="relative overflow-hidden rounded-[2rem] border border-border/70 bg-card p-10 text-center sm:p-16">
          <div className="absolute inset-x-0 -top-24 mx-auto h-48 w-48 rounded-full bg-ember/20 blur-3xl" />
          <h2 className="relative text-3xl font-bold sm:text-4xl">
            Ready to explore your pathway?
          </h2>
          <p className="relative mx-auto mt-4 max-w-xl text-sm leading-relaxed text-muted-foreground sm:text-base">
            Join an intake cohort, pick a programme, and build skills that show up in projects —
            not just on paper.
          </p>
          <div className="relative mt-8 flex flex-wrap justify-center gap-3">
            <Link
              to="/contact"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Join a cohort <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/programs"
              className="inline-flex items-center gap-2 rounded-full border border-border px-6 py-3.5 font-display text-sm font-semibold transition-colors hover:bg-accent"
            >
              View all programmes
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
