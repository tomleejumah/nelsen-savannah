import { useEffect, useState } from "react";
import { Link, useRouterState } from "@tanstack/react-router";
import { onAuthStateChanged, type User } from "firebase/auth";
import { ChevronDown, Menu, X } from "lucide-react";

import { PROGRAMS } from "@/data/site";
import { getFirebaseAuth } from "@/lib/firebase";
import { bumpAuthGeneration, getAuthGeneration } from "@/lib/lmsAuth";
import { primaryWorkspacePath } from "@/lib/lmsCapabilities";
import { fetchLmsMe, type MeDto } from "@/lib/lmsApi";
import { cn } from "@/lib/utils";
import { BrandMark } from "./BrandMark";
import { ThemeToggle } from "./ThemeToggle";

const LINKS = [
  { to: "/", label: "Home" },
  { to: "/programs", label: "Our Programs", dropdown: true },
  { to: "/learning", label: "Learning" },
  { to: "/invest", label: "Invest" },
  { to: "/tourism", label: "Tourism" },
  { to: "/blogs", label: "Blogs" },
  { to: "/events", label: "Events" },
  { to: "/contact", label: "Contact Us" },
] as const;

const navLinkClass =
  "whitespace-nowrap rounded-full px-3.5 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent/60 hover:text-maroon focus-visible:text-maroon";

function pathIsActive(pathname: string, to: string) {
  if (to === "/") return pathname === "/";
  return pathname === to || pathname.startsWith(`${to}/`);
}

/** Shared SPA shell is prerendered at `/` — defer active styles until mount so Home does not stick. */
function useNavPathname() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const [ready, setReady] = useState(false);
  useEffect(() => {
    setReady(true);
  }, []);
  return ready ? pathname : null;
}

function navActiveOptions(to: string) {
  return { exact: to === "/", includeSearch: false } as const;
}

