import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.4.0",
  tagName: "v1.4.0",
  versionCode: 8,
  apkName: "NowTuneUp.apk",
  apkSize: 12723726,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "2f8fab66f2c8906e95110fa3f3e02414d830eea5c051d5dc3e52c5410e03d994",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T13:40:45.000Z",
  releaseNotes: "Adds calculated Turbo Pressure from MAP and barometric OBD-II PIDs with kPa, bar and PSI display. Adds mirrored HUD Mode with brightness, color and burn-in protection controls. Adds adaptive Phone, Tablet and Android Head Unit layouts with navigation rail, wide-screen dashboard density and immersive operation.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
