import { useEffect, useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Globe, Mail, MapPin, Send } from "lucide-react";
import { toast } from "sonner";

import { CAREER_AREAS, ORG, PARTNER_ECOSYSTEM, PROGRAMS } from "@/data/site";

type ContactIntent = "learner" | "campus" | "partner" | "careers" | "facilitator";

const ROLES: { id: ContactIntent; label: string }[] = [
  { id: "learner", label: "Join programme" },
  { id: "campus", label: "Host on campus" },
  { id: "partner", label: "Partner" },
  { id: "careers", label: "Work with us" },
  { id: "facilitator", label: "Facilitator" },
];

export const Route = createFileRoute("/contact")({
  validateSearch: (search: Record<string, unknown>) => ({
    intent:
      typeof search.intent === "string" &&
      ROLES.some((r) => r.id === search.intent)
        ? (search.intent as ContactIntent)
        : undefined,
  }),
  head: () => ({
    meta: [
      { title: "Contact — Join, Host, Partner | Nelsen Savannah" },
      {
        name: "description",
        content: `Reach ${ORG.name} — ${ORG.email}. Join a programme, invite us to your campus, partner, or explore careers.`,
      },
      { property: "og:title", content: "Contact | Nelsen Savannah" },
    ],
  }),
  component: ContactPage,
});

function ContactPage() {
  const { intent } = Route.useSearch();
  const [role, setRole] = useState<ContactIntent>("learner");

  useEffect(() => {
    if (intent) setRole(intent);
  }, [intent]);

  const isCampus = role === "campus";
  const isCareers = role === "careers";
  const contactEmail = isCareers ? ORG.recruitEmail : ORG.email;

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 sm:px-8 lg:grid-cols-[1fr_1.1fr]">
        <div>
          <p className="eyebrow text-ember">Contact us</p>
          <h1 className="mt-4 text-4xl font-bold sm:text-5xl">
            {isCampus
              ? "Invite Nelsen Savannah to your campus or community"
              : isCareers
                ? "Apply to work with Nelsen Savannah"
                : "Join, host, partner, or work with us"}
          </h1>
          <p className="mt-5 max-w-md text-base leading-relaxed text-muted-foreground">
            {isCampus
              ? "Your campus has talent. Your community has ideas. Tell us about your institution, expected participants, and the programme you would like to host."
              : isCareers
                ? "Send your profile to our recruit desk. We review applications for programme, technology, research, and operations roles."
                : "Learners, campuses, partners, and talent — we reply within two working days by email."}
          </p>

          {role === "partner" && (
            <ul className="mt-8 space-y-3 text-sm text-muted-foreground">
              {PARTNER_ECOSYSTEM.map((p) => (
                <li key={p.title}>
                  <span className="font-semibold text-foreground">{p.title}:</span> {p.detail}
                </li>
              ))}
            </ul>
          )}

          {isCareers && (
            <ul className="mt-8 space-y-4 text-sm text-muted-foreground">
              {CAREER_AREAS.map((area) => (
                <li key={area.title}>
                  <span className="block font-semibold text-foreground">{area.title}</span>
                  {area.roles}
                </li>
              ))}
              <li>Don't see your role? Send us your profile and join our talent network.</li>
            </ul>
          )}

          <ul className="mt-10 space-y-4 text-sm">
            <li>
              <a
                href={`mailto:${contactEmail}`}
                className="flex items-center gap-3 text-muted-foreground transition-colors hover:text-maroon"
              >
                <span className="icon-chip">
                  <Mail className="h-4 w-4" />
                </span>
                {contactEmail}
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
            const lines = [`Role: ${role}`];
            for (const [key, value] of fd.entries()) {
              if (String(value).trim()) lines.push(`${key}: ${value}`);
            }
            const subject = encodeURIComponent(
              isCareers
                ? `Nelsen Savannah — role application`
                : `Nelsen Savannah — ${role}`,
            );
            const body = encodeURIComponent(lines.join("\n"));
            window.location.href = `mailto:${contactEmail}?subject=${subject}&body=${body}`;
            toast.success("Opening your email app", {
              description: isCareers
                ? `Applications go to ${ORG.recruitEmail}`
                : "If nothing opens, email us directly.",
            });
          }}
          className="rounded-3xl border border-border/70 bg-card p-7 shadow-elevated sm:p-9"
        >
          <div className="grid gap-2">
            <span className="eyebrow text-muted-foreground">I am enquiring as</span>
            <div className="grid grid-cols-2 gap-2 rounded-2xl bg-secondary p-1.5 sm:grid-cols-3">
              {ROLES.map((r) => (
                <button
                  key={r.id}
                  type="button"
                  onClick={() => setRole(r.id)}
                  className={`rounded-xl px-2 py-2 font-display text-xs font-semibold transition-colors sm:text-sm ${
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
            {isCampus ? (
              <>
                <Field
                  label="Organization / institution name"
                  name="organization"
                  placeholder="University, college, hub…"
                  required
                />
                <Field label="Contact person" name="name" placeholder="Your full name" required />
                <Field label="Email" name="email" type="email" placeholder="you@email.com" required />
                <Field label="Phone" name="phone" placeholder="+254 7…" />
                <Field label="Country" name="country" placeholder="Kenya" required />
                <Field label="City / town" name="city" placeholder="Nairobi" required />
                <label className="grid gap-1.5 text-sm sm:col-span-2">
                  <span className="font-medium">Organization type</span>
                  <input
                    name="organizationType"
                    required
                    placeholder="University, college, innovation hub, NGO…"
                    className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                  />
                </label>
                <label className="grid gap-1.5 text-sm sm:col-span-2">
                  <span className="font-medium">Programme you want to host</span>
                  <select
                    name="program"
                    className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                  >
                    {PROGRAMS.map((p) => (
                      <option key={p.slug} value={p.title}>
                        {p.title}
                      </option>
                    ))}
                    <option value="Custom / multi-programme">Custom / multi-programme</option>
                  </select>
                </label>
                <Field
                  label="Expected participants"
                  name="expectedParticipants"
                  placeholder="e.g. 40"
                />
                <Field label="Preferred date" name="preferredDate" placeholder="Month / term" />
              </>
            ) : (
              <>
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
              </>
            )}
          </div>

          <label className="mt-4 grid gap-1.5 text-sm">
            <span className="font-medium">
              {isCampus
                ? "Tell us about your community / campus"
                : "What would you like to know?"}
            </span>
            <textarea
              name="message"
              rows={4}
              required
              placeholder={
                isCampus
                  ? "What would you like us to deliver? Facilities, audience, and timing."
                  : "Your background, preferred intake, and what you hope to build or learn."
              }
              className="rounded-xl border border-input bg-background px-3 py-2.5 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </label>

          {isCampus && (
            <label className="mt-4 grid gap-1.5 text-sm">
              <span className="font-medium">What would you like us to deliver?</span>
              <textarea
                name="delivery"
                rows={3}
                placeholder="Workshops, mentorship, innovation challenge, blended cohort…"
                className="rounded-xl border border-input bg-background px-3 py-2.5 text-sm outline-none focus:ring-2 focus:ring-ring"
              />
            </label>
          )}

          <button
            type="submit"
            className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
          >
            {isCampus ? "Submit invitation" : isCareers ? "Submit application" : "Send message"}{" "}
            <Send className="h-4 w-4" />
          </button>
          <p className="mt-3 text-center text-xs text-muted-foreground">
            Or email{" "}
            <a
              href={`mailto:${contactEmail}`}
              className="font-medium text-foreground underline-offset-2 hover:underline"
            >
              {contactEmail}
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
