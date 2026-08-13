export const LMS_API_BASE =
  import.meta.env.VITE_LMS_API_BASE ??
  "https://api.tommlyjumah.dev/nisisi-africa";

export type MeDto = {
  uid: string;
  email: string;
  displayName: string;
  firstName: string;
  lastName: string;
  photoUrl: string;
  userRole: "Mentee" | "Mentor" | "SchoolAdmin" | "SuperAdmin" | "Admin";
  schoolId?: string;
  schoolName?: string;
  shell?: "student" | "mentor" | "school" | "admin";
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

export type ModuleDto = {
  moduleId: string;
  trackId: string;
  title: string;
  does: string;
  estimatedMinutes: number;
  lessonCount: number;
  modulePercent?: number;
  status?: string;
};

export type LessonDto = {
  lessonId: string;
  moduleId: string;
  trackId: string;
  title: string;
  does: string;
  type: string;
  estimatedMinutes: number;
  hasQuiz: boolean;
  hasAssignment: boolean;
  lessonPercent: number;
  status: string;
  contentUrl?: string | null;
  mediaId?: string | null;
  playbackUrl?: string | null;
  playbackExpiresAt?: number | null;
  bodyHtml?: string | null;
  quiz?: { mode: string; prompt: string } | null;
  assignmentPrompt?: string | null;
};

export type SubmissionDto = {
  id: string;
  lessonId: string;
  trackId: string;
  moduleId: string;
  uid: string;
  status: string;
  text: string;
  score: number | null;
  feedback: string | null;
  assignmentId?: string | null;
  submittedAt: number;
  markedAt: number | null;
};

export type QueueItemDto = {
  id: string;
  lessonId: string;
  lessonTitle: string;
  trackId: string;
  menteeId: string;
  menteeName: string;
  menteeAvatar: string;
  text: string;
  submittedAt: number;
};

export type MenteeProgressDto = {
  uid: string;
  displayName: string;
  photoUrl: string;
  trackId: string;
  trackPercent: number;
  lastActiveAt: number;
};

export type AssignmentDto = {
  id: string;
  schoolId: string | null;
  trackId: string | null;
  lessonId: string | null;
  title: string;
  prompt: string;
  assignedBy: string;
  assigneeUid: string | null;
  cohort: string | null;
  dueAt: number | null;
  createdAt: number;
};

export type SchoolDto = {
  schoolId: string;
  name: string;
  createdAt: number;
  updatedAt: number;
};

export type SchoolMemberDto = {
  uid: string;
  email: string;
  displayName: string;
  photoUrl: string;
  userRole: string;
  schoolId: string;
};

export type CertificateDto = {
  trackId: string;
  courseTitle: string;
  issuedAt: number;
  verifyUrl: string;
  pdfUrl: string | null;
  trackPercent: number;
};

export type TrackDetailDto = {
  track: TrackCardDto;
  modules: ModuleDto[];
  enrollment: {
    trackId: string;
    trackPercent: number;
    status: string;
  } | null;
};

export type ModuleDetailDto = {
  module: ModuleDto;
  lessons: LessonDto[];
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

export async function fetchLmsTrack(idToken: string, trackId: string) {
  return lmsFetch<TrackDetailDto>(
    `/lms/tracks/${encodeURIComponent(trackId)}`,
    idToken,
  );
}

export async function fetchLmsModule(idToken: string, moduleId: string) {
  return lmsFetch<ModuleDetailDto>(
    `/lms/modules/${encodeURIComponent(moduleId)}`,
    idToken,
  );
}

export async function fetchLmsLesson(idToken: string, lessonId: string) {
  return lmsFetch<{ lesson: LessonDto }>(
    `/lms/lessons/${encodeURIComponent(lessonId)}`,
    idToken,
  );
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

export async function submitLessonQuiz(
  idToken: string,
  lessonId: string,
  body: { score?: number; passed?: boolean; lastPlatform?: "web" | "android" },
) {
  return lmsFetch<{
    lessonId: string;
    quizPct: number;
    lessonPercent?: number;
    trackPercent?: number;
  }>(`/lms/lessons/${encodeURIComponent(lessonId)}/quiz`, idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...body, lastPlatform: body.lastPlatform || "web" }),
  });
}

export async function submitAssignment(
  idToken: string,
  body: {
    lessonId: string;
    text: string;
    platform?: string;
    assignmentId?: string;
  },
) {
  return lmsFetch<{ submission: SubmissionDto }>("/lms/submissions", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      lessonId: body.lessonId,
      text: body.text,
      platform: body.platform || "web",
      assignmentId: body.assignmentId,
    }),
  });
}

export async function fetchMySubmissions(idToken: string, trackId?: string) {
  const q = trackId ? `?trackId=${encodeURIComponent(trackId)}` : "";
  return lmsFetch<{ submissions: SubmissionDto[] }>(
    `/lms/submissions/me${q}`,
    idToken,
  );
}

