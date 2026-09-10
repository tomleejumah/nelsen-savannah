import { Link } from "@tanstack/react-router";

import { ORG } from "@/data/site";

export function Footer() {
  return (
    <footer className="border-t border-hairline bg-cream">
      <div className="mx-auto flex max-w-6xl flex-col gap-3 px-5 py-8 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <p>
          © {new Date().getFullYear()} {ORG.legal}
        </p>
        <p>
          {ORG.location} ·{" "}
          <a href={`mailto:${ORG.email}`} className="transition-colors hover:text-brick">
            {ORG.email}
          </a>
        </p>
      </div>
      <div className="sr-only">
        <Link to="/programs">Programs</Link>
        <Link to="/learning">Learning</Link>
        <Link to="/contact">Contact</Link>
      </div>
    </footer>
  );
}
