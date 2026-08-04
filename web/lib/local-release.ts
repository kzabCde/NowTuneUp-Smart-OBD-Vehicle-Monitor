import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.8.0",
  tagName: "v1.8.0",
  versionCode: 17,
  apkName: "NowTuneUp.apk",
  apkSize: 12904734,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "f3107d96f6cf19a1d221814312d296acddc38cb62ef19322a6e725bd690be9e3",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-04T08:12:14.000Z",
  releaseNotes: "NowTuneUp 1.8.0 introduces Accurate Time Slip with dedicated speed-priority OBD sampling, transport-boundary monotonic timestamps, speed PID readiness checks, OBD + GNSS + accelerometer sensor fusion, GNSS accuracy and satellite status, slope analysis, reaction time, optional one-foot rollout, raw telemetry replay, speed graphs, run comparison, vehicle-specific best runs, CSV and image export, foreground OBD session ownership and hardware diagnostic reports.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
