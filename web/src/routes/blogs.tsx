import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowRight } from "lucide-react";

export const Route = createFileRoute("/blogs")({
  head: () => ({
    meta: [
      { title: "Blogs — Coming soon | Nelsen Savannah" },
      {
        name: "description",
        content:
          "Stories, programme notes, and learner journeys from Nelsen Savannah — publishing soon.",
      },
      { property: "og:title", content: "Blogs | Nelsen Savannah" },
    ],
  }),
  component: BlogsPage,
});

function BlogsPage() {
  return (
    <div className="pb-24 pt-36 sm:pt-44">
      <div className="mx-auto max-w-2xl px-5 text-center sm:px-8">
        <p className="eyebrow text-ember">Blogs</p>
        <h1 className="mt-4 text-4xl font-bold sm:text-5xl">Coming soon</h1>
        <p className="mt-5 text-base leading-relaxed text-muted-foreground">
          We&apos;re preparing programme notes and learner stories. No posts yet — check back after
          the next intake, or follow events and programmes in the meantime.
        </p>
        <div className="mt-8 flex flex-wrap justify-center gap-3">
          <Link
            to="/events"
            className="inline-flex items-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow"
          >
            Upcoming events <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            to="/programs"
            className="inline-flex items-center gap-2 rounded-full border border-border px-6 py-3.5 font-display text-sm font-semibold transition-colors hover:bg-accent"
          >
            Our programmes
          </Link>
        </div>
      </div>
    </div>
  );
}
