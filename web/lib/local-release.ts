import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.16.0",
  tagName: "v1.16.0",
  versionCode: 26,
  apkName: "NowTuneUp-v1.16.0-release.apk",
  apkSize: 13023126,
  downloadUrl: "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.16.0/NowTuneUp-v1.16.0-release.apk",
  sha256: "04e510416c9131ac887bce273caf930a0ea91e617405dec663b922851f68382c",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-09-21T03:06:18.000Z",
  releaseNotes: "NowTuneUp 1.16.0 adds interactive Time Slip speed, estimated acceleration and distance graphs, previous-run comparison, improved result history, a more minimal interface, and the new Pulse N visual identity across the app and website.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
