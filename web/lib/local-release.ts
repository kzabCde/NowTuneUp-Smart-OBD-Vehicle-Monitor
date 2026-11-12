import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  "version": "1.19.1",
  "tagName": "v1.19.1",
  "versionCode": 30,
  "apkName": "NowTuneUp-v1.19.1-release.apk",
  "apkSize": 13203350,
  "downloadUrl": "https://github.com/kzabCde/NowTuneUp-Smart-OBD-Vehicle-Monitor/releases/download/v1.19.1/NowTuneUp-v1.19.1-release.apk",
  "sha256": "9ebeb8d486e91599aa10229b14c559e3eb608edf966608ca5fe93c23542446d9",
  "minimumAndroid": "Android 8.0",
  "publishedAt": "2026-11-12T09:32:05Z",
  "releaseNotes": "NowTuneUp 1.19.1 introduces Home, Performance, History, Garage and Settings; two primary gauge presentations; persistent dashboard and widget editing; adaptive navigation; light/dark themes; and locally shared performance reports. Existing OBD connections and stored data are retained. Performance timing remains an estimate from OBD speed."
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
