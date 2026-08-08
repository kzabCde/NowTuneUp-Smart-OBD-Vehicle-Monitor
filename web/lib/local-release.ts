import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.9.0",
  tagName: "v1.9.0",
  versionCode: 19,
  apkName: "NowTuneUp.apk",
  apkSize: 12904734,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "f3deed1444eafcceffaad4dd11b53ab54be0d02aba2db9c5d8bf456ba8be8885",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-08T03:59:48.000Z",
  releaseNotes: "NowTuneUp 1.9.0 combines the 1.8.2 stability foundation with Vehicle Intelligence: adaptive ELM327 health-based pacing, demand-based PID polling, KOEO Turbo baseline calibration and quality gating, persisted last-session reports, VIN-based vehicle profiles, Mode 01 readiness, Stored/Pending/Permanent DTC scanning, freeze-frame trigger reading, adapter compatibility self-test, and a simplified diagnostics UX.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