export function Navbar() {
  const pathname = useNavPathname();
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const [mobilePrograms, setMobilePrograms] = useState(false);
  const [user, setUser] = useState<User | null>(null);
  const [me, setMe] = useState<MeDto | null>(null);

  const linkActive = (to: string) => pathname != null && pathIsActive(pathname, to);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  useEffect(() => {
    return onAuthStateChanged(getFirebaseAuth(), (next) => {
      const gen = bumpAuthGeneration();
      setUser(next);
      if (!next) {
        setMe(null);
        return;
      }
      void (async () => {
        try {
          const token = await next.getIdToken();
          if (gen !== getAuthGeneration()) return;
          const envelope = await fetchLmsMe(token);
          if (gen !== getAuthGeneration()) return;
          setMe(envelope.ok && envelope.data ? envelope.data : null);
        } catch {
          if (gen !== getAuthGeneration()) return;
          setMe(null);
        }
      })();
    });
  }, []);

  return (
    <header className="fixed inset-x-0 top-0 z-50 px-3 pt-3 sm:px-6 sm:pt-5">
      <nav
        className={cn(
          "mx-auto flex items-center gap-3 transition-all duration-500 ease-out",
          scrolled
            ? "glass-panel max-w-6xl rounded-full px-5 py-2 shadow-elevated sm:px-6"
            : "max-w-7xl rounded-3xl border border-transparent px-2 py-3 sm:px-4",
        )}
      >
        <Link
          to="/"
          activeOptions={navActiveOptions("/")}
          activeProps={{ className: "" }}
          className="flex min-w-0 items-center"
          aria-label="Nelsen Savannah — home"
        >
          <BrandMark />
        </Link>

        <div className="ml-auto hidden items-center gap-1 lg:flex">
          {LINKS.map((link) =>
            "dropdown" in link && link.dropdown ? (
              <div key={link.to} className="group relative">
                <Link
                  to={link.to}
                  activeOptions={navActiveOptions(link.to)}
                  activeProps={{ className: "" }}
                  className={cn(
                    navLinkClass,
                    "flex items-center gap-1",
                    linkActive(link.to) && "text-maroon",
                  )}
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
                activeOptions={navActiveOptions(link.to)}
                activeProps={{ className: "" }}
                className={cn(navLinkClass, linkActive(link.to) && "text-maroon")}
              >
                {link.label}
              </Link>
            ),
          )}
        </div>

        <div className="ml-auto flex items-center gap-2 lg:ml-2">
          <ThemeToggle />
          {user ? (
            <Link
              to={primaryWorkspacePath(me)}
              className="hidden whitespace-nowrap rounded-full bg-ember-gradient px-4 py-2 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 sm:inline-flex"
            >
              Workspace
            </Link>
          ) : (
            <Link
              to="/contact"
              className="hidden whitespace-nowrap rounded-full bg-ember-gradient px-4 py-2 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 sm:inline-flex"
            >
              Join a cohort
            </Link>
          )}
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
        <div className="glass-panel mx-auto mt-2 max-h-[calc(100vh-6rem)] max-w-7xl overflow-y-auto rounded-2xl p-3 shadow-elevated lg:hidden">
          {LINKS.map((link) =>
            "dropdown" in link && link.dropdown ? (
              <div key={link.to}>
                <div className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-2">
                  <Link
                    to={link.to}
                    onClick={() => setOpen(false)}
                    activeOptions={navActiveOptions(link.to)}
                    activeProps={{ className: "" }}
                    className={cn(
                      "min-w-0 truncate rounded-xl px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent/70 hover:text-maroon focus-visible:text-maroon",
                      linkActive(link.to) && "text-maroon",
                    )}
                  >
                    {link.label}
                  </Link>
                  <button
                    type="button"
                    aria-label="Toggle programs list"
                    aria-expanded={mobilePrograms}
                    onClick={() => setMobilePrograms((v) => !v)}
                    className="grid h-9 w-9 shrink-0 place-items-center rounded-full border border-border/70"
                  >
                    <ChevronDown
                      className={cn(
                        "h-4 w-4 transition-transform",
                        mobilePrograms && "rotate-180",
                      )}
                    />
                  </button>
                </div>
                {mobilePrograms && (
                  <div className="mb-1 ml-3 space-y-0.5 border-l border-border/60 pl-2">
                    {PROGRAMS.map((p) => (
                      <Link
                        key={p.slug}
                        to="/programs"
                        hash={p.slug}
                        onClick={() => setOpen(false)}
                        className="block min-w-0 rounded-xl px-3 py-2 transition-colors hover:bg-accent/70"
                      >
                        <span className="block truncate font-display text-sm font-semibold text-foreground">
                          {p.title}
                        </span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {p.audience}
                        </span>
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <Link
                key={link.to}
                to={link.to}
                onClick={() => setOpen(false)}
                activeOptions={navActiveOptions(link.to)}
                activeProps={{ className: "" }}
                className={cn(
                  "block rounded-xl px-3 py-2.5 text-sm font-medium text-foreground transition-colors hover:bg-accent/70 hover:text-maroon focus-visible:text-maroon",
                  linkActive(link.to) && "text-maroon",
                )}
              >
                {link.label}
              </Link>
            ),
          )}
          {user ? (
            <Link
              to={primaryWorkspacePath(me)}
              onClick={() => setOpen(false)}
              className="mt-2 block rounded-xl bg-ember-gradient px-3 py-2.5 text-center font-display text-sm font-semibold text-maroon-foreground"
            >
              Workspace
            </Link>
          ) : (
            <Link
              to="/contact"
              onClick={() => setOpen(false)}
              className="mt-2 block rounded-xl bg-ember-gradient px-3 py-2.5 text-center font-display text-sm font-semibold text-maroon-foreground"
            >
              Join a cohort
            </Link>
          )}
        </div>
      )}
    </header>
  );
}
