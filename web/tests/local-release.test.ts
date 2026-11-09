import { describe, expect, it } from "vitest";
import { getLocalRelease, localRelease } from "@/lib/local-release";

describe("published Android release metadata", () => {
  it("points to the signed v1.18.0 APK and official checksum", () => {
    expect(localRelease.version).toBe("1.18.0");
    expect(localRelease.tagName).toBe("v1.18.0");
    expect(localRelease.versionCode).toBe(28);
    expect(localRelease.apkName).toBe("NowTuneUp-v1.18.0-release.apk");
    expect(localRelease.apkSize).toBe(13_072_278);
    expect(localRelease.sha256).toBe(
      "a059ad4354f2eab4f4b0998c3bdc3abaaa2448c5928962abd1c5206dfa3a233e",
    );
    expect(localRelease.downloadUrl).toBe(
      "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.18.0/NowTuneUp-v1.18.0-release.apk",
    );
  });

  it("exposes current production details without treating older versions as current", () => {
    expect(getLocalRelease("1.18.0")).toEqual(localRelease);
    expect(getLocalRelease("1.17.0")).toBeNull();
    expect(localRelease.releaseNotes).toContain("Motorsport Performance Suite");
  });
});
