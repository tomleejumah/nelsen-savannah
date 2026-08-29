import { createFileRoute, Link, Outlet, useChildMatches } from "@tanstack/react-router";
import { ArrowRight, CheckCircle2 } from "lucide-react";

import workshopImg from "@/assets/programs-workshop.jpg";
import {
  AI_FOCUS_AREAS,
  CAMPUS_PHASES,
  LEARNING_FORMAT,
  LEARNING_MODEL,
  PARTNER_ECOSYSTEM,
  PROGRAM_PILLARS,
  PROGRAMS,
  WHAT_YOU_GAIN,
} from "@/data/site";

export const Route = createFileRoute("/programs")({
  head: () => ({
    meta: [
      {
        title:
          "Programmes — Future Safari, Robotics, Data & AI, Creative, Software, Sauti, Kijiji | Nelsen Savannah",
      },
      {
        name: "description",
        content:
          "Seven connected programmes at Nelsen Savannah Innovation Hub — technology education, creativity, communication, and practical problem-solving.",
      },
      { property: "og:title", content: "Programmes | Nelsen Savannah Innovation Hub" },
      {
        property: "og:description",
        content:
          "Future Safari, Robotics & Automation, Data & AI, Creative Lab, Software Engineering, Sauti Academy, and Kijiji Hub.",
      },
    ],
  }),
  component: ProgramsLayout,
});

/** Parent of /programs/$slug — render child detail or the portfolio index. */
function ProgramsLayout() {
  const childMatches = useChildMatches();
  if (childMatches.length > 0) return <Outlet />;
  return <ProgramsIndexPage />;
}

const toneClass = {
  brand: "bg-brand/10 text-brand-soft",
  ember: "bg-ember/10 text-ember",
  maroon: "bg-maroon/10 text-maroon",
} as const;

function ProgramsIndexPage() {
  return (
    <div className="pb-24">
      <section className="relative overflow-hidden bg-background px-5 pb-20 pt-36 sm:px-8 sm:pt-44">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.08),transparent_55%)]"
        />
        <div className="relative mx-auto max-w-4xl text-center">
          <p className="eyebrow text-ember">Programme portfolio</p>
          <h1 className="mt-4 text-4xl font-bold text-foreground sm:text-6xl">
            Seven pathways. One innovation Hub.
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
            {LEARNING_MODEL} Pick the programme that matches where you are — or where you want to
            grow next.
          </p>
        </div>
        <img
          src={workshopImg}
          alt="Learners at a Nelsen Savannah Innovation Hub workshop"
          width={1600}
          height={912}
          loading="lazy"
          className="relative mx-auto mt-14 w-full max-w-6xl rounded-3xl border border-border/70 object-cover shadow-elevated"
        />
      </section>

      <section className="border-y border-border/60 bg-secondary/40 py-20">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <p className="eyebrow text-ember">What we deliver</p>
          <h2 className="mt-4 text-3xl font-bold">Six pillars across every programme</h2>
          <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {PROGRAM_PILLARS.map((p) => (
              <article
                key={p.title}
                className="rounded-3xl border border-border/70 bg-card p-6"
              >
                <h3 className="text-lg font-bold">{p.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8">
        <p className="eyebrow text-ember">Learning format</p>
        <h2 className="mt-4 text-3xl font-bold">Blended, applied, and inclusive</h2>
        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {LEARNING_FORMAT.map((item) => (
            <article
              key={item.title}
              className="rounded-3xl border border-border/70 bg-card p-7"
            >
              <h3 className="text-lg font-bold">{item.title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{item.body}</p>
            </article>
          ))}
        </div>
        <div className="mt-10 flex flex-wrap gap-2">
          {WHAT_YOU_GAIN.map((item) => (
            <span
              key={item}
              className="rounded-full border border-border/70 bg-card px-3 py-1.5 text-xs font-semibold text-foreground"
            >
              + {item}
            </span>
          ))}
        </div>
      </section>

      <section className="border-y border-border/60 bg-background py-20 dark:bg-hero-gradient">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <p className="eyebrow text-ember">2030 focus</p>
          <h2 className="mt-4 text-3xl font-bold text-foreground dark:text-on-dark">
            Campuses, AI solutions, and partners
          </h2>
          <div className="mt-10 grid gap-6 lg:grid-cols-3">
            {CAMPUS_PHASES.map((phase) => (
              <article
                key={phase.phase}
                className="rounded-3xl border border-border/70 bg-card p-7 dark:border-transparent dark:glass-dark"
              >
                <h3 className="font-display text-sm font-semibold text-ember">{phase.phase}</h3>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground dark:text-on-dark/70">
                  {phase.markets}
                </p>
              </article>
            ))}
          </div>
          <p className="mt-10 text-sm text-muted-foreground dark:text-on-dark/70">
            AI solution focus areas: {AI_FOCUS_AREAS.join(" • ")}.
          </p>
          <div className="mt-10 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {PARTNER_ECOSYSTEM.map((p) => (
              <article
                key={p.title}
                className="rounded-3xl border border-border/70 bg-card p-6 dark:border-transparent dark:glass-dark"
              >
                <h3 className="text-base font-bold">{p.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground dark:text-on-dark/70">
                  {p.detail}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto mt-20 grid max-w-7xl gap-6 px-5 sm:px-8 lg:grid-cols-2">
        {PROGRAMS.map((p, i) => (
          <article
            key={p.slug}
            id={p.slug}
            className="scroll-mt-28 rounded-3xl border border-border/70 bg-card p-7 transition-shadow hover:shadow-elevated"
          >
            <div className="flex items-center justify-between gap-4">
              <span className={`rounded-full px-3 py-1 text-xs font-semibold ${toneClass[p.tone]}`}>
                {p.audience}
              </span>
              <span className="font-display text-sm text-muted-foreground/60">
                0{i + 1}
              </span>
            </div>
            <h2 className="mt-5 text-2xl font-bold">
              <Link
                to="/programs/$slug"
                params={{ slug: p.slug }}
                className="hover:text-ember"
              >
                {p.title}
              </Link>
            </h2>
            <p className="mt-1 text-sm font-medium text-ember">{p.subtitle}</p>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{p.blurb}</p>
            <ul className="mt-5 space-y-2 text-sm text-muted-foreground">
              {p.topics.map((item) => (
                <li key={item} className="flex items-start gap-2">
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-ember" />
                  {item}
                </li>
              ))}
            </ul>
            <Link
              to="/programs/$slug"
              params={{ slug: p.slug }}
              className="mt-6 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-foreground hover:text-ember"
            >
              About this programme <ArrowRight className="h-4 w-4" />
            </Link>
          </article>
        ))}
      </section>
    </div>
  );
}
