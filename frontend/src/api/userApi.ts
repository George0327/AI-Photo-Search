import { fetchJson, patchJson } from "./apiClient";

export type UserDto = {
  id: number;
  email: string;
  fullName: string;
  profilePictureUrl: string | null;
  role: "USER" | "ADMIN" | "SUPER_ADMIN" | "OWNER";
  active: boolean;
  createdAt: string;
};

export function fetchAllUsers(): Promise<UserDto[]> {
  return fetchJson<UserDto[]>("/api/admin/users");
}

export function changeUserRole(userId: number, role: UserDto["role"]): Promise<UserDto> {
  return patchJson<UserDto>(`/api/admin/users/${userId}/role`, { role });
}
