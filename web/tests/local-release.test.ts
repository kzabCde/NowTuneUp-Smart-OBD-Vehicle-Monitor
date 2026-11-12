// @vitest-environment node

import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { URL } from "node:url";
import { describe, expect, it } from "vitest";
import { getLocalRelease, localRelease } from "@/lib/local-release";

describe("published Android release metadata", () => {
  it("matches the APK actually bundled for download", () => {
    const apk = readFileSync(new URL("../public/downloads/NowTuneUp.apk", import.meta.url));
    expect(localRelease.apkSize).toBe(apk.length);
    expect(localRelease.sha256).toBe(createHash("sha256").update(apk).digest("hex"));
    expect(localRelease.tagName).toBe(`v${localRelease.version}`);
    expect(localRelease.apkName).toBe(`NowTuneUp-v${localRelease.version}-release.apk`);
    expect(localRelease.downloadUrl).toBe(
      `https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/${localRelease.tagName}/${localRelease.apkName}`,
    );
  });

  it("distinguishes verified current metadata from unknown versions", () => {
    expect(getLocalRelease(localRelease.version)).toEqual(localRelease);
    expect(getLocalRelease("0.0.0")).toBeNull();
    expect(localRelease.versionCode).toBeGreaterThan(0);
    expect(localRelease.releaseNotes.length).toBeGreaterThan(0);
  });
});
