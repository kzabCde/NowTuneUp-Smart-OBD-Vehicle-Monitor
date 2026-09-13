import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.15.0",
  tagName: "v1.15.0",
  versionCode: 25,
  apkName: "NowTuneUp-v1.15.0-release.apk",
  apkSize: 13006986,
  downloadUrl: "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.15.0/NowTuneUp-v1.15.0-release.apk",
  sha256: "f0e0fb8c80536ce1307883fbd7defe8fb20f5476d0094a89b7513d31ee75a85c",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-09-13T04:07:20.000Z",
  releaseNotes: "NowTuneUp 1.15.0 introduces the unified Graphite and Daylight interface, adaptive launcher and notification icons, accessible reduced-motion animation, clearer Thai-friendly screens, and an empty first-run experience where users create their own vehicle profiles and dashboards.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
