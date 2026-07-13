import { BASE_URL } from "./apiClient";

export type AuthUser = {
  authenticated: boolean;
  sub: string;
  email: string;
  name: string;
  picture: string;
  authorities: string[];
};

const ANONYMOUS_USER: AuthUser = {
  authenticated: false,
  sub: "",
  email: "",
  name: "",
  picture: "",
  authorities: [],
};

export async function fetchCurrentUser(): Promise<AuthUser> {
  // plain fetch: a signed-out /me is an anonymous user, not a session-expired error
  try {
    const response = await fetch(`${BASE_URL}/api/auth/me`, { credentials: "include" });
    if (!response.ok) return ANONYMOUS_USER;
    return await response.json();
  } catch (error) {
    console.warn("Failed to fetch current user:", error);
    return ANONYMOUS_USER;
  }
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
  } catch (error) {
    // redirect anyway, the cookie may already be gone on the server
    console.warn("Logout request failed:", error);
  }
  window.location.href = "/";
}
