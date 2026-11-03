import { createFileRoute } from "@tanstack/react-router";
import { PolicyPage } from "@/components/site/PolicyPage";
export const Route = createFileRoute("/privacy-policy")({
  head: () => ({ meta: [{ title: "Privacy Policy | Nelsen Savannah" }, { name: "description", content: "How Nelsen Savannah handles personal information." }] }),
  component: () => <PolicyPage title="Privacy Policy" intro="How we collect, use and protect information when you use our learning platform." sections={[
    { title: "Information we collect", body: "When you register or use our services, we may collect your name, email address, account details, school or course enrolment information, messages and learning activity. We may also collect device and technical information needed to operate and secure the platform." },
    { title: "How we use information", body: "We use this information to provide accounts, learning services, course administration, communications, support, platform security and service improvements. Notifications may be sent about activity relevant to your account or enrolled programmes." },
    { title: "Sharing and service providers", body: "Information may be processed by infrastructure, authentication, messaging and payment providers that help us operate the platform. We do not publish private course messages to the public. Information may be disclosed where required by law or to protect users and the service." },
    { title: "Retention and security", body: "We retain information for as long as reasonably necessary to deliver services, meet legal obligations and resolve disputes. We use access controls and other safeguards, but no internet service can guarantee absolute security." },
    { title: "Your choices and rights", body: "You may contact us to request access to, correction of or deletion of personal data, subject to applicable legal and operational requirements. You can also manage applicable device notification permissions." },
    { title: "Children and learners", body: "Where programmes involve children, parents, guardians and participating schools should ensure appropriate authorisation. Contact us with any concern about a learner's information." },
    { title: "Updates", body: "We may revise this policy when our services or legal obligations change. The current version will be available at this address." }
  ]} />
});