import type { ReleaseInfo } from "@/lib/validation/releases";

export const localRelease: ReleaseInfo = {
  version: "1.6.0",
  tagName: "v1.6.0",
  versionCode: 10,
  apkName: "NowTuneUp.apk",
  apkSize: 12805826,
  downloadUrl: "/downloads/NowTuneUp.apk",
  sha256: "28cc8d3cb933e17af0694178985ce2a0a59603ab6a38a95eaead15e56baa3f4a",
  minimumAndroid: "Android 8.0",
  publishedAt: "2026-08-02T14:09:23.000Z",
  releaseNotes: "Introduces premium full circular automotive gauges with layered metallic bezels, improved needles, digital values, warning and critical arcs, and six colored gauge presets. Removes visually weak colorless presets with backward-compatible migration. Refreshes the Dashboard Editor, themes, empty and stale states while preserving Bluetooth ELM327, USB, Turbo, HUD, diagnostics, alerts and saved dashboards.",
};

export function getLocalRelease(version?: string) {
  if (version && version !== localRelease.version) return null;
  return localRelease;
}
