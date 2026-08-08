export const LMS_API_BASE =
  import.meta.env.VITE_LMS_API_BASE ?? "http://localhost:5002";

export type MeDto = {
  uid: string;
  email: string;
  displayName: string;
  firstName: string;
  lastName: string;
  photoUrl: string;
  userRole: "Mentee" | "Mentor" | "Admin";
  capabilities: Record<string, boolean>;
};

export type TrackCardDto = {
  courseId: string;
  tutorId: string;
  courseImageUrl: string;
  tutorAvatarUrl: string;
  tutorName: string;
  courseTitle: string;
  duration: string;
  lessons: string;
  courseLink: string;
  isLiked: boolean;
  trackId: string;
  programSlug: string;
  does: string;
  trackPercent: number;
  enrolled: boolean;
  audience: string[];
  moduleCount: number;
};

export type LmsEnvelope<T> = {
  ok: boolean;
  source: "postgres" | "sqlite" | "rtdb";
  data: T | null;
  error: string | null;
};

async function lmsFetch<T>(
  path: string,
  idToken?: string | null,
): Promise<LmsEnvelope<T>> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (idToken) headers.Authorization = `Bearer ${idToken}`;
  const res = await fetch(`${LMS_API_BASE}${path}`, { headers });
  const json = (await res.json()) as LmsEnvelope<T>;
  if (!res.ok && !json.error) {
    return {
      ok: false,
      source: json.source ?? "sqlite",
      data: null,
      error: `HTTP ${res.status}`,
    };
  }
  return json;
}

export async function fetchLmsMe(idToken: string) {
  return lmsFetch<MeDto>("/lms/me", idToken);
}

export async function fetchLmsTracks(idToken: string) {
  return lmsFetch<{ tracks: TrackCardDto[] }>("/lms/tracks", idToken);
}
