import { describe, expect, it } from "vitest";
import { releaseRepository } from "@/lib/github/repository";

describe("release repository configuration", () => {
  it("uses explicit release settings first", () => {
    expect(releaseRepository({
      GITHUB_REPOSITORY_OWNER: "official",
      GITHUB_REPOSITORY_NAME: "nowtuneup",
      VERCEL_GIT_REPO_OWNER: "fork",
      VERCEL_GIT_REPO_SLUG: "website",
    })).toEqual({ owner: "official", name: "nowtuneup" });
  });

  it("discovers the connected Vercel GitHub repository", () => {
    expect(releaseRepository({
      VERCEL_GIT_REPO_OWNER: "nowtuneup",
      VERCEL_GIT_REPO_SLUG: "vehicle-monitor",
    })).toEqual({ owner: "nowtuneup", name: "vehicle-monitor" });
  });

  it("supports the GitHub Actions repository variable", () => {
    expect(releaseRepository({ GITHUB_REPOSITORY: "nowtuneup/vehicle-monitor" }))
      .toEqual({ owner: "nowtuneup", name: "vehicle-monitor" });
  });

  it("rejects incomplete repository settings", () => {
    expect(() => releaseRepository({ GITHUB_REPOSITORY_OWNER: "nowtuneup" }))
      .toThrow("Release service is not configured");
  });
});
