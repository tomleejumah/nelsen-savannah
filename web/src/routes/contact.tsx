import { useState } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";
import { Globe, Mail, MapPin, Send } from "lucide-react";
import { toast } from "sonner";

import { ORG, PROGRAMS } from "@/data/site";

export const Route = createFileRoute("/contact")({
  head: () => ({
    meta: [
      { title: "Contact — Enrol or Partner | Nelsen Savannah Innovation Hub" },
      {
        name: "description",
        content: `Reach ${ORG.hubName} in Nairobi — ${ORG.email} or ${ORG.website.replace(/^https?:\/\//, "")}.`,
      },
      { property: "og:title", content: "Contact | Nelsen Savannah Innovation Hub" },
    ],
  }),
  component: ContactPage,
});

function ContactPage() {
  const [role, setRole] = useState("learner");

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 sm:px-8 lg:grid-cols-[1fr_1.1fr]">
        <div>
          <p className="eyebrow text-ember">Contact us</p>
          <h1 className="mt-4 text-4xl font-bold sm:text-5xl">
            Tell us which programme fits, and we will guide your next step
          </h1>
          <p className="mt-5 max-w-md text-base leading-relaxed text-muted-foreground">
            Learners, facilitators, schools, and partners — enquire about intakes, cohorts, and
            collaboration. We reply within two working days by email.
          </p>
          <p className="mt-4 max-w-md text-sm text-muted-foreground">
            Investing?{" "}
            <Link to="/invest#inquire" className="font-semibold text-maroon hover:underline">
              Investor inquiry
            </Link>
            . Planning a trip?{" "}
            <Link to="/tourism#inquire" className="font-semibold text-maroon hover:underline">
              Trip inquiry
            </Link>
            .
          </p>

          <ul className="mt-10 space-y-4 text-sm">
            <li>
              <a
                href={`mailto:${ORG.email}`}
                className="flex items-center gap-3 text-muted-foreground transition-colors hover:text-maroon"
              >
                <span className="icon-chip">
                  <Mail className="h-4 w-4" />
                </span>
                {ORG.email}
              </a>
            </li>
            <li>
              <a
                href={ORG.website}
                target="_blank"
                rel="noreferrer"
                className="flex items-center gap-3 text-muted-foreground transition-colors hover:text-maroon"
              >
                <span className="icon-chip">
                  <Globe className="h-4 w-4" />
                </span>
                {ORG.website.replace(/^https?:\/\//, "")}
              </a>
            </li>
            <li className="flex items-center gap-3 text-muted-foreground">
              <span className="icon-chip">
                <MapPin className="h-4 w-4" />
              </span>
              {ORG.location}
            </li>
          </ul>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            const fd = new FormData(e.target as HTMLFormElement);
            const name = String(fd.get("name") || "");
            const email = String(fd.get("email") || "");
            const phone = String(fd.get("phone") || "");
            const program = String(fd.get("program") || "");
            const message = String(fd.get("message") || "");
            const subject = encodeURIComponent(`Nelsen Savannah — ${role}: ${name}`);
            const body = encodeURIComponent(
              [
                `Role: ${role}`,
                `Name: ${name}`,
                `Email: ${email}`,
                `Phone: ${phone}`,
                `Program: ${program}`,
                "",
                message,
              ].join("\n"),
            );
            window.location.href = `mailto:${ORG.email}?subject=${subject}&body=${body}`;
            toast.success("Opening your email app", {
              description: "If nothing opens, email us directly.",
            });
          }}
          className="rounded-3xl border border-border/70 bg-card p-7 shadow-elevated sm:p-9"
        >
          <div className="grid gap-2">
            <span className="eyebrow text-muted-foreground">I am enquiring as</span>
            <div className="grid grid-cols-3 gap-2 rounded-2xl bg-secondary p-1.5">
              {[
                { id: "learner", label: "Learner" },
                { id: "facilitator", label: "Facilitator" },
                { id: "partner", label: "Partner" },
              ].map((r) => (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => setRole(r.id)}
                  className={`rounded-xl py-2 font-display text-sm font-semibold transition-colors ${
                    role === r.id
                      ? "bg-ember-gradient text-maroon-foreground"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {r.label}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <Field label="Full name" name="name" placeholder="Your full name" required />
            <Field label="Email" name="email" type="email" placeholder="you@email.com" required />
            <Field label="Phone (optional)" name="phone" placeholder="+254 7…" />
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Programme of interest</span>
              <select
                name="program"
                className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                {PROGRAMS.map((p) => (
                  <option key={p.slug} value={p.title}>
                    {p.title}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <label className="mt-4 grid gap-1.5 text-sm">
            <span className="font-medium">What would you like to know?</span>
            <textarea
              name="message"
              rows={4}
              required
              placeholder="Your background, preferred intake, and what you hope to build or learn."
              className="rounded-xl border border-input bg-background px-3 py-2.5 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </label>

          <button
            type="submit"
            className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
          >
            Send message <Send className="h-4 w-4" />
          </button>
          <p className="mt-3 text-center text-xs text-muted-foreground">
            Or email{" "}
            <a
              href={`mailto:${ORG.email}`}
              className="font-medium text-foreground underline-offset-2 hover:underline"
            >
              {ORG.email}
            </a>
          </p>
        </form>
      </div>
    </div>
  );
}

function Field({
  label,
  name,
  type = "text",
  placeholder,
  required,
}: {
  label: string;
  name: string;
  type?: string;
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <label className="grid gap-1.5 text-sm">
      <span className="font-medium">{label}</span>
      <input
        name={name}
        type={type}
        required={required}
        placeholder={placeholder}
        className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
      />
    </label>
  );
}
