import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.6.1",
  tagName: "v1.6.1",
  versionCode: 12,
  apkName: "NowTuneUp.apk",
  apkSize: 12822210,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "3a243b394cb721197a7b83aa8fdbecb74f9314bd6d3696d1c41e14b4b2a90385",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-03T03:24:56.000Z",
  releaseNotes: "Revised Bluetooth stability build. Uses cancellable non-blocking RFCOMM reads, a longer quiet-window drain for late ELM327 responses, insecure-to-secure SPP fallback, optional adaptive ELM timing, faster priority polling and soft command-stream resynchronization before a full reconnect. Premium gauges, dashboards, USB, Turbo, HUD, DTC and saved settings remain unchanged.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