export async function fetchMyCertificates(idToken: string) {
  return lmsFetch<{ certificates: CertificateDto[] }>(
    "/lms/certificates/me",
    idToken,
  );
}

export async function fetchSubmissionQueue(idToken: string) {
  return lmsFetch<{ queue: QueueItemDto[] }>("/lms/submissions/queue", idToken);
}

export async function markSubmission(
  idToken: string,
  submissionId: string,
  body: { score: number; passed?: boolean; feedback?: string },
) {
  return lmsFetch<{
    submission: {
      id: string;
      status: string;
      score: number;
      feedback: string;
      assignmentPct: number;
      lessonPercent: number | null;
      trackPercent: number | null;
    };
  }>(`/lms/submissions/${encodeURIComponent(submissionId)}/mark`, idToken, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function fetchMenteeProgress(idToken: string, mentorId: string) {
  return lmsFetch<{ mentees: MenteeProgressDto[] }>(
    `/lms/admin/mentees/${encodeURIComponent(mentorId)}/progress`,
    idToken,
  );
}

export async function createAssignment(
  idToken: string,
  body: {
    title: string;
    prompt?: string;
    trackId?: string;
    lessonId?: string;
    assigneeUid?: string;
    cohort?: string;
    dueAt?: number;
  },
) {
  return lmsFetch<{ assignment: AssignmentDto }>("/lms/assignments", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function fetchMyAssignments(idToken: string) {
  return lmsFetch<{ assignments: AssignmentDto[] }>(
    "/lms/assignments/me",
    idToken,
  );
}

export async function fetchAssignedOutbox(idToken: string) {
  return lmsFetch<{ assignments: AssignmentDto[] }>(
    "/lms/assignments/assigned",
    idToken,
  );
}

export async function fetchSchools(idToken: string) {
  return lmsFetch<{ schools: SchoolDto[] }>("/lms/schools", idToken);
}

export async function createSchool(
  idToken: string,
  body: {
    name: string;
    schoolId?: string;
    adminUid?: string;
    adminEmail?: string;
    adminDisplayName?: string;
  },
) {
  return lmsFetch<{ school: SchoolDto }>("/lms/schools", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function patchSchoolAdmins(
  idToken: string,
  schoolId: string,
  body: { adminUid?: string; adminUids?: string[]; email?: string; displayName?: string },
) {
  return lmsFetch<{ schoolId: string; adminUids: string[] }>(
    `/lms/schools/${encodeURIComponent(schoolId)}/admins`,
    idToken,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
}

export async function fetchSchoolMembers(idToken: string, schoolId: string) {
  return lmsFetch<{ members: SchoolMemberDto[] }>(
    `/lms/schools/${encodeURIComponent(schoolId)}/members`,
    idToken,
  );
}

export async function registerSchoolMentor(
  idToken: string,
  schoolId: string,
  body: { uid: string; email?: string; displayName?: string },
) {
  return lmsFetch<{ member: SchoolMemberDto }>(
    `/lms/schools/${encodeURIComponent(schoolId)}/mentors`,
    idToken,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
}

export async function registerSchoolMentee(
  idToken: string,
  schoolId: string,
  body: { uid: string; email?: string; displayName?: string },
) {
  return lmsFetch<{ member: SchoolMemberDto }>(
    `/lms/schools/${encodeURIComponent(schoolId)}/mentees`,
    idToken,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
}

export async function patchSchoolMemberRole(
  idToken: string,
  schoolId: string,
  uid: string,
  userRole: string,
) {
  return lmsFetch<{ uid: string; userRole: string; schoolId: string }>(
    `/lms/schools/${encodeURIComponent(schoolId)}/members/${encodeURIComponent(uid)}/role`,
    idToken,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ userRole }),
    },
  );
}

export type AdminStatsDto = {
  enrollmentsTotal: number;
  avgTrackPercent: number;
  completions30d: number;
  byTrack: { trackId: string; enrolled: number; avgPercent: number }[];
  schoolId?: string | null;
};

export type SchoolDashboardDto = {
  schoolId: string;
  schoolName: string;
  rosterCount: number;
  mentors: number;
  mentees: number;
  enrollments: number;
  avgCompletion: number;
  atRisk: {
    uid: string;
    displayName: string;
    trackId: string;
    trackPercent: number;
    lastActiveAt: number;
  }[];
  logoUrl: string | null;
  accentColor: string | null;
};

export async function fetchAdminStats(idToken: string) {
  return lmsFetch<AdminStatsDto>("/lms/admin/stats", idToken);
}

export async function adminCreateTrack(
  idToken: string,
  body: {
    trackId: string;
    title: string;
    blurb?: string;
    schoolId?: string;
    published?: boolean;
  },
) {
  return lmsFetch<{ track: { trackId: string; schoolId?: string } }>(
    "/lms/admin/tracks",
    idToken,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
}

export async function adminCreateModule(
  idToken: string,
  body: { moduleId: string; trackId: string; title: string; does?: string },
) {
  return lmsFetch<{ module: { moduleId: string } }>("/lms/admin/modules", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function adminCreateLesson(
  idToken: string,
  body: {
    lessonId: string;
    moduleId: string;
    trackId: string;
    title: string;
    type?: string;
    hasQuiz?: boolean;
    hasAssignment?: boolean;
  },
) {
  return lmsFetch<{ lesson: { lessonId: string } }>("/lms/admin/lessons", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export type MediaUploadTicketDto = {
  mediaId: string;
  status: string;
  driver: string;
  bucket: string | null;
  objectKey: string;
  schoolId: string;
  scope: string;
  scopeId: string | null;
  uploadUrl: string;
  method: string;
  headers: Record<string, string>;
  expiresAt: number;
  ttlSeconds: number;
};

export type MediaPlaybackDto = {
  url: string;
  expiresAt: number;
  driver: string;
  ttlSeconds: number;
  mimeType?: string | null;
  durationSec?: number | null;
};

export async function requestMediaUploadUrl(
  idToken: string,
  body: {
    filename: string;
    contentType: string;
    sizeBytes?: number;
    scope?: "track" | "module" | "lesson" | "branding" | "misc";
    scopeId?: string;
    lessonId?: string;
    schoolId?: string;
    durationSeconds?: number;
  },
) {
  return lmsFetch<MediaUploadTicketDto>("/lms/media/upload-url", idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function finalizeMediaUpload(
  idToken: string,
  mediaId: string,
  body: { durationSeconds?: number } = {},
) {
  return lmsFetch<{
    mediaId: string;
    status: string;
    driver: string;
    sizeBytes: number;
    mimeType: string | null;
    durationSec: number | null;
    lessonId: string | null;
  }>(`/lms/media/${encodeURIComponent(mediaId)}/finalize`, idToken, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function fetchMediaPlaybackUrl(idToken: string, mediaId: string) {
  return lmsFetch<MediaPlaybackDto>(
    `/lms/media/${encodeURIComponent(mediaId)}/url`,
    idToken,
  );
}

/**
 * Raw PUT straight to the presigned target — the bytes never touch the LMS API.
 * XHR rather than fetch because large lesson video needs a progress signal.
 */
export function putToPresignedUrl(
  ticket: Pick<MediaUploadTicketDto, "uploadUrl" | "method" | "headers">,
  file: Blob,
  onProgress?: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(ticket.method || "PUT", ticket.uploadUrl, true);
    for (const [key, value] of Object.entries(ticket.headers || {})) {
      xhr.setRequestHeader(key, value);
    }
    xhr.upload.onprogress = (event) => {
      if (onProgress && event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new Error(`Storage rejected upload (HTTP ${xhr.status})`));
    xhr.onerror = () =>
      reject(
        new Error(
          "Upload failed to reach storage — check the bucket CORS policy allows PUT from this origin",
        ),
      );
    xhr.send(file);
  });
}

export async function uploadLessonMedia(
  idToken: string,
  params: { lessonId: string; file: File; onProgress?: (pct: number) => void },
) {
  const ticket = await requestMediaUploadUrl(idToken, {
    filename: params.file.name,
    contentType: params.file.type || "application/octet-stream",
    sizeBytes: params.file.size,
    scope: "lesson",
    scopeId: params.lessonId,
  });
  if (!ticket.ok || !ticket.data) {
    throw new Error(ticket.error || "Could not get an upload URL");
  }
  await putToPresignedUrl(ticket.data, params.file, params.onProgress);
  const finalized = await finalizeMediaUpload(idToken, ticket.data.mediaId);
  if (!finalized.ok || !finalized.data) {
    throw new Error(finalized.error || "Upload could not be finalized");
  }
  return finalized.data;
}

export async function fetchSchoolDashboard(idToken: string, schoolId: string) {
  return lmsFetch<SchoolDashboardDto>(
    `/lms/schools/${encodeURIComponent(schoolId)}/dashboard`,
    idToken,
  );
}

export async function importSchoolRoster(
  idToken: string,
  schoolId: string,
  csv: string,
) {
  return lmsFetch<{ imported: number; errors: { line: number; error: string }[] }>(
    `/lms/schools/${encodeURIComponent(schoolId)}/roster`,
    idToken,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ csv }),
    },
  );
}

export async function patchSchoolBranding(
  idToken: string,
  schoolId: string,
  body: { name?: string; logoUrl?: string; accentColor?: string },
) {
  return lmsFetch<{ school: SchoolDto }>(
    `/lms/schools/${encodeURIComponent(schoolId)}`,
    idToken,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
}
