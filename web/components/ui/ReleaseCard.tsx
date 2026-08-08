import Link from "next/link";
import type { ReleaseInfo } from "@/lib/validation/releases";

export function bytes(size: number) {
  return size > 0 ? `${(size / 1024 / 1024).toFixed(1)} MB` : "APK";
}

export function ReleaseCard({ release, compact = false }: { release: ReleaseInfo; compact?: boolean }) {
  return (
    <article className="panel overflow-hidden">
      <div className="grid gap-0 lg:grid-cols-[1fr_auto]">
        <div className="p-6 md:p-8">
          <div className="flex flex-wrap items-center gap-2">
            <span className="chip"><span className="status-dot" aria-hidden="true" /> Stable</span>
            <span className="chip">Build {release.versionCode}</span>
            <span className="chip">{release.minimumAndroid}+</span>
          </div>
          <p className="eyebrow mt-6">Latest production release</p>
          <h2 className="mt-2 text-4xl font-black tracking-[-0.045em] md:text-5xl">NowTuneUp {release.version}</h2>
          <p className="muted mt-3">{release.apkName} · {bytes(release.apkSize)}</p>
          {!compact && <p className="mt-5 max-w-3xl leading-7 text-slate-300">{release.releaseNotes}</p>}
        </div>

        <div className="flex min-w-64 flex-col justify-center gap-3 border-t border-white/8 bg-white/[0.018] p-6 lg:border-l lg:border-t-0 md:p-8">
          <Link className="button" href={release.downloadUrl} download aria-label={`Download NowTuneUp ${release.version} APK`}>
            Download APK
          </Link>
          <Link className="button secondary" href={`/releases/${encodeURIComponent(release.version)}`}>
            Release details
          </Link>
          <p className="mt-1 text-center text-xs text-slate-500">Official signed Android package</p>
        </div>
      </div>
    </article>
  );
}
