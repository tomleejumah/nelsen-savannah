/** Public site events — seat caps enforced server-side (web mirrors eventId + seats). */

export const SITE_EVENTS = {
  "evt-intake-aug-2026": {
    title: "Innovation Hub — First Intake",
    seats: 100,
    price: "Free",
  },
  "evt-future-safari-open": { title: "Future Safari: Innovation Open Day", seats: 60, price: "Free" },
  "evt-robotics-lab": { title: "Robotics & Automation Lab: Build Session", seats: 40, price: "Free" },
  "evt-data-ai-capstone": { title: "Data & AI Academy: Capstone Showcase", seats: 80, price: "Free" },
  "evt-kijiji-demo-day": { title: "Kijiji Hub: Community Demo Day", seats: 60, price: "Free" },
  "evt-creative-lab": { title: "Savannah Creative Lab: Portfolio Review", seats: 40, price: "Free" },
};

export function getEventCap(eventId) {
  return SITE_EVENTS[eventId] ?? null;
}
