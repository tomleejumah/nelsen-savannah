import { Link } from "@tanstack/react-router";

import { ORG } from "@/data/site";

/** Site-wide marketing footer — keep identical on every route via `__root`. */
export function Footer() {
  return (
    <footer className="border-y border-hairline bg-cream text-ink">
      <div className="mx-auto flex max-w-7xl flex-col gap-3 px-5 py-6 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <p>
          © {new Date().getFullYear()} {ORG.legal}
        </p>
        <p>
          {ORG.location} ·{" "}
          <a
            href={`mailto:${ORG.email}`}
            className="transition-colors hover:text-brick"
          >
            {ORG.email}
          </a>
        </p>
      </div>
      <nav aria-label="Legal policies" className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-3 border-t border-hairline px-5 py-5 text-sm sm:px-8">
        <Link to="/privacy-policy" className="transition-colors hover:text-brick">Privacy Policy</Link>
        <Link to="/refund-policy" className="transition-colors hover:text-brick">Refund Policy</Link>
        <Link to="/shipping-policy" className="transition-colors hover:text-brick">Shipping Policy</Link>
      </nav>
      <div className="sr-only">
        <Link to="/programs">Programs</Link>
        <Link to="/login">Sign in</Link>
        <Link to="/contact" search={{ intent: undefined }}>
          Contact
        </Link>
      </div>
    </footer>
  );
}
