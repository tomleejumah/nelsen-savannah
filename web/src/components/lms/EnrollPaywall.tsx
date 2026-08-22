import { useState } from "react";
import type { User } from "firebase/auth";

import { checkoutTrack, enrollInTrack } from "@/lib/lmsApi";

export function formatTrackPrice(price?: {
  isPaid?: boolean;
  amountMinor?: number;
  currency?: string;
} | null) {
  if (!price?.isPaid || !price.amountMinor) return "Free";
  const n = price.amountMinor / 100;
  return `${price.currency || "USD"} ${n.toFixed(2)}`;
}

type PaywallState = {
  trackId: string;
  title?: string;
  price: { isPaid: boolean; amountMinor: number; currency: string };
};

export function useEnrollPaywall(
  user: User | null,
  onEnrolled: () => Promise<void> | void,
) {
  const [paywall, setPaywall] = useState<PaywallState | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function enroll(trackId: string, title?: string) {
    if (!user) return;
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const result = await enrollInTrack(token, trackId);
      if (result.ok) {
        setPaywall(null);
        await onEnrolled();
        return;
      }
      const price = result.data?.price;
      if (result.data?.code === "TRACK_PAYMENT_REQUIRED" && price) {
        setPaywall({ trackId, title, price });
        return;
      }
      setError(result.error || "Enroll failed");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Enroll failed");
    } finally {
      setBusy(false);
    }
  }

  async function payAndEnroll() {
    if (!user || !paywall) return;
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const paid = await checkoutTrack(token, paywall.trackId);
      if (!paid.ok) {
        setError(paid.error || "Checkout failed");
        return;
      }
      const enrolled = await enrollInTrack(token, paywall.trackId);
      if (!enrolled.ok) {
        setError(enrolled.error || "Enroll failed after payment");
        return;
      }
      setPaywall(null);
      await onEnrolled();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Checkout failed");
    } finally {
      setBusy(false);
    }
  }

  return { enroll, payAndEnroll, paywall, setPaywall, busy, error, setError };
}

export function EnrollPaywallModal({
  paywall,
  busy,
  error,
  onClose,
  onPay,
}: {
  paywall: PaywallState;
  busy: boolean;
  error: string | null;
  onClose: () => void;
  onPay: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center">
      <div className="w-full max-w-md rounded-3xl border border-border bg-card p-6 shadow-elevated">
        <p className="eyebrow text-ember">Paywall</p>
        <h2 className="mt-2 font-display text-xl font-semibold">
          Unlock {paywall.title || "this track"}
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Demo checkout — no live card or M-Pesa yet. Paying records access so you
          can enroll and see the purchase on your profile.
        </p>
        <p className="mt-4 font-display text-2xl font-semibold">
          {formatTrackPrice(paywall.price)}
        </p>
        {error ? (
          <p className="mt-3 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </p>
        ) : null}
        <div className="mt-6 flex flex-wrap gap-2">
          <button
            type="button"
            disabled={busy}
            onClick={onPay}
            className="rounded-full bg-ember-gradient px-5 py-2 text-sm font-semibold text-maroon-foreground disabled:opacity-60"
          >
            {busy ? "Processing…" : "Pay (demo) & enroll"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-border px-4 py-2 text-sm"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  );
}
