import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.13.0",
  tagName: "v1.13.0",
  versionCode: 23,
  apkName: "NowTuneUp.apk",
  apkSize: 12937506,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "9e4cd6e28c8011ef9de1142202ffcd895816c4e150f97dd5de5431cd86c5ef63",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-09-06T23:57:31.000Z",
  releaseNotes: "NowTuneUp 1.13.0 consolidates the 1.10–1.13 release train: adaptive adapter profiles and health history, Live Data session recording with Min/Max/Average summaries, Diagnostics v2 history and optional Mode 06 monitoring, and OBD-only Time Slip v2 with automatic launch validation, interpolated crossings and run quality scoring.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
