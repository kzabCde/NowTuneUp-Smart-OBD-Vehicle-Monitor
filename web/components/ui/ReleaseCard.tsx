import Link from "next/link";
import type { ReleaseInfo } from "@/lib/validation/releases";

export function bytes(size: number) {
  return size > 0 ? `${(size / 1024 / 1024).toFixed(1)} MB` : "APK";
}

export function ReleaseCard({ release, compact = false }: { release: ReleaseInfo; compact?: boolean }) {
  const splitLayout = compact ? "" : "min-[900px]:grid-cols-[minmax(0,1fr)_280px]";
  const releaseFactsLayout = compact
    ? "grid gap-5"
    : "grid gap-6 md:grid-cols-[minmax(0,1fr)_auto] md:items-end";
  const packagePanelBorder = compact
    ? "border-t border-white/8"
    : "border-t border-white/8 min-[900px]:border-l min-[900px]:border-t-0";

  return (
    <article className="panel min-w-0 max-w-full overflow-hidden">
      <div className={`grid min-w-0 max-w-full ${splitLayout}`}>
        <div className="min-w-0 p-6 md:p-8">
          <div className="flex flex-wrap items-center gap-2">
            <span className="chip"><span className="status-dot" aria-hidden="true" /> Stable</span>
            <span className="chip">Build {release.versionCode}</span>
            <span className="chip">{release.minimumAndroid}+</span>
          </div>

          <div className={`mt-7 min-w-0 ${releaseFactsLayout}`}>
            <div className="min-w-0">
              <p className="eyebrow">Production release</p>
              <h2 className="mt-2 break-words text-4xl font-black tracking-[-0.055em] sm:text-5xl md:text-6xl">
                NowTuneUp {release.version}
              </h2>
              <p className="muted mt-3 break-words text-sm">{release.apkName} · {bytes(release.apkSize)}</p>
            </div>
            <div className={`technical-card min-w-0 px-4 ${compact ? "w-full" : "w-full md:w-auto md:min-w-44"}`}>
              <div className="spec-row"><span className="spec-key">Channel</span><span className="spec-value status-good">Stable</span></div>
              <div className="spec-row"><span className="spec-key">Package</span><span className="spec-value">APK</span></div>
            </div>
          </div>

          {!compact && <p className="mt-6 max-w-3xl leading-7 text-[#c9ccc0]">{release.releaseNotes}</p>}
        </div>

        <div className={`telemetry-grid min-w-0 max-w-full flex flex-col justify-center gap-3 bg-black/15 p-6 md:p-8 ${packagePanelBorder}`}>
          <p className="data-label">Official Android package</p>
          <Link className="button mt-2 max-w-full" href={release.downloadUrl} download aria-label={`Download NowTuneUp ${release.version} APK`}>
            Download APK
          </Link>
          <Link className="button secondary max-w-full" href={`/releases/${encodeURIComponent(release.version)}`}>
            Release details
          </Link>
          <p className="muted mt-1 break-words text-xs leading-5">Verify build facts and SHA-256 on the official download page.</p>
        </div>
      </div>
    </article>
  );
}
