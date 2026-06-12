import { describe, expect, it } from "vitest";
import { assignableRoles, getHighestRole, getRank, isElevated } from "./roles";

describe("getHighestRole", () => {
  it("returns the highest known role", () => {
    expect(getHighestRole(["ROLE_USER", "ROLE_ADMIN"])).toBe("ROLE_ADMIN");
    expect(getHighestRole(["ROLE_OWNER", "ROLE_ADMIN"])).toBe("ROLE_OWNER");
  });

  it("falls back to ROLE_USER when nothing matches", () => {
    expect(getHighestRole([])).toBe("ROLE_USER");
    expect(getHighestRole(["SOMETHING_ELSE"])).toBe("ROLE_USER");
  });
});

describe("isElevated", () => {
  it("is true for admin roles only", () => {
    expect(isElevated(["ROLE_ADMIN"])).toBe(true);
    expect(isElevated(["ROLE_SUPER_ADMIN"])).toBe(true);
    expect(isElevated(["ROLE_OWNER"])).toBe(true);
    expect(isElevated(["ROLE_USER"])).toBe(false);
    expect(isElevated([])).toBe(false);
  });
});

describe("assignableRoles", () => {
  it("only allows assigning strictly lower roles", () => {
    expect(assignableRoles("ROLE_OWNER")).toEqual([
      "ROLE_USER",
      "ROLE_ADMIN",
      "ROLE_SUPER_ADMIN",
    ]);
    expect(assignableRoles("ROLE_ADMIN")).toEqual(["ROLE_USER"]);
    expect(assignableRoles("ROLE_USER")).toEqual([]);
  });
});

describe("getRank", () => {
  it("orders roles correctly", () => {
    expect(getRank("ROLE_OWNER")).toBeGreaterThan(getRank("ROLE_SUPER_ADMIN"));
    expect(getRank("ROLE_SUPER_ADMIN")).toBeGreaterThan(getRank("ROLE_ADMIN"));
    expect(getRank("ROLE_ADMIN")).toBeGreaterThan(getRank("ROLE_USER"));
  });
});
