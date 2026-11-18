import { createFileRoute } from "@tanstack/react-router";
import { PolicyPage } from "@/components/site/PolicyPage";
export const Route = createFileRoute("/shipping-policy")({
  head: () => ({ meta: [{ title: "Shipping Policy | Nelsen Savannah" }, { name: "description", content: "Delivery information for Nelsen Savannah digital services and physical materials." }] }),
  component: () => <PolicyPage title="Shipping Policy" intro="How programme access, digital materials and any physical items are delivered." sections={[
    { title: "Digital services", body: "Most of our offerings are educational services delivered online or at the hub. Digital course access, notices and learning materials are made available through your account or the channels described when you enrol. These do not require physical shipping." },
    { title: "Physical items", body: "If a programme includes a physical kit, certificate or other item, delivery or collection arrangements, any charges and estimated timelines should be confirmed with you before payment. We do not promise a standard nationwide shipping rate or delivery window for items unless stated in the specific offer." },
    { title: "Delivery issues", body: "If you have not received expected access or an agreed physical item, contact us with your enrolment or order details so we can investigate." },
    { title: "Changes and availability", body: "Delivery arrangements may vary by programme and location. Any material change to an agreed arrangement should be communicated to affected participants." }
  ]} />
});