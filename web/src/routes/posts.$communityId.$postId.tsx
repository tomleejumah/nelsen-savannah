import { createFileRoute, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/posts/$communityId/$postId")({
  component: SharedPostFallback,
});

function SharedPostFallback() {
  return (
    <main style={{ maxWidth: 640, margin: "64px auto", padding: 24 }}>
      <h1>Shared Nelsen post</h1>
      <p>This post opens directly in the Nelsen Android app. The Web community reader is not available yet.</p>
      <p><Link to="/login">Sign in to Nelsen</Link></p>
    </main>
  );
}
