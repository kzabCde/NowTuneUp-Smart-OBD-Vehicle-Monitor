import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.7.0",
  tagName: "v1.7.0",
  versionCode: 14,
  apkName: "NowTuneUp.apk",
  apkSize: 12871362,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "991b498690493933903835d232dd24623ddaa8020135772b0b55e92336ea321c",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-03T15:29:55.000Z",
  releaseNotes: "NowTuneUp 1.7.0 adds Time Slip performance testing with standing and rolling starts, speed milestones, estimated 60 ft through 1 mile distance splits, live timing, local history, sharing and CSV export. Distance measurements remain marked Estimated / Low confidence until GPS and accelerometer sensor fusion is added. Bluetooth, USB, dashboard, diagnostics and saved profiles remain supported.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
