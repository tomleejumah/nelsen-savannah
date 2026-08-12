import type { MeDto } from "@/lib/lmsApi";

export type LmsShell = "student" | "mentor" | "school" | "admin";

export function shellHomePath(shell: LmsShell): string {
  switch (shell) {
    case "mentor":
      return "/teach";
    case "school":
      return "/school";
    case "admin":
      return "/admin";
    default:
      return "/learning";
  }
}

export function shellFromMe(me: MeDto | null | undefined): LmsShell {
  if (me?.shell === "mentor" || me?.shell === "school" || me?.shell === "admin") {
    return me.shell;
  }
  const role = me?.userRole;
  if (role === "SuperAdmin" || role === "Admin") return "admin";
  if (role === "SchoolAdmin") return "school";
  if (role === "Mentor") return "mentor";
  return "student";
}

export function canAccessShell(me: MeDto | null | undefined, shell: LmsShell): boolean {
  const mine = shellFromMe(me);
  if (shell === "student") return true;
  if (shell === "mentor") {
    return mine === "mentor" || mine === "school" || mine === "admin";
  }
  if (shell === "school") {
    return mine === "school" || mine === "admin";
  }
  return mine === "admin";
}
