import type { Metadata } from "next";
import { localRelease } from "@/lib/local-release";
import { ReleaseCard } from "@/components/ui/ReleaseCard";

export const metadata: Metadata = {
  title: "Release history",
  description: "NowTuneUp Android release history, current production build and milestone highlights.",
};

const history = [
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
    description: "Introduced the dedicated Time Slip timing architecture and performance sampling groundwork before the 1.8.1 stability simplification.",
  },
];

export default function Releases() {
  return (
    <>
      <section className="shell section-space">
        <p className="eyebrow">Release history</p>
        <h1 className="mt-4 max-w-5xl text-5xl font-black leading-[.96] tracking-[-0.055em] md:text-7xl">Production builds, with the important changes up front.</h1>
        <p className="muted mt-6 max-w-3xl text-lg leading-8">The latest signed APK is the canonical download. Previous milestones remain listed here to make the product direction and major stability changes easy to follow.</p>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <ReleaseCard release={localRelease} />
        </div>
      </section>

      <section className="section-rule">
        <div className="shell section-space">
          <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end">
            <div><p className="eyebrow">Earlier milestones</p><h2 className="mt-4 text-4xl font-black tracking-[-0.045em] md:text-6xl">How NowTuneUp got here.</h2></div>
            <p className="muted max-w-md text-sm leading-6">Historical entries summarize product milestones. The official download button always points to the current production APK.</p>
          </div>

          <div className="mt-10 grid gap-4">
            {history.map((release, index) => (
              <article key={release.version} className="panel grid gap-6 p-6 md:grid-cols-[160px_1fr] md:p-8">
                <div>
                  <span className="text-xs font-black text-cyan-300">0{index + 2}</span>
                  <p className="mt-4 text-3xl font-black tracking-[-0.05em]">{release.version}</p>
                  <p className="muted mt-2 text-sm">{release.date}</p>
                </div>
                <div>
                  <p className="eyebrow">{release.label}</p>
                  <p className="muted mt-4 max-w-3xl leading-7">{release.description}</p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="shell section-space">
        <div className="panel-soft flex flex-col justify-between gap-4 p-6 md:flex-row md:items-center">
          <div><strong>Release verification</strong><p className="muted mt-1 text-sm">The current download page publishes the production APK size, build number and SHA-256 checksum.</p></div>
          <a href="/download" className="button">Verify current APK</a>
        </div>
      </section>
    </>
  );
}
