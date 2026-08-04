import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.8.1",
  tagName: "v1.8.1",
  versionCode: 18,
  apkName: "NowTuneUp.apk",
  apkSize: 12871966,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "e106864d624de59882e6fecacff0fa8e5e07c4739abdeac70b7481ce2ac49cd5",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-04T13:14:06.000Z",
  releaseNotes: "NowTuneUp 1.8.1 is a stability-first update with safer ELM327 command pacing, resilient realtime recovery, stabilized Turbo Pressure filtering, a repaired OBD-only Time Slip flow, removal of duplicate Peak versus Max information, supported-only Live Data, and a much simpler four-section Settings experience.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
