import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/posts/$communityId/$postId")({
  beforeLoad: ({ params }) => {
    throw redirect({
      to: "/login",
      search: { next: `/posts/${encodeURIComponent(params.communityId)}/${encodeURIComponent(params.postId)}` },
    });
  },
});
