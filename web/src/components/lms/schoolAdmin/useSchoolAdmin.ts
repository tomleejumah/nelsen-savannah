import { useCallback, useEffect, useMemo, useState } from "react";
import type { User } from "firebase/auth";

import {
  decideSchoolApplication,
  fetchLmsTracks,
  fetchMenteeProgress,
  fetchSchoolApplications,
  fetchSchoolDashboard,
  fetchSchoolMembers,
  fetchSchoolMoney,
  fetchSchoolTutorPayouts,
  importSchoolRoster,
  patchSchoolBranding,
  patchSchoolMemberRole,
  patchSchoolMemberStatus,
  putSchoolTrackMentors,
  registerSchoolMentee,
  registerSchoolMentor,
  type MeDto,
  type MenteeProgressDto,
  type SchoolApplicationDto,
  type SchoolDashboardDto,
  type SchoolMemberDto,
  type TrackCardDto,
} from "@/lib/lmsApi";

export function useSchoolAdmin(user: User, me: MeDto) {
  const schoolId = me.schoolId || me.activeSchoolId || "";
  const [members, setMembers] = useState<SchoolMemberDto[]>([]);
  const [dash, setDash] = useState<SchoolDashboardDto | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [mentorUid, setMentorUid] = useState("");
  const [mentorEmail, setMentorEmail] = useState("");
  const [mentorName, setMentorName] = useState("");
  const [menteeUid, setMenteeUid] = useState("");
  const [menteeEmail, setMenteeEmail] = useState("");
  const [menteeName, setMenteeName] = useState("");
  const [csv, setCsv] = useState("uid,email,displayName,role\n");
  const [accent, setAccent] = useState("");
  const [logoUrl, setLogoUrl] = useState("");
  const [seats, setSeats] = useState(10);
  const [phone, setPhone] = useState("");
  const [payMethod, setPayMethod] = useState<"card" | "mpesa">("card");
  const [payMsg, setPayMsg] = useState<string | null>(null);
  const [moneyNote, setMoneyNote] = useState<string | null>(null);
  const [payoutNote, setPayoutNote] = useState<string | null>(null);
  const [balance, setBalance] = useState(0);
  const [tracks, setTracks] = useState<TrackCardDto[]>([]);
  const [mentees, setMentees] = useState<MenteeProgressDto[]>([]);
  const [assignTrackId, setAssignTrackId] = useState<string | null>(null);
  const [assignUids, setAssignUids] = useState<string[]>([]);
  const [assignMsg, setAssignMsg] = useState<string | null>(null);
  const [lastInviteUrl, setLastInviteUrl] = useState<string | null>(null);
  const [applications, setApplications] = useState<SchoolApplicationDto[]>([]);

  const load = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const token = await user.getIdToken();
      const [m, d, money, payouts, t, progress, apps] = await Promise.all([
        fetchSchoolMembers(token, schoolId),
        fetchSchoolDashboard(token, schoolId),
        fetchSchoolMoney(token, schoolId),
        fetchSchoolTutorPayouts(token, schoolId),
        fetchLmsTracks(token),
        fetchMenteeProgress(token, me.uid),
        fetchSchoolApplications(token, schoolId, "pending"),
      ]);
      if (!m.ok) setError(m.error || "Could not load roster");
      setMembers(m.data?.members || []);
      setDash(d.data || null);
      if (d.data?.accentColor) setAccent(d.data.accentColor);
      if (d.data?.logoUrl) setLogoUrl(d.data.logoUrl);
      if (money.ok && money.data) {
        setBalance(money.data.balance);
        setMoneyNote(money.data.note);
      }
      if (payouts.ok && payouts.data) setPayoutNote(payouts.data.note);
      setTracks(t.data?.tracks || []);
      setMentees(progress.data?.mentees || []);
      setApplications(apps.data?.applications || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Network error");
    } finally {
      setBusy(false);
    }
  }, [user, schoolId, me.uid]);

  useEffect(() => {
    void load();
  }, [load]);

  const schoolMentors = useMemo(() => {
    const byUid = new Map<
      string,
      { uid: string; email?: string; displayName?: string }
    >();
    for (const m of members) {
      if (m.userRole === "Mentor" && m.uid && m.status !== "suspended") {
        byUid.set(m.uid, m);
      }
    }
    for (const m of dash?.assignableMentors || []) {
      if (m.uid && !byUid.has(m.uid)) byUid.set(m.uid, m);
    }
    for (const c of dash?.byCourse || []) {
      for (const m of c.mentors || []) {
        if (m.uid && !byUid.has(m.uid)) {
          byUid.set(m.uid, { uid: m.uid, displayName: m.displayName });
        }
      }
    }
    return [...byUid.values()].sort((a, b) =>
      String(a.displayName || a.uid).localeCompare(String(b.displayName || b.uid)),
    );
  }, [members, dash]);

  const studentMembers = useMemo(
    () =>
      members.filter(
        (m) =>
          m.userRole === "Mentee" ||
          m.status === "invited" ||
          m.status === "applied",
      ),
    [members],
  );

  async function addMentor(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await registerSchoolMentor(token, schoolId, {
      email: mentorEmail.trim(),
      ...(mentorName.trim() ? { displayName: mentorName.trim() } : {}),
      ...(mentorUid.trim() ? { uid: mentorUid.trim() } : {}),
    });
    setMsg(
      result.ok
        ? result.data?.member?.status === "invited"
          ? "Invite saved — they become a mentor when they sign in with that email."
          : "Mentor attached to this school."
        : result.error || "Failed",
    );
    if (result.ok) {
      setLastInviteUrl(result.data?.member?.inviteUrl || null);
      setMentorUid("");
      setMentorEmail("");
      setMentorName("");
      await load();
    }
  }

  async function addMentee(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await registerSchoolMentee(token, schoolId, {
      email: menteeEmail.trim(),
      ...(menteeName.trim() ? { displayName: menteeName.trim() } : {}),
      ...(menteeUid.trim() ? { uid: menteeUid.trim() } : {}),
    });
    setMsg(
      result.ok
        ? result.data?.member?.status === "invited"
          ? "Invite saved — share the join link, or they join when they sign in with that email."
          : "Mentee attached to this school."
        : result.error || "Failed",
    );
    if (result.ok) {
      setLastInviteUrl(result.data?.member?.inviteUrl || null);
      setMenteeUid("");
      setMenteeEmail("");
      setMenteeName("");
      await load();
    }
  }

  async function escalate(uid: string) {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolMemberRole(token, schoolId, uid, "Mentor");
    setMsg(result.ok ? "Escalated to Mentor." : result.error || "Failed");
    if (result.ok) await load();
  }

  async function setMentorEnabled(uid: string, enabled: boolean) {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolMemberStatus(
      token,
      schoolId,
      uid,
      enabled ? "active" : "suspended",
    );
    setMsg(
      result.ok
        ? enabled
          ? "Mentor re-enabled."
          : "Mentor disabled and removed from courses."
        : result.error || "Failed",
    );
    if (result.ok) await load();
  }

  async function approveJoin(uid: string) {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolMemberStatus(
      token,
      schoolId,
      uid,
      "active",
    );
    setMsg(
      result.ok
        ? "Join request approved — student can enroll."
        : result.error || "Failed",
    );
    if (result.ok) await load();
  }

  async function copyInvite(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setMsg("Invite link copied.");
    } catch {
      setMsg(url);
    }
  }

  async function decideApp(
    applicationId: string,
    status: "approved" | "rejected",
  ) {
    setMsg(null);
    const token = await user.getIdToken();
    const result = await decideSchoolApplication(
      token,
      schoolId,
      applicationId,
      status,
    );
    setMsg(
      result.ok
        ? status === "approved"
          ? "Mentor approved."
          : "Application rejected."
        : result.error || "Failed",
    );
    if (result.ok) await load();
  }

  async function onRoster(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await importSchoolRoster(token, schoolId, csv);
    setMsg(
      result.ok
        ? `Imported ${result.data?.imported ?? 0} members`
        : result.error || "Import failed",
    );
    if (result.ok) await load();
  }

  async function onBrand(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    const token = await user.getIdToken();
    const result = await patchSchoolBranding(token, schoolId, {
      ...(accent.trim() ? { accentColor: accent.trim() } : {}),
      ...(logoUrl.trim() ? { logoUrl: logoUrl.trim() } : {}),
    });
    setMsg(result.ok ? "Branding saved." : result.error || "Failed");
  }

  async function saveTrackMentors(trackId: string) {
    setMsg(null);
    setAssignMsg(null);
    if (schoolMentors.length === 0) {
      setAssignMsg(
        "Invite a mentor first, then assign them here.",
      );
      return;
    }
    const token = await user.getIdToken();
    const result = await putSchoolTrackMentors(
      token,
      schoolId,
      trackId,
      assignUids,
    );
    const text = result.ok
      ? "Trainers assigned to this course."
      : result.error || "Failed";
    setMsg(text);
    setAssignMsg(result.ok ? null : text);
    if (result.ok) {
      setAssignTrackId(null);
      await load();
    }
  }

  return {
    schoolId,
    members,
    studentMembers,
    schoolMentors,
    dash,
    busy,
    error,
    msg,
    setMsg,
    mentorUid,
    setMentorUid,
    mentorEmail,
    setMentorEmail,
    mentorName,
    setMentorName,
    menteeUid,
    setMenteeUid,
    menteeEmail,
    setMenteeEmail,
    menteeName,
    setMenteeName,
    csv,
    setCsv,
    accent,
    setAccent,
    logoUrl,
    setLogoUrl,
    seats,
    setSeats,
    phone,
    setPhone,
    payMethod,
    setPayMethod,
    payMsg,
    setPayMsg,
    moneyNote,
    payoutNote,
    balance,
    tracks,
    mentees,
    assignTrackId,
    setAssignTrackId,
    assignUids,
    setAssignUids,
    assignMsg,
    setAssignMsg,
    lastInviteUrl,
    applications,
    load,
    addMentor,
    addMentee,
    escalate,
    setMentorEnabled,
    approveJoin,
    copyInvite,
    decideApp,
    onRoster,
    onBrand,
    saveTrackMentors,
  };
}

export type SchoolAdminApi = ReturnType<typeof useSchoolAdmin>;
