import { Link } from "@tanstack/react-router";
import { Mail, MapPin, Phone } from "lucide-react";

import { ORG, PROGRAMS } from "@/data/site";
import { BrandMark } from "./BrandMark";

export function Footer() {
  return (
    <footer className="border-t border-border/60 bg-secondary/40">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 py-14 sm:px-8 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
        <div>
          <BrandMark size="sm" />
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-muted-foreground">
            {ORG.legal}. We connect young people with mentors who have already walked the road —
            careers, communication, work and life.
          </p>
        </div>

        <nav className="text-sm">
          <h3 className="eyebrow text-muted-foreground">Programs</h3>
          <ul className="mt-4 space-y-2">
            {PROGRAMS.slice(0, 5).map((p) => (
              <li key={p.slug}>
                <Link
                  to="/programs"
                  hash={p.slug}
                  className="text-muted-foreground transition-colors hover:text-maroon"
                >
                  {p.title}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav className="text-sm">
          <h3 className="eyebrow text-muted-foreground">Explore</h3>
          <ul className="mt-4 space-y-2">
            {[
              { to: "/learning", label: "Learning" },
              { to: "/events", label: "Events" },
              { to: "/media", label: "Media" },
              { to: "/contact", label: "Contact us" },
            ].map((l) => (
              <li key={l.to}>
                <Link
                  to={l.to}
                  className="text-muted-foreground transition-colors hover:text-maroon"
                >
                  {l.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div className="text-sm">
          <h3 className="eyebrow text-muted-foreground">Reach us</h3>
          <ul className="mt-4 space-y-3 text-muted-foreground">
            <li>
              <a
                href={`mailto:${ORG.email}`}
                className="flex items-center gap-2 transition-colors hover:text-maroon"
              >
                <Mail className="h-4 w-4 shrink-0 text-ember" /> {ORG.email}
              </a>
            </li>
            <li>
              <a
                href={`https://wa.me/${ORG.whatsapp}`}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-2 transition-colors hover:text-maroon"
              >
                <Phone className="h-4 w-4 shrink-0 text-ember" /> {ORG.phone}
              </a>
            </li>
            <li className="flex items-center gap-2">
              <MapPin className="h-4 w-4 shrink-0 text-ember" /> {ORG.location}
            </li>
          </ul>
        </div>
      </div>
      <div className="border-t border-border/60 px-5 py-5 text-center text-xs text-muted-foreground sm:px-8">
        © {new Date().getFullYear()} {ORG.legal}. All rights reserved.
      </div>
    </footer>
  );
}