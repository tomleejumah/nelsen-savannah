import { Link } from "@tanstack/react-router";
import { ORG } from "@/data/site";

type Section = { title: string; body: string };
export function PolicyPage({ title, intro, sections }: { title: string; intro: string; sections: Section[] }) {
  return (
    <article className="bg-cream text-ink">
      <div className="mx-auto max-w-4xl px-5 pb-20 pt-24 sm:px-8 sm:pt-32">
        <Link to="/" className="text-sm text-brick hover:underline">← Back to home</Link>
        <p className="mt-10 text-xs font-semibold uppercase tracking-[0.2em] text-brick">Policies & information</p>
        <h1 className="mt-3 font-display text-4xl tracking-tight sm:text-5xl">{title}</h1>
        <p className="mt-5 max-w-2xl text-base leading-7 text-muted-foreground">{intro}</p>
        <p className="mt-4 text-xs text-muted-foreground">Last updated: 3 November 2026</p>
        <div className="mt-12 border-t border-hairline">
          {sections.map((section) => (
            <section key={section.title} className="border-b border-hairline py-7">
              <h2 className="font-display text-xl sm:text-2xl">{section.title}</h2>
              <p className="mt-3 leading-7 text-muted-foreground">{section.body}</p>
            </section>
          ))}
        </div>
        <section className="mt-12 bg-cream-deep p-6 sm:p-8">
          <h2 className="font-display text-xl">Questions or requests?</h2>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">Contact {ORG.legal} at <a className="text-brick underline underline-offset-4" href={`mailto:${ORG.email}`}>{ORG.email}</a>.</p>
        </section>
        <nav aria-label="Other policies" className="mt-10 flex flex-wrap gap-x-6 gap-y-3 text-sm">
          <Link to="/privacy-policy" className="text-brick hover:underline">Privacy Policy</Link>
          <Link to="/refund-policy" className="text-brick hover:underline">Refund Policy</Link>
          <Link to="/shipping-policy" className="text-brick hover:underline">Shipping Policy</Link>
        </nav>
      </div>
    </article>
  );
}
