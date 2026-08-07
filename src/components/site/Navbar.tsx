import { useEffect, useState } from "react";
import { Link } from "@tanstack/react-router";
import { ChevronDown, Menu, X } from "lucide-react";

import logoAsset from "@/assets/nelsen-logo.png.asset.json";
import { PROGRAMS } from "@/data/site";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "./ThemeToggle";

const LINKS = [
  { to: "/", label: "Home" },
  { to: "/programs", label: "Our Programs", dropdown: true },
  { to: "/blogs", label: "Blogs" },
  { to: "/events", label: "Events" },
  { to: "/contact", label: "Contact Us" },
] as const;

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header className="fixed inset-x-0 top-0 z-50 px-3 pt-3 sm:px-6 sm:pt-5">
      <nav
        className={cn(
          "mx-auto flex items-center gap-3 transition-all duration-500 ease-out",
          scrolled
            ? "glass-panel max-w-5xl rounded-full px-4 py-2 shadow-elevated sm:px-5"
            : "max-w-7xl rounded-3xl border border-transparent px-2 py-3 sm:px-4",
        )}
      >
        <Link to="/" className="flex min-w-0 items-center" aria-label="Nelsen Savannah — home">
          <img
            src={logoAsset.url}
            alt="Nelsen Savannah logo"
            width={900}
            height={129}
            className="h-7 w-auto shrink-0 sm:h-8"
          />
        </Link>

        <div className="ml-auto hidden items-center gap-1 lg:flex">
          {LINKS.map((link) =>
            "dropdown" in link && link.dropdown ? (
              <div key={link.to} className="group relative">
                <Link
                  to={link.to}
                  className="flex items-center gap-1 rounded-full px-3.5 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent/60 hover:text-foreground data-[status=active]:text-foreground"
                >
                  {link.label}
                  <ChevronDown className="h-3.5 w-3.5 transition-transform group-hover:rotate-180" />
                </Link>
                <div className="invisible absolute left-1/2 top-full w-[min(92vw,34rem)] -translate-x-1/2 pt-3 opacity-0 transition-all duration-300 group-hover:visible group-hover:opacity-100">
                  <div className="glass-panel grid gap-1 rounded-2xl p-2 shadow-elevated sm:grid-cols-2">
                    {PROGRAMS.map((p) => (
                      <Link
                        key={p.slug}
                        to="/programs"
                        hash={p.slug}
                        className="rounded-xl px-3 py-2.5 transition-colors hover:bg-accent/70"
                      >
                        <span className="block font-display text-sm font-semibold text-foreground">
                          {p.title}
                        </span>
                        <span className="block text-xs text-muted-foreground">{p.audience}</span>
                      </Link>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <Link
                key={link.to}
                to={link.to}
                activeOptions={{ exact: link.to === "/" }}
                className="rounded-full px-3.5 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent/60 hover:text-foreground data-[status=active]:text-foreground"
              >
                {link.label}
              </Link>
            ),
          )}
        </div>

        <div className="ml-auto flex items-center gap-2 lg:ml-2">
          <ThemeToggle />
          <Link
            to="/contact"
            className="hidden rounded-full bg-ember-gradient px-4 py-2 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 sm:inline-flex"
          >
            Join a cohort
          </Link>
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            aria-label="Toggle menu"
            className="grid h-9 w-9 place-items-center rounded-full border border-border/70 lg:hidden"
          >
            {open ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
          </button>
        </div>
      </nav>

      {open && (
        <div className="glass-panel mx-auto mt-2 max-w-7xl rounded-2xl p-3 shadow-elevated lg:hidden">
          {LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              onClick={() => setOpen(false)}
              className="block rounded-xl px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent/70"
            >
              {link.label}
            </Link>
          ))}
        </div>
      )}
    </header>
  );
}