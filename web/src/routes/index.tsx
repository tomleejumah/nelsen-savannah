import { Link, createFileRoute } from "@tanstack/react-router";

import heroImg from "@/assets/gallery-learners.jpg";
import {
  FACILITATORS,
  GOALS_2030,
  ORG,
  PROGRAMS,
  ROADMAP,
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

const PATHWAY_META: Record<
  string,
  { tag: string; title: string; line: string }
> = {
  "future-safari": {
    tag: "Entry",
    title: "Future Safari",
    line: "A first look at emerging tech — the on-ramp before choosing a track.",
  },
  "savannah-robotics-automation-lab": {
    tag: "Builder",
    title: "Robotics & Automation Lab",
    line: "Design, wire, and program robots that move and respond.",
  },
  "savannah-data-ai-academy": {
    tag: "Builder",
    title: "Data & AI Academy",
    line: "Work real datasets end to end and ship a capstone project.",
  },
  "savannah-software-engineering-lab": {
    tag: "Builder",
    title: "Software Engineering Lab",
    line: "Write and ship software with the tools working engineers use.",
  },
  "savannah-creative-lab": {
    tag: "Creative",
    title: "Creative Lab",
    line: "Design, illustration, and visual storytelling with real briefs.",
  },
  "savannah-sauti-academy": {
    tag: "Creative",
    title: "Sauti Academy",
    line: "Audio, voice, and podcasting — from recording to a finished episode.",
  },
  "kijiji-hub": {
    tag: "Community",
    title: "Kijiji Hub",
    line: "Where finished projects meet the community that will use them.",
  },
};

const PATHWAY_ORDER = [
  "future-safari",
  "savannah-robotics-automation-lab",
  "savannah-data-ai-academy",
  "savannah-software-engineering-lab",
  "savannah-creative-lab",
  "savannah-sauti-academy",
  "kijiji-hub",
] as const;

const AUDIENCE = [
  "Recent graduates and young professionals",
  "Career changers moving into digital work",
  "Developers, designers, and digital creators",
  "Educators and community leaders",
  "Anyone building something for their community",
] as const;

const GAIN_TAGS = [
  "Technical skills",
  "Project experience",
  "Career readiness",
  "Collaboration",
  "Entrepreneurship",
  "African network",
] as const;

const GOALS_FEATURED = [
  GOALS_2030.find((g) => g.label === "Young Africans reached")!,
  GOALS_2030.find((g) => g.label === "Campuses & communities")!,
  GOALS_2030.find((g) => g.label === "Developers & digital creators")!,
  GOALS_2030.find((g) => g.label === "African countries")!,
];

const ROADMAP_HOME = ROADMAP.filter((r) => r.phase === "Live" || r.phase === "Next");

function Index() {
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
              <Link
                to="/programs"
                className="underline decoration-hairline underline-offset-4 transition-colors hover:text-brick hover:decoration-brick"
              >
                See how a cohort runs
              </Link>
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
            <Link
              to="/programs"
              className="inline-flex items-center bg-primary px-5 py-3 text-sm font-medium text-primary-foreground transition-opacity hover:opacity-90"
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

      {/* Pathways */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            Seven pathways, one hub
          </h2>
          <p className="mt-4 max-w-2xl text-base leading-relaxed text-muted-foreground">
            Start wherever fits — exploration for beginners, specialist tracks for people ready to
            build something real.
          </p>

          <ul className="mt-12 border-t border-hairline">
            {PATHWAY_ORDER.map((slug) => {
              const meta = PATHWAY_META[slug];
              const program = PROGRAMS.find((p) => p.slug === slug);
              if (!meta || !program) return null;
              return (
                <li
                  key={slug}
                  className="grid gap-2 border-b border-hairline py-5 sm:grid-cols-[6.5rem_minmax(0,1fr)_auto] sm:items-baseline sm:gap-6"
                >
                  <span className="text-sm text-brick">{meta.tag}</span>
                  <div className="min-w-0 sm:flex sm:items-baseline sm:gap-6">
                    <h3 className="shrink-0 font-display text-lg tracking-tight text-ink sm:w-56 lg:w-64">
                      {meta.title}
                    </h3>
                    <p className="mt-1 text-sm leading-relaxed text-muted-foreground sm:mt-0">
                      {meta.line}
                    </p>
                  </div>
                  <Link
                    to="/programs/$slug"
                    params={{ slug }}
                    className="text-sm text-brick underline decoration-brick/40 underline-offset-4 transition-colors hover:decoration-brick"
                  >
                    Details
                  </Link>
                </li>
              );
            })}
          </ul>
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
              {AUDIENCE.map((item) => (
                <li
                  key={item}
                  className="border-b border-white/15 py-4 text-sm leading-relaxed text-on-dark/85"
                >
                  {item}
                </li>
              ))}
            </ul>
            <div className="grid grid-cols-2 gap-3 self-start">
              {GAIN_TAGS.map((tag) => (
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

      {/* 2030 goals */}
      <section className="border-b border-hairline bg-cream-deep">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            What we&apos;re building toward by 2030
          </h2>
          <p className="mt-4 max-w-xl text-base leading-relaxed text-muted-foreground">
            Targets, not results yet — the scale we&apos;re organising the hub to reach.
          </p>

          <dl className="mt-12 grid border border-hairline sm:grid-cols-2 lg:grid-cols-4">
            {GOALS_FEATURED.map((g) => (
              <div
                key={g.label}
                className="border-hairline px-5 py-7 sm:border-r sm:last:border-r-0 [&:nth-child(-n+2)]:border-b lg:[&:nth-child(-n+2)]:border-b-0"
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

      {/* Roadmap */}
      <section className="border-b border-hairline">
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="font-display text-3xl tracking-tight text-ink sm:text-4xl">
            What&apos;s live, what&apos;s next
          </h2>

          <ul className="mt-12 border-t border-hairline">
            {ROADMAP_HOME.map((r) => (
              <li
                key={r.title}
                className="grid gap-2 border-b border-hairline py-6 sm:grid-cols-[5rem_1fr] sm:gap-8"
              >
                <span className="text-sm text-brick">{r.phase}</span>
                <div>
                  <h3 className="font-display text-xl tracking-tight text-ink">{r.title}</h3>
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
        <div className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20">
          <h2 className="max-w-xl font-display text-3xl tracking-tight sm:text-4xl">
            Africa&apos;s next builders are already here.
          </h2>
          <p className="mt-4 max-w-lg text-base text-primary-foreground/75">
            Join a programme, bring one to your campus, or partner with us.
          </p>
          <Link
            to="/programs"
            className="mt-8 inline-flex items-center bg-ochre px-5 py-3 text-sm font-medium text-ink transition-opacity hover:opacity-90"
          >
            Join a programme
          </Link>
        </div>
      </section>
    </div>
  );
}
