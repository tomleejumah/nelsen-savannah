import { createFileRoute } from "@tanstack/react-router";
import { Link } from "@tanstack/react-router";
import { ArrowRight, CheckCircle2 } from "lucide-react";

import workshopImg from "@/assets/programs-workshop.jpg";
import { PROGRAMS } from "@/data/site";

export const Route = createFileRoute("/programs")({
  head: () => ({
    meta: [
      { title: "Our Programs — Sela, Trailblazers & Codelab | Nelsen Savannah" },
      {
        name: "description",
        content:
          "Programs from the Nelsen Savannah app: Sela programme, Trailblazers, Scripture Safari, and Go for it Codelab.",
      },
      { property: "og:title", content: "Our Programs | Nelsen Savannah" },
      {
        property: "og:description",
        content: "Sela programme, Trailblazers, Scripture Safari, and Go for it Codelab.",
      },
    ],
  }),
  component: ProgramsPage,
});

const toneClass = {
  brand: "bg-brand/10 text-brand-soft",
  ember: "bg-ember/10 text-ember",
  maroon: "bg-maroon/10 text-maroon",
} as const;

function ProgramsPage() {
  return (
    <div className="pb-24">
      <section className="relative overflow-hidden bg-background px-5 pb-20 pt-36 sm:px-8 sm:pt-44">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.08),transparent_55%)]"
        />
        <div className="relative mx-auto max-w-4xl text-center">
          <p className="eyebrow text-ember">Our Programs</p>
          <h1 className="mt-4 text-4xl font-bold text-foreground sm:text-6xl">
            Structured guidance for every stage of the climb
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Each program runs in cohorts with a trained mentor, clear goals, and sessions you can
            actually attend. Pick the one that matches where you are right now.
          </p>
        </div>
        <img
          src={workshopImg}
          alt="Young people laughing during a Nelsen Savanna leadership workshop"
          width={1600}
          height={912}
          loading="lazy"
          className="relative mx-auto mt-14 w-full max-w-6xl rounded-3xl border border-border/70 object-cover shadow-elevated"
        />
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
            <h2 className="mt-5 text-2xl font-bold">{p.title}</h2>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{p.blurb}</p>
            <ul className="mt-5 space-y-2 text-sm text-muted-foreground">
              {["Matched mentor within 14 days", "Monthly in-person or online sessions", "Progress review at week 12"].map(
                (item) => (
                  <li key={item} className="flex items-start gap-2">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-ember" />
                    {item}
                  </li>
                ),
              )}
            </ul>
            <Link
              to="/contact"
              className="mt-6 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-foreground hover:text-ember"
            >
              Apply to this program <ArrowRight className="h-4 w-4" />
            </Link>
          </article>
        ))}
      </section>
    </div>
  );
}