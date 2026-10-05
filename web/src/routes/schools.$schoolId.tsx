import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/schools/$schoolId")({
  beforeLoad: ({ params }) => {
    throw redirect({ to: "/learning", search: { schoolId: params.schoolId } });
  },
});
