import { createFileRoute, Link } from "@tanstack/react-router";
import {
  ArrowRight,
  BadgeCheck,
  Building2,
  ClipboardList,
  FileSearch,
  Handshake,
  MapPinned,
  MessageCircle,
  Search,
  ShieldCheck,
} from "lucide-react";

import { DeskInquiryForm } from "@/components/site/DeskInquiryForm";

export const Route = createFileRoute("/invest")({
  head: () => ({
    meta: [
      { title: "Invest — Opportunities in Kenya | Nelsen Savannah" },
      {
        name: "description",
        content:
          "A Kenya investment desk for international and diaspora capital — research, shortlist, diligence, and a clear path to close.",
      },
      { property: "og:title", content: "Invest | Nelsen Savannah" },
    ],
  }),
  component: InvestPage,
});

const INTEREST_OPTIONS = [
  { value: "Operating businesses", label: "Operating businesses" },
  { value: "Hospitality & tourism", label: "Hospitality & tourism" },
  { value: "Agribusiness", label: "Agribusiness" },
  { value: "Development projects", label: "Development projects" },
  { value: "Joint ventures", label: "Joint ventures" },
  { value: "Portfolio / open", label: "Portfolio / open" },
];

/** Five steps that earn trust before capital moves. */
const TRUST_STEPS = [
  {
    id: "listen",
    step: "01",
    icon: MessageCircle,
    eyebrow: "Listen",
    title: "Understand your mandate",
    body: "Ticket size, risk appetite, timeline — we start with what you actually want to deploy, not a generic pitch deck.",
  },
  {
    id: "research",
    step: "02",
    icon: Search,
    eyebrow: "Research",
    title: "Do the homework first",
    body: "Market and operator research on our side, so you only see theses that survive a first filter — not every rumour on a chat thread.",
  },
  {
    id: "shortlist",
    step: "03",
    icon: ClipboardList,
    eyebrow: "Shortlist",
    title: "Curate what fits",
    body: "A tight shortlist matched to your interest — fewer options, clearer rationale, ready for a serious conversation.",
  },
  {
    id: "diligence",
    step: "04",
    icon: ShieldCheck,
    eyebrow: "Diligence",
    title: "Verify before money moves",
    body: "Local checks, partner intros, and document trails so trust is earned on evidence — not on a sales call.",
  },
  {
    id: "close",
    step: "05",
    icon: BadgeCheck,
    eyebrow: "Close path",
    title: "Counsel to term sheet",
    body: "We walk you to counsel and a clean term sheet. Your lawyers and bank stay yours — we stay the local desk that got you there.",
  },
] as const;

const EXTRAS = [
  {
    icon: FileSearch,
    title: "We do the local work",
    body: "Research, diligence intros, and counsel introductions before serious money moves.",
  },
  {
    icon: Building2,
    title: "Built for international capital",
    body: "Especially diaspora and international investors who want a trusted local desk in Kenya — not a cold listing site.",
  },
  {
    icon: Handshake,
    title: "Why Kenya, why now",
    body: "A growing economy, investable operators, and a market you can visit and verify in one week.",
  },
  {
    icon: MapPinned,
    title: "Clear path to close",
    body: "Intro call → NDA → shortlist → diligence → counsel → term sheet.",
  },
] as const;

