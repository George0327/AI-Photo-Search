export type AppRole = "ROLE_USER" | "ROLE_ADMIN" | "ROLE_SUPER_ADMIN" | "ROLE_OWNER";

const RANK: Record<AppRole, number> = {
  ROLE_USER: 0,
  ROLE_ADMIN: 1,
  ROLE_SUPER_ADMIN: 2,
  ROLE_OWNER: 3,
};

export function getHighestRole(authorities: string[]): AppRole {
  const elevated = authorities.filter((a): a is AppRole => a in RANK);
  if (elevated.length === 0) return "ROLE_USER";
  return elevated.reduce((best, cur) => (RANK[cur] > RANK[best] ? cur : best));
}

export function getRank(role: AppRole): number {
  return RANK[role];
}

export function isElevated(authorities: string[]): boolean {
  return authorities.some(
    (a) => a === "ROLE_ADMIN" || a === "ROLE_SUPER_ADMIN" || a === "ROLE_OWNER"
  );
}

export function assignableRoles(myRole: AppRole): AppRole[] {
  return (Object.keys(RANK) as AppRole[]).filter((r) => RANK[r] < RANK[myRole]);
}

export const ROLE_LABELS: Record<AppRole, string> = {
  ROLE_USER: "User",
  ROLE_ADMIN: "Admin",
  ROLE_SUPER_ADMIN: "Super Admin",
  ROLE_OWNER: "Owner",
};

export const ROLE_COLORS: Record<AppRole, string> = {
  ROLE_USER: "gray",
  ROLE_ADMIN: "blue",
  ROLE_SUPER_ADMIN: "violet",
  ROLE_OWNER: "grape",
};
