import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight, BookOpen, GraduationCap, Layers, Sparkles } from "lucide-react";

import { LEARNING_TRACKS, LMS_FEATURES } from "@/data/site";

export const Route = createFileRoute("/learning")({
  head: () => ({
    meta: [
      { title: "Learning — Tracks & The Nelsen LMS | Nelsen Savanna" },
      {
        name: "description",
        content:
          "Structured learning tracks for mentees and mentors — career foundations, communication, workplace readiness and wellbeing — soon powered by the Nelsen LMS.",
      },
      { property: "og:title", content: "Learning | Nelsen Savanna" },
      {
        property: "og:description",
        content:
          "Guided learning paths, mentor-marked assignments and competency tracking, built for Kenyan youth.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: LearningPage,
});

const toneClass = {
  brand: "bg-brand/10 text-brand-soft",
  ember: "bg-ember/10 text-ember",
  maroon: "bg-maroon/10 text-maroon",
} as const;

function LearningPage() {
  return (
    <div className="pb-24">
      <section className="relative overflow-hidden bg-hero-gradient px-5 pb-20 pt-36 sm:px-8 sm:pt-44">
        <div className="mx-auto max-w-4xl text-center">
          <p className="eyebrow text-ember">Learning</p>
          <h1 className="mt-4 text-4xl font-bold text-on-dark sm:text-6xl">
            Mentorship you attend. Learning you keep.
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-base leading-relaxed text-on-dark/70">
            Every cohort is backed by a structured syllabus. These are the tracks your mentor walks
            you through — and the foundation of the Nelsen LMS we are building next.
          </p>
          <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
            <Link
              to="/contact"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Request a track <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/programs"
              className="inline-flex items-center gap-2 rounded-full border border-white/20 px-6 py-3 font-display text-sm font-semibold text-on-dark transition-colors hover:bg-white/10"
            >
              See the programs
            </Link>
          </div>
        </div>
      </section>

      <section className="mx-auto mt-20 max-w-7xl px-5 sm:px-8">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="eyebrow text-ember">Tracks</p>
            <h2 className="mt-3 text-3xl font-bold sm:text-4xl">Six learning tracks</h2>
          </div>
          <p className="max-w-md text-sm leading-relaxed text-muted-foreground">
            Modules are delivered in sessions with your mentor today, and will move into the LMS as
            self-paced lessons with assignments.
          </p>
        </div>

        <div className="mt-10 grid gap-6 lg:grid-cols-3">
          {LEARNING_TRACKS.map((track) => (
            <article
              key={track.slug}
              className="flex flex-col rounded-3xl border border-border/70 bg-card p-7 transition-shadow hover:shadow-elevated"
            >
              <span
                className={`self-start rounded-full px-3 py-1 text-xs font-semibold ${toneClass[track.tone]}`}
              >
                {track.level}
              </span>
              <h3 className="mt-5 text-xl font-bold leading-snug">{track.title}</h3>
              <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">
                {track.blurb}
              </p>
              <div className="mt-6 flex items-center gap-5 border-t border-border/60 pt-4 text-xs text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <Layers className="h-3.5 w-3.5 text-ember" /> {track.modules} modules
                </span>
                <span className="flex items-center gap-1.5">
                  <BookOpen className="h-3.5 w-3.5 text-ember" /> ~{track.hours} hrs
                </span>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="mx-auto mt-24 max-w-7xl px-5 sm:px-8">
        <div className="rounded-3xl border border-border/70 bg-secondary/40 p-8 sm:p-12">
          <div className="flex flex-wrap items-center gap-3">
            <span className="inline-flex items-center gap-2 rounded-full bg-maroon/10 px-3 py-1 text-xs font-semibold text-maroon">
              <Sparkles className="h-3.5 w-3.5" /> In development
            </span>
            <span className="text-xs text-muted-foreground">Nelsen LMS</span>
          </div>
          <h2 className="mt-5 max-w-3xl text-3xl font-bold sm:text-4xl">
            The Nelsen LMS will host every track, assignment and certificate
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            A multi-tenant learning system for mentees, mentors and supervisors. Until it ships,
            these tracks run inside your cohort sessions — nothing waits on the software.
          </p>

          <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {LMS_FEATURES.map((f) => (
              <div key={f.title} className="rounded-2xl border border-border/60 bg-card p-6">
                <GraduationCap className="h-5 w-5 text-ember" />
                <h3 className="mt-4 font-display text-base font-semibold">{f.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{f.detail}</p>
              </div>
            ))}
          </div>

          <Link
            to="/contact"
            className="mt-10 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-foreground hover:text-ember"
          >
            Join the early access list <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </section>
    </div>
  );
}
