import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/courses/$trackId")({
  beforeLoad: ({ params }) => {
    throw redirect({ to: "/learning/$trackId", params: { trackId: params.trackId } });
  },
});
