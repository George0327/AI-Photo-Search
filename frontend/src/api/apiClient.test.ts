import { describe, expect, it } from "vitest";
import { extractErrorMessage } from "./apiClient";

function jsonResponse(body: unknown, status = 500): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("extractErrorMessage", () => {
  it("prefers the backend's error field", async () => {
    const response = jsonResponse({ error: "Album not found" }, 404);
    expect(await extractErrorMessage(response)).toBe("Album not found");
  });

  it("falls back to the status line when the error field is missing", async () => {
    const response = jsonResponse({ message: "nope" }, 500);
    expect(await extractErrorMessage(response)).toContain("500");
  });

  it("falls back to the status line for non-JSON bodies", async () => {
    const response = new Response("<html>Bad gateway</html>", {
      status: 502,
      statusText: "Bad Gateway",
    });
    expect(await extractErrorMessage(response)).toBe("Request failed: 502 Bad Gateway");
  });

  it("ignores empty error strings", async () => {
    const response = jsonResponse({ error: "" }, 500);
    expect(await extractErrorMessage(response)).toContain("Request failed");
  });
});
