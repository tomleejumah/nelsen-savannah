import { useState } from "react";
import { Send } from "lucide-react";
import { toast } from "sonner";

import { ORG } from "@/data/site";
import { LMS_API_BASE } from "@/lib/lmsApi";

type FieldBase = {
  name: string;
  label: string;
  required?: boolean;
  /** Only render when another field’s value is one of these */
  showWhen?: { field: string; values: string[] };
};

type Field =
  | (FieldBase & {
      type?: "text" | "email" | "tel" | "number" | "date";
      placeholder?: string;
    })
  | (FieldBase & {
      type: "select";
      options: { value: string; label: string }[];
    })
  | (FieldBase & {
      type: "textarea";
      placeholder?: string;
      rows?: number;
    });

type Props = {
  desk: "invest" | "tourism";
  title: string;
  blurb: string;
  subjectPrefix: string;
  fields: Field[];
  submitLabel: string;
};

function isVisible(field: Field, values: Record<string, string>) {
  if (!field.showWhen) return true;
  const current = values[field.showWhen.field] ?? "";
  return field.showWhen.values.includes(current);
}

export function DeskInquiryForm({
  desk,
  title,
  blurb,
  subjectPrefix,
  fields,
  submitLabel,
}: Props) {
  const initial: Record<string, string> = {};
  for (const field of fields) {
    if (field.type === "select" && field.options[0]) {
      initial[field.name] = field.options[0].value;
    }
  }
  const [values, setValues] = useState(initial);
  const [sending, setSending] = useState(false);

  const visibleFields = fields.filter((f) => isVisible(f, values));

  return (
    <section id="inquire" className="mx-auto max-w-7xl scroll-mt-28 px-5 sm:px-8">
      <div className="grid gap-10 lg:grid-cols-[1fr_1.1fr]">
        <div>
          <p className="eyebrow text-ember">{desk === "invest" ? "Invest desk" : "Tourism desk"}</p>
          <h2 className="mt-3 font-display text-3xl font-bold">{title}</h2>
          <p className="mt-4 max-w-md text-sm leading-relaxed text-muted-foreground">{blurb}</p>
          <p className="mt-6 text-sm text-muted-foreground">
            Or email{" "}
            <a className="font-medium text-maroon hover:underline" href={`mailto:${ORG.email}`}>
              {ORG.email}
            </a>
          </p>
        </div>

        <form
          onSubmit={(e) => {
            e.preventDefault();
            void (async () => {
              const fd = new FormData(e.target as HTMLFormElement);
              const fieldMap: Record<string, string> = {};
              const lines = [`Desk: ${desk}`];
              for (const field of visibleFields) {
                const val = String(fd.get(field.name) || "").trim();
                fieldMap[field.label] = val || "—";
                lines.push(`${field.label}: ${val || "—"}`);
              }
              const name = String(fd.get("name") || "Inquiry");
              const email = String(fd.get("email") || "").trim();
              const subject = `${subjectPrefix} — ${name}`;

              setSending(true);
              try {
                const res = await fetch(`${LMS_API_BASE}/inquiries`, {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({
                    desk,
                    subject,
                    replyTo: email || undefined,
                    fields: fieldMap,
                  }),
                });
                const json = (await res.json().catch(() => null)) as {
                  ok?: boolean;
                  error?: string;
                  code?: string;
                } | null;

                if (res.ok && json?.ok) {
                  toast.success("Inquiry sent", {
                    description: `We’ll reply to ${email || "your email"}.`,
                  });
                  (e.target as HTMLFormElement).reset();
                  setValues(initial);
                  return;
                }

                // Resend not configured yet — open mailto so nothing is lost
                window.location.href = `mailto:${ORG.email}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(lines.join("\n"))}`;
                toast.message("Opening your email app", {
                  description:
                    json?.code === "RESEND_UNCONFIGURED"
                      ? "Server mail isn’t live yet — finish in your inbox."
                      : json?.error || "Sent via your email app instead.",
                });
              } catch {
                window.location.href = `mailto:${ORG.email}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(lines.join("\n"))}`;
                toast.message("Opening your email app", {
                  description: "Couldn’t reach the server — finish in your inbox.",
                });
              } finally {
                setSending(false);
              }
            })();
          }}
          className="rounded-3xl border border-border/70 bg-card p-7 shadow-elevated sm:p-9"
        >
          <div className="grid gap-4 sm:grid-cols-2">
            {visibleFields.map((field) => {
              if (field.type === "select") {
                return (
                  <label key={field.name} className="grid gap-1.5 text-sm sm:col-span-2">
                    <span className="font-medium">{field.label}</span>
                    <select
                      name={field.name}
                      required={field.required}
                      value={values[field.name] ?? field.options[0]?.value ?? ""}
                      onChange={(e) =>
                        setValues((prev) => ({ ...prev, [field.name]: e.target.value }))
                      }
                      className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                    >
                      {field.options.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </label>
                );
              }
              if (field.type === "textarea") {
                return (
                  <label key={field.name} className="grid gap-1.5 text-sm sm:col-span-2">
                    <span className="font-medium">
                      {field.label}
                      {!field.required ? (
                        <span className="ml-1 font-normal text-muted-foreground">(optional)</span>
                      ) : null}
                    </span>
                    <textarea
                      name={field.name}
                      required={field.required}
                      rows={field.rows ?? 3}
                      placeholder={field.placeholder}
                      className="rounded-xl border border-input bg-background px-3 py-2.5 text-sm outline-none focus:ring-2 focus:ring-ring"
                    />
                  </label>
                );
              }
              const isDate = field.type === "date";
              return (
                <label
                  key={field.name}
                  className={`grid gap-1.5 text-sm ${isDate ? "" : ""}`}
                >
                  <span className="font-medium">{field.label}</span>
                  <input
                    name={field.name}
                    type={field.type ?? "text"}
                    required={field.required}
                    placeholder={isDate ? undefined : field.placeholder}
                    min={isDate ? new Date().toISOString().slice(0, 10) : undefined}
                    className="h-11 rounded-xl border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                  />
                </label>
              );
            })}
          </div>
          <button
            type="submit"
            disabled={sending}
            className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-full bg-ember-gradient px-6 py-3.5 font-display text-sm font-semibold text-maroon-foreground shadow-ember-glow transition-transform hover:-translate-y-0.5 disabled:opacity-60"
          >
            {sending ? "Sending…" : submitLabel} <Send className="h-4 w-4" />
          </button>
        </form>
      </div>
    </section>
  );
}
