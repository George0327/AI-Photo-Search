export type AuthUser = {
  authenticated: boolean;
  sub: string;
  email: string;
  name: string;
  picture: string;
  authorities: string[];
};

const BACKEND_BASE_URL = "http://localhost:8080";

export async function fetchCurrentUser(): Promise<AuthUser> {
  const response = await fetch("/api/auth/me", {
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
  window.location.href = `${BACKEND_BASE_URL}/oauth2/authorization/google`;
}

export async function logout() {
  await fetch("/api/auth/logout", {
    method: "POST",
    credentials: "include",
  });

  window.location.href = "/";
}
