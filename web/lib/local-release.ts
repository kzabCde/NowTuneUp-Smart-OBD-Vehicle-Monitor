import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.1.0",
  tagName: "v1.1.0",
  versionCode: 2,
  apkName: "NowTuneUp.apk",
  apkSize: 12576242,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "ab07b75ab6a28a7b8d8c5b22e0a9df41a1776376a96dc76ec404a21450cec406",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-24T10:51:12.000Z",
  releaseNotes: "Dashboard customization with Digital, Analog and Hybrid modes, theme presets, editable layouts, threshold colors, Reduce Motion, Driving Mode and adjustable refresh rate.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
