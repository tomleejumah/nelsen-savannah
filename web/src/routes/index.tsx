import { Link, createFileRoute } from "@tanstack/react-router";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { useEffect, useState } from "react";

import brandBanner from "@/assets/hero-brand.jpg";
import heroImg from "@/assets/gallery-learners.jpg";
import { type AppEvent, eventDateLabel, eventVenue } from "@/data/events";
import { loadHubEvents } from "@/lib/hubEvents";
import {
  APPROACH,
  CORE_VALUES,
  FACILITATORS,
  FIRST_INTAKE,
  GOALS_2030,
  INNOVATION_CYCLE,
  ORG,
  PRACTICAL_APPLICATION,
  PROGRAM_PILLARS,
  PROGRAMS,
  ROADMAP,
  WHAT_YOU_GAIN,
  WHO_SHOULD_JOIN,
} from "@/data/site";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Nelsen Savannah — Practical digital skills for Africa's next builders" },
      {
        name: "description",
        content:
          "Robotics, data, software, design, and audio — practical digital skills at the Nelsen Savannah Innovation Hub in Nairobi.",
      },
      { property: "og:title", content: "Nelsen Savannah" },
      {
        property: "og:description",
        content:
          "Practical digital skills for Africa's next builders. Seven pathways at the Nairobi hub.",
      },
    ],
  }),
  component: Index,
});

