import { createFileRoute, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/stories/$storyId")({
  component: SharedStoryFallback,
});

function SharedStoryFallback() {
  return (
    <main style={{ maxWidth: 640, margin: "64px auto", padding: 24 }}>
      <h1>Shared Nelsen story</h1>
      <p>This story opens directly in the Nelsen Android app. Expired or deleted stories are rejected by the app.</p>
      <p><Link to="/login">Sign in to Nelsen</Link></p>
    </main>
  );
}
