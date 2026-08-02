import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.5.0",
  tagName: "v1.5.0",
  versionCode: 9,
  apkName: "NowTuneUp.apk",
  apkSize: 12789442,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "84747cc32923287b1d26bd1c8146e2c5221f07dc07c0539a7c9ca0f32656d2ba",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-02T13:05:07.000Z",
  releaseNotes: "Adds Bluetooth Classic SPP support for paired ELM327 adapters with Android 12+ permissions, RFCOMM device selection, controlled ELM327 initialization, ECU verification, supported-PID polling, Thai connection guidance, exponential reconnect, diagnostic logs and confirmed Mode 04 DTC clearing. USB, Dashboard, Turbo, HUD, Tablet and Head Unit features remain available. BLE remains an experimental disabled transport placeholder until adapter-specific GATT UUIDs are known.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
