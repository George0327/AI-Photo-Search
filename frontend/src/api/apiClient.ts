export const BASE_URL = import.meta.env.VITE_API_URL ?? "";

export const SESSION_EXPIRED_EVENT = "session-expired";

export function dispatchSessionExpired() {
  window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

export async function extractErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (body && typeof body.error === "string" && body.error.length > 0) {
      return body.error;
    }
  } catch {
    // not JSON, so fall back to the status message below
  }
  return `Request failed: ${response.status} ${response.statusText}`;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${url}`, { credentials: "include", ...init });

  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }

  if (!response.ok) {
    throw new Error(await extractErrorMessage(response));
  }

  const contentType = response.headers.get("content-type");
  if (!contentType || !contentType.includes("application/json")) {
    throw new Error("Backend did not return JSON. You may need to log in first.");
  }

  return response.json();
}

export function fetchJson<T>(url: string): Promise<T> {
  return request<T>(url);
}

export function postJson<T>(url: string): Promise<T> {
  return request<T>(url, { method: "POST" });
}

export function postJsonBody<T>(url: string, body: unknown): Promise<T> {
  return request<T>(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export async function postVoid(url: string): Promise<void> {
  const response = await fetch(`${BASE_URL}${url}`, {
    method: "POST",
    credentials: "include",
  });
  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response));
  }
}

export async function deleteVoid(url: string): Promise<void> {
  const response = await fetch(`${BASE_URL}${url}`, {
    method: "DELETE",
    credentials: "include",
  });
  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response));
  }
}

export function patchJson<T>(url: string, body: unknown): Promise<T> {
  return request<T>(url, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function postFormData<T>(url: string, formData: FormData): Promise<T> {
  return request<T>(url, { method: "POST", body: formData });
}
