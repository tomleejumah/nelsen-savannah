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

export type EnrollmentDto = {
  uid: string;
  trackId: string;
  status: string;
  trackPercent: number;
  modulesCompleted: number;
  modulesTotal: number;
  lessonsCompleted: number;
  lessonsTotal: number;
  mentorId: string | null;
  enrolledAt: number;
  platform: string;
  courseTitle?: string;
  courseImageUrl?: string;
  nextLessonId?: string | null;
};

export type ProgressMapDto = {
  byLessonId: Record<
    string,
    {
      lessonPercent: number;
      status: string;
      contentPct: number;
      updatedAt: number;
    }
  >;
  byTrackId: Record<
    string,
    {
      trackPercent: number;
      status: string;
      nextLessonId?: string | null;
    }
  >;
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
  init?: RequestInit,
): Promise<LmsEnvelope<T>> {
  const headers: Record<string, string> = {
    Accept: "application/json",
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (idToken) headers.Authorization = `Bearer ${idToken}`;
  const res = await fetch(`${LMS_API_BASE}${path}`, { ...init, headers });
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

export async function enrollInTrack(idToken: string, trackId: string) {
  return lmsFetch<{ enrollment: EnrollmentDto }>("/lms/enrollments", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ trackId, platform: "web" }),
  });
}

export async function fetchMyEnrollments(idToken: string) {
  return lmsFetch<{ enrollments: EnrollmentDto[] }>(
    "/lms/enrollments/me",
    idToken,
  );
}

export async function patchLessonProgress(
  idToken: string,
  lessonId: string,
  body: {
    opened?: boolean;
    contentPct?: number;
    quizPct?: number;
    assignmentPct?: number;
    lastPlatform?: "web" | "android";
  },
) {
  return lmsFetch<{
    progress: {
      lessonId: string;
      lessonPercent: number;
      trackPercent: number;
      modulePercent: number;
      status: string;
    };
  }>(`/lms/progress/${lessonId}`, idToken, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...body, lastPlatform: body.lastPlatform || "web" }),
  });
}

export async function fetchMyProgress(idToken: string, trackId?: string) {
  const q = trackId ? `?trackId=${encodeURIComponent(trackId)}` : "";
  return lmsFetch<ProgressMapDto>(`/lms/progress/me${q}`, idToken);
}
