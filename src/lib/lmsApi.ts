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

export type LmsEnvelope<T> = {
  ok: boolean;
  source: "postgres" | "sqlite" | "rtdb";
  data: T | null;
  error: string | null;
};

export async function fetchLmsMe(idToken: string): Promise<LmsEnvelope<MeDto>> {
  const res = await fetch(`${LMS_API_BASE}/lms/me`, {
    headers: {
      Authorization: `Bearer ${idToken}`,
      Accept: "application/json",
    },
  });
  const json = (await res.json()) as LmsEnvelope<MeDto>;
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
