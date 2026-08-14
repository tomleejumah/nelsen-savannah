import { createFileRoute } from "@tanstack/react-router";
import { ArrowUpRight, Clock } from "lucide-react";

import { MEDIA } from "@/data/site";

export const Route = createFileRoute("/blogs")({
  head: () => ({
    meta: [
      { title: "Blogs — Stories & Guidance | Nelsen Savannah" },
      {
        name: "description",
        content:
          "Articles from mentors — career paths, interview technique, mentorship and money habits.",
      },
      { property: "og:title", content: "Blogs | Nelsen Savannah" },
    ],
  }),
  component: BlogsPage,
});

function BlogsPage() {
  const featured = MEDIA[0]!;
  const rest = MEDIA.slice(1);

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <header className="mx-auto max-w-7xl px-5 sm:px-8">
        <p className="eyebrow text-ember">Blogs</p>
        <h1 className="mt-4 max-w-3xl text-4xl font-bold sm:text-5xl">
          Notes from mentors, written for the person deciding right now
        </h1>
      </header>

      <section className="mx-auto mt-12 max-w-7xl px-5 sm:px-8">
        <article className="group grid overflow-hidden rounded-3xl border border-border/70 bg-card lg:grid-cols-[1.1fr_1fr]">
          <div className="bg-hero-gradient p-10 sm:p-14">
            <span className="eyebrow text-ember">{featured.fileType}</span>
            <h2 className="mt-4 text-3xl font-bold text-on-dark sm:text-4xl">
              {featured.title}
            </h2>
          </div>
          <div className="flex flex-col justify-between gap-6 p-8 sm:p-12">
            <p className="text-base leading-relaxed text-muted-foreground">
              {featured.description}
            </p>
            <div className="flex items-center justify-between text-sm text-muted-foreground">
              <span className="flex items-center gap-1.5">
                <Clock className="h-4 w-4" /> {featured.readMinutes} min read
              </span>
              <span className="flex items-center gap-1 font-display font-semibold text-foreground group-hover:text-ember">
                Open <ArrowUpRight className="h-4 w-4" />
              </span>
            </div>
          </div>
        </article>

        <div className="mt-6 grid gap-6 md:grid-cols-3">
          {rest.map((post) => (
            <article
              key={post.postID}
              className="group flex flex-col rounded-3xl border border-border/70 bg-card p-7 transition-shadow hover:shadow-elevated"
            >
              <span className="eyebrow text-ember">{post.fileType}</span>
              <h3 className="mt-3 text-xl font-bold leading-snug">{post.title}</h3>
              <p className="mt-3 flex-1 text-sm leading-relaxed text-muted-foreground">
                {post.description}
              </p>
              <div className="mt-6 flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  {new Date(post.timestamp).toLocaleDateString("en-GB", {
                    day: "numeric",
                    month: "short",
                    year: "numeric",
                  })}
                </span>
                <span className="flex items-center gap-1.5">
                  <Clock className="h-3.5 w-3.5" /> {post.readMinutes} min
                </span>
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
