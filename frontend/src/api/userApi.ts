import { fetchJson, patchJson } from "./apiClient";
import { USERS_PAGE_SIZE } from "../constants";

export type UserDto = {
  id: number;
  email: string;
  fullName: string;
  profilePictureUrl: string | null;
  role: "USER" | "ADMIN" | "SUPER_ADMIN" | "OWNER";
  active: boolean;
  createdAt: string;
};

export type UserPageResponse = {
  users: UserDto[];
  totalUsers: number;
  page: number;
  size: number;
};

export function fetchUsers(page = 0, size = USERS_PAGE_SIZE): Promise<UserPageResponse> {
  return fetchJson<UserPageResponse>(`/api/admin/users?page=${page}&size=${size}`);
}

export function changeUserRole(userId: number, role: UserDto["role"]): Promise<UserDto> {
  return patchJson<UserDto>(`/api/admin/users/${userId}/role`, { role });
}
