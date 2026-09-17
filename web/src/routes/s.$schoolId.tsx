import { createFileRoute, redirect } from "@tanstack/react-router";

/** Deep link: /s/$schoolId → learning catalog filtered to that school. */
export const Route = createFileRoute("/s/$schoolId")({
  beforeLoad: ({ params }) => {
    const schoolId = String(params.schoolId || "").trim();
    if (!schoolId) {
      throw redirect({ to: "/learning" });
    }
    throw redirect({
      to: "/learning",
      search: { schoolId },
    });
  },
});
