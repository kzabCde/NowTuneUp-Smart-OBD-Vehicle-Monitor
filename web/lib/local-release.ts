import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.1.1",
  tagName: "v1.1.1",
  versionCode: 3,
  apkName: "NowTuneUp.apk",
  apkSize: 12609006,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "1924463d037b4b42ca68d76fd01f6808dd34fd96d62142942802801bcdb4c85e",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T05:41:40.000Z",
  releaseNotes: "Usability update with guided USB/ELM327 connection steps, clearer loading and error states, improved default dashboards, practical mobile settings, resizable widgets and distinct Classic, Sport, Minimal, Neon and OEM gauges.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
