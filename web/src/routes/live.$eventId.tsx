import { useEffect } from "react";
import { createFileRoute } from "@tanstack/react-router";

/**
 * Browser fallback for the same canonical HTTPS link handled by Android
 * App Links. Users without the Android app land on the matching event card.
 */
export const Route = createFileRoute("/live/$eventId")({
  component: LiveLinkPage,
  head: () => ({ meta: [{ title: "Live session — Nelsen Savannah" }] }),
});

function LiveLinkPage() {
  const { eventId } = Route.useParams();
  const destination = `/events#${encodeURIComponent(eventId)}`;
  useEffect(() => {
    window.location.replace(destination);
  }, [destination]);

  return (
    <main className="mx-auto min-h-screen max-w-3xl px-6 py-36">
      <h1 className="text-3xl font-bold">Opening live session</h1>
      <p className="mt-4 text-muted-foreground">
        Taking you to the live event or replay on Nelsen Savannah.
      </p>
      <a className="mt-6 inline-block font-semibold underline" href={destination}>
        View the live event
      </a>
    </main>
  );
}
