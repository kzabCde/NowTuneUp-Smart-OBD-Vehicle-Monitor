import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.9.0",
  tagName: "v1.9.0",
  versionCode: 19,
  apkName: "NowTuneUp.apk",
  apkSize: 12904734,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "f773bba79348f4fdefae7b2755f87f72bfbf802f4dd0680dd1f800045ec79e41",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-08T04:13:40.000Z",
  releaseNotes: "NowTuneUp 1.9.0 combines the 1.8.2 stability foundation with Vehicle Intelligence: adaptive ELM327 health-based pacing, demand-based PID polling, KOEO Turbo baseline calibration and quality gating, persisted last-session reports, VIN-based vehicle profiles, Mode 01 readiness, Stored/Pending/Permanent DTC scanning, freeze-frame trigger reading, adapter compatibility self-test, and a simplified diagnostics UX.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
