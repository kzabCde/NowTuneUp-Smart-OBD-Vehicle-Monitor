import Link from "next/link";
import type { ReleaseInfo } from "@/lib/validation/releases";

export function bytes(size: number) {
  return size > 0 ? `${(size / 1024 / 1024).toFixed(1)} MB` : "APK";
}

export function ReleaseCard({ release, compact = false }: { release: ReleaseInfo; compact?: boolean }) {
  return (
    <article className="panel overflow-hidden">
      <div className="grid lg:grid-cols-[1fr_300px]">
        <div className="p-6 md:p-8">
          <div className="flex flex-wrap items-center gap-2">
            <span className="chip"><span className="status-dot" aria-hidden="true" /> Stable</span>
            <span className="chip">Build {release.versionCode}</span>
            <span className="chip">{release.minimumAndroid}+</span>
          </div>

          <div className="mt-7 flex flex-col justify-between gap-6 md:flex-row md:items-end">
            <div>
              <p className="eyebrow">Production release</p>
              <h2 className="mt-2 text-4xl font-black tracking-[-0.055em] md:text-6xl">NowTuneUp {release.version}</h2>
              <p className="muted mt-3 text-sm">{release.apkName} · {bytes(release.apkSize)}</p>
            </div>
            <div className="technical-card min-w-44 px-4">
              <div className="spec-row"><span className="spec-key">Channel</span><span className="spec-value status-good">Stable</span></div>
              <div className="spec-row"><span className="spec-key">Package</span><span className="spec-value">APK</span></div>
            </div>
          </div>

          {!compact && <p className="mt-6 max-w-3xl leading-7 text-[#c9ccc0]">{release.releaseNotes}</p>}
        </div>

        <div className="telemetry-grid flex flex-col justify-center gap-3 border-t border-white/8 bg-black/15 p-6 lg:border-l lg:border-t-0 md:p-8">
          <p className="data-label">Official Android package</p>
          <Link className="button mt-2" href={release.downloadUrl} download aria-label={`Download NowTuneUp ${release.version} APK`}>
            Download APK
          </Link>
          <Link className="button secondary" href={`/releases/${encodeURIComponent(release.version)}`}>
            Release details
          </Link>
          <p className="muted mt-1 text-xs leading-5">Verify build facts and SHA-256 on the official download page.</p>
        </div>
      </div>
    </article>
  );
}
