import { createFileRoute, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/groups/$groupId")({
  component: SharedGroupFallback,
});

function SharedGroupFallback() {
  return (
    <main style={{ maxWidth: 640, margin: "64px auto", padding: 24 }}>
      <h1>Shared Nelsen group</h1>
      <p>This group opens directly in the Nelsen Android app, where membership and join permissions are checked.</p>
      <p><Link to="/login">Sign in to Nelsen</Link></p>
    </main>
  );
}
