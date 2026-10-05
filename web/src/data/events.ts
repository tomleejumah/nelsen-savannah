/**
 * Site events — aligned with Android `Event` (Firebase `Events/{eventId}`).
 * Seat caps must match api/src/data/siteEvents.js
 */

export type AppEvent = {
  eventId: string;
  title: string;
  /** Epoch ms (Android Long) */
  date: number;
  startTime: string;
  endTime: string;
  eventType: string;
  mentorId: string;
  menteeId: string;
  mentorName: string;
  menteeName: string;
  /** Legacy numeric event state. Live events also expose liveStatus. */
  status: number;
  liveStatus?: "scheduled" | "live" | "ended" | null;
  liveAvailability?: "unknown" | "available" | "unavailable" | null;
  description: string | null;
  mode: "physical" | "online";
  location: string;
  meetingLink: string;
  participants?: string[] | null;
  seats?: number;
  seatsTaken?: number;
  price?: string;
  program?: string;
  facilitators?: string[];
};

export function eventFormat(e: AppEvent): "In person" | "Online" {
  return e.mode === "online" ? "Online" : "In person";
}

export function isLiveEvent(e: AppEvent): boolean {
  return e.eventType?.toLowerCase() === "live";
}

export function liveStatusLabel(e: AppEvent): "SCHEDULED LIVE" | "LIVE NOW" | "REPLAY" {
  if (e.liveStatus === "live") return "LIVE NOW";
  if (e.liveStatus === "ended") return "REPLAY";
  return "SCHEDULED LIVE";
}

export function youtubeVideoId(raw: string): string {
  try {
    const url = new URL(raw);
    const host = url.hostname.toLowerCase().replace(/^www\./, "");
    if (host === "youtu.be") return url.pathname.split("/").filter(Boolean)[0] || "";
    if (host === "youtube.com" || host.endsWith(".youtube.com")) {
      const watchId = url.searchParams.get("v");
      if (watchId) return watchId;
      const parts = url.pathname.split("/").filter(Boolean);
      if (["embed", "live", "shorts"].includes(parts[0] || "")) return parts[1] || "";
    }
  } catch {
    return "";
  }
  return "";
}

export function youtubeEmbedUrl(raw: string): string {
  const id = youtubeVideoId(raw);
  return id ? `https://www.youtube.com/embed/${encodeURIComponent(id)}?playsinline=1&rel=0&modestbranding=1` : "";
}

export function eventVenue(e: AppEvent): string {
  if (isLiveEvent(e)) return "YouTube Live";
  if (e.mode === "online") return e.meetingLink ? "Online meeting" : "Online";
  return e.location || "TBA";
}

export function eventTimeRange(e: AppEvent): string {
  if (!e.startTime && !e.endTime) return "";
  if (!e.endTime) return e.startTime;
  return `${e.startTime} – ${e.endTime}`;
}

export function eventDateLabel(e: AppEvent): string {
  return new Date(e.date).toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

export const EVENTS: AppEvent[] = [
  {
    eventId: "evt-intake-aug-2026",
    title: "Innovation Hub — First Intake",
    date: Date.parse("2026-08-29T09:00:00+03:00"),
    startTime: "9:00 AM",
    endTime: "4:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Tomee Juma & Evans Nyairo",
    menteeName: "",
    status: 0,
    description:
      "Opening intake for Nelsen Savannah Innovation Hub — meet facilitators, tour the programmes, and reserve your free seat for the first cohort.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    meetingLink: "",
    seats: 100,
    price: "Free",
    program: "All programmes",
    facilitators: ["Tomee Juma", "Evans Nyairo"],
  },
  {
    eventId: "evt-future-safari-open",
    title: "Future Safari: Innovation Open Day",
    date: Date.parse("2026-09-12T09:00:00+03:00"),
    startTime: "9:00 AM",
    endTime: "1:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah Innovation Hub",
    menteeName: "",
    status: 0,
    description:
      "Digital literacy, design thinking, and emerging tech awareness — explore the innovation cycle.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    meetingLink: "",
    seats: 60,
    price: "Free",
    program: "Future Safari",
  },
  {
    eventId: "evt-robotics-lab",
    title: "Robotics & Automation Lab: Build Session",
    date: Date.parse("2026-09-24T17:00:00+03:00"),
    startTime: "5:00 PM",
    endTime: "8:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah Innovation Hub",
    menteeName: "",
    status: 0,
    description:
      "Hands-on Arduino, sensors, and actuators — introductory build for new robotics cohort members.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    meetingLink: "",
    seats: 40,
    price: "Free",
    program: "Savannah Robotics & Automation Lab",
  },
  {
    eventId: "evt-data-ai-capstone",
    title: "Data & AI Academy: Capstone Showcase",
    date: Date.parse("2026-10-04T14:00:00+03:00"),
    startTime: "2:00 PM",
    endTime: "5:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah Innovation Hub",
    menteeName: "",
    status: 0,
    description:
      "Learners present data and AI projects — evidence-led decisions and responsible use of emerging tools.",
    mode: "online",
    location: "",
    meetingLink: "https://meet.google.com/",
    seats: 80,
    price: "Free",
    program: "Savannah Data & AI Academy",
  },
  {
    eventId: "evt-kijiji-demo-day",
    title: "Kijiji Hub: Community Demo Day",
    date: Date.parse("2026-10-18T10:00:00+03:00"),
    startTime: "10:00 AM",
    endTime: "4:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah Innovation Hub",
    menteeName: "",
    status: 0,
    description:
      "Community innovation pitches — from problem identification through MVPs, business models, and launch.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    meetingLink: "",
    seats: 60,
    price: "Free",
    program: "Kijiji Hub",
  },
  {
    eventId: "evt-creative-lab",
    title: "Savannah Creative Lab: Portfolio Review",
    date: Date.parse("2026-11-07T15:00:00+03:00"),
    startTime: "3:00 PM",
    endTime: "6:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah Innovation Hub",
    menteeName: "",
    status: 0,
    description:
      "Creative outputs, storytelling, and design critique — build a portfolio that communicates your work clearly.",
    mode: "physical",
    location: "Nairobi Innovation Hub (venue TBA)",
    meetingLink: "",
    seats: 40,
    price: "Free",
    program: "Savannah Creative Lab",
  },
];

export const FEATURED_EVENT = EVENTS[0]!;
