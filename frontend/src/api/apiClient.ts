export function dispatchSessionExpired() {
  window.dispatchEvent(new Event("session-expired"));
}

export async function fetchJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { credentials: "include" });

  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status} ${response.statusText}`);
  }

  const contentType = response.headers.get("content-type");
  if (!contentType || !contentType.includes("application/json")) {
    throw new Error("Backend did not return JSON. You may need to log in first.");
  }

  return response.json();
}

export async function postJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { method: "POST", credentials: "include" });

  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status} ${response.statusText}`);
  }

  const contentType = response.headers.get("content-type");
  if (!contentType || !contentType.includes("application/json")) {
    throw new Error("Backend did not return JSON. You may need to log in first.");
  }

  return response.json();
}

export async function patchJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: "PATCH",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (response.status === 401) {
    dispatchSessionExpired();
    throw new Error("Session expired");
  }

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status} ${response.statusText}`);
  }

  const contentType = response.headers.get("content-type");
  if (!contentType || !contentType.includes("application/json")) {
    throw new Error("Backend did not return JSON. You may need to log in first.");
  }

  return response.json();
}
