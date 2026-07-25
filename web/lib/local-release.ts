import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.3.1",
  tagName: "v1.3.1",
  versionCode: 7,
  apkName: "NowTuneUp.apk",
  apkSize: 12690958,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "b831721321c984234cb5cd26a8949d48ab4ee51a10e5bcd6957f320225791ff8",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-07-25T12:00:50.000Z",
  releaseNotes: "Adds Gauge Focus Mode with tap-to-hide controls, touch lock, keep-screen-on, automatic focus, swipeable dashboard pages and compact connection status. Adds Peak/Min/Max, threshold alerts with sound, vibration, hysteresis and cooldown, data freshness and stale-value protection, automatic reconnect and last-dashboard resume.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
