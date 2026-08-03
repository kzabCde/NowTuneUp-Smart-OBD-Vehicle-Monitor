import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.6.2",
  tagName: "v1.6.2",
  versionCode: 13,
  apkName: "NowTuneUp.apk",
  apkSize: 12822210,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "92f6e09c4950e3871357d000f5c49c61096cc2e03fcef606229aeb094f9a1e20",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-03T10:52:16.000Z",
  releaseNotes: "NowTuneUp 1.6.2 removes Trip recording and bundled dashboard layouts, starts with user-created saved profiles, and replaces technical widget and color-code setup with everyday Thai labels and visual named color choices. Existing saved profiles are preserved. Bluetooth realtime stability, USB, Turbo, HUD and DTC behavior remain unchanged.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
