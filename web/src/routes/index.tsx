import { Link, createFileRoute } from "@tanstack/react-router";

import heroImg from "@/assets/gallery-learners.jpg";
import {
  APPROACH,
  CORE_VALUES,
  FACILITATORS,
  GOALS_2030,
  INNOVATION_CYCLE,
  ORG,
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

function Index() {
  return (
    <div className="bg-cream text-ink">
      {/* Hero */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-7xl px-5 pb-16 pt-28 sm:px-8 sm:pb-20 sm:pt-36">
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
              <Link
                to="/programs"
                className="underline decoration-hairline underline-offset-4 transition-colors hover:text-brick hover:decoration-brick"
              >
                See the programmes
              </Link>
            </p>
          </div>

          <div className="mt-12 grid gap-8 lg:grid-cols-[1.35fr_0.65fr] lg:items-stretch lg:gap-10">
            <figure className="relative aspect-[4/3] overflow-hidden sm:aspect-[16/11] lg:aspect-auto lg:min-h-0">
              <img
                src={heroImg}
                alt="Learners building together at the Nelsen Savannah Nairobi hub"
                width={1024}
                height={877}
                className="h-full w-full object-cover lg:absolute lg:inset-0"
              />
              <figcaption className="absolute bottom-3 left-3 z-10 bg-ink/75 px-2.5 py-1 text-xs text-on-dark">
                A build session at the Nairobi hub
              </figcaption>
            </figure>

            <dl className="flex flex-col justify-between">
              {HERO_STATS.map((s) => (
                <div
                  key={s.value}
                  className="grid grid-cols-[7.5rem_1fr] items-baseline gap-4 border-b border-hairline py-4 first:border-t sm:grid-cols-[8.5rem_1fr] sm:py-5"
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
            <Link
              to="/programs"
              className="inline-flex items-center bg-ember-gradient px-5 py-3 text-sm font-medium text-maroon-foreground shadow-ember-glow transition-opacity hover:opacity-90"
            >
              Join a programme
            </Link>
            <Link
              to="/contact"
              className="inline-flex items-center border border-ink px-5 py-3 text-sm font-medium text-ink transition-colors hover:bg-ink hover:text-on-dark"
            >
              Talk to us
            </Link>
          </div>
        </div>
      </section>

      {/* 2030 goals — second after landing */}
      <section className="border-b border-hairline bg-cream-deep">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20">
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

      {/* Vision & mission */}
      <section className="border-b border-hairline">
        <div className="mx-auto grid max-w-7xl gap-10 px-5 py-16 sm:px-8 sm:py-20 lg:grid-cols-2 lg:gap-16">
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

      {/* Audience — fixed dark chocolate band (readable in light + dark) */}
      <section className="bg-ink-deep text-on-ink-deep">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight sm:text-4xl">
            Built for more than students
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-on-ink-deep/70">
            Recent graduates, career changers, working developers and designers, and the educators
            who train the next group — all in the same hub.
          </p>

          <div className="mt-12 grid gap-10 lg:grid-cols-[1.1fr_0.9fr] lg:gap-16">
            <ul className="border-t border-white/15">
              {WHO_SHOULD_JOIN.map((item) => (
                <li
                  key={item}
                  className="border-b border-white/15 py-4 text-sm leading-relaxed text-on-ink-deep/85"
                >
                  {item}
                </li>
              ))}
            </ul>
            <div className="grid grid-cols-2 gap-3 self-start">
              {WHAT_YOU_GAIN.map((tag) => (
                <span
                  key={tag}
                  className="border border-white/35 px-3 py-3 text-center text-sm text-on-ink-deep"
                >
                  {tag}
                </span>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* How we work — process steps */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20">
          <div className="mx-auto max-w-2xl text-center">
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-ember">
              How we work
            </p>
            <h2 className="mt-3 font-display text-3xl tracking-tight text-ink sm:text-4xl">
              From explore to launch at the Hub
            </h2>
            <p className="mt-4 text-base leading-relaxed text-muted-foreground">
              Seven habits learners practise across every programme — then ship something real.
            </p>
          </div>

          <div className="mt-16 space-y-12">
            <div className="relative">
              <div
                aria-hidden
                className="pointer-events-none absolute left-[12%] right-[12%] top-6 hidden h-px bg-hairline lg:block"
              />
              <ol className="grid gap-x-6 gap-y-12 sm:grid-cols-2 lg:grid-cols-4">
                {CORE_VALUES.slice(0, 4).map((v, i) => (
                  <li key={v.key} className="relative flex flex-col items-center text-center">
                    <span className="relative z-10 flex h-12 w-12 items-center justify-center rounded-xl bg-ink-deep font-display text-sm font-semibold text-on-ink-deep shadow-elevated">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <h3 className="mt-5 font-display text-lg tracking-tight text-ink">
                      {v.key
                        .toLowerCase()
                        .replace(/\b\w/g, (c) => c.toUpperCase())}
                    </h3>
                    <p className="mt-2 max-w-[16rem] text-sm leading-relaxed text-muted-foreground">
                      {v.detail}
                    </p>
                  </li>
                ))}
              </ol>
            </div>

            <div className="relative mx-auto max-w-3xl">
              <div
                aria-hidden
                className="pointer-events-none absolute left-[16%] right-[16%] top-6 hidden h-px bg-hairline sm:block"
              />
              <ol className="grid gap-x-6 gap-y-12 sm:grid-cols-3">
                {CORE_VALUES.slice(4).map((v, i) => (
                  <li key={v.key} className="relative flex flex-col items-center text-center">
                    <span className="relative z-10 flex h-12 w-12 items-center justify-center rounded-xl bg-ink-deep font-display text-sm font-semibold text-on-ink-deep shadow-elevated">
                      {String(i + 5).padStart(2, "0")}
                    </span>
                    <h3 className="mt-5 font-display text-lg tracking-tight text-ink">
                      {v.key
                        .toLowerCase()
                        .replace(/\b\w/g, (c) => c.toUpperCase())}
                    </h3>
                    <p className="mt-2 max-w-[16rem] text-sm leading-relaxed text-muted-foreground">
                      {v.detail}
                    </p>
                  </li>
                ))}
              </ol>
            </div>
          </div>

          <p className="mt-14 text-center text-sm text-muted-foreground">
            {INNOVATION_CYCLE.join(" → ")}
          </p>
        </div>
      </section>

      {/* Roadmap */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20">
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

      {/* Dark CTA */}
      <section className="bg-primary text-primary-foreground">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8 sm:py-20">
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
