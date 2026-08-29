import { Link } from "@tanstack/react-router";
import { ArrowLeft, ArrowRight, BookOpen, CheckCircle2 } from "lucide-react";

import { LEARNING_FORMAT, PROGRAM_PILLARS, type Program } from "@/data/site";

const toneClass = {
  brand: "bg-brand/10 text-brand-soft",
  ember: "bg-ember/10 text-ember",
  maroon: "bg-maroon/10 text-maroon",
} as const;

type Props = {
  program: Program;
};

/** Reusable programme detail layout — data from `PROGRAMS` in site.ts. */
export function ProgramDetail({ program }: Props) {
  return (
    <div className="pb-24">
      <section className="relative overflow-hidden bg-background px-5 pb-16 pt-36 sm:px-8 sm:pt-44">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,oklch(0.52_0.21_25_/_0.08),transparent_55%)]"
        />
        <div className="relative mx-auto max-w-4xl">
          <Link
            to="/programs"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground transition-colors hover:text-ember"
          >
            <ArrowLeft className="h-4 w-4" /> All programmes
          </Link>
          <span
            className={`mt-8 inline-block rounded-full px-3 py-1 text-xs font-semibold ${toneClass[program.tone]}`}
          >
            {program.audience}
          </span>
          <h1 className="mt-5 text-4xl font-bold text-foreground sm:text-5xl">{program.title}</h1>
          <p className="mt-2 text-lg font-medium text-ember">{program.subtitle}</p>
          <p className="mt-5 max-w-2xl text-base leading-relaxed text-muted-foreground">
            {program.blurb}
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/contact"
              search={{ intent: "learner" }}
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Enquire about this programme <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/learning/$trackId"
              params={{ trackId: program.slug }}
              className="inline-flex items-center gap-2 rounded-full border border-border px-6 py-3 font-display text-sm font-semibold transition-colors hover:bg-accent"
            >
              <BookOpen className="h-4 w-4" /> View learning track
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-4xl px-5 sm:px-8">
        <p className="eyebrow text-ember">About this programme</p>
        <div className="mt-6 space-y-4 text-base leading-relaxed text-muted-foreground">
          {program.about.map((paragraph) => (
            <p key={paragraph.slice(0, 40)}>{paragraph}</p>
          ))}
        </div>
      </section>

      <section className="mx-auto mt-16 max-w-4xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Who it is for</p>
        <ul className="mt-6 space-y-3">
          {program.forWho.map((item) => (
            <li
              key={item}
              className="flex items-start gap-3 rounded-2xl border border-border/70 bg-card px-5 py-4 text-sm text-muted-foreground"
            >
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-ember" />
              {item}
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto mt-16 max-w-4xl px-5 sm:px-8">
        <p className="eyebrow text-ember">What you will learn</p>
        <ul className="mt-6 grid gap-3 sm:grid-cols-2">
          {program.topics.map((item) => (
            <li
              key={item}
              className="flex items-start gap-2 rounded-2xl border border-border/70 bg-card px-5 py-4 text-sm text-muted-foreground"
            >
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-ember" />
              {item}
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto mt-16 max-w-4xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Outcomes</p>
        <ul className="mt-6 space-y-3">
          {program.outcomes.map((item) => (
            <li
              key={item}
              className="flex items-start gap-3 text-sm leading-relaxed text-muted-foreground"
            >
              <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-ember" />
              {item}
            </li>
          ))}
        </ul>
      </section>

      <section className="border-y border-border/60 bg-secondary/40 py-16 mt-16">
        <div className="mx-auto max-w-4xl px-5 sm:px-8">
          <p className="eyebrow text-ember">How we deliver it</p>
          <div className="mt-8 grid gap-5 sm:grid-cols-2">
            {PROGRAM_PILLARS.slice(0, 4).map((pillar) => (
              <article
                key={pillar.title}
                className="rounded-2xl border border-border/70 bg-card p-5"
              >
                <h2 className="text-sm font-bold">{pillar.title}</h2>
                <p className="mt-2 text-sm text-muted-foreground">{pillar.body}</p>
              </article>
            ))}
          </div>
          <p className="mt-8 text-sm leading-relaxed text-muted-foreground">
            {LEARNING_FORMAT[0].body}
          </p>
        </div>
      </section>

      <section className="mx-auto max-w-4xl px-5 py-16 sm:px-8">
        <div className="rounded-3xl border border-border/70 bg-card p-8 text-center sm:p-12">
          <h2 className="text-2xl font-bold">Ready to join {program.title}?</h2>
          <p className="mx-auto mt-3 max-w-lg text-sm text-muted-foreground">
            Enquire about the next intake or explore the learning track online.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link
              to="/contact"
              search={{ intent: "learner" }}
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground"
            >
              Enquire now <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/events"
              className="inline-flex items-center gap-2 rounded-full border border-border px-6 py-3 font-display text-sm font-semibold transition-colors hover:bg-accent"
            >
              Upcoming events
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
