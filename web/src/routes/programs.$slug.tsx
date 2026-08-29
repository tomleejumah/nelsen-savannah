import { createFileRoute, notFound } from "@tanstack/react-router";

import { ProgramDetail } from "@/components/site/ProgramDetail";
import { getProgramBySlug } from "@/data/site";

export const Route = createFileRoute("/programs/$slug")({
  head: ({ params }) => {
    const program = getProgramBySlug(params.slug);
    const title = program?.title ?? "Programme";
    return {
      meta: [
        { title: `${title} — Programmes | Nelsen Savannah` },
        {
          name: "description",
          content: program?.blurb ?? "Nelsen Savannah Innovation Hub programme.",
        },
        { property: "og:title", content: `${title} | Nelsen Savannah` },
      ],
    };
  },
  component: ProgramDetailPage,
});

function ProgramDetailPage() {
  const { slug } = Route.useParams();
  const program = getProgramBySlug(slug);
  if (!program) throw notFound();
  return <ProgramDetail program={program} />;
}
