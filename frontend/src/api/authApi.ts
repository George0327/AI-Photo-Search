import { BASE_URL } from "./apiClient";

export type AuthUser = {
  authenticated: boolean;
  sub: string;
  email: string;
  name: string;
  picture: string;
  authorities: string[];
};

export async function fetchCurrentUser(): Promise<AuthUser> {
  const response = await fetch(`${BASE_URL}/api/auth/me`, {
    credentials: "include",
  });

  if (!response.ok) {
    return {
      authenticated: false,
      sub: "",
      email: "",
      name: "",
      picture: "",
      authorities: [],
    };
  }

  return response.json();
}

export function loginWithGoogle() {
  window.location.href = `${BASE_URL}/oauth2/authorization/google`;
}

export async function logout() {
  try {
    await fetch(`${BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
    });
  } catch {}
  window.location.href = "/";
}
