import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { Mail, MapPin, Phone, Send } from "lucide-react";
import { toast } from "sonner";

import { ORG, PROGRAMS } from "@/data/site";

export const Route = createFileRoute("/contact")({
  head: () => ({
    meta: [
      { title: "Contact Us — Become a Mentor or Join a Cohort | Nelsen Savanna" },
      {
        name: "description",
        content:
          "Talk to Nelsen Savanna about joining a mentorship cohort, mentoring young people, or partnering with us in Nairobi, Kenya.",
      },
      { property: "og:title", content: "Contact Nelsen Savanna" },
      {
        property: "og:description",
        content: "Join as a mentee, apply to mentor, or partner with our youth guidance programs.",
      },
    ],
  }),
  component: ContactPage,
});

function ContactPage() {
  const [role, setRole] = useState("mentee");

  return (
    <div className="pb-24 pt-32 sm:pt-40">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 sm:px-8 lg:grid-cols-[1fr_1.1fr]">
        <div>
          <p className="eyebrow text-ember">Contact us</p>
          <h1 className="mt-4 text-4xl font-bold sm:text-5xl">
            Tell us where you are, and we will match the guidance
          </h1>
          <p className="mt-5 max-w-md text-base leading-relaxed text-muted-foreground">
            Whether you are a student picking a path, a junior professional stuck at a plateau, or a
            senior ready to mentor — start here. We reply within two working days.
          </p>

          <ul className="mt-10 space-y-4 text-sm">
            {[
              { icon: Mail, label: ORG.email },
              { icon: Phone, label: ORG.phone },
              { icon: MapPin, label: ORG.location },
            ].map(({ icon: Icon, label }) => (
              <li key={label} className="flex items-center gap-3">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-maroon/10 text-maroon">
                  <Icon className="h-4 w-4" />
                </span>
                <span className="text-muted-foreground">{label}</span>
              </li>
            ))}
          </ul>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            toast.success("Message received", {
              description: "Our team will get back to you within two working days.",
            });
            (e.target as HTMLFormElement).reset();
          }}
          className="rounded-3xl border border-border/70 bg-card p-7 shadow-elevated sm:p-9"
        >
          <div className="grid gap-2">
            <span className="eyebrow text-muted-foreground">I am joining as</span>
            <div className="grid grid-cols-3 gap-2 rounded-2xl bg-secondary p-1.5">
              {["mentee", "mentor", "partner"].map((r) => (
                <button
                  key={r}
                  type="button"
                  onClick={() => setRole(r)}
                  className={`rounded-xl py-2 font-display text-sm font-semibold capitalize transition-colors ${
                    role === r
                      ? "bg-ember-gradient text-maroon-foreground"
                      : "text-muted-foreground hover:text-foreground"
                  }`}
                >
                  {r}
                </button>
              ))}
            </div>
          </div>

          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <Field label="Full name" name="name" placeholder="Amina Wanjiru" required />
            <Field label="Email" name="email" type="email" placeholder="you@email.com" required />
            <Field label="Phone" name="phone" placeholder="+254 7.." />
            <label className="grid gap-1.5 text-sm">
              <span className="font-medium">Program of interest</span>
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
            <span className="font-medium">What do you need help with?</span>
            <textarea
              name="message"
              rows={4}
              required
              placeholder="A short paragraph about your situation and goals."
              className="rounded-xl border border-input bg-background px-3 py-2.5 text-sm outline-none focus:ring-2 focus:ring-ring"
            />
          </label>

          <button
            type="submit"
            className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-full bg-ember-gradient px-6 py-3 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5"
          >
            Send message <Send className="h-4 w-4" />
          </button>
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
        placeholder={placeholder}
        required={required}
        className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
      />
    </label>
  );
}