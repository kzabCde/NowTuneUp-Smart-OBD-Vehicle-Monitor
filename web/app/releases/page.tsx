import type { Metadata } from "next";
import Link from "next/link";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

export const metadata: Metadata = {
  title: "Release history",
  description: "NowTuneUp Android release history, current production build and milestone highlights.",
};

const history = [
  {
    version: "1.13.0",
    label: "Vehicle intelligence consolidation",
    date: "September 7, 2026",
    description: "Consolidated adaptive adapter profiles and health history, Live Data recording and summaries, Diagnostics v2 history and optional Mode 06 monitoring, plus OBD-only Time Slip v2 quality scoring.",
  },
  {
    version: "1.9.0",
    label: "Adaptive stability & vehicle intelligence",
    date: "August 2026",
    description: "Expanded adaptive connection behavior and vehicle intelligence foundations that later releases built on for diagnostics, telemetry quality and session context.",
  },
  {
    version: "1.8.1",
    label: "Stability & UX hotfix",
    date: "August 4, 2026",
    description: "Stabilized realtime polling and Turbo Pressure, repaired the OBD-only Time Slip flow, removed duplicate Peak presentation and simplified Settings and Live Data.",
  },
  {
    version: "1.8.0",
    label: "Accurate Time Slip",
    date: "August 4, 2026",
    description: "Introduced the dedicated Time Slip timing architecture and performance sampling groundwork before the later stability and quality improvements.",
  },
];

export default function Releases() {
  return (
    <>
      <section className="page-hero">
        <div className="shell">
          <div className="flex flex-wrap gap-2"><span className="chip"><span className="status-dot" aria-hidden="true" /> Current {localRelease.version}</span><span className="chip">Build {localRelease.versionCode}</span></div>
          <p className="eyebrow mt-8">Release history</p>
          <h1 className="page-title mt-4">Production first. History second.</h1>
          <p className="page-lede mt-7">The current signed APK stays visually dominant. Earlier milestones remain available so stability changes and product direction are easy to trace without turning this page into a raw changelog.</p>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="section-heading mb-8"><p className="eyebrow">Current production</p><h2 className="section-title">The build to install now.</h2></div>
          <ReleaseCard release={localRelease} />
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space grid gap-10 lg:grid-cols-[.72fr_1.28fr]">
          <div className="section-heading lg:sticky lg:top-28 lg:self-start">
            <p className="eyebrow">Earlier milestones</p>
            <h2 className="section-title">How the current architecture got here.</h2>
            <p className="section-copy">Historical entries summarize meaningful product milestones. The official download always points to the current production APK.</p>
          </div>

          <div className="release-rail grid gap-10">
            {history.map((release) => (
              <article key={release.version} className="release-dot">
                <div className="flex flex-wrap items-center gap-2"><span className="chip">v{release.version}</span><span className="data-label">{release.date}</span></div>
                <p className="eyebrow mt-5">{release.label}</p>
                <h3 className="mt-3 text-3xl font-black tracking-[-0.045em]">NowTuneUp {release.version}</h3>
                <p className="muted mt-4 max-w-3xl leading-7">{release.description}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="panel grid gap-6 p-7 md:grid-cols-[1fr_auto] md:items-center md:p-9">
            <div>
              <p className="eyebrow">Release verification</p>
              <h2 className="mt-3 text-3xl font-black tracking-[-0.045em]">Version, build, file size and checksum in one place.</h2>
              <p className="muted mt-3 max-w-2xl leading-7">Use the download page when you need to verify the production APK rather than relying on a filename alone.</p>
            </div>
            <Link href="/download" className="button">Verify current APK</Link>
          </div>
        </div>
      </section>
    </>
  );
}
