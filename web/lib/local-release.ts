import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.14.0",
  tagName: "v1.14.0",
  versionCode: 24,
  apkName: "NowTuneUp-v1.14.0-release.apk",
  apkSize: 12988486,
  downloadUrl: "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.14.0/NowTuneUp-v1.14.0-release.apk",
  sha256: "89b640b159dde6a4f61947cf11d0860bd79b71451cb5ab434392e0989fd20156",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-09-07T19:39:31.000Z",
  releaseNotes: "NowTuneUp 1.14.0 adds user-created vehicle profiles with zero default/demo vehicles, a premium reduce-motion-aware automotive animation system and N + tachometer visual language, plus expanded Time Slip presets for 0–60, 0–100, 1/4 mile, 1/2 mile and 1 mile. OBD telemetry and timing remain driven by actual measurement data, independent from presentation animation.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
