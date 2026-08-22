import type { AppEvent } from "@/data/events";
import { fetchPublicHubEvents, type HubEventDto } from "@/lib/lmsApi";
import { EVENTS as FALLBACK_EVENTS } from "@/data/events";

export function hubEventToAppEvent(e: HubEventDto): AppEvent {
  return {
    eventId: e.eventId,
    title: e.title,
    date: e.date,
    startTime: e.startTime,
    endTime: e.endTime,
    eventType: e.eventType,
    mentorId: e.mentorId,
    menteeId: e.menteeId,
    mentorName: e.mentorName,
    menteeName: e.menteeName,
    status: e.status,
    description: e.description,
    mode: e.mode,
    location: e.location,
    meetingLink: e.meetingLink,
    participants: e.participants,
    program: e.program,
    seats: e.seats,
    seatsTaken: e.seatsTaken,
    price: e.price,
    facilitators: e.facilitators,
  };
}

export async function loadHubEvents(): Promise<AppEvent[]> {
  const res = await fetchPublicHubEvents();
  if (res.ok && res.data?.events?.length) {
    return res.data.events.map(hubEventToAppEvent);
  }
  return FALLBACK_EVENTS;
}
