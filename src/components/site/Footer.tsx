import { Link } from "@tanstack/react-router";
import { Mail, MapPin, Phone } from "lucide-react";

import logoAsset from "@/assets/nelsen-logo.png.asset.json";
import { ORG, PROGRAMS } from "@/data/site";

export function Footer() {
  return (
    <footer className="border-t border-border/60 bg-secondary/40">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 py-14 sm:px-8 lg:grid-cols-[1.4fr_1fr_1fr_1fr]">
        <div>
          <img
            src={logoAsset.url}
            alt="Nelsen Savannah logo"
            width={900}
            height={129}
            loading="lazy"
            className="h-8 w-auto"
          />
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
                  className="text-muted-foreground transition-colors hover:text-foreground"
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
              { to: "/events", label: "Events" },
              { to: "/blogs", label: "Blogs" },
              { to: "/contact", label: "Contact us" },
            ].map((l) => (
              <li key={l.to}>
                <Link
                  to={l.to}
                  className="text-muted-foreground transition-colors hover:text-foreground"
                >
                  {l.label}
                </Link>
              </li>
            ))}
            <li className="text-muted-foreground/60">Gallery — coming soon</li>
          </ul>
        </nav>

        <div className="text-sm">
          <h3 className="eyebrow text-muted-foreground">Reach us</h3>
          <ul className="mt-4 space-y-3 text-muted-foreground">
            <li className="flex items-center gap-2">
              <Mail className="h-4 w-4 shrink-0 text-ember" /> {ORG.email}
            </li>
            <li className="flex items-center gap-2">
              <Phone className="h-4 w-4 shrink-0 text-ember" /> {ORG.phone}
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