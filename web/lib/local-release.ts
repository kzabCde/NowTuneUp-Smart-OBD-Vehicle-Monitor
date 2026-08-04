import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.7.2",
  tagName: "v1.7.2",
  versionCode: 16,
  apkName: "NowTuneUp.apk",
  apkSize: 12904130,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "1473cf9d79c73ea70655f3a8d5de6114c6986781b4200f91748210e9f50bc4e0",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-04T05:24:37.000Z",
  releaseNotes: "NowTuneUp 1.7.2 stabilizes Turbo Pressure with synchronized MAP/BARO freshness, median spike rejection, adaptive time-based smoothing, a zero-boost deadband and clean reconnect resets. Time Slip now separates Setup, Armed, Running, Result and History states, adds quick presets, a smooth monotonic display timer, live sample quality, landscape Track Mode, clearer result comparison and persistent OBD-only distance confidence labels. Bluetooth, USB, dashboard profiles, diagnostics and sharing remain supported.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
