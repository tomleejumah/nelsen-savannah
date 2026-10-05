import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/stories/$storyId")({
  beforeLoad: ({ params }) => {
    throw redirect({ to: "/login", search: { next: `/stories/${encodeURIComponent(params.storyId)}` } });
  },
});
