import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/groups/$groupId")({
  beforeLoad: ({ params }) => {
    throw redirect({ to: "/login", search: { next: `/groups/${encodeURIComponent(params.groupId)}` } });
  },
});