const HERO_STATS = [
  {
    value: "7",
    label: "programme pathways, from first exposure to specialist",
  },
  {
    value: "Aug 2026",
    label: "first cohort intake, Nairobi hub",
  },
  {
    value: String(FACILITATORS.length),
    label: "lead facilitators, both practitioners",
  },
  {
    value: "100%",
    label: "project-based — you leave with something built",
  },
] as const;

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
    <div className="bg-cream text-ink">
      {/* Hero */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 pb-16 pt-28 sm:px-8 sm:pb-20 sm:pt-36">
          <p className="text-sm text-brick">
            Nairobi, Kenya — an education and innovation hub
          </p>

          <div className="mt-8 grid gap-8 lg:grid-cols-[1.15fr_0.85fr] lg:items-end lg:gap-12">
            <h1 className="font-display text-[2.35rem] leading-[1.08] tracking-tight text-ink sm:text-5xl lg:text-[3.35rem]">
              Practical digital skills for Africa&apos;s next builders.
            </h1>
            <p className="max-w-md text-[0.95rem] leading-relaxed text-muted-foreground sm:text-base">
              Robotics, data, software, design, and audio — for graduates, career changers,
              developers, and educators who want to build, not just study.{" "}
              <a
                href="#programmes"
                className="underline decoration-hairline underline-offset-4 transition-colors hover:text-brick hover:decoration-brick"
              >
                See the programmes
              </a>
            </p>
          </div>

          <div className="mt-12 grid gap-8 lg:grid-cols-[1.2fr_0.8fr] lg:gap-10">
            <figure className="relative overflow-hidden">
              <img
                src={heroImg}
                alt="Learners building together at the Nelsen Savannah Nairobi hub"
                width={1024}
                height={877}
                className="aspect-[4/3] w-full object-cover sm:aspect-[16/11]"
              />
              <figcaption className="absolute bottom-3 left-3 bg-ink/75 px-2.5 py-1 text-xs text-on-dark">
                A build session at the Nairobi hub
              </figcaption>
            </figure>

            <dl className="flex flex-col justify-center">
              {HERO_STATS.map((s) => (
                <div
                  key={s.value}
                  className="grid grid-cols-[7.5rem_1fr] items-baseline gap-4 border-b border-hairline py-5 first:border-t sm:grid-cols-[8.5rem_1fr]"
                >
                  <dt className="font-display text-2xl tracking-tight text-ink sm:text-3xl">
                    {s.value}
                  </dt>
                  <dd className="text-sm leading-snug text-muted-foreground">{s.label}</dd>
                </div>
              ))}
            </dl>
          </div>

          <div className="mt-10 flex flex-wrap gap-3">
            <a
              href="#programmes"
              className="inline-flex items-center bg-ember-gradient px-5 py-3 text-sm font-medium text-maroon-foreground shadow-ember-glow transition-opacity hover:opacity-90"
            >
              Join a programme
            </a>
            <Link
              to="/contact"
              className="inline-flex items-center border border-ink px-5 py-3 text-sm font-medium text-ink transition-colors hover:bg-ink hover:text-on-dark"
            >
              Talk to us
            </Link>
          </div>
        </div>
      </section>

      {/* Programmes — early, full cards */}
      <section id="programmes" className="scroll-mt-28 border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            Seven pathways, one hub
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Start wherever fits — exploration for beginners, specialist tracks for people ready to
            build something real. Every pathway ends in a project you can show.
          </p>

          <div className="mt-12 grid gap-5 lg:grid-cols-2">
            {PROGRAMS.map((p, i) => (
              <article
                key={p.slug}
                className="rounded-3xl border border-hairline bg-card p-7 transition-shadow hover:shadow-elevated"
              >
                <div className="flex items-center justify-between gap-4">
                  <span className={`rounded-full px-3 py-1 text-xs font-semibold ${toneClass[p.tone]}`}>
                    {p.audience}
                  </span>
                  <span className="font-display text-sm text-muted-foreground/60">
                    {String(i + 1).padStart(2, "0")}
                  </span>
                </div>
                <h3 className="mt-5 font-display text-2xl tracking-tight text-ink">
                  <Link
                    to="/programs/$slug"
                    params={{ slug: p.slug }}
                    className="transition-colors hover:text-brick"
                  >
                    {p.title}
                  </Link>
                </h3>
                <p className="mt-1 text-sm font-medium text-brick">{p.subtitle}</p>
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
                  className="mt-6 inline-flex items-center gap-1.5 font-display text-sm font-semibold text-ink transition-colors hover:text-brick"
                >
                  About this programme <ArrowRight className="h-4 w-4" />
                </Link>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Vision & mission */}
      <section className="border-b border-hairline">
        <div className="mx-auto grid max-w-6xl gap-10 px-5 py-16 sm:px-8 sm:py-20 lg:grid-cols-2 lg:gap-16">
          <div>
            <p className="text-sm text-brick">Our vision</p>
            <h2 className="mt-3 font-display text-2xl tracking-tight text-ink sm:text-3xl">
              {ORG.vision}
            </h2>
          </div>
          <div>
            <p className="text-sm text-brick">Our mission</p>
            <h2 className="mt-3 font-display text-2xl tracking-tight text-ink sm:text-3xl">
              {ORG.mission}
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-muted-foreground">{APPROACH}</p>
          </div>
        </div>
      </section>

      {/* First intake / events */}
      <section className="border-b border-hairline bg-cream-deep">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="max-w-2xl">
              <p className="text-sm text-brick">First intake · {FIRST_INTAKE.label}</p>
              <h2 className="mt-3 font-display text-3xl tracking-tight text-ink sm:text-4xl">
                Reserve your free seat
                {featured ? ` — ${featured.title}` : ""}
              </h2>
              <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
                With {FACILITATORS.map((f) => f.name).join(" and ")}
                {featured ? ` · ${eventVenue(featured)}` : ""}
              </p>
            </div>
            <Link
              to="/events"
              hash={featured?.eventId}
              className="text-sm text-brick underline decoration-brick/40 underline-offset-4 hover:decoration-brick"
            >
              Reserve free seat
            </Link>
          </div>
          {hubEvents.length > 0 ? (
            <ul className="mt-12 border-t border-hairline">
              {hubEvents.slice(0, 3).map((e) => (
                <li key={e.eventId} className="border-b border-hairline py-5">
                  <Link to="/events" hash={e.eventId} className="group block">
                    <span className="text-sm text-brick">{eventDateLabel(e)}</span>
                    <h3 className="mt-2 font-display text-xl tracking-tight text-ink group-hover:text-brick">
                      {e.title}
                    </h3>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {eventVenue(e)} · {e.price ?? "Free"}
                    </p>
                  </Link>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      </section>

      {/* Audience — navy */}
      <section className="bg-navy text-on-dark">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight sm:text-4xl">
            Built for more than students
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-on-dark/70">
            Recent graduates, career changers, working developers and designers, and the educators
            who train the next group — all in the same hub.
          </p>

          <div className="mt-12 grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:gap-16">
            <ul className="border-t border-white/15">
              {WHO_SHOULD_JOIN.map((item) => (
                <li
                  key={item}
                  className="border-b border-white/15 py-4 text-sm leading-relaxed text-on-dark/85"
                >
                  {item}
                </li>
              ))}
            </ul>
            <div className="grid grid-cols-2 gap-3 self-start">
              {WHAT_YOU_GAIN.map((tag) => (
                <span
                  key={tag}
                  className="border border-white/35 px-3 py-3 text-center text-sm text-on-dark"
                >
                  {tag}
                </span>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* How we deliver */}
      <section className="border-b border-hairline bg-cream-deep">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            Learn, build, connect, create impact
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Six pillars across every programme — practical skills through real projects, with
            mentorship and a path to opportunity.
          </p>
          <ul className="mt-12 border-t border-hairline">
            {PROGRAM_PILLARS.map((p) => (
              <li
                key={p.title}
                className="grid gap-2 border-b border-hairline py-5 sm:grid-cols-[12rem_1fr] sm:gap-8"
              >
                <h3 className="font-display text-lg tracking-tight text-ink">{p.title}</h3>
                <p className="text-sm leading-relaxed text-muted-foreground">{p.body}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* Practical application */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            Build, test, and present — not just study
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Learners apply concepts through robotics builds, data capstones, creative portfolios,
            software projects, and community innovation challenges.
          </p>
          <ul className="mt-12 border-t border-hairline">
            {PRACTICAL_APPLICATION.map((item) => (
              <li
                key={item}
                className="border-b border-hairline py-4 text-sm leading-relaxed text-muted-foreground"
              >
                {item}
              </li>
            ))}
          </ul>
          <Link
            to="/learning"
            className="mt-8 inline-flex text-sm text-brick underline decoration-brick/40 underline-offset-4 hover:decoration-brick"
          >
            Browse learning tracks
          </Link>
        </div>
      </section>

      {/* 2030 goals — Community projects first */}
      <section className="border-b border-hairline bg-cream-deep">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            What we&apos;re building toward by 2030
          </h2>
          <p className="mt-4 max-w-xl text-base leading-relaxed text-muted-foreground">
            Targets, not results yet — the scale we&apos;re organising the hub to reach.
          </p>

          <dl className="mt-12 grid border border-hairline sm:grid-cols-2 lg:grid-cols-4">
            {GOALS_2030.map((g) => (
              <div
                key={g.label}
                className="border-b border-r border-hairline px-5 py-7 last:border-r-0 sm:[&:nth-child(2n)]:border-r-0 lg:[&:nth-child(2n)]:border-r lg:[&:nth-child(4n)]:border-r-0"
              >
                <dt className="font-display text-3xl tracking-tight text-brick sm:text-4xl">
                  {g.value}
                </dt>
                <dd className="mt-2 text-sm text-muted-foreground">{g.label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </section>

      {/* Core values */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            How we work at the Hub
          </h2>
          <ul className="mt-12 border-t border-hairline">
            {CORE_VALUES.map((v) => (
              <li
                key={v.key}
                className="grid gap-2 border-b border-hairline py-5 sm:grid-cols-[11rem_1fr] sm:gap-8"
              >
                <span className="text-sm font-medium uppercase tracking-wide text-brick">
                  {v.key}
                </span>
                <p className="text-sm leading-relaxed text-muted-foreground">{v.detail}</p>
              </li>
            ))}
          </ul>
          <div className="mt-10 flex flex-wrap gap-x-1 gap-y-2 text-sm text-muted-foreground">
            {INNOVATION_CYCLE.map((step, i) => (
              <span key={step}>
                {i > 0 ? <span className="mx-1 text-hairline">→</span> : null}
                {step}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* Roadmap */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            What&apos;s live, what&apos;s next
          </h2>
          <p className="mt-4 max-w-2xl text-sm leading-relaxed text-muted-foreground">
            Programmes and the learning platform are live. Cohort milestones, payments, and
            certificates are next on the roadmap.
          </p>

          <ul className="mt-12 border-t border-hairline">
            {ROADMAP.map((r) => (
              <li
                key={r.title}
                className="grid gap-2 border-b border-hairline py-6 sm:grid-cols-[5rem_1fr] sm:gap-8"
              >
                <span className="text-sm text-brick">{r.phase}</span>
                <div>
                  <div className="flex flex-wrap items-baseline gap-3">
                    <h3 className="font-display text-xl tracking-tight text-ink">{r.title}</h3>
                    <span className="text-xs text-muted-foreground">{r.status}</span>
                  </div>
                  <p className="mt-2 max-w-2xl text-sm leading-relaxed text-muted-foreground">
                    {r.body}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* Gallery */}
      <section className="border-b border-hairline bg-cream-deep">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            Learn. Build. Create.
          </h2>
          <p className="mt-4 max-w-xl text-base leading-relaxed text-muted-foreground">
            Moments from Nelsen Savannah — learners coding, designing, and shipping work in Kenya.
          </p>
          <div className="mt-12 grid gap-5 lg:grid-cols-[1.15fr_0.85fr]">
            <img
              src={heroImg}
              alt="Nelsen Savannah learners at work — design, data, and code"
              width={1024}
              height={877}
              loading="lazy"
              className="h-full min-h-[16rem] w-full object-cover"
            />
            <img
              src={brandBanner}
              alt="Nelsen Savannah Kenya — learners studying and creating"
              width={1024}
              height={405}
              loading="lazy"
              className="h-full min-h-[16rem] w-full object-cover"
            />
          </div>
        </div>
      </section>

      {/* Dark CTA */}
      <section className="bg-primary text-primary-foreground">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="max-w-xl font-display text-3xl tracking-tight sm:text-4xl">
            Africa&apos;s next builders are already here.
          </h2>
          <p className="mt-4 max-w-lg text-base text-primary-foreground/75">
            Join a programme, bring one to your campus, partner with us, or apply to work here.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link
              to="/learning"
              className="inline-flex items-center bg-ochre px-5 py-3 text-sm font-medium text-ink transition-opacity hover:opacity-90"
            >
              Join a programme
            </Link>
            <Link
              to="/contact"
              search={{ intent: "campus" }}
              className="inline-flex items-center border border-primary-foreground/40 px-5 py-3 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-foreground/10"
            >
              Host a programme
            </Link>
            <Link
              to="/contact"
              search={{ intent: "partner" }}
              className="inline-flex items-center border border-primary-foreground/40 px-5 py-3 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-foreground/10"
            >
              Become a partner
            </Link>
            <Link
              to="/contact"
              search={{ intent: "careers" }}
              className="inline-flex items-center border border-primary-foreground/40 px-5 py-3 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary-foreground/10"
            >
              Work with us
            </Link>
          </div>
        </div>
      </section>
    </div>
  );
}
