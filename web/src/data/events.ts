/**
 * Site events — aligned with Android `Event` (Firebase `Events/{eventId}`).
 * Source of truth fields match CreateEventActivity / Event.toMap().
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
  /** 0 upcoming · 1 completed · 2 today */
  status: number;
  description: string | null;
  mode: "physical" | "online";
  /** Physical venue / address (Android `location`) */
  location: string;
  meetingLink: string;
  participants?: string[] | null;
  /** Site RSVP extras (not required on Android create) */
  seats?: number;
  seatsTaken?: number;
  price?: string;
  program?: string;
};

export function eventFormat(e: AppEvent): "In person" | "Online" {
  return e.mode === "online" ? "Online" : "In person";
}

export function eventVenue(e: AppEvent): string {
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
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

/** Sample / seed events — same shape Android writes to Firebase */
export const EVENTS: AppEvent[] = [
  {
    eventId: "evt-sela-kickoff",
    title: "Sela programme: Cohort Kickoff",
    date: Date.parse("2026-09-12T09:00:00+03:00"),
    startTime: "9:00 AM",
    endTime: "4:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah",
    menteeName: "",
    status: 0,
    description:
      "Meet your mentor, set goals for the season, and walk out with a clear next-step map.",
    mode: "physical",
    location: "Sarit Expo Centre, Westlands",
    meetingLink: "",
    seats: 400,
    seatsTaken: 318,
    price: "Free",
    program: "Sela programme",
  },
  {
    eventId: "evt-trailblazers-lab",
    title: "Trailblazers: Live Practice Lab",
    date: Date.parse("2026-09-24T17:30:00+03:00"),
    startTime: "5:30 PM",
    endTime: "8:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah",
    menteeName: "",
    status: 0,
    description: "Peer cohorts, mentor panels, and written feedback within 48 hours.",
    mode: "physical",
    location: "Nelsen Savannah Hub, Kilimani",
    meetingLink: "",
    seats: 60,
    seatsTaken: 51,
    price: "KES 500",
    program: "Trailblazers",
  },
  {
    eventId: "evt-codelab-sprint",
    title: "Go for it Codelab Sprint Day",
    date: Date.parse("2026-10-04T10:00:00+03:00"),
    startTime: "10:00 AM",
    endTime: "1:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah",
    menteeName: "",
    status: 0,
    description: "Ship a small project with mentor review — portfolio-ready by the end of the day.",
    mode: "online",
    location: "",
    meetingLink: "https://meet.google.com/",
    seats: 120,
    seatsTaken: 44,
    price: "Free",
    program: "Go for it Codelab",
  },
  {
    eventId: "evt-scripture-safari",
    title: "Scripture Safari Campus Gathering",
    date: Date.parse("2026-10-18T14:00:00+03:00"),
    startTime: "2:00 PM",
    endTime: "5:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah",
    menteeName: "",
    status: 0,
    description:
      "Faith-rooted conversation on purpose, pressure, and walking with a mentor community.",
    mode: "physical",
    location: "Kenyatta University Main Hall",
    meetingLink: "",
    seats: 250,
    seatsTaken: 96,
    price: "Free",
    program: "Scripture Safari",
  },
  {
    eventId: "evt-mentor-mixer",
    title: "Nelsen Savannah Mentor Mixer",
    date: Date.parse("2026-11-07T18:00:00+03:00"),
    startTime: "6:00 PM",
    endTime: "9:00 PM",
    eventType: "event",
    mentorId: "",
    menteeId: "",
    mentorName: "Nelsen Savannah",
    menteeName: "",
    status: 0,
    description:
      "Curated pairing night across programs. Come with one question, leave with a mentor.",
    mode: "physical",
    location: "The Alchemist, Westlands",
    meetingLink: "",
    seats: 150,
    seatsTaken: 143,
    price: "KES 1,000",
    program: "Trailblazers",
  },
];
