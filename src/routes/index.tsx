import { Link, createFileRoute } from "@tanstack/react-router";
import { ArrowRight, Briefcase, Compass, Quote, Star, UsersRound } from "lucide-react";

import heroImg from "@/assets/hero-mentorship.jpg";
import { EVENTS, eventVenue } from "@/data/events";
import { PARTNERS, PROGRAMS, REVIEWS, ROADMAP, STATS } from "@/data/site";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Nelsen Savanna — Mentors & Mentees, Guided Career Pathways" },
      {
        name: "description",
        content:
          "Nelsen Savanna connects Kenyan youth with trained mentors — career mapping, communication and interview skills, wellbeing, and junior-to-senior workplace mentorship.",
      },
      { property: "og:title", content: "Nelsen Savanna — Mentors & Mentees" },
      {
        property: "og:description",
        content:
          "Youth guidance in every aspect: careers, comms, interviews and social life. We bring mentors and mentees together.",
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
              Every young person deserves a{" "}
              <span className="bg-ember-gradient bg-clip-text text-transparent">map</span>, not a
              guess.
            </h1>
            <p className="mt-6 max-w-xl text-base leading-relaxed text-muted-foreground sm:text-lg">
              Companion site to the Nelsen Savannah app — we pair teens, students and junior
              professionals with mentors who have already walked the road. Not just the four careers
              everyone talks about.
            </p>
            <div className="mt-9 flex flex-wrap gap-3">
              <Link
                to="/contact"
                className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
              >
                Find me a mentor <ArrowRight className="h-4 w-4" />
              </Link>
              <Link
                to="/programs"
                className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-6 py-3.5 font-display text-sm font-semibold text-foreground transition-colors hover:bg-accent"
              >
                Become a mentor
              </Link>
            </div>

            <dl className="mt-14 grid grid-cols-2 gap-6 sm:grid-cols-4">
              {STATS.map((s) => (
                <div key={s.label}>
                  <dt className="font-display text-2xl font-bold text-foreground sm:text-3xl">
                    {s.value}
                  </dt>
                  <dd className="mt-1 text-xs text-muted-foreground">{s.label}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="relative">
            <div className="absolute -inset-6 rounded-[2.5rem] bg-ember/10 blur-3xl" />
            <img
              src={heroImg}
              alt="A young mentee in conversation with her professional mentor"
              width={1200}
              height={1408}
              className="relative w-full rounded-[2rem] border border-border/70 object-cover shadow-elevated"
            />
            <div className="glass-panel absolute -bottom-6 left-4 right-4 rounded-2xl p-4 sm:left-8 sm:right-8">
              <p className="text-xs text-muted-foreground">
                <span className="font-display font-semibold text-foreground">380 mentors</span>{" "}
                across tech, health, finance, media, trades and public service.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="mx-auto max-w-7xl px-5 py-24 sm:px-8">
        <div className="max-w-2xl">
          <p className="eyebrow text-ember">How it works</p>
          <h2 className="mt-4 text-3xl font-bold sm:text-4xl">
            Two groups, one deliberate bridge
          </h2>
        </div>
        <div className="mt-12 grid gap-6 lg:grid-cols-3">
          {[
            {
              icon: Compass,
              title: "Tell us where you stand",
              body: "A short intake on your stage, interests and constraints — school, campus, or first job.",
            },
            {
              icon: UsersRound,
              title: "We match, not guess",
              body: "You are paired with a trained mentor in your field within 14 days, plus a cohort of peers.",
            },
            {
              icon: Star,
              title: "Sessions with structure",
              body: "Monthly sessions, written goals, and a review at week 12 so progress is visible, not vague.",
            },
          ].map(({ icon: Icon, title, body }, i) => (
            <article
              key={title}
              className="rounded-3xl border border-border/70 bg-card p-8 transition-shadow hover:shadow-elevated"
            >
              <div className="flex items-center justify-between">
                <span className="grid h-11 w-11 place-items-center rounded-xl bg-maroon/10 text-maroon">
                  <Icon className="h-5 w-5" />
                </span>
                <span className="font-display text-sm text-muted-foreground/50">0{i + 1}</span>
              </div>
              <h3 className="mt-6 text-xl font-bold">{title}</h3>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{body}</p>
            </article>
          ))}
        </div>
      </section>

      {/* Programs */}
      {/* Hiring partners */}
      <section className="border-y border-border/60 bg-hero-gradient py-24">
        <div className="mx-auto grid max-w-7xl gap-12 px-5 sm:px-8 lg:grid-cols-[1fr_1.1fr] lg:items-center">
          <div>
            <p className="eyebrow text-ember">Beyond mentorship</p>
            <h2 className="mt-4 text-3xl font-bold text-on-dark sm:text-4xl">
              We have partnered with hiring agents — and we link our people to them
            </h2>
            <p className="mt-5 max-w-xl text-base leading-relaxed text-on-dark/70">
              Mentorship is where it starts, not where it ends. We work with recruitment agents and
              employers, and when a mentee is ready we put their name in front of them. Guidance,
              then a real door to walk through.
            </p>
            <Link
              to="/contact"
              className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Partner with us <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          <div className="grid gap-4">
            {PARTNERS.map((p) => (
              <article key={p.name} className="glass-dark flex gap-4 rounded-3xl p-6">
                <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-ember/15 text-ember">
                  <Briefcase className="h-5 w-5" />
                </span>
                <div>
                  <h3 className="font-display text-base font-semibold text-on-dark">{p.name}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-on-dark/70">{p.detail}</p>
                </div>
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
                Guidance for the parts of life nobody schedules
              </h2>
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
            <p className="eyebrow text-ember">Upcoming</p>
            <h2 className="mt-4 text-3xl font-bold sm:text-4xl">Next three rooms to be in</h2>
          </div>
          <Link
            to="/events"
            className="inline-flex items-center gap-1.5 font-display text-sm font-semibold hover:text-ember"
          >
            Reserve a seat <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
        <div className="mt-12 grid gap-5 md:grid-cols-3">
          {EVENTS.slice(0, 3).map((e) => (
            <Link
              key={e.eventId}
              to="/events"
              className="group flex flex-col rounded-3xl border border-border/70 bg-card p-7 transition-all hover:-translate-y-1 hover:shadow-elevated"
            >
              <span className="eyebrow text-ember">{e.program ?? eventVenue(e)}</span>
              <h3 className="mt-3 text-lg font-bold leading-snug">{e.title}</h3>
              <p className="mt-2 flex-1 text-sm text-muted-foreground">{eventVenue(e)}</p>
              <span className="mt-5 text-xs text-muted-foreground">
                {(e.seats ?? 0) - (e.seatsTaken ?? 0)} seats left · {e.price ?? "—"}
              </span>
            </Link>
          ))}
        </div>
      </section>

      {/* Reviews */}
      <section className="border-t border-border/60 bg-hero-gradient py-24">
        <div className="mx-auto max-w-7xl px-5 sm:px-8">
          <div className="max-w-2xl">
            <p className="eyebrow text-ember">Reviews</p>
            <h2 className="mt-4 text-3xl font-bold text-on-dark sm:text-4xl">
              What mentees and mentors say
            </h2>
          </div>
          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {REVIEWS.map((r) => (
              <figure key={r.name} className="glass-dark rounded-3xl p-7">
                <Quote className="h-6 w-6 text-ember" />
                <blockquote className="mt-4 text-sm leading-relaxed text-on-dark/85">
                  {r.quote}
                </blockquote>
                <figcaption className="mt-6 border-t border-white/10 pt-4">
                  <span className="block font-display text-sm font-semibold text-on-dark">
                    {r.name}
                  </span>
                  <span className="block text-xs text-on-dark/60">{r.role}</span>
                </figcaption>
              </figure>
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
              The mentorship programme comes first. The learning app, the LMS and the gallery follow
              — here is the honest order.
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
            Ready to stop guessing your next step?
          </h2>
          <p className="relative mx-auto mt-4 max-w-xl text-sm leading-relaxed text-muted-foreground sm:text-base">
            Cohorts open every month. Join as a mentee, or bring your experience and mentor someone
            who needs the road map you wish you had.
          </p>
          <div className="relative mt-8 flex flex-wrap justify-center gap-3">
            <Link
              to="/contact"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              Join a cohort <ArrowRight className="h-4 w-4" />
            </Link>
            <Link
              to="/events"
              className="inline-flex items-center gap-2 rounded-full border border-border px-6 py-3.5 font-display text-sm font-semibold transition-colors hover:bg-accent"
            >
              Browse events
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