function InvestPage() {
  return (
    <div className="pb-24">
      <section className="relative overflow-hidden px-5 pb-20 pt-36 sm:px-8 sm:pt-44">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,oklch(0.52_0.21_25_/_0.14),transparent_55%),linear-gradient(180deg,oklch(0.22_0.04_40_/_0.06),transparent_40%)]"
        />
        <div className="relative mx-auto max-w-4xl">
          <p className="eyebrow text-ember">Invest</p>
          <h1 className="mt-4 font-display text-4xl font-bold text-foreground sm:text-6xl">
            Kenya is open. Bring capital with intent.
          </h1>
          <p className="mt-6 max-w-2xl text-base leading-relaxed text-muted-foreground sm:text-lg">
            Nelsen Savannah is a local investment desk — we research, shortlist, and diligence
            opportunities so international and diaspora capital can move with clarity, not guesswork.
          </p>
          <div className="mt-10 flex flex-wrap gap-3">
            <a
              href="#path"
              className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
            >
              How we earn trust <ArrowRight className="h-4 w-4" />
            </a>
            <a
              href="#inquire"
              className="inline-flex items-center gap-2 rounded-full border border-border bg-card px-6 py-3 font-display text-sm font-semibold text-foreground transition-colors hover:bg-accent"
            >
              Request shortlist
            </a>
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-muted-foreground">Why this desk</p>
        <h2 className="mt-3 font-display text-3xl font-bold">Built to move investors</h2>
        <ul className="mt-10 grid gap-8 sm:grid-cols-2">
          {EXTRAS.map((item) => (
            <li key={item.title} className="space-y-3 border-t border-border/70 pt-6">
              <span className="icon-chip">
                <item.icon className="h-5 w-5" aria-hidden />
              </span>
              <h3 className="font-display text-lg font-semibold">{item.title}</h3>
              <p className="text-sm leading-relaxed text-muted-foreground">{item.body}</p>
            </li>
          ))}
        </ul>
      </section>

      <section className="mx-auto mt-20 max-w-7xl px-5 sm:px-8">
        <div className="grid gap-0 overflow-hidden rounded-[2rem] border border-border/60 lg:grid-cols-2">
          <div className="bg-card/50 p-8 sm:p-12">
            <p className="eyebrow text-ember">For investors</p>
            <h2 className="mt-4 font-display text-2xl font-bold sm:text-3xl">
              Arrive with intent. Leave with a map.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
              Intro call → NDA → shortlist → diligence → counsel → term sheet. We sit between you
              and the opportunity so you never start from a cold forward.
            </p>
            <a
              href="#inquire"
              className="mt-8 inline-flex items-center gap-2 rounded-full bg-ember-gradient px-5 py-2.5 font-display text-sm font-semibold text-maroon-foreground"
            >
              Request shortlist <ArrowRight className="h-4 w-4" />
            </a>
          </div>
          <div className="bg-hero-gradient p-8 text-on-dark sm:p-12">
            <p className="eyebrow text-ember">How we work</p>
            <h2 className="mt-4 font-display text-2xl font-bold sm:text-3xl">
              A local desk between capital and opportunity.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-on-dark/80">
              Deals come from operators we know, assets we hold, and partners we trust. Your job is
              capital and decision; ours is to surface what is investable and walk the path with
              you.
            </p>
          </div>
        </div>
      </section>

      <section id="path" className="mx-auto mt-20 max-w-7xl scroll-mt-28 px-5 sm:px-8">
        <p className="eyebrow text-muted-foreground">How trust is earned</p>
        <h2 className="mt-3 max-w-xl font-display text-3xl font-bold">
          Five steps before capital moves.
        </h2>
        <p className="mt-3 max-w-2xl text-sm text-muted-foreground">
          Research and diligence first — so when you write, you’re choosing from work already done.
        </p>
        <ol className="mt-10 grid gap-8 sm:grid-cols-2 lg:grid-cols-3">
          {TRUST_STEPS.map((item) => (
            <li key={item.id} className="space-y-4 border-t border-border/70 pt-6">
              <div className="flex items-center gap-3">
                <span className="icon-chip-lg">
                  <item.icon className="h-5 w-5" aria-hidden />
                </span>
                <span className="font-display text-sm font-semibold text-muted-foreground">
                  {item.step}
                </span>
              </div>
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {item.eyebrow}
              </p>
              <h3 className="font-display text-xl font-semibold">{item.title}</h3>
              <p className="text-sm leading-relaxed text-muted-foreground">{item.body}</p>
            </li>
          ))}
        </ol>
        <a
          href="#inquire"
          className="mt-10 inline-flex items-center gap-2 text-sm font-semibold text-maroon hover:underline"
        >
          Request shortlist <ArrowRight className="h-3.5 w-3.5" />
        </a>
      </section>

      <div className="mt-20">
        <DeskInquiryForm
          desk="invest"
          title="Investor inquiry"
          blurb="Name, email, and what you’re interested in — we send that to the desk."
          subjectPrefix="Invest inquiry"
          submitLabel="Send investor inquiry"
          fields={[
            {
              name: "name",
              label: "Full name",
              placeholder: "Your full name",
              required: true,
            },
            {
              name: "email",
              label: "Email",
              type: "email",
              placeholder: "you@email.com",
              required: true,
            },
            {
              name: "sector",
              label: "Interest",
              type: "select",
              required: true,
              options: INTEREST_OPTIONS,
            },
          ]}
        />
      </div>

      <section className="mx-auto mt-10 max-w-7xl px-5 sm:px-8">
        <p className="text-sm text-muted-foreground">
          Looking for parks and safari instead?{" "}
          <Link to="/tourism" className="font-semibold text-maroon hover:underline">
            Visit our tourism desk
          </Link>
          .
        </p>
      </section>
    </div>
  );
}
