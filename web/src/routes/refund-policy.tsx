import { createFileRoute } from "@tanstack/react-router";
import { PolicyPage } from "@/components/site/PolicyPage";
export const Route = createFileRoute("/refund-policy")({
  head: () => ({ meta: [{ title: "Refund Policy | Nelsen Savannah" }, { name: "description", content: "How to request a refund for eligible Nelsen Savannah purchases." }] }),
  component: () => <PolicyPage title="Refund Policy" intro="Information about requesting a refund for programme fees and other payments." sections={[
    { title: "Requesting a refund", body: "If you believe a payment was made in error, duplicated, or the paid service was not provided as described, contact us using the email below. Include the programme or service name, transaction reference, payment date and a short explanation. Do not send passwords or full payment credentials." },
    { title: "How requests are assessed", body: "We review each request against the terms communicated at purchase, the service already delivered and applicable Kenyan consumer protection law. A refund is not automatically guaranteed solely because a request is submitted." },
    { title: "Cancelled or unavailable programmes", body: "If Nelsen Savannah cancels a paid programme or cannot provide a purchased service, contact us to discuss a refund or another suitable remedy in accordance with applicable law." },
    { title: "Processing", body: "We will acknowledge and review refund requests and communicate the outcome and any applicable payment reversal process. Actual processing time can depend on the original payment provider." },
    { title: "Your statutory rights", body: "Nothing in this policy is intended to limit rights or remedies available under applicable consumer protection law." }
  ]} />
});